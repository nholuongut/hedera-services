/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule.impl.handlers;

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.fromPbj;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.scheduled.ScheduleDeleteTransactionBody;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hapi.fees.usage.SigUsage;
import com.hedera.node.app.hapi.fees.usage.schedule.ScheduleOpsUsage;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.node.app.service.schedule.ReadableScheduleStore;
import com.hedera.node.app.service.schedule.ScheduleRecordBuilder;
import com.hedera.node.app.service.schedule.WritableScheduleStore;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.SchedulingConfig;
import com.hederahashgraph.api.proto.java.FeeData;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#SCHEDULE_DELETE}.
 */
@Singleton
public class ScheduleDeleteHandler extends AbstractScheduleHandler implements TransactionHandler {
    private final ScheduleOpsUsage scheduleOpsUsage = new ScheduleOpsUsage();

    @Inject
    public ScheduleDeleteHandler() {
        super();
    }

    @Override
    public void pureChecks(@Nullable final TransactionBody currentTransaction) throws PreCheckException {
        getValidScheduleDeleteBody(currentTransaction);
    }

    @NonNull
    private ScheduleDeleteTransactionBody getValidScheduleDeleteBody(@Nullable final TransactionBody currentTransaction)
            throws PreCheckException {
        if (currentTransaction != null) {
            final ScheduleDeleteTransactionBody scheduleDeleteTransaction = currentTransaction.scheduleDelete();
            if (scheduleDeleteTransaction != null) {
                if (scheduleDeleteTransaction.scheduleID() != null) {
                    return scheduleDeleteTransaction;
                } else {
                    throw new PreCheckException(ResponseCodeEnum.INVALID_SCHEDULE_ID);
                }
            } else {
                throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION_BODY);
            }
        } else {
            throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION);
        }
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        Objects.requireNonNull(context, NULL_CONTEXT_MESSAGE);
        final ReadableScheduleStore scheduleStore = context.createStore(ReadableScheduleStore.class);
        final SchedulingConfig schedulingConfig = context.configuration().getConfigData(SchedulingConfig.class);
        final boolean isLongTermEnabled = schedulingConfig.longTermEnabled();
        final TransactionBody currentTransaction = context.body();
        final ScheduleDeleteTransactionBody scheduleDeleteTransaction = getValidScheduleDeleteBody(currentTransaction);
        if (scheduleDeleteTransaction.scheduleID() != null) {
            final Schedule scheduleData =
                    preValidate(scheduleStore, isLongTermEnabled, scheduleDeleteTransaction.scheduleID());
            final Key adminKey = scheduleData.adminKey();
            if (adminKey != null) context.requireKey(adminKey);
            else throw new PreCheckException(ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE);
            // Once deleted or executed, no later transaction will change that status.
            if (scheduleData.deleted()) throw new PreCheckException(ResponseCodeEnum.SCHEDULE_ALREADY_DELETED);
            if (scheduleData.executed()) throw new PreCheckException(ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED);
        } else {
            throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION_BODY);
        }
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        Objects.requireNonNull(context, NULL_CONTEXT_MESSAGE);
        final WritableScheduleStore scheduleStore = context.storeFactory().writableStore(WritableScheduleStore.class);
        final TransactionBody currentTransaction = context.body();
        final SchedulingConfig schedulingConfig = context.configuration().getConfigData(SchedulingConfig.class);
        try {
            final ScheduleDeleteTransactionBody scheduleToDelete = getValidScheduleDeleteBody(currentTransaction);
            final ScheduleID idToDelete = scheduleToDelete.scheduleID();
            if (idToDelete != null) {
                final boolean isLongTermEnabled = schedulingConfig.longTermEnabled();
                final Schedule scheduleData = reValidate(scheduleStore, isLongTermEnabled, idToDelete);
                if (scheduleData.hasAdminKey()) {
                    final SignatureVerification verificationResult =
                            context.keyVerifier().verificationFor(scheduleData.adminKeyOrThrow());
                    if (verificationResult.passed()) {
                        scheduleStore.delete(idToDelete, context.consensusNow());
                        final ScheduleRecordBuilder scheduleRecords =
                                context.recordBuilders().getOrCreate(ScheduleRecordBuilder.class);
                        scheduleRecords.scheduleID(idToDelete);
                    } else {
                        throw new HandleException(ResponseCodeEnum.UNAUTHORIZED);
                    }
                } else {
                    throw new HandleException(ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE);
                }
            } else {
                throw new HandleException(ResponseCodeEnum.INVALID_SCHEDULE_ID);
            }
        } catch (final IllegalStateException ignored) {
            throw new HandleException(ResponseCodeEnum.INVALID_SCHEDULE_ID);
        } catch (final PreCheckException translate) {
            throw new HandleException(translate.responseCode());
        }
    }

    /**
     * Verify that the transaction and schedule still meet the validation criteria expressed in the
     * {@link AbstractScheduleHandler#preValidate(ReadableScheduleStore, boolean, ScheduleID)} method.
     * @param scheduleStore a Readable source of Schedule data from state
     * @param isLongTermEnabled a flag indicating if long term scheduling is enabled in configuration.
     * @param idToDelete the Schedule ID of the item to mark as deleted.
     * @return a schedule metadata read from state for the ID given, if all validation checks pass.
     * @throws HandleException if any validation check fails.
     */
    @NonNull
    protected Schedule reValidate(
            @NonNull final ReadableScheduleStore scheduleStore,
            final boolean isLongTermEnabled,
            @Nullable final ScheduleID idToDelete)
            throws HandleException {
        try {
            final Schedule validSchedule = preValidate(scheduleStore, isLongTermEnabled, idToDelete);
            // Once deleted or executed, no later transaction will change that status.
            if (validSchedule.deleted()) throw new HandleException(ResponseCodeEnum.SCHEDULE_ALREADY_DELETED);
            if (validSchedule.executed()) throw new HandleException(ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED);
            return validSchedule;
        } catch (final PreCheckException translated) {
            throw new HandleException(translated.responseCode());
        }
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        requireNonNull(feeContext);
        final var op = feeContext.body();

        final var scheduleStore = feeContext.readableStore(ReadableScheduleStore.class);
        final var schedule = scheduleStore.get(op.scheduleDeleteOrThrow().scheduleIDOrThrow());

        return feeContext
                .feeCalculatorFactory()
                .feeCalculator(SubType.DEFAULT)
                .legacyCalculate(sigValueObj -> usageGiven(
                        fromPbj(op),
                        sigValueObj,
                        schedule,
                        feeContext
                                .configuration()
                                .getConfigData(LedgerConfig.class)
                                .scheduleTxExpiryTimeSecs()));
    }

    private FeeData usageGiven(
            final com.hederahashgraph.api.proto.java.TransactionBody txn,
            final SigValueObj svo,
            final Schedule schedule,
            final long scheduledTxExpiryTimeSecs) {
        final var sigUsage = new SigUsage(svo.getTotalSigCount(), svo.getSignatureSize(), svo.getPayerAcctSigCount());

        if (schedule != null) {
            return scheduleOpsUsage.scheduleDeleteUsage(txn, sigUsage, schedule.calculatedExpirationSecond());
        } else {
            final long latestExpiry =
                    txn.getTransactionID().getTransactionValidStart().getSeconds() + scheduledTxExpiryTimeSecs;
            return scheduleOpsUsage.scheduleDeleteUsage(txn, sigUsage, latestExpiry);
        }
    }
}
