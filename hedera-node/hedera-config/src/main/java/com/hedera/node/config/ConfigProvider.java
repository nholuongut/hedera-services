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

package com.hedera.node.config;

import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The ConfigProvider interface is used to provide the configuration. This interface can be seen as the "config
 * facility". Whenever you want to access a configuration property that can change at runtime you should not store the
 * {@link Configuration} instance.
 */
public interface ConfigProvider {

    /**
     * Returns the configuration.
     *
     * @return the configuration
     */
    @NonNull
    VersionedConfiguration getConfiguration();
}
