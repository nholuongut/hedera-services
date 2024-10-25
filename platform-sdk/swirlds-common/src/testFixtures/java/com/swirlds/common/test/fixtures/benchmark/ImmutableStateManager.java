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

package com.swirlds.common.test.fixtures.benchmark;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.utility.AutoCloseableWrapper;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Get an immutable state.
 */
public class ImmutableStateManager<S extends MerkleNode> implements StateManager<S> {

    private final ConcurrentLinkedDeque<S> states;

    public ImmutableStateManager() {
        states = new ConcurrentLinkedDeque<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AutoCloseableWrapper<S> getState() {
        // FUTURE WORK this is not thread safe
        //  What if this state is deleted before we are finished with it?

        return new AutoCloseableWrapper<>(states.size() == 0 ? null : states.getLast(), () -> {});
    }

    /**
     * Get a queue of the current immutable states.
     */
    public ConcurrentLinkedDeque<S> getStates() {
        return states;
    }
}
