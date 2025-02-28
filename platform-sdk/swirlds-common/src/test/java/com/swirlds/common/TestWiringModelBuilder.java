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

package com.swirlds.common;

import static com.swirlds.common.test.fixtures.junit.tags.TestQualifierTags.TIMING_SENSITIVE;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.model.WiringModelBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.Tag;

/**
 * A simple version of a wiring model for scenarios where the wiring model is not needed.
 */
@Tag(TIMING_SENSITIVE)
public final class TestWiringModelBuilder {

    private TestWiringModelBuilder() {}

    /**
     * Build a wiring model using the default configuration.
     *
     * @return a new wiring model
     */
    @NonNull
    public static WiringModel create() {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        return WiringModelBuilder.create(platformContext).build();
    }
}
