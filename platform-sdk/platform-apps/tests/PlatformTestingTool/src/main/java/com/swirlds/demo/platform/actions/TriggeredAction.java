/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.demo.platform.actions;

import java.util.function.Supplier;

/**
 * @param <N>
 * @param <S>
 */
public class TriggeredAction<N, S> {

    /**
     *
     */
    private Trigger<N, S> trigger;

    /**
     *
     */
    private Action<N, S> action;

    /**
     *
     */
    private boolean autoReset;

    /**
     *
     */
    public TriggeredAction() {
        this.autoReset = false;
    }

    /**
     * @param trigger
     * @param action
     */
    public TriggeredAction(final Trigger<N, S> trigger, final Action<N, S> action) {
        this();

        if (trigger == null) {
            throw new IllegalArgumentException("trigger");
        }

        if (action == null) {
            throw new IllegalArgumentException("action");
        }

        this.trigger = trigger;
        this.action = action;
    }

    /**
     * @param node
     * @param state
     */
    public void check(final N node, final S state) {
        if (trigger.test(node, state)) {
            action.execute(node, state);

            if (autoReset) {
                reset();
            }
        }
    }

    /**
     * @param node
     * @param stateSupplier
     */
    public void check(final N node, final Supplier<S> stateSupplier) {
        if (stateSupplier == null) {
            throw new IllegalArgumentException("stateSupplier");
        }

        check(node, stateSupplier.get());
    }

    /**
     * @param nodeSupplier
     * @param stateSupplier
     */
    public void check(final Supplier<N> nodeSupplier, final Supplier<S> stateSupplier) {
        if (nodeSupplier == null) {
            throw new IllegalArgumentException("nodeSupplier");
        }

        check(nodeSupplier.get(), stateSupplier);
    }

    /**
     * @return
     */
    public TriggeredAction<N, S> withAutoReset() {
        autoReset = true;
        return this;
    }

    /**
     * @return
     */
    public TriggeredAction<N, S> withoutAutoReset() {
        autoReset = false;
        return this;
    }

    /**
     *
     */
    public void reset() {}

    /**
     * @return
     */
    public Trigger<N, S> getTrigger() {
        return trigger;
    }

    /**
     * @param trigger
     * @return
     */
    public TriggeredAction<N, S> withTrigger(final Trigger<N, S> trigger) {
        this.trigger = trigger;
        return this;
    }

    /**
     * @return
     */
    public Action<N, S> getAction() {
        return action;
    }

    /**
     * @param action
     * @return
     */
    public TriggeredAction<N, S> withAction(final Action<N, S> action) {
        this.action = action;
        return this;
    }
}
