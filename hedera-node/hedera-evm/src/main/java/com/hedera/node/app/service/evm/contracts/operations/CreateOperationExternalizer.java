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

package com.hedera.node.app.service.evm.contracts.operations;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Handles side effects for {@link AbstractEvmRecordingCreateOperation}
 */
public interface CreateOperationExternalizer {
    /**
     * Handle external side effects
     * @param frame         current message frame
     * @param childFrame    child message frame to be created
     */
    void externalize(MessageFrame frame, MessageFrame childFrame);

    /**
     * Should lazy creation fail based on environment
     * @param frame current message frame
     * @param contractAddress the target contract address
     * @return should it fail
     */
    boolean shouldFailBasedOnLazyCreation(MessageFrame frame, Address contractAddress);
}
