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

package com.hedera.node.app.service.contract.impl.exec.gas;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.ToLongBiFunction;

/**
 * Computes the gas requirements for dispatching transactions or queries.
 */
public class SystemContractGasCalculator {
    private static final long FIXED_VIEW_GAS_COST = 100L;

    private final TinybarValues tinybarValues;
    private final CanonicalDispatchPrices dispatchPrices;
    private final ToLongBiFunction<TransactionBody, AccountID> feeCalculator;

    public SystemContractGasCalculator(
            @NonNull final TinybarValues tinybarValues,
            @NonNull final CanonicalDispatchPrices dispatchPrices,
            @NonNull final ToLongBiFunction<TransactionBody, AccountID> feeCalculator) {
        this.tinybarValues = requireNonNull(tinybarValues);
        this.dispatchPrices = requireNonNull(dispatchPrices);
        this.feeCalculator = requireNonNull(feeCalculator);
    }

    /**
     * Convenience method that, given a transaction body whose cost can be directly compared to the minimum
     * cost of a dispatch type, returns the gas requirement for the transaction to be dispatched.
     *
     * @param body the transaction body to be dispatched
     * @param dispatchType the type of dispatch whose minimum cost should be respected
     * @param payer the payer of the transaction
     * @return the gas requirement for the transaction to be dispatched
     */
    public long gasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final DispatchType dispatchType,
            @NonNull final AccountID payer) {
        requireNonNull(body);
        requireNonNull(dispatchType);
        return gasRequirement(body, payer, canonicalPriceInTinybars(dispatchType));
    }

    /**
     * Given a transaction body whose dispatch gas requirement must be at least equivalent to a given minimum
     * amount of tinybars, returns the gas requirement for the transaction to be dispatched.
     *
     * @param body the transaction body to be dispatched
     * @param payer the payer of the transaction
     * @param minimumPriceInTinybars the minimum equivalent tinybar cost of the dispatch
     * @return the gas requirement for the transaction to be dispatched
     */
    public long gasRequirement(
            @NonNull final TransactionBody body, @NonNull final AccountID payer, final long minimumPriceInTinybars) {
        final var nominalPriceInTinybars = feeCalculator.applyAsLong(body, payer);
        final var priceInTinybars = Math.max(minimumPriceInTinybars, nominalPriceInTinybars);
        return asGasRequirement(priceInTinybars);
    }

    /**
     * Given a transaction body whose, returns the gas requirement for the transaction to be
     * dispatched using the gas price of the top-level HAPI operation and no markup.
     *
     * @param body the transaction body to be dispatched
     * @param payer the payer of the transaction
     * @return the gas requirement for the transaction to be dispatched
     */
    public long topLevelGasRequirement(@NonNull final TransactionBody body, @NonNull final AccountID payer) {
        return feeCalculator.applyAsLong(body, payer) / tinybarValues.topLevelTinybarGasPrice();
    }

    /**
     * Returns the gas price for the top-level HAPI operation.
     *
     * @return the gas price for the top-level HAPI operation
     */
    public long topLevelGasPrice() {
        return tinybarValues.topLevelTinybarGasPrice();
    }

    /**
     * Given a dispatch type, returns the canonical gas requirement for that dispatch type.
     * Useful when providing a ballpark gas requirement in the absence of a valid
     * transaction body for the dispatch type.
     *
     * @param dispatchType the dispatch type
     * @return the canonical gas requirement for that dispatch type
     */
    public long canonicalGasRequirement(@NonNull final DispatchType dispatchType) {
        return asGasRequirement(canonicalPriceInTinybars(dispatchType));
    }

    /**
     * Although mono-service compares the fixed {@code 100 gas} cost to the implied gas requirement of a
     * stand-in {@link com.hedera.hapi.node.transaction.TransactionGetRecordQuery}, this stand-in query's
     * cost will be less than {@code 100 gas} at any exchange rate that sustains the existence of the network.
     * So for simplicity, we drop that comparison and just return the fixed {@code 100 gas} cost.
     *
     * @return the minimum gas requirement for a view query
     */
    public long viewGasRequirement() {
        return FIXED_VIEW_GAS_COST;
    }

    /**
     * Given a dispatch type, returns the canonical price for that dispatch type.
     *
     * @param dispatchType the dispatch type
     * @return the canonical price for that dispatch type
     */
    public long canonicalPriceInTinybars(@NonNull final DispatchType dispatchType) {
        requireNonNull(dispatchType);
        return tinybarValues.asTinybars(dispatchPrices.canonicalPriceInTinycents(dispatchType));
    }

    /**
     * Given a dispatch, returns the canonical price for that dispatch.
     *
     * @param body the transaction body to be dispatched
     * @param payer the payer account
     * @return the canonical price for that dispatch
     */
    public long canonicalPriceInTinybars(@NonNull final TransactionBody body, @NonNull final AccountID payer) {
        return feeCalculator.applyAsLong(body, payer);
    }

    /**
     * Given a gas requirement, returns the equivalent tinybar cost at the current gas price.
     *
     * @param gas the gas requirement
     * @return the equivalent tinybar cost at the current gas price
     */
    public long gasCostInTinybars(final long gas) {
        return gas * tinybarValues.childTransactionTinybarGasPrice();
    }

    /**
     * Given a tinybar price, returns the equivalent gas requirement at the current gas price.
     *
     * @param tinybarPrice the tinybar price
     * @return the equivalent gas requirement at the current gas price
     */
    private long asGasRequirement(final long tinybarPrice) {
        return asGasRequirement(tinybarPrice, tinybarValues.childTransactionTinybarGasPrice());
    }

    private long asGasRequirement(final long tinybarPrice, final long gasPrice) {
        // We round up to the nearest gas unit, and then add 20% to account for the premium
        // of doing a HAPI operation from inside the EVM
        final var gasRequirement = (tinybarPrice + gasPrice - 1) / gasPrice;
        return gasRequirement + (gasRequirement / 5);
    }
}
