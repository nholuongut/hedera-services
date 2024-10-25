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

package com.hedera.node.config.types;

import com.hedera.node.config.validation.EmulatesMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * A simple key-value pair. This record is used to create {@link java.util.Map} like structures for config data
 * properties. See {@link EmulatesMap} for more details.
 */
public record KeyValuePair(@NonNull String key, @NonNull String value) {

    /**
     * Creates a new {@link KeyValuePair} instance.
     *
     * @param key   the key
     * @param value the value
     * @throws NullPointerException if either key or value is null
     */
    public KeyValuePair {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
    }
}
