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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.freeze;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall.FailureCustomizer.NOOP_CUSTOMIZER;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Translates {@code freeze()}, {@code unfreeze()} calls to the HTS system contract.
 */
@Singleton
public class FreezeUnfreezeTranslator extends AbstractCallTranslator<HtsCallAttempt> {
    public static final Function FREEZE = new Function("freezeToken(address,address)", ReturnTypes.INT_64);
    public static final Function UNFREEZE = new Function("unfreezeToken(address,address)", ReturnTypes.INT_64);
    private final FreezeUnfreezeDecoder decoder;

    @Inject
    public FreezeUnfreezeTranslator(@NonNull final FreezeUnfreezeDecoder decoder) {
        this.decoder = decoder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return matchesClassicSelector(attempt.selector());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Call callFrom(@NonNull final HtsCallAttempt attempt) {
        return new DispatchForResponseCodeHtsCall(
                attempt,
                bodyForClassic(attempt),
                Arrays.equals(attempt.selector(), FREEZE.selector())
                        ? FreezeUnfreezeTranslator::freezeGasRequirement
                        : FreezeUnfreezeTranslator::unfreezeGasRequirement,
                NOOP_CUSTOMIZER);
    }

    public static long freezeGasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        return systemContractGasCalculator.gasRequirement(body, DispatchType.FREEZE, payerId);
    }

    public static long unfreezeGasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        return systemContractGasCalculator.gasRequirement(body, DispatchType.UNFREEZE, payerId);
    }

    private TransactionBody bodyForClassic(@NonNull final HtsCallAttempt attempt) {
        if (Arrays.equals(attempt.selector(), FREEZE.selector())) {
            return decoder.decodeFreeze(attempt);
        } else {
            return decoder.decodeUnfreeze(attempt);
        }
    }

    private static boolean matchesClassicSelector(@NonNull final byte[] selector) {
        return Arrays.equals(selector, FREEZE.selector()) || Arrays.equals(selector, UNFREEZE.selector());
    }
}
