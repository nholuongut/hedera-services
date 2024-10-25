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

package com.hedera.node.app.service.token.records;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.TokenAssociation;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * A {@code RecordBuilder} specialization for tracking the effects of a {@code CryptoTransfer}
 * transaction.
 */
public interface CryptoTransferRecordBuilder extends SingleTransactionRecordBuilder {
    /**
     * Tracks the <b>net</b> hbar transfers that need to be applied to the associated accounts
     * (accounts are specified in the {@code TransferList} input param)
     *
     * @param hbarTransfers the net list of adjustments to make to account balances
     * @return this builder
     */
    @NonNull
    CryptoTransferRecordBuilder transferList(@NonNull TransferList hbarTransfers);

    /**
     * Tracks the <b>net</b> token transfers that need to be applied to the associated accounts,
     * including both fungible and non-fungible types
     *
     * @param tokenTransferLists the net list of balance or ownership changes for the given
     *                           fungible and non-fungible tokens
     * @return this builder
     */
    @NonNull
    CryptoTransferRecordBuilder tokenTransferLists(@NonNull List<TokenTransferList> tokenTransferLists);

    /**
     * Tracks the total custom fees assessed in the transaction
     * @param assessedCustomFees the total custom fees assessed in the transaction
     * @return this builder
     */
    @NonNull
    CryptoTransferRecordBuilder assessedCustomFees(@NonNull final List<AssessedCustomFee> assessedCustomFees);

    /**
     * Tracks the total amount of hbars paid as staking rewards in the transaction
     * @param paidStakingRewards the total amount of hbars paid as staking rewards
     * @return this builder
     */
    CryptoTransferRecordBuilder paidStakingRewards(@NonNull final List<AccountAmount> paidStakingRewards);

    /**
     * Adds the token relations that are created by auto associations.
     * This information is needed while building the transfer list, to set the auto association flag.
     * @param tokenAssociation the token association that is created by auto association
     * @return the builder
     */
    CryptoTransferRecordBuilder addAutomaticTokenAssociation(@NonNull final TokenAssociation tokenAssociation);

    /**
     * Tracks the result of a contract call, if any. It is used to update the transaction record.
     * @param result the result of a contract call
     * @return this builder
     */
    @NonNull
    CryptoTransferRecordBuilder contractCallResult(@Nullable ContractFunctionResult result);
}
