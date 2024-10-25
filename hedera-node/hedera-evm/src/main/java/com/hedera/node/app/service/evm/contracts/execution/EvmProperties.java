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

package com.hedera.node.app.service.evm.contracts.execution;

import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;

public interface EvmProperties {

    /**
     * @return the chain ID in bytes32 format.
     */
    Bytes32 chainIdBytes32();

    String evmVersion();

    Address fundingAccountAddress();

    boolean dynamicEvmVersion();

    int maxGasRefundPercentage();

    boolean isRedirectTokenCallsEnabled();

    boolean isLazyCreationEnabled();

    boolean isCreate2Enabled();

    boolean allowCallsToNonContractAccounts();

    Set<Address> grandfatherContracts();

    boolean callsToNonExistingEntitiesEnabled(Address target);
}
