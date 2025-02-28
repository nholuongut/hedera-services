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

package com.hedera.node.app.service.addressbook.impl;

import static com.hedera.node.app.service.addressbook.impl.AddressBookServiceImpl.NODES_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.app.service.addressbook.ReadableNodeStore;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Iterator;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Nodes.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 */
public class ReadableNodeStoreImpl implements ReadableNodeStore {
    /** The underlying data storage class that holds the node data. */
    private final ReadableKVState<EntityNumber, Node> nodesState;

    /**
     * Create a new {@link ReadableNodeStoreImpl} instance.
     *
     * @param states The state to use.
     */
    public ReadableNodeStoreImpl(@NonNull final ReadableStates states) {
        requireNonNull(states);

        this.nodesState = states.get(NODES_KEY);
    }

    /**
     * Returns the node needed.
     *
     * @param nodeId node id being looked up
     * @return node
     */
    @Override
    @Nullable
    public Node get(final long nodeId) {
        return nodesState.get(EntityNumber.newBuilder().number(nodeId).build());
    }

    /**
     * Returns the number of topics in the state.
     * @return the number of topics in the state.
     */
    public long sizeOfState() {
        return nodesState.size();
    }

    protected <T extends ReadableKVState<EntityNumber, Node>> T nodesState() {
        return (T) nodesState;
    }

    @NonNull
    public Iterator<EntityNumber> keys() {
        return nodesState().keys();
    }
}
