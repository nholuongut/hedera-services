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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.tokeninfo;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokeninfo.TokenInfoCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokeninfo.TokenInfoTranslator;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater.Enhancement;
import com.swirlds.config.api.Configuration;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenInfoTranslatorTest {
    @Mock
    private HtsCallAttempt attempt;

    @Mock
    private SystemContractGasCalculator gasCalculator;

    @Mock
    private Enhancement enhancement;

    @Mock
    Configuration configuration;

    private TokenInfoTranslator subject;

    @BeforeEach
    void setUp() {
        subject = new TokenInfoTranslator();
    }

    @Test
    void matchesTokenInfoTranslatorTest() {
        given(attempt.selector()).willReturn(TokenInfoTranslator.TOKEN_INFO.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void callFromTest() {
        final Tuple tuple = new Tuple(FUNGIBLE_TOKEN_HEADLONG_ADDRESS);
        final Bytes inputBytes = Bytes.wrapByteBuffer(TokenInfoTranslator.TOKEN_INFO.encodeCall(tuple));
        given(attempt.input()).willReturn(inputBytes);
        given(attempt.enhancement()).willReturn(enhancement);
        given(attempt.configuration()).willReturn(configuration);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

        final var call = subject.callFrom(attempt);
        assertThat(call).isInstanceOf(TokenInfoCall.class);
    }
}
