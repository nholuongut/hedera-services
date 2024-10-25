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

package com.swirlds.platform.system;

import com.hedera.hapi.node.base.SemanticVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Set;

/**
 * Holds a static reference to information about the current software version. Needed due to inability to cleanly inject
 * contextual data during deserialization.
 *
 * @deprecated this class is a short term work around, do not add new dependencies on this class
 */
@Deprecated
public final class StaticSoftwareVersion {

    /**
     * The current software version.
     */
    private static Set<Long> softwareVersionClassIdSet;

    /**
     * Semantic version of the software.
     */
    private static SemanticVersion semanticVersion;

    private StaticSoftwareVersion() {}

    /**
     * Set the current software version.
     *
     * @param softwareVersion the current software version
     */
    public static void setSoftwareVersion(@NonNull final SoftwareVersion softwareVersion) {
        softwareVersionClassIdSet = Set.of(softwareVersion.getClassId());
        semanticVersion = softwareVersion.getPbjSemanticVersion();
    }

    /**
     * Reset this object. Required for testing.
     */
    public static void reset() {
        softwareVersionClassIdSet = null;
    }

    /**
     * Get a set that contains the class ID of the current software version. A convenience method that avoids the
     * recreation of the set every time it is needed.
     *
     * @return a set that contains the class ID of the current software version
     */
    @NonNull
    public static Set<Long> getSoftwareVersionClassIdSet() {
        if (softwareVersionClassIdSet == null) {
            throw new IllegalStateException("Software version not set");
        }
        return softwareVersionClassIdSet;
    }

    /**
     * Get the semantic version of the software.
     *
     * @return the semantic version of the software
     */
    @Nullable
    public static SemanticVersion getSemanticVersion() {
        return semanticVersion;
    }
}
