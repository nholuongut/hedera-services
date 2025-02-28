/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hapi.fees.usage.util;

import com.hedera.node.app.hapi.fees.usage.BaseTransactionMeta;
import com.hedera.node.app.hapi.fees.usage.SigUsage;
import com.hedera.node.app.hapi.fees.usage.state.UsageAccumulator;
import javax.inject.Inject;

public class UtilOpsUsage {

    @Inject
    public UtilOpsUsage() {
        // Default constructor
    }

    public void prngUsage(
            final SigUsage sigUsage,
            final BaseTransactionMeta baseMeta,
            final UtilPrngMeta utilPrngMeta,
            final UsageAccumulator accumulator) {
        accumulator.resetForTransaction(baseMeta, sigUsage);
        var baseSize = utilPrngMeta.getMsgBytesUsed();
        accumulator.addBpt(baseSize);
    }
}
