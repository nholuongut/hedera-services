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

package com.hedera.node.app.workflows.handle.throttle;

import static com.hedera.hapi.node.base.HederaFunctionality.CONSENSUS_SUBMIT_MESSAGE;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.hapi.node.base.HederaFunctionality.ETHEREUM_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.workflows.handle.throttle.DispatchUsageManager.WorkDone.FEES_ONLY;
import static com.hedera.node.app.workflows.handle.throttle.DispatchUsageManager.WorkDone.USER_TRANSACTION;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.contract.EthereumTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.throttle.ThrottleServiceManager;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.Dispatch;
import com.hedera.node.app.workflows.handle.metric.HandleWorkflowMetrics;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.SelfNodeInfo;
import java.time.Instant;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DispatchUsageManagerTest {
    private static final long GAS_USED = 123L;
    private static final long GAS_LIMIT = 456L;
    public static final Bytes ETH_WITH_TO_ADDRESS = Bytes.fromHex(
            "02f8ad82012a80a000000000000000000000000000000000000000000000000000000000000003e8a0000000000000000000000000000000000000000000000000000000746a528800831e848094fee687d5088faff48013a6767505c027e2742536880de0b6b3a764000080c080a0f5ddf2394311e634e2147bf38583a017af45f4326bdf5746cac3a1110f973e4fa025bad52d9a9f8b32eb983c9fb8959655258bd75e2826b2c6a48d4c26ec30d112");
    public static final EthTxData ETH_DATA_WITH_TO_ADDRESS =
            requireNonNull(EthTxData.populateEthTxData(ETH_WITH_TO_ADDRESS.toByteArray()));
    private static final Instant CONSENSUS_NOW = Instant.ofEpochSecond(1_234_567L, 890);
    public static final Configuration DEFAULT_CONFIG = HederaTestConfigBuilder.createConfig();
    private static final AccountID CREATOR_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(3).build();
    private static final AccountID OTHER_NODE_ID =
            AccountID.newBuilder().accountNum(4).build();
    private static final AccountID PAYER_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(1_234).build();
    private static final TransactionBody NONDESCRIPT_TXN_BODY = TransactionBody.newBuilder()
            .nodeAccountID(CREATOR_ACCOUNT_ID)
            .transactionID(
                    TransactionID.newBuilder().accountID(PAYER_ACCOUNT_ID).build())
            .build();
    private static final TransactionBody CONTRACT_CALL_TXN_BODY = TransactionBody.newBuilder()
            .transactionID(
                    TransactionID.newBuilder().accountID(PAYER_ACCOUNT_ID).build())
            .contractCall(
                    ContractCallTransactionBody.newBuilder().gas(GAS_LIMIT).build())
            .build();
    private static final TransactionBody CONTRACT_CREATE_TXN_BODY = TransactionBody.newBuilder()
            .transactionID(
                    TransactionID.newBuilder().accountID(PAYER_ACCOUNT_ID).build())
            .contractCreateInstance(
                    ContractCreateTransactionBody.newBuilder().gas(GAS_LIMIT).build())
            .build();
    private static final TransactionBody ETH_TXN_BODY = TransactionBody.newBuilder()
            .transactionID(
                    TransactionID.newBuilder().accountID(PAYER_ACCOUNT_ID).build())
            .ethereumTransaction(EthereumTransactionBody.newBuilder()
                    .ethereumData(ETH_WITH_TO_ADDRESS)
                    .build())
            .build();
    private static final TransactionInfo ETH_TXN_INFO = new TransactionInfo(
            Transaction.DEFAULT, ETH_TXN_BODY, SignatureMap.DEFAULT, Bytes.EMPTY, ETHEREUM_TRANSACTION);
    private static final TransactionInfo CRYPTO_TRANSFER_TXN_INFO = new TransactionInfo(
            Transaction.DEFAULT, NONDESCRIPT_TXN_BODY, SignatureMap.DEFAULT, Bytes.EMPTY, CRYPTO_TRANSFER);
    private static final TransactionInfo CONTRACT_CALL_TXN_INFO = new TransactionInfo(
            Transaction.DEFAULT, CONTRACT_CALL_TXN_BODY, SignatureMap.DEFAULT, Bytes.EMPTY, CONTRACT_CALL);
    private static final TransactionInfo CONTRACT_CREATE_TXN_INFO = new TransactionInfo(
            Transaction.DEFAULT, CONTRACT_CREATE_TXN_BODY, SignatureMap.DEFAULT, Bytes.EMPTY, CONTRACT_CREATE);
    private static final TransactionInfo SUBMIT_TXN_INFO = new TransactionInfo(
            Transaction.DEFAULT, NONDESCRIPT_TXN_BODY, SignatureMap.DEFAULT, Bytes.EMPTY, CONSENSUS_SUBMIT_MESSAGE);

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private SelfNodeInfo selfNodeInfo;

    @Mock
    private ReadableStoreFactory readableStoreFactory;

    @Mock
    private ReadableAccountStore readableAccountStore;

    @Mock
    private SingleTransactionRecordBuilderImpl recordBuilder;

    @Mock
    private HandleWorkflowMetrics handleWorkflowMetrics;

    @Mock
    private ThrottleServiceManager throttleServiceManager;

    @Mock
    private NetworkUtilizationManager networkUtilizationManager;

    @Mock
    private SavepointStackImpl stack;

    @Mock
    private Dispatch dispatch;

    private DispatchUsageManager subject;

    @BeforeEach
    void setUp() {
        subject = new DispatchUsageManager(
                networkInfo, handleWorkflowMetrics, throttleServiceManager, networkUtilizationManager);
    }

    @Test
    void doesNotScreeNonContractOperation() throws DispatchUsageManager.ThrottleException {
        given(dispatch.txnInfo()).willReturn(CRYPTO_TRANSFER_TXN_INFO);

        subject.screenForCapacity(dispatch);

        verifyNoInteractions(networkUtilizationManager);
    }

    @Test
    void alwaysTracksContractUtilization() {
        given(dispatch.txnInfo()).willReturn(CONTRACT_CALL_TXN_INFO);
        given(dispatch.consensusNow()).willReturn(CONSENSUS_NOW);
        given(dispatch.stack()).willReturn(stack);

        assertDoesNotThrow(() -> subject.screenForCapacity(dispatch));
    }

    @Test
    void throwsThrottleExceptionIfGasThrottled() {
        given(dispatch.txnInfo()).willReturn(CONTRACT_CALL_TXN_INFO);
        given(dispatch.consensusNow()).willReturn(CONSENSUS_NOW);
        given(dispatch.stack()).willReturn(stack);
        given(networkUtilizationManager.wasLastTxnGasThrottled()).willReturn(true);

        Assertions.assertThatThrownBy(() -> subject.screenForCapacity(dispatch))
                .isInstanceOf(DispatchUsageManager.ThrottleException.class);
    }

    @Test
    void tracksNoUsageIfNotUserDispatch() {
        given(dispatch.txnCategory()).willReturn(HandleContext.TransactionCategory.CHILD);

        subject.trackUsage(dispatch, FEES_ONLY);

        verifyNoInteractions(networkUtilizationManager);
    }

    @Test
    void tracksJustFeePaymentsIfOnlyWorkDone() {
        given(dispatch.txnCategory()).willReturn(HandleContext.TransactionCategory.USER);
        given(dispatch.consensusNow()).willReturn(CONSENSUS_NOW);
        given(dispatch.stack()).willReturn(stack);

        subject.trackUsage(dispatch, FEES_ONLY);

        verify(networkUtilizationManager).trackFeePayments(CONSENSUS_NOW, stack);
        verify(throttleServiceManager).saveThrottleSnapshotsAndCongestionLevelStartsTo(stack);
    }

    @Test
    void tracksUtilizationForNonContractOperationsAndNothingElseOnSuccess() {
        given(dispatch.txnCategory()).willReturn(HandleContext.TransactionCategory.USER);
        given(dispatch.txnInfo()).willReturn(CRYPTO_TRANSFER_TXN_INFO);
        given(recordBuilder.status()).willReturn(SUCCESS);
        given(dispatch.recordBuilder()).willReturn(recordBuilder);
        given(dispatch.consensusNow()).willReturn(CONSENSUS_NOW);
        given(dispatch.stack()).willReturn(stack);

        subject.trackUsage(dispatch, USER_TRANSACTION);

        verify(networkUtilizationManager).trackTxn(CRYPTO_TRANSFER_TXN_INFO, CONSENSUS_NOW, stack);
        verify(throttleServiceManager).saveThrottleSnapshotsAndCongestionLevelStartsTo(stack);
    }

    @Test
    void tracksUtilizationForNonAutoCreatingOperations() {
        given(dispatch.txnCategory()).willReturn(HandleContext.TransactionCategory.USER);
        given(dispatch.txnInfo()).willReturn(SUBMIT_TXN_INFO);
        given(dispatch.consensusNow()).willReturn(CONSENSUS_NOW);
        given(dispatch.stack()).willReturn(stack);
        given(dispatch.recordBuilder()).willReturn(recordBuilder);

        subject.trackUsage(dispatch, USER_TRANSACTION);

        verify(networkUtilizationManager).trackTxn(SUBMIT_TXN_INFO, CONSENSUS_NOW, stack);
        verify(throttleServiceManager).saveThrottleSnapshotsAndCongestionLevelStartsTo(stack);
    }

    @Test
    void leaksUnusedGasForContractCall() {
        given(dispatch.txnCategory()).willReturn(HandleContext.TransactionCategory.USER);
        given(dispatch.txnInfo()).willReturn(CONTRACT_CALL_TXN_INFO);
        given(recordBuilder.hasContractResult()).willReturn(true);
        given(recordBuilder.getGasUsedForContractTxn()).willReturn(GAS_USED);
        given(dispatch.recordBuilder()).willReturn(recordBuilder);
        given(dispatch.config()).willReturn(DEFAULT_CONFIG);
        given(dispatch.stack()).willReturn(stack);

        subject.trackUsage(dispatch, USER_TRANSACTION);

        verify(handleWorkflowMetrics).addGasUsed(GAS_USED);
        verify(networkUtilizationManager).leakUnusedGasPreviouslyReserved(CONTRACT_CALL_TXN_INFO, GAS_LIMIT - GAS_USED);
        verify(throttleServiceManager).saveThrottleSnapshotsAndCongestionLevelStartsTo(stack);
    }

    @Test
    void leaksUnusedGasForContractCreate() {
        given(dispatch.txnCategory()).willReturn(HandleContext.TransactionCategory.USER);
        given(dispatch.txnInfo()).willReturn(CONTRACT_CREATE_TXN_INFO);
        given(recordBuilder.hasContractResult()).willReturn(true);
        given(recordBuilder.getGasUsedForContractTxn()).willReturn(GAS_USED);
        given(dispatch.recordBuilder()).willReturn(recordBuilder);
        given(dispatch.config()).willReturn(DEFAULT_CONFIG);
        given(dispatch.stack()).willReturn(stack);

        subject.trackUsage(dispatch, USER_TRANSACTION);

        verify(handleWorkflowMetrics).addGasUsed(GAS_USED);
        verify(networkUtilizationManager)
                .leakUnusedGasPreviouslyReserved(CONTRACT_CREATE_TXN_INFO, GAS_LIMIT - GAS_USED);
        verify(throttleServiceManager).saveThrottleSnapshotsAndCongestionLevelStartsTo(stack);
    }

    @Test
    void leaksUnusedGasForEthTx() {
        given(dispatch.txnCategory()).willReturn(HandleContext.TransactionCategory.USER);
        given(dispatch.txnInfo()).willReturn(ETH_TXN_INFO);
        given(recordBuilder.hasContractResult()).willReturn(true);
        given(recordBuilder.getGasUsedForContractTxn()).willReturn(GAS_USED);
        given(dispatch.recordBuilder()).willReturn(recordBuilder);
        given(dispatch.config()).willReturn(DEFAULT_CONFIG);
        given(dispatch.stack()).willReturn(stack);
        given(dispatch.readableStoreFactory()).willReturn(readableStoreFactory);
        given(readableStoreFactory.getStore(ReadableAccountStore.class)).willReturn(readableAccountStore);

        subject.trackUsage(dispatch, USER_TRANSACTION);

        verify(handleWorkflowMetrics).addGasUsed(GAS_USED);
        verify(networkUtilizationManager)
                .leakUnusedGasPreviouslyReserved(ETH_TXN_INFO, ETH_DATA_WITH_TO_ADDRESS.gasLimit() - GAS_USED);
        verify(throttleServiceManager).saveThrottleSnapshotsAndCongestionLevelStartsTo(stack);
    }

    @Test
    void doesNotLeakUnusedGasForContractOperationWithoutResult() {
        given(dispatch.txnCategory()).willReturn(HandleContext.TransactionCategory.USER);
        given(dispatch.txnInfo()).willReturn(CONTRACT_CALL_TXN_INFO);
        given(dispatch.recordBuilder()).willReturn(recordBuilder);
        given(dispatch.stack()).willReturn(stack);

        subject.trackUsage(dispatch, USER_TRANSACTION);

        verify(handleWorkflowMetrics, never()).addGasUsed(GAS_USED);
        verify(networkUtilizationManager, never()).leakUnusedGasPreviouslyReserved(any(), anyLong());
        verify(throttleServiceManager).saveThrottleSnapshotsAndCongestionLevelStartsTo(stack);
    }

    @Test
    void reclaimsSelfFrontendCapacityOnFailedImplicitCreation() {
        given(dispatch.txnCategory()).willReturn(HandleContext.TransactionCategory.USER);
        given(dispatch.txnInfo()).willReturn(CRYPTO_TRANSFER_TXN_INFO);
        given(dispatch.recordBuilder()).willReturn(recordBuilder);
        given(recordBuilder.status()).willReturn(INVALID_ACCOUNT_AMOUNTS);
        given(dispatch.stack()).willReturn(stack);
        given(dispatch.readableStoreFactory()).willReturn(readableStoreFactory);
        given(readableStoreFactory.getStore(ReadableAccountStore.class)).willReturn(readableAccountStore);
        given(throttleServiceManager.numImplicitCreations(NONDESCRIPT_TXN_BODY, readableAccountStore))
                .willReturn(1);
        given(networkInfo.selfNodeInfo()).willReturn(selfNodeInfo);
        given(selfNodeInfo.accountId()).willReturn(CREATOR_ACCOUNT_ID);

        subject.trackUsage(dispatch, USER_TRANSACTION);

        verify(throttleServiceManager).reclaimFrontendThrottleCapacity(1, CRYPTO_CREATE);
        verify(throttleServiceManager).saveThrottleSnapshotsAndCongestionLevelStartsTo(stack);
    }

    @Test
    void doesNotReclaimSelfFrontendCapacityOnZeroFailedImplicitCreation() {
        given(dispatch.txnCategory()).willReturn(HandleContext.TransactionCategory.USER);
        given(dispatch.txnInfo()).willReturn(CRYPTO_TRANSFER_TXN_INFO);
        given(dispatch.recordBuilder()).willReturn(recordBuilder);
        given(recordBuilder.status()).willReturn(INVALID_ACCOUNT_AMOUNTS);
        given(dispatch.stack()).willReturn(stack);
        given(dispatch.readableStoreFactory()).willReturn(readableStoreFactory);
        given(readableStoreFactory.getStore(ReadableAccountStore.class)).willReturn(readableAccountStore);
        given(throttleServiceManager.numImplicitCreations(NONDESCRIPT_TXN_BODY, readableAccountStore))
                .willReturn(0);

        subject.trackUsage(dispatch, USER_TRANSACTION);

        verify(throttleServiceManager, never()).reclaimFrontendThrottleCapacity(anyInt(), any());
        verify(throttleServiceManager).saveThrottleSnapshotsAndCongestionLevelStartsTo(stack);
    }

    @Test
    void doesntReclaimSelfFrontendCapacityOnFailedImplicitCreationFromOtherNode() {
        given(dispatch.txnCategory()).willReturn(HandleContext.TransactionCategory.USER);
        given(dispatch.txnInfo()).willReturn(CRYPTO_TRANSFER_TXN_INFO);
        given(dispatch.recordBuilder()).willReturn(recordBuilder);
        given(recordBuilder.status()).willReturn(INVALID_ACCOUNT_AMOUNTS);
        given(dispatch.stack()).willReturn(stack);
        given(dispatch.readableStoreFactory()).willReturn(readableStoreFactory);
        given(readableStoreFactory.getStore(ReadableAccountStore.class)).willReturn(readableAccountStore);
        given(throttleServiceManager.numImplicitCreations(NONDESCRIPT_TXN_BODY, readableAccountStore))
                .willReturn(1);
        given(networkInfo.selfNodeInfo()).willReturn(selfNodeInfo);
        given(selfNodeInfo.accountId()).willReturn(OTHER_NODE_ID);

        subject.trackUsage(dispatch, USER_TRANSACTION);

        verify(throttleServiceManager, never()).reclaimFrontendThrottleCapacity(anyInt(), any());
        verify(throttleServiceManager).saveThrottleSnapshotsAndCongestionLevelStartsTo(stack);
    }
}
