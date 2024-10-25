/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state;

import static com.swirlds.common.merkle.utility.MerkleUtils.rehashTree;
import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static com.swirlds.common.test.fixtures.RandomUtils.randomInstant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.BasicSoftwareVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BirthRoundStateMigrationTests {

    @NonNull
    private SignedState generateSignedState(
            @NonNull final Random random, @NonNull final PlatformContext platformContext) {

        final long round = random.nextLong(1, 1_000_000);

        final List<Hash> judgeHashes = new ArrayList<>();
        final int judgeHashCount = random.nextInt(5, 10);
        for (int i = 0; i < judgeHashCount; i++) {
            judgeHashes.add(randomHash(random));
        }

        final Instant consensusTimestamp = randomInstant(random);

        final long nextConsensusNumber = random.nextLong(0, Long.MAX_VALUE);

        final List<MinimumJudgeInfo> minimumJudgeInfoList = new ArrayList<>();
        long generation = random.nextLong(1, 1_000_000);
        for (int i = 0; i < 26; i++) {
            final long judgeRound = round - 25 + i;
            minimumJudgeInfoList.add(new MinimumJudgeInfo(judgeRound, generation));
            generation += random.nextLong(1, 100);
        }

        final ConsensusSnapshot snapshot = new ConsensusSnapshot(
                round, judgeHashes, minimumJudgeInfoList, nextConsensusNumber, consensusTimestamp);

        return new RandomSignedStateGenerator(random)
                .setConsensusSnapshot(snapshot)
                .setRound(round)
                .build();
    }

    @Test
    void generationModeTest() {
        final Random random = getRandomPrintSeed();
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final SignedState signedState = generateSignedState(random, platformContext);
        final Hash originalHash = signedState.getState().getHash();

        final BasicSoftwareVersion previousSoftwareVersion =
                (BasicSoftwareVersion) signedState.getState().getPlatformState().getCreationSoftwareVersion();

        final BasicSoftwareVersion newSoftwareVersion =
                new BasicSoftwareVersion(previousSoftwareVersion.getSoftwareVersion() + 1);

        BirthRoundStateMigration.modifyStateForBirthRoundMigration(
                signedState, AncientMode.GENERATION_THRESHOLD, newSoftwareVersion);

        assertEquals(originalHash, signedState.getState().getHash());

        // Rehash the state, just in case
        rehashTree(signedState.getState());

        assertEquals(originalHash, signedState.getState().getHash());
    }

    @Test
    void alreadyMigratedTest() {
        final Random random = getRandomPrintSeed();
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final SignedState signedState = generateSignedState(random, platformContext);

        final BasicSoftwareVersion previousSoftwareVersion =
                (BasicSoftwareVersion) signedState.getState().getPlatformState().getCreationSoftwareVersion();

        final BasicSoftwareVersion newSoftwareVersion =
                new BasicSoftwareVersion(previousSoftwareVersion.getSoftwareVersion() + 1);

        signedState.getState().getPlatformState().setLastRoundBeforeBirthRoundMode(signedState.getRound() - 100);
        signedState.getState().getPlatformState().setFirstVersionInBirthRoundMode(previousSoftwareVersion);
        signedState.getState().getPlatformState().setLowestJudgeGenerationBeforeBirthRoundMode(100);
        rehashTree(signedState.getState());
        final Hash originalHash = signedState.getState().getHash();

        BirthRoundStateMigration.modifyStateForBirthRoundMigration(
                signedState, AncientMode.BIRTH_ROUND_THRESHOLD, newSoftwareVersion);

        assertEquals(originalHash, signedState.getState().getHash());

        // Rehash the state, just in case
        rehashTree(signedState.getState());

        assertEquals(originalHash, signedState.getState().getHash());
    }

    @Test
    void migrationTest() {
        final Random random = getRandomPrintSeed();
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final SignedState signedState = generateSignedState(random, platformContext);
        final Hash originalHash = signedState.getState().getHash();

        final BasicSoftwareVersion previousSoftwareVersion =
                (BasicSoftwareVersion) signedState.getState().getPlatformState().getCreationSoftwareVersion();

        final BasicSoftwareVersion newSoftwareVersion =
                new BasicSoftwareVersion(previousSoftwareVersion.getSoftwareVersion() + 1);

        final long lastRoundMinimumJudgeGeneration = signedState
                .getState()
                .getPlatformState()
                .getSnapshot()
                .getMinimumJudgeInfoList()
                .getLast()
                .minimumJudgeAncientThreshold();

        BirthRoundStateMigration.modifyStateForBirthRoundMigration(
                signedState, AncientMode.BIRTH_ROUND_THRESHOLD, newSoftwareVersion);

        assertNotEquals(originalHash, signedState.getState().getHash());

        // We expect these fields to be populated at the migration boundary
        assertEquals(
                newSoftwareVersion, signedState.getState().getPlatformState().getFirstVersionInBirthRoundMode());
        assertEquals(
                lastRoundMinimumJudgeGeneration,
                signedState.getState().getPlatformState().getLowestJudgeGenerationBeforeBirthRoundMode());
        assertEquals(
                signedState.getRound(),
                signedState.getState().getPlatformState().getLastRoundBeforeBirthRoundMode());

        // All of the judge info objects should now be using a birth round equal to the round of the state
        for (final MinimumJudgeInfo minimumJudgeInfo :
                signedState.getState().getPlatformState().getSnapshot().getMinimumJudgeInfoList()) {
            assertEquals(signedState.getRound(), minimumJudgeInfo.minimumJudgeAncientThreshold());
        }
    }
}
