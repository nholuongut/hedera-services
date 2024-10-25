/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit.extensions;

import static com.hedera.services.bdd.junit.extensions.ExtensionUtils.hapiTestMethodOf;
import static org.junit.platform.commons.support.AnnotationSupport.isAnnotated;

import com.hedera.services.bdd.junit.GenesisHapiTest;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.junit.hedera.HederaNetwork;
import com.hedera.services.bdd.junit.hedera.embedded.EmbeddedNetwork;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.RepeatableKeyGenerator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * An extension that binds the target network to the current thread before invoking
 * each {@link HapiTest}-annotated test method.
 *
 * <p><b>(FUTURE)</b> - implement {@link org.junit.jupiter.api.extension.BeforeAllCallback}
 * and {@link org.junit.jupiter.api.extension.AfterAllCallback} to handle creating "private"
 * networks for annotated test classes and targeting them instead of the shared network.
 */
public class NetworkTargetingExtension implements BeforeEachCallback, AfterEachCallback {
    public static final AtomicReference<HederaNetwork> SHARED_NETWORK = new AtomicReference<>();
    public static final AtomicReference<RepeatableKeyGenerator> REPEATABLE_KEY_GENERATOR = new AtomicReference<>();

    @Override
    public void beforeEach(@NonNull final ExtensionContext extensionContext) {
        hapiTestMethodOf(extensionContext).ifPresent(method -> {
            if (isAnnotated(method, GenesisHapiTest.class)) {
                final var targetNetwork = new EmbeddedNetwork(method.getName().toUpperCase(), method.getName());
                targetNetwork.start();
                HapiSpec.TARGET_NETWORK.set(targetNetwork);
            } else {
                HapiSpec.TARGET_NETWORK.set(SHARED_NETWORK.get());
                // If there are properties to preserve, bind that info to the thread before executing the test factory
                if (isAnnotated(method, LeakyHapiTest.class)) {
                    HapiSpec.PROPERTIES_TO_PRESERVE.set(
                            List.of(method.getAnnotation(LeakyHapiTest.class).overrides()));
                }
            }
        });
    }

    @Override
    public void afterEach(@NonNull final ExtensionContext extensionContext) {
        HapiSpec.TARGET_NETWORK.remove();
        HapiSpec.PROPERTIES_TO_PRESERVE.remove();
    }
}
