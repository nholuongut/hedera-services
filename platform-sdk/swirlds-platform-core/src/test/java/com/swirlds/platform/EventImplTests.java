/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import static com.swirlds.platform.consensus.ConsensusConstants.MIN_TRANS_TIMESTAMP_INCR_NANOS;
import static com.swirlds.platform.test.fixtures.event.EventImplTestUtils.createEventImpl;
import static com.swirlds.platform.util.PayloadUtils.isSystemPayload;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.platform.event.EventPayload.PayloadOneOfType;
import com.hedera.pbj.runtime.OneOf;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import com.swirlds.platform.system.transaction.Transaction;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import com.swirlds.platform.test.fixtures.event.TransactionUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class EventImplTests {
    private Random random;

    @BeforeEach
    void setUp() {
        random = RandomUtils.getRandomPrintSeed();
    }

    @Test
    @DisplayName("consensusTransactionIterator() does not iterate system transactions")
    void testConsensusTransactionIterator() {
        final TransactionData data = mixedTransactions();

        final EventImpl event =
                createEventImpl(new TestingEventBuilder(random).setTransactions(data.transactions()), null, null);

        final Iterator<ConsensusTransaction> iter = event.consensusTransactionIterator();
        final Set<OneOf<PayloadOneOfType>> transactionSet = new HashSet<>();
        while (iter.hasNext()) {
            transactionSet.add(iter.next().getPayload());
        }

        verifyTransactionIterator(data, transactionSet);
        assertEquals(
                data.transactions.size() - data.systemIndices.size(),
                event.getNumAppTransactions(),
                "The number of application transactions is incorrect.");
    }

    @Test
    @DisplayName("transactionIterator() does not iterate system transactions")
    void testTransactionIterator() {
        final TransactionData data = mixedTransactions();

        final EventImpl event =
                createEventImpl(new TestingEventBuilder(random).setTransactions(data.transactions()), null, null);

        final Iterator<Transaction> iter = event.transactionIterator();

        final Set<OneOf<PayloadOneOfType>> transactionSet = new HashSet<>();
        while (iter.hasNext()) {
            transactionSet.add(iter.next().getPayload());
        }

        verifyTransactionIterator(data, transactionSet);
        assertEquals(
                data.transactions.size() - data.systemIndices.size(),
                event.getNumAppTransactions(),
                "The number of application transactions is incorrect.");
    }

    private void verifyTransactionIterator(
            final TransactionData data, final Set<OneOf<PayloadOneOfType>> transactionSet) {
        for (int i = 0; i < data.transactions.size(); i++) {
            final OneOf<PayloadOneOfType> t = data.transactions.get(i);
            final boolean isSystem = isSystemPayload(t);
            if (data.systemIndices.contains(i)) {
                assertTrue(isSystem, String.format("Transaction at index %d should be iterated", i));
                assertFalse(
                        transactionSet.contains(t),
                        String.format("Iterated transactions should include system transaction %s", t));
            } else {
                assertFalse(isSystem, String.format("Transaction at index %d should not be iterated", i));
                assertTrue(
                        transactionSet.contains(t),
                        String.format("Iterated transactions should not include system transaction %s", t));
            }
        }
    }

    private TransactionData mixedTransactions() {
        final int numTransactions = 10_000;
        final List<OneOf<PayloadOneOfType>> mixedTransactions = new ArrayList<>();
        final List<Integer> systemIndices = new ArrayList<>();

        for (int i = 0; i < numTransactions; i++) {
            if (random.nextBoolean()) {
                mixedTransactions.add(i, TransactionUtils.incrementingSystemTransaction());
                systemIndices.add(i);
            } else {
                mixedTransactions.add(i, TransactionUtils.incrementingSwirldTransaction());
            }
        }
        return new TransactionData(mixedTransactions, systemIndices);
    }

    @Test
    @DisplayName("consensusReached() propagates consensus data to all transactions")
    void testConsensusReached() {
        final List<OneOf<PayloadOneOfType>> mixedTransactions = List.of(
                TransactionUtils.incrementingSystemTransaction(),
                TransactionUtils.incrementingSwirldTransaction(),
                TransactionUtils.incrementingSystemTransaction(),
                TransactionUtils.incrementingSwirldTransaction(),
                TransactionUtils.incrementingSystemTransaction());

        final Instant eventConsTime = Instant.now();
        final EventImpl event = createEventImpl(
                new TestingEventBuilder(random)
                        .setTransactions(mixedTransactions)
                        .setConsensusTimestamp(eventConsTime),
                null,
                null);

        event.getBaseEvent().setConsensusTimestampsOnPayloads();

        final ConsensusTransactionImpl[] transactions = event.getTransactions();
        for (int i = 0; i < transactions.length; i++) {
            final Instant transConsTime = eventConsTime.plusNanos(i * MIN_TRANS_TIMESTAMP_INCR_NANOS);
            assertEquals(
                    transConsTime,
                    transactions[i].getConsensusTimestamp(),
                    "Consensus timestamp does not match the expected value based on transaction index and event "
                            + "consensus time.");
        }
    }

    private record TransactionData(List<OneOf<PayloadOneOfType>> transactions, List<Integer> systemIndices) {}
}
