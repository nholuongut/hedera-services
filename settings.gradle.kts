/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

pluginManagement { includeBuild("gradle/plugins") }

plugins { id("com.hedera.gradle.settings") }

// "BOM" with versions of 3rd party dependencies
include("hedera-dependency-versions")

// Project to aggregate code coverage data for the whole repository into one report
include(":reports", "gradle/reports")

// Hedera Node projects
include(":app", "hedera-node/hedera-app")

include(":app-hapi-fees", "hedera-node/hapi-fees")

include(":app-hapi-utils", "hedera-node/hapi-utils")

include(":app-service-addressbook", "hedera-node/hedera-addressbook-service")

include(":app-service-addressbook-impl", "hedera-node/hedera-addressbook-service-impl")

include(":app-service-consensus", "hedera-node/hedera-consensus-service")

include(":app-service-consensus-impl", "hedera-node/hedera-consensus-service-impl")

include(":app-service-contract", "hedera-node/hedera-smart-contract-service")

include(":app-service-contract-impl", "hedera-node/hedera-smart-contract-service-impl")

include(":hedera-evm", "hedera-node/hedera-evm")

include(":hedera-evm-impl", "hedera-node/hedera-evm-impl")

include(":app-service-file", "hedera-node/hedera-file-service")

include(":app-service-file-impl", "hedera-node/hedera-file-service-impl")

include(":app-service-network-admin", "hedera-node/hedera-network-admin-service")

include(":app-service-network-admin-impl", "hedera-node/hedera-network-admin-service-impl")

include(":app-service-schedule", "hedera-node/hedera-schedule-service")

include(":app-service-schedule-impl", "hedera-node/hedera-schedule-service-impl")

include(":app-service-token", "hedera-node/hedera-token-service")

include(":app-service-token-impl", "hedera-node/hedera-token-service-impl")

include(":app-service-util", "hedera-node/hedera-util-service")

include(":app-service-util-impl", "hedera-node/hedera-util-service-impl")

include(":app-spi", "hedera-node/hedera-app-spi")

include(":config", "hedera-node/hedera-config")

include(":hapi", "hapi")

include(":services-cli", "hedera-node/cli-clients")

include(":test-clients", "hedera-node/test-clients")

// Platform SDK projects
include(":swirlds-platform", "platform-sdk")

include(":swirlds", "platform-sdk/swirlds")

include(":swirlds-base", "platform-sdk/swirlds-base")

include(":swirlds-logging", "platform-sdk/swirlds-logging")

include(":swirlds-logging-log4j-appender", "platform-sdk/swirlds-logging-log4j-appender")

include(":swirlds-common", "platform-sdk/swirlds-common")

include(":swirlds-config-api", "platform-sdk/swirlds-config-api")

include(":swirlds-config-processor", "platform-sdk/swirlds-config-processor")

include(":swirlds-config-impl", "platform-sdk/swirlds-config-impl")

include(":swirlds-metrics-api", "platform-sdk/swirlds-metrics-api")

include(":swirlds-metrics-impl", "platform-sdk/swirlds-metrics-impl")

include(":swirlds-config-extensions", "platform-sdk/swirlds-config-extensions")

include(":swirlds-fchashmap", "platform-sdk/swirlds-fchashmap")

include(":swirlds-fcqueue", "platform-sdk/swirlds-fcqueue")

include(":swirlds-merkle", "platform-sdk/swirlds-merkle")

include(":swirlds-merkledb", "platform-sdk/swirlds-jasperdb")

include(":swirlds-virtualmap", "platform-sdk/swirlds-virtualmap")

include(":swirlds-platform-core", "platform-sdk/swirlds-platform-core")

include(":swirlds-state-api", "platform-sdk/swirlds-state-api")

include(":swirlds-cli", "platform-sdk/swirlds-cli")

include(":swirlds-benchmarks", "platform-sdk/swirlds-benchmarks")

include(":swirlds-platform-test", "platform-sdk/swirlds-unit-tests/core/swirlds-platform-test")

// Platform demo/test applications
includeAllProjects("platform-sdk/platform-apps/demos")

includeAllProjects("platform-sdk/platform-apps/tests")

//Platform-base demo applications
include(":swirlds-platform-base-example", "example-apps/swirlds-platform-base-example")

fun include(name: String, path: String) {
    include(name)
    project(name).projectDir = File(rootDir, path)
}

fun includeAllProjects(containingFolder: String) {
    File(rootDir, containingFolder).listFiles()?.forEach { folder ->
        if (File(folder, "build.gradle.kts").exists()) {
            val name = ":${folder.name}"
            include(name)
            project(name).projectDir = folder
        }
    }
}

// The HAPI API version to use for Protobuf sources.
val hapiProtoVersion = "0.52.0"

dependencyResolutionManagement {
    // Protobuf tool versions
    versionCatalogs.create("libs") {
        version("google-proto", "3.19.4")
        version("grpc-proto", "1.45.1")
        version("hapi-proto", hapiProtoVersion)

        plugin("pbj", "com.hedera.pbj.pbj-compiler").version("0.8.9")
    }
}
