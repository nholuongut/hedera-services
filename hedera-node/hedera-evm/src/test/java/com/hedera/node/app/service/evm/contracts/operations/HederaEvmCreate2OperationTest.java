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

package com.hedera.node.app.service.evm.contracts.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmStackedWorldUpdater;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Create2Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaEvmCreate2OperationTest {
    private static final long baseGas = 100L;
    private static final Bytes salt = Bytes.fromHexString("0x2a");
    private static final Bytes oneOffsetStackItem = Bytes.of(10);
    private static final Bytes twoOffsetStackItem = Bytes.of(20);
    private static final MutableBytes initcode = MutableBytes.of((byte) 0xaa);
    private static final Address recipientAddr = Address.fromHexString("0x0102030405060708090a0b0c0d0e0f1011121314");

    @Mock
    private EvmProperties evmProperties;

    @Mock
    private MessageFrame frame;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private HederaEvmStackedWorldUpdater stackedUpdater;

    private HederaEvmCreate2Operation subject;

    @Mock
    private CreateOperationExternalizer externalizer;

    @BeforeEach
    void setup() {
        subject = new HederaEvmCreate2Operation(gasCalculator, evmProperties, externalizer);
    }

    @Test
    void computesExpectedCost() {
        given(gasCalculator.create2OperationGasCost(frame)).willReturn(baseGas);

        var actualGas = subject.cost(frame);

        assertEquals(baseGas, actualGas);
    }

    @Test
    void enabledOnlyIfCreate2IsEnabled() {
        assertFalse(subject.isEnabled());

        given(evmProperties.isCreate2Enabled()).willReturn(true);

        assertTrue(subject.isEnabled());
    }

    @Test
    void computesExpectedTargetAddress() {
        final var expectedAddress = Address.BLS12_G1ADD;
        final var canonicalSource = Address.BLS12_G1MULTIEXP;
        final var besuOp = new Create2Operation(gasCalculator, 48 * 1024);

        givenMemoryStackItems();
        given(frame.getStackItem(3)).willReturn(salt);
        given(frame.getRecipientAddress()).willReturn(canonicalSource);
        given(frame.readMutableMemory(oneOffsetStackItem.toLong(), twoOffsetStackItem.toLong()))
                .willReturn(initcode);
        final var expectedAlias = besuOp.targetContractAddress(frame);

        given(frame.getWorldUpdater()).willReturn(stackedUpdater);
        given(frame.getRecipientAddress()).willReturn(recipientAddr);
        given(stackedUpdater.priorityAddress(recipientAddr)).willReturn(canonicalSource);
        given(stackedUpdater.newAliasedContractAddress(recipientAddr, expectedAlias))
                .willReturn(expectedAddress);

        final var actualAlias = subject.targetContractAddress(frame);
        assertEquals(expectedAlias, actualAlias);
    }

    private void givenMemoryStackItems() {
        given(frame.getStackItem(1)).willReturn(oneOffsetStackItem);
        given(frame.getStackItem(2)).willReturn(twoOffsetStackItem);
    }
}
