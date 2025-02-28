/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.event.tipset;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static com.swirlds.platform.event.creation.tipset.Tipset.merge;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.swirlds.base.time.Time;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.consensus.ConsensusConstants;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.creation.tipset.Tipset;
import com.swirlds.platform.event.creation.tipset.TipsetTracker;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.EventConstants;
import com.swirlds.platform.system.events.EventDescriptor;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("TipsetTracker Tests")
class TipsetTrackerTests {

    private static void assertTipsetEquality(
            @NonNull final AddressBook addressBook, @NonNull final Tipset expected, @NonNull final Tipset actual) {
        assertEquals(expected.size(), actual.size());

        for (final Address address : addressBook) {
            assertEquals(
                    expected.getTipGenerationForNode(address.getNodeId()),
                    actual.getTipGenerationForNode(address.getNodeId()));
        }
    }

    @ParameterizedTest
    @EnumSource(AncientMode.class)
    @DisplayName("Basic Behavior Test")
    void basicBehaviorTest(final AncientMode ancientMode) {
        final Random random = getRandomPrintSeed(0);

        final int nodeCount = random.nextInt(10, 20);
        final AddressBook addressBook =
                RandomAddressBookBuilder.create(random).withSize(nodeCount).build();

        final Map<NodeId, EventDescriptor> latestEvents = new HashMap<>();
        final Map<EventDescriptor, Tipset> expectedTipsets = new HashMap<>();

        final TipsetTracker tracker = new TipsetTracker(Time.getCurrent(), addressBook, ancientMode);

        long birthRound = ConsensusConstants.ROUND_FIRST;

        for (int eventIndex = 0; eventIndex < 1000; eventIndex++) {

            final NodeId creator = addressBook.getNodeId(random.nextInt(nodeCount));
            final long generation;
            if (latestEvents.containsKey(creator)) {
                generation = latestEvents.get(creator).getGeneration() + 1;
            } else {
                generation = 1;
            }

            birthRound += random.nextLong(0, 3) / 2;

            final EventDescriptor selfParent = latestEvents.get(creator);
            final EventDescriptor fingerprint =
                    new EventDescriptor(randomHash(random), creator, generation, birthRound);
            latestEvents.put(creator, fingerprint);

            // Select some nodes we'd like to be our parents.
            final Set<NodeId> desiredParents = new HashSet<>();
            final int maxParentCount = random.nextInt(nodeCount);
            for (int parentIndex = 0; parentIndex < maxParentCount; parentIndex++) {
                final NodeId parent = addressBook.getNodeId(random.nextInt(nodeCount));

                // We are only trying to generate a random number of parents, the exact count is unimportant.
                // So it doesn't matter if the actual number of parents is less than the number we requested.
                if (parent.equals(creator)) {
                    continue;
                }
                desiredParents.add(parent);
            }

            // Select the actual parents.
            final List<EventDescriptor> parentFingerprints = new ArrayList<>(desiredParents.size());
            if (selfParent != null) {
                parentFingerprints.add(selfParent);
            }
            for (final NodeId parent : desiredParents) {
                final EventDescriptor parentFingerprint = latestEvents.get(parent);
                if (parentFingerprint != null) {
                    parentFingerprints.add(parentFingerprint);
                }
            }

            final Tipset newTipset = tracker.addEvent(fingerprint, parentFingerprints);
            assertSame(newTipset, tracker.getTipset(fingerprint));

            // Now, reconstruct the tipset manually, and make sure it matches what we were expecting.
            final List<Tipset> parentTipsets = new ArrayList<>(parentFingerprints.size());
            for (final EventDescriptor parentFingerprint : parentFingerprints) {
                parentTipsets.add(expectedTipsets.get(parentFingerprint));
            }

            final Tipset expectedTipset;
            if (parentTipsets.isEmpty()) {
                expectedTipset = new Tipset(addressBook).advance(creator, generation);
            } else {
                expectedTipset = merge(parentTipsets).advance(creator, generation);
            }

            expectedTipsets.put(fingerprint, expectedTipset);
            assertTipsetEquality(addressBook, expectedTipset, newTipset);
        }

        // At the very end, we shouldn't see any modified tipsets
        for (final EventDescriptor fingerprint : expectedTipsets.keySet()) {
            assertTipsetEquality(addressBook, expectedTipsets.get(fingerprint), tracker.getTipset(fingerprint));
        }

        // Slowly advance the ancient threshold, we should see tipsets disappear as we go.
        long ancientThreshold = ancientMode == AncientMode.BIRTH_ROUND_THRESHOLD
                ? ConsensusConstants.ROUND_FIRST
                : EventConstants.FIRST_GENERATION;
        while (tracker.size() > 0) {
            ancientThreshold += random.nextInt(1, 5);
            final EventWindow eventWindow =
                    new EventWindow(1, ancientThreshold, 1 /* ignored in this context */, ancientMode);
            tracker.setEventWindow(eventWindow);
            assertEquals(eventWindow, tracker.getEventWindow());
            for (final EventDescriptor fingerprint : expectedTipsets.keySet()) {
                if (fingerprint.getAncientIndicator(ancientMode) < ancientThreshold) {
                    assertNull(tracker.getTipset(fingerprint));
                } else {
                    assertTipsetEquality(addressBook, expectedTipsets.get(fingerprint), tracker.getTipset(fingerprint));
                }
            }
        }
    }
}
