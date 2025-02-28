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

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@code RecordBuilder} specialization for tracking {@code NodeStakeUpdate} at midnight UTC every day.
 */
public interface NodeStakeUpdateRecordBuilder {
    /**
     * Sets the status.
     *
     * @param status the status
     * @return the builder
     */
    NodeStakeUpdateRecordBuilder status(@NonNull ResponseCodeEnum status);

    /**
     * Sets the transaction.
     *
     * @param transaction the transaction
     * @return the builder
     */
    @NonNull
    NodeStakeUpdateRecordBuilder transaction(@NonNull final Transaction transaction);

    /**
     * Sets the record's memo.
     *
     * @param memo the memo
     * @return the builder
     */
    @NonNull
    NodeStakeUpdateRecordBuilder memo(@NonNull final String memo);
}
