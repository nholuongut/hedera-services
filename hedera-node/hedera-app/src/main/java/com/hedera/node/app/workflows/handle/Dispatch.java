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

package com.hedera.node.app.workflows.handle;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.fees.FeeAccumulator;
import com.hedera.node.app.service.token.records.FinalizeContext;
import com.hedera.node.app.signature.AppKeyVerifier;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.node.app.workflows.prehandle.PreHandleWorkflow;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.state.spi.Service;
import com.swirlds.state.spi.info.NodeInfo;
import java.time.Instant;
import java.util.Set;

/**
 * The fundamental unit of work in the handle workflow.
 *
 * <p>A dispatch can originate from any of,
 * <ol>
 *     <li>A user-submitted HAPI transaction at consensus.</li>
 *     <li>A {@link Service} that needs to delegate to another
 *     {@link Service} to reuse logic or change state owned by
 *     that service.</li>
 *     <li>A facility in the app itself; for example, the
 *     auto-renewal/expiration facility.</li>
 * </ol>
 *
 * <p>In the scope of a dispatch we,
 * <ol>
 *     <li>Validate preconditions on creator, payer, and inputs.</li>
 *     <li>Charge fees for the work implied by the inputs.</li>
 *     <li>Check authorization for this work.</li>
 *     <li>Screen for network capacity to do the work.</li>
 *     <li>Dispatch the work to an appropriate handler.</li>
 *     <li>Finalize a record that captures all state changed
 *     by the work for the node's output stream.</li>
 *     <li>Commit all these changes as an atomic unit.</li>
 * </ol>
 *
 * <p>A {@link Dispatch} gives the handler ultimately doing its
 * work a {@link HandleContext}. This context does not merely include
 * details on the work to be done, but crucially supports triggering
 * further "child" dispatches that will themselves go through the
 * lifecycle described above.
 *
 * @see DispatchProcessor
 * @see DispatchHandleContext
 * @see TransactionCategory
 */
public interface Dispatch {
    /**
     * The builder for the transaction record of this dispatch.
     *
     * @return the builder
     */
    SingleTransactionRecordBuilderImpl recordBuilder();

    /**
     * The configuration for the dispatch.
     *
     * @return the configuration
     */
    Configuration config();

    /**
     * The fees to be charged for this dispatch.
     *
     * @return the fees
     */
    Fees fees();

    /**
     * The transaction info for the dispatch.
     *
     * @return the transaction info
     */
    TransactionInfo txnInfo();

    /**
     * The id of the payer for this dispatch. Depending on the category
     * of the dispatch, may require proof of authorization in the form
     * of valid signatures.
     *
     * @return the payer
     */
    AccountID payerId();

    /**
     * The readable store factory for the dispatch.
     *
     * @return the readable store factory
     */
    ReadableStoreFactory readableStoreFactory();

    /**
     * The fee accumulator for the dispatch.
     *
     * @return the fee accumulator
     */
    FeeAccumulator feeAccumulator();

    /**
     * The key verifier for the dispatch.
     *
     * @return the key verifier
     */
    AppKeyVerifier keyVerifier();

    /**
     * The node info of the dispatch creator.
     *
     * @return the creator
     */
    NodeInfo creatorInfo();

    /**
     * The consensus time at which the dispatch is being done.
     *
     * @return the consensus time
     */
    Instant consensusNow();

    /**
     * The keys that must have signed to fully authorize the work
     * in this dispatch.
     *
     * @return the required keys
     */
    Set<Key> requiredKeys();

    /**
     * The hollow accounts that must have provided signatures proving
     * ownership of the keys matching their aliases to fully authorize
     * the work in this dispatch.
     *
     * @return the hollow accounts
     */
    Set<Account> hollowAccounts();

    /**
     * The handle context for the work done in this dispatch.
     *
     * @return the handle context
     */
    HandleContext handleContext();

    /**
     * The savepoint stack for this dispatch.
     *
     * @return the savepoint stack
     */
    SavepointStackImpl stack();

    /**
     * The transaction category for this dispatch.
     *
     * @return the transaction category
     */
    TransactionCategory txnCategory();

    /**
     * The context in which to finalize the record of this dispatch.
     *
     * @return the finalize context
     */
    FinalizeContext finalizeContext();

    /**
     * The record list builder for the dispatch.
     *
     * @return the record list builder
     */
    RecordListBuilder recordListBuilder();

    /**
     * The platform state for the dispatch.
     *
     * @return the platform state
     */
    PlatformState platformState();

    /**
     * The result of applying the {@link PreHandleWorkflow} to the dispatch;
     * will be a synthetic result for a child dispatch.
     *
     * @return the pre-handle result
     */
    PreHandleResult preHandleResult();
}
