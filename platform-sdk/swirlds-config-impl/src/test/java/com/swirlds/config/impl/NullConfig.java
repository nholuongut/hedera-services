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

package com.swirlds.config.impl;

import static com.swirlds.config.api.ConfigProperty.NULL_DEFAULT_VALUE;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.util.List;
import java.util.Set;

@ConfigData("null")
public record NullConfig(
        @ConfigProperty(defaultValue = NULL_DEFAULT_VALUE) List<Integer> list,
        @ConfigProperty(defaultValue = NULL_DEFAULT_VALUE) Set<Integer> set,
        @ConfigProperty(defaultValue = NULL_DEFAULT_VALUE) String value) {}
