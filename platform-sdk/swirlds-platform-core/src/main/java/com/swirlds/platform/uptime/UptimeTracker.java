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

package com.swirlds.platform.uptime;

import static com.swirlds.common.units.TimeUnit.UNIT_MICROSECONDS;
import static com.swirlds.common.units.TimeUnit.UNIT_NANOSECONDS;
import static com.swirlds.platform.system.UptimeData.NO_ROUND;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.utility.CompareTo;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.status.StatusActionSubmitter;
import com.swirlds.platform.system.status.actions.SelfEventReachedConsensusAction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Monitors the uptime of nodes in the network.
 */
public class UptimeTracker {

    private final NodeId selfId;
    private final Time time;
    private final AddressBook addressBook;

    private final UptimeMetrics uptimeMetrics;
    private final Duration degradationThreshold;

    /**
     * The last consensus time a self event was created.
     */
    private final AtomicReference<Instant> lastSelfEventTime = new AtomicReference<>();

    /**
     * Enables submitting platform status actions.
     */
    private final StatusActionSubmitter statusActionSubmitter;

    /**
     * Construct a new uptime detector.
     *
     * @param platformContext       the platform context
     * @param addressBook           the address book
     * @param statusActionSubmitter enables submitting platform status actions
     * @param selfId                the ID of this node
     * @param time                  a source of time
     */
    public UptimeTracker(
            @NonNull PlatformContext platformContext,
            @NonNull final AddressBook addressBook,
            @NonNull final StatusActionSubmitter statusActionSubmitter,
            @NonNull final NodeId selfId,
            @NonNull final Time time) {

        this.selfId = Objects.requireNonNull(selfId, "selfId must not be null");
        this.time = Objects.requireNonNull(time);
        this.addressBook = Objects.requireNonNull(addressBook);
        this.statusActionSubmitter = Objects.requireNonNull(statusActionSubmitter);
        this.degradationThreshold = platformContext
                .getConfiguration()
                .getConfigData(UptimeConfig.class)
                .degradationThreshold();
        this.uptimeMetrics = new UptimeMetrics(platformContext.getMetrics(), addressBook, this::isSelfDegraded);
    }

    /**
     * Look at the events in a round to determine which nodes are up and which nodes are down.
     *
     * @param round       the round to analyze
     * @param uptimeData  the uptime data that is in the current round's state, is modified by this method
     * @param addressBook the address book for this round
     */
    public void handleRound(
            @NonNull final ConsensusRound round,
            @NonNull final MutableUptimeData uptimeData,
            @NonNull final AddressBook addressBook) {

        if (round.isEmpty()) {
            return;
        }

        final Instant start = time.now();

        addAndRemoveNodes(uptimeData, addressBook);
        final Map<NodeId, ConsensusEvent> lastEventsInRoundByCreator = new HashMap<>();
        final Map<NodeId, ConsensusEvent> judgesByCreator = new HashMap<>();
        scanRound(round, lastEventsInRoundByCreator, judgesByCreator);
        updateState(addressBook, uptimeData, lastEventsInRoundByCreator, judgesByCreator, round.getRoundNum());
        reportUptime(uptimeData, round.getConsensusTimestamp(), round.getRoundNum());

        final Instant end = time.now();
        final Duration elapsed = Duration.between(start, end);
        uptimeMetrics
                .getUptimeComputationTimeMetric()
                .update(UNIT_NANOSECONDS.convertTo(elapsed.toNanos(), UNIT_MICROSECONDS));
    }

    /**
     * Add and remove nodes as necessary. Will only make changes if address book membership in this round is different
     * from the address book in the previous round, or at genesis.
     *
     * @param uptimeData  the uptime data
     * @param addressBook the current address book
     */
    private void addAndRemoveNodes(
            @NonNull final MutableUptimeData uptimeData, @NonNull final AddressBook addressBook) {
        final Set<NodeId> addressBookNodes = addressBook.getNodeIdSet();
        final Set<NodeId> trackedNodes = uptimeData.getTrackedNodes();
        for (final NodeId nodeId : addressBookNodes) {
            if (!trackedNodes.contains(nodeId)) {
                // node was added
                uptimeMetrics.addMetricsForNode(nodeId);
                uptimeData.addNode(nodeId);
            }
        }
        for (final NodeId nodeId : trackedNodes) {
            if (!addressBookNodes.contains(nodeId)) {
                // node was removed
                uptimeMetrics.removeMetricsForNode(nodeId);
                uptimeData.removeNode(nodeId);
            }
        }
    }

