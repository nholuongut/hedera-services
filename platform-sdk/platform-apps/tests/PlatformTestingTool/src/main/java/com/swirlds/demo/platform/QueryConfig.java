/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.demo.platform;

/**
 * This class is responsible for configuring how does PTT send queries for querying a leaf in the latest signed state
 */
public class QueryConfig {
    /** defines how many queries should be sent in each second for querying a leaf in the latest signed state */
    private long queriesSentPerSec = -1;

    public long getQueriesSentPerSec() {
        return queriesSentPerSec;
    }

    public void setQueriesSentPerSec(final long queriesSentPerSec) {
        this.queriesSentPerSec = queriesSentPerSec;
    }
}
