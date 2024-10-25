/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.fixtures.event;

import com.hedera.hapi.platform.event.EventPayload.PayloadOneOfType;
import com.hedera.pbj.runtime.OneOf;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.event.hashing.StatefulEventHasher;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.events.EventDescriptor;
import com.swirlds.platform.system.events.UnsignedEvent;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

public class RandomEventUtils {
    public static final Instant DEFAULT_FIRST_EVENT_TIME_CREATED = Instant.ofEpochMilli(1588771316678L);

    /**
     * Similar to randomEvent, but the timestamp used for the event's creation timestamp
     * is provided by an argument.
     */
    public static IndexedEvent randomEventWithTimestamp(
            final Random random,
            final NodeId creatorId,
            final Instant timestamp,
            final long birthRound,
            final ConsensusTransactionImpl[] transactions,
            final EventImpl selfParent,
            final EventImpl otherParent,
            final boolean fakeHash) {

        final UnsignedEvent unsignedEvent = randomUnsignedEventWithTimestamp(
                random, creatorId, timestamp, birthRound, transactions, selfParent, otherParent, fakeHash);

        final byte[] sig = new byte[SignatureType.RSA.signatureLength()];
        random.nextBytes(sig);

        return new IndexedEvent(new PlatformEvent(unsignedEvent, sig), selfParent, otherParent);
    }

    /**
     * Similar to randomEventHashedData but where the timestamp provided to this
     * method is the timestamp used as the creation timestamp for the event.
     */
    public static UnsignedEvent randomUnsignedEventWithTimestamp(
            @NonNull final Random random,
            @NonNull final NodeId creatorId,
            @NonNull final Instant timestamp,
            final long birthRound,
            @Nullable final ConsensusTransactionImpl[] transactions,
            @Nullable final EventImpl selfParent,
            @Nullable final EventImpl otherParent,
            final boolean fakeHash) {

        final EventDescriptor selfDescriptor = (selfParent == null || selfParent.getBaseHash() == null)
                ? null
                : new EventDescriptor(
                        selfParent.getBaseHash(),
                        selfParent.getCreatorId(),
                        selfParent.getGeneration(),
                        selfParent.getBaseEvent().getBirthRound());
        final EventDescriptor otherDescriptor = (otherParent == null || otherParent.getBaseHash() == null)
                ? null
                : new EventDescriptor(
                        otherParent.getBaseHash(),
                        otherParent.getCreatorId(),
                        otherParent.getGeneration(),
                        otherParent.getBaseEvent().getBirthRound());

        final List<OneOf<PayloadOneOfType>> convertedTransactions = new ArrayList<>();
        if (transactions != null) {
            Stream.of(transactions)
                    .map(ConsensusTransactionImpl::getPayload)
                    .map(one -> new OneOf<>(PayloadOneOfType.APPLICATION_PAYLOAD, one.as()))
                    .forEach(convertedTransactions::add);
        }
        final UnsignedEvent unsignedEvent = new UnsignedEvent(
                new BasicSoftwareVersion(1),
                creatorId,
                selfDescriptor,
                otherDescriptor == null ? Collections.emptyList() : Collections.singletonList(otherDescriptor),
                birthRound,
                timestamp,
                convertedTransactions);

        if (fakeHash) {
            unsignedEvent.setHash(RandomUtils.randomHash(random));
        } else {
            new StatefulEventHasher().hashEvent(unsignedEvent);
        }
        return unsignedEvent;
    }
}
