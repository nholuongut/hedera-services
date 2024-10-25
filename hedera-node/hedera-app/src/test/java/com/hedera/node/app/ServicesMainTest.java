/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app;

import static com.swirlds.platform.system.SystemExitCode.NODE_ADDRESS_MISMATCH;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import com.hedera.node.app.version.HederaSoftwareVersion;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.config.legacy.ConfigurationException;
import com.swirlds.platform.config.legacy.LegacyConfigProperties;
import com.swirlds.platform.config.legacy.LegacyConfigPropertiesLoader;
import com.swirlds.platform.state.State;
import com.swirlds.platform.system.SystemExitUtils;
import com.swirlds.platform.util.BootstrapUtils;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class ServicesMainTest {
    private static final MockedStatic<LegacyConfigPropertiesLoader> legacyConfigPropertiesLoaderMockedStatic =
            mockStatic(LegacyConfigPropertiesLoader.class);
    private static final MockedStatic<BootstrapUtils> bootstrapUtilsMockedStatic = mockStatic(BootstrapUtils.class);

    @Mock
    private LegacyConfigProperties legacyConfigProperties;

    private final ServicesMain subject = new ServicesMain();

    // no local nodes specified but more than one match in address book
    @Test
    void hardExitOnTooManyLocalNodes() {
        withBadCommandLineArgs();
        String[] args = {};

        try (MockedStatic<SystemExitUtils> systemExitUtilsMockedStatic = mockStatic(SystemExitUtils.class)) {
            assertThatThrownBy(() -> ServicesMain.main(args)).isInstanceOf(ConfigurationException.class);

            systemExitUtilsMockedStatic.verify(() -> SystemExitUtils.exitSystem(NODE_ADDRESS_MISMATCH));
        }
    }

    // local node specified which does not match the address book
    @Test
    void hardExitOnNonMatchingNodeId() {
        withBadCommandLineArgs();
        String[] args = {"-local", "1234"}; // 1234 does not match anything in address book

        try (MockedStatic<SystemExitUtils> systemExitUtilsMockedStatic = mockStatic(SystemExitUtils.class)) {
            assertThatThrownBy(() -> ServicesMain.main(args)).isInstanceOf(ConfigurationException.class);

            systemExitUtilsMockedStatic.verify(() -> SystemExitUtils.exitSystem(NODE_ADDRESS_MISMATCH));
        }
    }

    // more than one local node specified which matches the address book
    @Test
    void hardExitOnTooManyMatchingNodes() {
        withBadCommandLineArgs();
        String[] args = {"-local", "1", "2"}; // both "1" and "2" match entries in address book

        try (MockedStatic<SystemExitUtils> systemExitUtilsMockedStatic = mockStatic(SystemExitUtils.class)) {
            systemExitUtilsMockedStatic
                    .when(() -> SystemExitUtils.exitSystem(any()))
                    .thenThrow(new UnsupportedOperationException());
            assertThatThrownBy(() -> ServicesMain.main(args)).isInstanceOf(UnsupportedOperationException.class);

            systemExitUtilsMockedStatic.verify(() -> SystemExitUtils.exitSystem(NODE_ADDRESS_MISMATCH));
        }
    }

    @Test
    void returnsSerializableVersion() {
        assertInstanceOf(HederaSoftwareVersion.class, subject.getSoftwareVersion());
    }

    @Test
    void noopsAsExpected() {
        // expect:
        Assertions.assertDoesNotThrow(subject::run);
    }

    @Test
    void createsNewMerkleStateRoot() {
        // expect:
        assertThat(subject.newMerkleStateRoot(), instanceOf(State.class));
        // FUTURE WORK: https://github.com/hashgraph/hedera-services/issues/11773
        // assertThat(subject.newMerkleStateRoot(), instanceOf(MerkleHederaState.class));
    }

    private void withBadCommandLineArgs() {
        legacyConfigPropertiesLoaderMockedStatic
                .when(() -> LegacyConfigPropertiesLoader.loadConfigFile(any()))
                .thenReturn(legacyConfigProperties);

        List<NodeId> nodeIds = new ArrayList<>();
        nodeIds.add(new NodeId(1));
        nodeIds.add(new NodeId(2));

        bootstrapUtilsMockedStatic
                .when(() -> BootstrapUtils.getNodesToRun(any(), any()))
                .thenReturn(nodeIds);
    }
}
