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

import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import java.util.List;

/**
 * A {@code RecordBuilder} specialization for reading the transfer list from child records.
 */
public interface ChildRecordBuilder {

    /**
     * Get the transfer list from the child record.
     *
     * @return the transfer list
     */
    TransferList transferList();

    /**
     * Get the token transfer lists, if any, from the child record.
     *
     * @return the token transfer lists
     */
    List<TokenTransferList> tokenTransferLists();
}
