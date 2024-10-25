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

package com.hedera.node.app.service.contract.impl.exec;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.streams.ContractActions;
import com.hedera.hapi.streams.ContractStateChanges;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransactionResult;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import com.hedera.node.app.service.contract.impl.records.ContractCreateRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Summarizes the outcome of an EVM message call.
 *
 * @param result the result of the call
 * @param status the resolved status of the call
 * @param tinybarGasPrice the tinybar-denominated gas price used for the call
 * @param actions any contract actions that should be externalized in a sidecar
 * @param stateChanges any contract state changes that should be externalized in a sidecar
 */
public record CallOutcome(
        @NonNull ContractFunctionResult result,
        @NonNull ResponseCodeEnum status,
        @Nullable ContractID recipientId,
        long tinybarGasPrice,
        @Nullable ContractActions actions,
        @Nullable ContractStateChanges stateChanges) {

    /**
     * Enumerates whether to externalize the result of aborted calls; needed for
     * mono-service fidelity, since only a top-level {@code EthereumTransaction}
     * would externalize the result of an aborted call there.
     */
    public enum ExternalizeAbortResult {
        YES,
        NO
    }

    public boolean hasStateChanges() {
        return stateChanges != null && !stateChanges.contractStateChanges().isEmpty();
    }

    public static CallOutcome fromResultsWithMaybeSidecars(
            @NonNull ContractFunctionResult result, @NonNull HederaEvmTransactionResult hevmResult) {
        return new CallOutcome(
                result,
                hevmResult.finalStatus(),
                hevmResult.recipientId(),
                hevmResult.gasPrice(),
                hevmResult.actions(),
                hevmResult.stateChanges());
    }

    public static CallOutcome fromResultsWithoutSidecars(
            @NonNull ContractFunctionResult result, @NonNull HederaEvmTransactionResult hevmResult) {
        return new CallOutcome(
                result, hevmResult.finalStatus(), hevmResult.recipientId(), hevmResult.gasPrice(), null, null);
    }

    public CallOutcome {
        requireNonNull(result);
        requireNonNull(status);
    }

    /**
     * Returns true if the call was successful.
     *
     * @return true if the call was successful
     */
    public boolean isSuccess() {
        return status == SUCCESS;
    }

    /**
     * Adds the call details to the given record builder.
     *
     * @param recordBuilder the record builder
     * @param externalizeAbortResult whether to externalize the result of aborted calls
     */
    public void addCallDetailsTo(
            @NonNull final ContractCallRecordBuilder recordBuilder,
            @NonNull final ExternalizeAbortResult externalizeAbortResult) {
        requireNonNull(recordBuilder);
        requireNonNull(externalizeAbortResult);
        if (!callWasAborted()) {
            recordBuilder.contractID(recipientId);
        }
        if (shouldExternalizeResult(externalizeAbortResult)) {
            recordBuilder.contractCallResult(result);
        }
        recordBuilder.withCommonFieldsSetFrom(this);
    }

    /**
     * Adds the create details to the given record builder.
     *
     * @param recordBuilder the record builder
     */
    public void addCreateDetailsTo(
            @NonNull final ContractCreateRecordBuilder recordBuilder,
            @NonNull final ExternalizeAbortResult externalizeAbortResult) {
        requireNonNull(recordBuilder);
        requireNonNull(externalizeAbortResult);
        recordBuilder.contractID(recipientIdIfCreated());
        if (shouldExternalizeResult(externalizeAbortResult)) {
            recordBuilder.contractCreateResult(result);
        }
        recordBuilder.withCommonFieldsSetFrom(this);
    }

    /**
     * Returns the gas cost of the call in tinybar (always zero if the call was aborted before constructing
     * the initial {@link org.hyperledger.besu.evm.frame.MessageFrame}).
     *
     * @return the gas cost of the call in tinybar
     */
    public long tinybarGasCost() {
        return tinybarGasPrice * result.gasUsed();
    }

    /**
     * Returns the ID of the contract that was created, or null if no contract was created.
     *
     * @return the ID of the contract that was created, or null if no contract was created
     */
    public @Nullable ContractID recipientIdIfCreated() {
        return representsTopLevelCreation() ? result.contractIDOrThrow() : null;
    }

    private boolean representsTopLevelCreation() {
        return isSuccess() && requireNonNull(result).hasEvmAddress();
    }

    private boolean shouldExternalizeResult(@NonNull final ExternalizeAbortResult externalizeAbortResult) {
        return !callWasAborted() || externalizeAbortResult == ExternalizeAbortResult.YES;
    }

    private boolean callWasAborted() {
        return result.gasUsed() == 0L;
    }
}
