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

package com.hedera.node.app.state;

import com.swirlds.platform.state.PlatformState;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PlatformStateAccessor {
    private PlatformState platformState = null;

    @Inject
    public PlatformStateAccessor() {
        // Default constructor
    }

    public PlatformState getPlatformState() {
        return platformState;
    }

    public void setPlatformState(PlatformState platformState) {
        this.platformState = platformState;
    }
}
