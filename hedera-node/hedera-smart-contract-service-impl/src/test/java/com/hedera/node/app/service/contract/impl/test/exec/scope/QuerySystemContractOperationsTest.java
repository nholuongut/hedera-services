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

package com.hedera.node.app.service.contract.impl.test.exec.scope;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.MOCK_VERIFICATION_STRATEGY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.base.TokenRelationship;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.QuerySystemContractOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.ResultTranslator;
import com.hedera.node.app.spi.fees.ExchangeRateInfo;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.config.api.Configuration;
import java.time.InstantSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuerySystemContractOperationsTest {
    @Mock
    private QueryContext context;

    @Mock
    private NftID nftID;

    @Mock
    private ResultTranslator<Nft> nftResultTranslator;

    @Mock
    private ResultTranslator<Token> tokenResultTranslator;

    @Mock
    private ResultTranslator<Account> accountResultTranslator;

    @Mock
    private ResultTranslator<TokenRelationship> tokenRelResultTranslator;

    @Mock
    private Configuration configuration;

    @Mock
    private ContractsConfig contractsConfig;

    @Mock
    private ExchangeRateInfo exchangeRateInfo;

    private final InstantSource instantSource = InstantSource.system();

    private QuerySystemContractOperations subject;

    @BeforeEach
    void setUp() {
        subject = new QuerySystemContractOperations(context, instantSource);
    }

    @Test
    void doesNotSupportAnyMutations() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.dispatch(
                        TransactionBody.DEFAULT, MOCK_VERIFICATION_STRATEGY, AccountID.DEFAULT, Object.class));
        assertThrows(
                UnsupportedOperationException.class, () -> subject.activeSignatureTestWith(MOCK_VERIFICATION_STRATEGY));

        assertDoesNotThrow(() -> subject.externalizeResult(ContractFunctionResult.DEFAULT, ResponseCodeEnum.SUCCESS));
    }

    @Test
    void exchangeRateTest() {
        final ExchangeRate exchangeRate = new ExchangeRate(1, 2, TimestampSeconds.DEFAULT);
        given(context.exchangeRateInfo()).willReturn(exchangeRateInfo);
        given(exchangeRateInfo.activeRate(any())).willReturn(exchangeRate);
        var result = subject.currentExchangeRate();
        assertThat(result).isEqualTo(exchangeRate);
    }
}
