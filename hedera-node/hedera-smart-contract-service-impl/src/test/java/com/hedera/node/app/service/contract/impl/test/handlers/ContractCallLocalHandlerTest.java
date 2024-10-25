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

package com.hedera.node.app.service.contract.impl.test.handlers;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.DEFAULT_CONFIG;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.DEFAULT_CONTRACTS_CONFIG;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SUCCESS_RESULT;
import static com.hedera.node.app.service.contract.impl.test.handlers.ContractCallHandlerTest.INTRINSIC_GAS_FOR_0_ARG_METHOD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FeeData;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.contract.ContractCallLocalQuery;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.node.app.service.contract.impl.exec.CallOutcome;
import com.hedera.node.app.service.contract.impl.exec.ContextQueryProcessor;
import com.hedera.node.app.service.contract.impl.exec.QueryComponent;
import com.hedera.node.app.service.contract.impl.handlers.ContractCallLocalHandler;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import java.time.InstantSource;
import java.util.function.Function;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractCallLocalHandlerTest {
    @Mock
    private QueryContext context;

    @Mock
    private QueryComponent.Factory factory;

    @Mock
    private ContractCallLocalQuery contractCallLocalQuery;

    @Mock
    private QueryHeader header;

    @Mock
    private ResponseHeader responseHeader;

    @Mock
    private Query query;

    @Mock
    private ContractID contractID;

    @Mock
    private ReadableAccountStore store;

    @Mock
    private ReadableTokenStore tokenStore;

    @Mock
    private Account contract;

    @Mock
    private QueryComponent component;

    @Mock
    private ContextQueryProcessor processor;

    @Mock
    private Configuration configuration;

    @Mock
    private ContractsConfig contractsConfig;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private GasCalculator gasCalculator;

    private final InstantSource instantSource = InstantSource.system();

    private ContractCallLocalHandler subject;

    @BeforeEach
    void setUp() {
        subject = new ContractCallLocalHandler(() -> factory, gasCalculator, instantSource);
    }

    @Test
    void extractHeaderTest() {
        // given:
        given(query.contractCallLocalOrThrow()).willReturn(contractCallLocalQuery);
        given(contractCallLocalQuery.header()).willReturn(header);

        // when:
        var header = subject.extractHeader(query);

        // then:
        assertThat(header).isNotNull();
    }

    @Test
    void createEmptyResponseTest() {
        // when:
        var response = subject.createEmptyResponse(responseHeader);

        // then:
        assertThat(response).isNotNull();
    }

    @Test
    void validatePositiveTest() {
        // given
        given(context.query()).willReturn(query);
        given(query.contractCallLocalOrThrow()).willReturn(contractCallLocalQuery);
        given(contractCallLocalQuery.contractID()).willReturn(contractID);
        given(contractCallLocalQuery.functionParameters()).willReturn(Bytes.EMPTY);
        given(context.createStore(ReadableAccountStore.class)).willReturn(store);
        given(store.getContractById(contractID)).willReturn(contract);
        givenAllowCallsToNonContractAccountOffConfig();

        // when:
        assertThatCode(() -> subject.validate(context)).doesNotThrowAnyException();
    }

    @Test
    void validateFailsOnNegativeGas() {
        // given
        given(context.query()).willReturn(query);
        given(query.contractCallLocalOrThrow()).willReturn(contractCallLocalQuery);
        given(contractCallLocalQuery.gas()).willReturn(-1L);

        // when:
        assertThatThrownBy(() -> subject.validate(context)).isInstanceOf(PreCheckException.class);
    }

    @Test
    void validateFailsOnExcessGas() {
        // given
        given(context.query()).willReturn(query);
        given(context.configuration()).willReturn(DEFAULT_CONFIG);
        given(query.contractCallLocalOrThrow()).willReturn(contractCallLocalQuery);
        given(contractCallLocalQuery.gas()).willReturn(DEFAULT_CONTRACTS_CONFIG.maxGasPerSec() + 1);

        // when:
        assertThatThrownBy(() -> subject.validate(context)).isInstanceOf(PreCheckException.class);
    }

    @Test
    void validateFailsIfNoContractIdTest() {
        // given
        given(context.query()).willReturn(query);
        given(query.contractCallLocalOrThrow()).willReturn(contractCallLocalQuery);
        given(contractCallLocalQuery.contractID()).willReturn(null);
        given(contractCallLocalQuery.functionParameters()).willReturn(Bytes.EMPTY);
        givenDefaultConfig();

        // when:
        assertThatThrownBy(() -> subject.validate(context)).isInstanceOf(PreCheckException.class);
    }

    @Test
    void validateFailsIfNoContractOrTokenTest() {
        // given
        given(context.query()).willReturn(query);
        given(query.contractCallLocalOrThrow()).willReturn(contractCallLocalQuery);
        given(contractCallLocalQuery.contractID()).willReturn(contractID);
        given(contractCallLocalQuery.functionParameters()).willReturn(Bytes.EMPTY);
        given(context.createStore(ReadableAccountStore.class)).willReturn(store);
        given(store.getContractById(contractID)).willReturn(null);
        given(context.createStore(ReadableTokenStore.class)).willReturn(tokenStore);
        given(tokenStore.get(any())).willReturn(null);
        givenAllowCallsToNonContractAccountOffConfig();

        // when:
        assertThatThrownBy(() -> subject.validate(context)).isInstanceOf(PreCheckException.class);
    }

    @Test
    void validateFailsIfGasIsLessThanIntrinsic() {
        // given
        given(context.query()).willReturn(query);
        given(query.contractCallLocalOrThrow()).willReturn(contractCallLocalQuery);
        given(contractCallLocalQuery.gas()).willReturn(INTRINSIC_GAS_FOR_0_ARG_METHOD - 1);
        givenAllowCallsToNonContractAccountOffConfig();

        // when:
        assertThatThrownBy(() -> subject.validate(context)).isInstanceOf(PreCheckException.class);
    }

    @Test
    void validateSucceedsIfContractDeletedTest() {
        // given
        given(context.query()).willReturn(query);
        given(query.contractCallLocalOrThrow()).willReturn(contractCallLocalQuery);
        given(contractCallLocalQuery.contractID()).willReturn(contractID);
        given(contractCallLocalQuery.functionParameters()).willReturn(Bytes.EMPTY);
        given(context.createStore(ReadableAccountStore.class)).willReturn(store);
        given(store.getContractById(contractID)).willReturn(contract);
        givenAllowCallsToNonContractAccountOffConfig();

        // when:
        assertThatCode(() -> subject.validate(context)).doesNotThrowAnyException();
        verify(contract, never()).deleted();
    }

    @Test
    void findResponsePositiveTest() {
        given(factory.create(any(), any(), eq(HederaFunctionality.CONTRACT_CALL_LOCAL)))
                .willReturn(component);
        given(component.contextQueryProcessor()).willReturn(processor);
        final var expectedResult = SUCCESS_RESULT.asQueryResult();
        final var expectedOutcome = new CallOutcome(
                expectedResult, SUCCESS_RESULT.finalStatus(), null, SUCCESS_RESULT.gasPrice(), null, null);
        given(processor.call()).willReturn(expectedOutcome);

        // given(processor.call()).willReturn(responseHeader);
        // when:
        var response = subject.findResponse(context, responseHeader);

        assertThat(response.contractCallLocal().header()).isEqualTo(responseHeader);
        assertThat(response.contractCallLocal().functionResult()).isEqualTo(expectedOutcome.result());
    }

    @Test
    void computesFeesSuccessfully() {

        final var id = ContractID.newBuilder().contractNum(10).build();
        given(context.query()).willReturn(query);
        given(query.contractCallLocalOrThrow()).willReturn(contractCallLocalQuery);
        given(context.feeCalculator()).willReturn(feeCalculator);
        givenAllowCallsToNonContractAccountOffConfig();

        // Mock the behavior of legacyCalculate method
        when(feeCalculator.legacyCalculate(any(Function.class))).thenAnswer(invocation -> {
            // Extract the callback passed to the method
            Function<SigValueObj, FeeData> passedCallback = invocation.getArgument(0);
            return new Fees(10L, 0L, 0L);
        });

        var fees = subject.computeFees(context);

        assertThat(fees).isEqualTo(new Fees(10L, 0L, 0L));
    }

    private void givenDefaultConfig() {
        given(context.configuration()).willReturn(DEFAULT_CONFIG);
    }

    private void givenAllowCallsToNonContractAccountOffConfig() {
        given(context.configuration()).willReturn(configuration);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
    }
}
