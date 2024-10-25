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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.associations;

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
 * Translates associate and dissociate calls to the HTS system contract. There are no special cases for
 * these calls, so the returned {@link Call} is simply an instance of {@link DispatchForResponseCodeHtsCall}.
 */
@Singleton
public class AssociationsTranslator extends AbstractCallTranslator<HtsCallAttempt> {
    public static final Function HRC_ASSOCIATE = new Function("associate()", ReturnTypes.INT);
    public static final Function ASSOCIATE_ONE = new Function("associateToken(address,address)", ReturnTypes.INT_64);
    public static final Function DISSOCIATE_ONE = new Function("dissociateToken(address,address)", ReturnTypes.INT_64);
    public static final Function HRC_DISSOCIATE = new Function("dissociate()", ReturnTypes.INT);
    public static final Function ASSOCIATE_MANY =
            new Function("associateTokens(address,address[])", ReturnTypes.INT_64);
    public static final Function DISSOCIATE_MANY =
            new Function("dissociateTokens(address,address[])", ReturnTypes.INT_64);

    private final AssociationsDecoder decoder;

    @Inject
    public AssociationsTranslator(@NonNull final AssociationsDecoder decoder) {
        this.decoder = decoder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return (attempt.isTokenRedirect() && matchesHrcSelector(attempt.selector()))
                || (!attempt.isTokenRedirect() && matchesClassicSelector(attempt.selector()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Call callFrom(@NonNull final HtsCallAttempt attempt) {
        return new DispatchForResponseCodeHtsCall(
                attempt,
                matchesHrcSelector(attempt.selector()) ? bodyForHrc(attempt) : bodyForClassic(attempt),
                AssociationsTranslator::gasRequirement);
    }

    public static long gasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        return systemContractGasCalculator.gasRequirement(body, DispatchType.ASSOCIATE, payerId);
    }

    private TransactionBody bodyForHrc(@NonNull final HtsCallAttempt attempt) {
        if (Arrays.equals(attempt.selector(), HRC_ASSOCIATE.selector())) {
            return decoder.decodeHrcAssociate(attempt);
        } else {
            return decoder.decodeHrcDissociate(attempt);
        }
    }

    private TransactionBody bodyForClassic(@NonNull final HtsCallAttempt attempt) {
        if (Arrays.equals(attempt.selector(), ASSOCIATE_ONE.selector())) {
            return decoder.decodeAssociateOne(attempt);
        } else if (Arrays.equals(attempt.selector(), ASSOCIATE_MANY.selector())) {
            return decoder.decodeAssociateMany(attempt);
        } else if (Arrays.equals(attempt.selector(), DISSOCIATE_ONE.selector())) {
            return decoder.decodeDissociateOne(attempt);
        } else {
            return decoder.decodeDissociateMany(attempt);
        }
    }

    private static boolean matchesHrcSelector(@NonNull final byte[] selector) {
        return Arrays.equals(selector, HRC_ASSOCIATE.selector()) || Arrays.equals(selector, HRC_DISSOCIATE.selector());
    }

    private static boolean matchesClassicSelector(@NonNull final byte[] selector) {
        return Arrays.equals(selector, ASSOCIATE_ONE.selector())
                || Arrays.equals(selector, DISSOCIATE_ONE.selector())
                || Arrays.equals(selector, ASSOCIATE_MANY.selector())
                || Arrays.equals(selector, DISSOCIATE_MANY.selector());
    }
}
