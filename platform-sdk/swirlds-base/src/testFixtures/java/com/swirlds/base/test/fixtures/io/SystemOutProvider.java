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

package com.swirlds.base.test.fixtures.io;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.stream.Stream;

/**
 * Provides access to the lines written to {@link System#out}. This is used to verify the output of {@link System#out}
 * for test. A test that uses this interface must be annotated with {@link WithSystemOut}. By doing so a
 * {@link SystemOutProvider} instance can be injected into the test by using the {@link jakarta.inject.Inject}
 * annotation.
 *
 * @see WithSystemOut
 */
public interface SystemOutProvider {

    /**
     * Returns a stream of lines written to {@link System#out}.
     *
     * @return a stream of lines written to {@link System#out}
     */
    @NonNull
    Stream<String> getLines();
}
