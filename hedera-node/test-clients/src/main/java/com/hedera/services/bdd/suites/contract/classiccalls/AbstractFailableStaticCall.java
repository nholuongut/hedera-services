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

package com.hedera.services.bdd.suites.contract.classiccalls;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

public abstract class AbstractFailableStaticCall extends AbstractFailableCall {
    public AbstractFailableStaticCall(@NonNull Set<ClassicFailureMode> failureModes) {
        super(failureModes);
    }

    @Override
    public boolean staticCallOk() {
        return true;
    }
}
