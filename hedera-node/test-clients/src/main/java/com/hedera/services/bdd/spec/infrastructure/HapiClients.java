/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.bdd.spec.infrastructure;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.junit.hedera.HederaNetwork;
import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.props.NodeConnectInfo;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.service.proto.java.AddressBookServiceGrpc.AddressBookServiceBlockingStub;
import com.hederahashgraph.service.proto.java.ConsensusServiceGrpc.ConsensusServiceBlockingStub;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc.CryptoServiceBlockingStub;
import com.hederahashgraph.service.proto.java.FileServiceGrpc.FileServiceBlockingStub;
import com.hederahashgraph.service.proto.java.FreezeServiceGrpc.FreezeServiceBlockingStub;
import com.hederahashgraph.service.proto.java.NetworkServiceGrpc.NetworkServiceBlockingStub;
import com.hederahashgraph.service.proto.java.ScheduleServiceGrpc.ScheduleServiceBlockingStub;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc.SmartContractServiceBlockingStub;
import com.hederahashgraph.service.proto.java.TokenServiceGrpc.TokenServiceBlockingStub;
import com.hederahashgraph.service.proto.java.UtilServiceGrpc;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiClients {
    static final Logger log = LogManager.getLogger(HapiClients.class);
    // The deadline for the server to respond to a blocking unary call
    private static final long DEADLINE_SECS = 30L;

    private final List<NodeConnectInfo> nodes;
    /**
     * Id of node-{host, port} pairs to use for non-workflow operations
     */
    private final Map<AccountID, String> stubIds;
    /**
     * Id of node-{host, port} pairs to use for non-workflow operations using TLS ports
     */
    private final Map<AccountID, String> tlsStubIds;

    /**
     * Maps from a node connection URI to a pool of {@link ChannelStubs} that have been created for that URI.
     * Many specs can run in parallel against the same nodes, so we use a concurrent map and copy-on-write
     * lists to avoid concurrent modification exceptions.
     *
     * <p>When a {@link HapiSpec} asks for stubs to a particular node's services, we return one of
     * the channels in the pool via round-robin selection.
     */
    private static final Map<String, List<ChannelStubs>> channelPools = new ConcurrentSkipListMap<>();
    /**
     * Maps from a channel stub ID (a URI string) to a sequence counter.
     * This map is used for selecting channels from the channel pool for that stub ID, supporting round-robin
     * channel stub selection, which minimizes the probability of a channel being used simultaneously.
     * This helps to work around issues in the GRPC client code.
     */
    private static final Map<String, AtomicInteger> stubSequences = new ConcurrentSkipListMap<>();

    private static final Function<String, List<ChannelStubs>> COPY_ON_WRITE_LIST_SUPPLIER =
            ignore -> new CopyOnWriteArrayList<>();

    /**
     * Given a specific node connection URI, we continue creating new {@link ChannelStubs} as new {@link HapiSpec}'s
     * initialize until we have reached thd number.
     */
    private static final int MAX_DESIRED_CHANNELS_PER_NODE = 50;

    private static final long MINIMUM_REBUILD_INTERVAL_MS = 30_000L;
    private static final AtomicReference<Instant> LAST_CHANNEL_REBUILD_TIME = new AtomicReference<>();

    private static ManagedChannel createNettyChannel(
            boolean useTls, final String host, final int port, final int tlsPort) {
        try {
            ManagedChannel channel;

            if (useTls) {
                String[] protocols = new String[] {"TLSv1.2", "TLSv1.3"};
                List<String> ciphers = Arrays.asList(
                        "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
                        "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                        "TLS_AES_256_GCM_SHA384");
                SslContextBuilder contextBuilder = GrpcSslContexts.configure(SslContextBuilder.forClient());
                contextBuilder.protocols(protocols).ciphers(ciphers, SupportedCipherSuiteFilter.INSTANCE);

                channel = NettyChannelBuilder.forAddress(host, tlsPort)
                        .negotiationType(NegotiationType.TLS)
                        .sslContext(contextBuilder.build())
                        .overrideAuthority("127.0.0.1")
                        .build();
            } else {
                channel = NettyChannelBuilder.forAddress(host, port)
                        .usePlaintext()
                        .build();
            }
            return channel;
        } catch (Exception e) {
            log.error("Error creating Netty channel", e);
        }
        return null;
    }

    private void ensureChannelStubsInPool(@NonNull final NodeConnectInfo node, final boolean useTls) {
        requireNonNull(node);
        final var channelUri = useTls ? node.tlsUri() : node.uri();
        final var existingPool = channelPools.computeIfAbsent(channelUri, COPY_ON_WRITE_LIST_SUPPLIER);
        if (existingPool.size() < MAX_DESIRED_CHANNELS_PER_NODE) {
            final var channel = createNettyChannel(useTls, node.getHost(), node.getPort(), node.getTlsPort());
            requireNonNull(channel, "Cannot continue without Netty channel");
            existingPool.add(ChannelStubs.from(channel, node, useTls));
        }
        stubSequences.putIfAbsent(channelUri, new AtomicInteger());
    }

    private HapiClients(final List<NodeConnectInfo> nodes) {
        this.nodes = nodes;
        stubIds = nodes.stream().collect(Collectors.toMap(NodeConnectInfo::getAccount, NodeConnectInfo::uri));
        tlsStubIds = nodes.stream().collect(Collectors.toMap(NodeConnectInfo::getAccount, NodeConnectInfo::tlsUri));
        nodes.forEach(node -> {
            ensureChannelStubsInPool(node, false);
            ensureChannelStubsInPool(node, true);
        });
    }

    public static HapiClients clientsFor(HapiSpecSetup setup) {
        return new HapiClients(setup.nodes());
    }

    public static HapiClients clientsFor(@NonNull final HederaNetwork network) {
        requireNonNull(network);
        return new HapiClients(network.nodes().stream()
                .map(HederaNode::hapiSpecInfo)
                .map(NodeConnectInfo::new)
                .toList());
    }

    public FileServiceBlockingStub getFileSvcStub(AccountID nodeId, boolean useTls) {
        return nextStubsFromPool(stubId(nodeId, useTls))
                .fileSvcStubs()
                .withDeadlineAfter(DEADLINE_SECS, TimeUnit.SECONDS);
    }

    public TokenServiceBlockingStub getTokenSvcStub(AccountID nodeId, boolean useTls) {
        return nextStubsFromPool(stubId(nodeId, useTls))
                .tokenSvcStubs()
                .withDeadlineAfter(DEADLINE_SECS, TimeUnit.SECONDS);
    }

    public AddressBookServiceBlockingStub getAddressBookSvcStub(AccountID nodeId, boolean useTls) {
        return nextStubsFromPool(stubId(nodeId, useTls))
                .addressBookSvcStubs()
                .withDeadlineAfter(DEADLINE_SECS, TimeUnit.SECONDS);
    }

    public CryptoServiceBlockingStub getCryptoSvcStub(AccountID nodeId, boolean useTls) {
        return nextStubsFromPool(stubId(nodeId, useTls))
                .cryptoSvcStubs()
                .withDeadlineAfter(DEADLINE_SECS, TimeUnit.SECONDS);
    }

    public FreezeServiceBlockingStub getFreezeSvcStub(AccountID nodeId, boolean useTls) {
        return nextStubsFromPool(stubId(nodeId, useTls))
                .freezeSvcStubs()
                .withDeadlineAfter(DEADLINE_SECS, TimeUnit.SECONDS);
    }

    public SmartContractServiceBlockingStub getScSvcStub(AccountID nodeId, boolean useTls) {
        return nextStubsFromPool(stubId(nodeId, useTls))
                .scSvcStubs()
                .withDeadlineAfter(DEADLINE_SECS, TimeUnit.SECONDS);
    }

    public ConsensusServiceBlockingStub getConsSvcStub(AccountID nodeId, boolean useTls) {
        return nextStubsFromPool(stubId(nodeId, useTls))
                .consSvcStubs()
                .withDeadlineAfter(DEADLINE_SECS, TimeUnit.SECONDS);
    }

    public NetworkServiceBlockingStub getNetworkSvcStub(AccountID nodeId, boolean useTls) {
        return nextStubsFromPool(stubId(nodeId, useTls))
                .networkSvcStubs()
                .withDeadlineAfter(DEADLINE_SECS, TimeUnit.SECONDS);
    }

    public ScheduleServiceBlockingStub getScheduleSvcStub(AccountID nodeId, boolean useTls) {
        return nextStubsFromPool(stubId(nodeId, useTls))
                .scheduleSvcStubs()
                .withDeadlineAfter(DEADLINE_SECS, TimeUnit.SECONDS);
    }

    public UtilServiceGrpc.UtilServiceBlockingStub getUtilSvcStub(AccountID nodeId, boolean useTls) {
        return nextStubsFromPool(stubId(nodeId, useTls))
                .utilSvcStubs()
                .withDeadlineAfter(DEADLINE_SECS, TimeUnit.SECONDS);
    }

    private String stubId(AccountID nodeId, boolean useTls) {
        return useTls ? tlsStubIds.get(nodeId) : stubIds.get(nodeId);
    }

    private static synchronized ChannelStubs nextStubsFromPool(@NonNull final String stubId) {
        requireNonNull(stubId);
        final List<ChannelStubs> stubs = channelPools.get(stubId);
        if (stubs == null || stubs.isEmpty()) {
            throw new IllegalArgumentException("Should have ensured at least one channel in pool");
        }
        final AtomicInteger currentSequence = stubSequences.get(stubId);
        if (currentSequence == null) {
            throw new IllegalArgumentException("Should have assigned a sequence counter to the pool");
        }
        return stubs.get(currentSequence.getAndIncrement() % stubs.size());
    }

    @Override
    public String toString() {
        MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this);
        for (int i = 0; i < nodes.size(); i++) {
            helper.add(String.format("node%d", i), nodes.get(i).toString());
        }
        return helper.toString();
    }

    public static synchronized void rebuildChannels() {
        final var maybeLastRebuildTime = LAST_CHANNEL_REBUILD_TIME.get();
        if (maybeLastRebuildTime != null) {
            final var msSinceLastRebuild = System.currentTimeMillis() - maybeLastRebuildTime.toEpochMilli();
            if (msSinceLastRebuild < MINIMUM_REBUILD_INTERVAL_MS) {
                return;
            }
        }
        log.info("Shutting down all managed channels");
        shutdownChannels();
        final var allUris = new ArrayList<>(channelPools.keySet());
        allUris.forEach(uri -> {
            final var closedChannels = channelPools.get(uri);
            final var reopenedChannels = rebuiltChannelsLike(closedChannels);
            log.info("Reopened {} channels for {}", reopenedChannels.size(), uri);
            channelPools.put(uri, reopenedChannels);
        });
        LAST_CHANNEL_REBUILD_TIME.set(Instant.now());
        log.info("Finished rebuilding channels at {}", LAST_CHANNEL_REBUILD_TIME.get());
    }

    private static List<ChannelStubs> rebuiltChannelsLike(@NonNull final List<ChannelStubs> channelStubs) {
        final List<ChannelStubs> newChannelStubs = new CopyOnWriteArrayList<>();
        channelStubs.forEach(channelStub -> {
            final var node = channelStub.nodeConnectInfo();
            final var useTls = channelStub.useTls();
            final var channel =
                    requireNonNull(createNettyChannel(useTls, node.getHost(), node.getPort(), node.getTlsPort()));
            newChannelStubs.add(ChannelStubs.from(channel, node, useTls));
        });
        return newChannelStubs;
    }

    /** Close all netty channels that are opened for clients */
    private static void closeChannels() {
        if (channelPools.isEmpty()) {
            return;
        }
        shutdownChannels();
        channelPools.clear();
    }

    private static void shutdownChannels() {
        channelPools.forEach((uri, channelPool) -> channelPool.forEach(ChannelStubs::shutdown));
    }

    /**
     * Should only be called once after all tests are done.
     */
    public static void tearDown() {
        closeChannels();
    }
}
