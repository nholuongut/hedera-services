/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.fixtures;

import edu.umd.cs.findbugs.annotations.NonNull;

/** Utility for creating human-readable names deterministically */
public final class NameUtils {
    private NameUtils() {}

    private static final String[] NAMES = {
        "Austin", "Bob", "Cody", "Dave", "Edward", "Fred", "Gina", "Hank", "Iris", "Judy", "Kelly",
        "Lazar", "Mike", "Nina", "Olie", "Pete", "Quin", "Rita", "Susi", "Tina", "Ursa", "Vera",
        "Will", "Xeno", "York", "Zeke"
    };

    /**
     * Return a human-readable and deterministic name based on a long value
     *
     * @param value the value to base the name on
     * @return a human-readable name
     */
    @NonNull
    public static String getName(final long value) {
        return NAMES[((int) value) % NAMES.length] + (value >= NAMES.length ? ("-" + String.format("%x", value)) : "");
    }
}
