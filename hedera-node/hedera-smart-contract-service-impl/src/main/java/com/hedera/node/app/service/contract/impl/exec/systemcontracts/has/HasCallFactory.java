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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.has;

import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.configOf;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.proxyUpdaterFor;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.systemContractGasCalculatorOf;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.CallAddressChecks;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.CallFactory;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.CallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.SyntheticIds;
import com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Factory to create a new {@link HasCallAttempt} for a given input and message frame.
 */
@Singleton
public class HasCallFactory implements CallFactory {
    private final SyntheticIds syntheticIds;
    private final CallAddressChecks addressChecks;
    private final VerificationStrategies verificationStrategies;
    private final List<CallTranslator> callTranslators;

    @Inject
    public HasCallFactory(
            @NonNull final SyntheticIds syntheticIds,
            @NonNull final CallAddressChecks addressChecks,
            @NonNull final VerificationStrategies verificationStrategies,
            @NonNull @Named("HasTranslators") final List<CallTranslator> callTranslators) {
        this.syntheticIds = requireNonNull(syntheticIds);
        this.addressChecks = requireNonNull(addressChecks);
        this.verificationStrategies = requireNonNull(verificationStrategies);
        this.callTranslators = requireNonNull(callTranslators);
    }

    /**
     * Creates a new {@link HasCallAttempt} for the given input and message frame.
     *
     * @param input the input
     * @param frame the message frame
     * @return the new attempt
     * @throws RuntimeException if the call cannot be created
     */
    @Override
    public @NonNull HasCallAttempt createCallAttemptFrom(
            @NonNull final Bytes input,
            @NonNull final FrameUtils.CallType callType,
            @NonNull final MessageFrame frame) {
        requireNonNull(input);
        requireNonNull(frame);
        final var enhancement = proxyUpdaterFor(frame).enhancement();
        return new HasCallAttempt(
                input,
                frame.getSenderAddress(),
                frame.getSenderAddress(),
                addressChecks.hasParentDelegateCall(frame),
                enhancement,
                configOf(frame),
                syntheticIds.converterFor(enhancement.nativeOperations()),
                verificationStrategies,
                systemContractGasCalculatorOf(frame),
                callTranslators,
                frame.isStatic());
    }
}