    /**
     * Check if this node should consider itself to be degraded.
     *
     * @return true if this node should consider itself to be degraded
     */
    public boolean isSelfDegraded() {
        final Instant eventTime = lastSelfEventTime.get();
        if (eventTime == null) {
            // Consider a node to be degraded until it has its first event reach consensus.
            return true;
        }

        final Instant now = time.now();
        final Duration durationSinceLastEvent = Duration.between(eventTime, now);
        return CompareTo.isGreaterThan(durationSinceLastEvent, degradationThreshold);
    }

    /**
     * Scan all events in the round.
     *
     * @param round                      the round
     * @param lastEventsInRoundByCreator the last event in the round by creator, is updated by this method
     * @param judgesByCreator            the judges by creator, is updated by this method
     */
    private void scanRound(
            @NonNull final Round round,
            @NonNull final Map<NodeId, ConsensusEvent> lastEventsInRoundByCreator,
            @NonNull final Map<NodeId, ConsensusEvent> judgesByCreator) {

        // capture previous self event consensus timestamp, so we can tell if the current round contains a
        // new self event
        final Instant previousSelfEventConsensusTimestamp = lastSelfEventTime.get();

        round.forEach(event -> {
            lastEventsInRoundByCreator.put(event.getCreatorId(), event);
            // Temporarily disabled until we properly detect judges in a round
            //            if (((EventImpl) event).isFamous()) {
            //                judgesByCreator.put(event.getCreatorId(), event);
            //            }
        });

        final ConsensusEvent lastSelfEvent = lastEventsInRoundByCreator.get(selfId);
        if (lastSelfEvent != null) {
            final Instant lastSelfEventConsensusTimestamp = lastSelfEvent.getConsensusTimestamp();
            if (!lastSelfEventConsensusTimestamp.equals(previousSelfEventConsensusTimestamp)) {
                lastSelfEventTime.set(lastSelfEventConsensusTimestamp);

                // the action receives the wall clock time, NOT the consensus timestamp
                statusActionSubmitter.submitStatusAction(new SelfEventReachedConsensusAction(time.now()));
            }
        }
    }

    /**
     * Update the uptime data based on the events in this round.
     *
     * @param addressBook                the current address book
     * @param uptimeData                 the uptime data to be updated
     * @param lastEventsInRoundByCreator the last event in the round by creator
     * @param judgesByCreator            the judges by creator
     * @param roundNum                   the round number
     */
    private void updateState(
            @NonNull final AddressBook addressBook,
            @NonNull final MutableUptimeData uptimeData,
            @NonNull final Map<NodeId, ConsensusEvent> lastEventsInRoundByCreator,
            @NonNull final Map<NodeId, ConsensusEvent> judgesByCreator,
            final long roundNum) {

        for (final Address address : addressBook) {
            final ConsensusEvent lastEvent = lastEventsInRoundByCreator.get(address.getNodeId());
            if (lastEvent != null) {
                uptimeData.recordLastEvent(lastEvent, roundNum);
            }

            // Temporarily disabled until we properly detect judges in a round
            //            final ConsensusEvent judge = judgesByCreator.get(address.getNodeId());
            //            if (judge != null) {
            //                uptimeData.recordLastJudge((EventImpl) judge);
            //            }
        }
    }

    /**
     * Report the uptime data.
     *
     * @param uptimeData the uptime data
     */
    private void reportUptime(
            @NonNull final MutableUptimeData uptimeData,
            @NonNull final Instant lastRoundEndTime,
            final long currentRound) {

        long nonDegradedConsensusWeight = 0;

        for (final Address address : addressBook) {
            final NodeId id = address.getNodeId();

            final Instant lastConsensusEventTime = uptimeData.getLastEventTime(id);
            if (lastConsensusEventTime != null) {
                final Duration timeSinceLastConsensusEvent = Duration.between(lastConsensusEventTime, lastRoundEndTime);

                if (CompareTo.isLessThanOrEqualTo(timeSinceLastConsensusEvent, degradationThreshold)) {
                    nonDegradedConsensusWeight += addressBook.getAddress(id).getWeight();
                }
            }

            final long lastEventRound = uptimeData.getLastEventRound(id);
            if (lastEventRound != NO_ROUND) {
                uptimeMetrics.getRoundsSinceLastConsensusEventMetric(id).update(currentRound - lastEventRound);
            }

            // Temporarily disabled until we properly detect judges in a round
            //            final long lastJudgeRound = uptimeData.getLastJudgeRound(id);
            //            if (lastJudgeRound != NO_ROUND) {
            //                uptimeMetrics.getRoundsSinceLastJudgeMetric(id).update(currentRound - lastJudgeRound);
            //            }
        }

        final double fractionOfNetworkAlive = (double) nonDegradedConsensusWeight / addressBook.getTotalWeight();
        uptimeMetrics.getHealthyNetworkFraction().update(fractionOfNetworkAlive);
    }
}
