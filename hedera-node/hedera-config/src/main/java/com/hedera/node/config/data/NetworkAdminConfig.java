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

package com.hedera.node.config.data;

import com.hedera.node.config.NodeProperty;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * Configuration for the NetworkAdmin service. Contains only the path to the upgrade artifacts directory.
 *
 * @param upgradeArtifactsPath path to the location where upgrade files are stored once uncompressed, and upgrade
 *                             marker files are written
 */
@ConfigData("networkAdmin")
public record NetworkAdminConfig(
        @ConfigProperty(defaultValue = "data/upgrade/current") @NodeProperty String upgradeArtifactsPath) {}
