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

package com.hedera.services.bdd.junit.hedera.embedded.fakes;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.transaction.SwirldTransaction;
import com.swirlds.platform.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;

public class FakeEvent implements Event {
    private final NodeId creatorId;
    private final Instant timeCreated;
    private final SemanticVersion version;
    public final SwirldTransaction transaction;

    public FakeEvent(
            @NonNull final NodeId creatorId,
            @NonNull final Instant timeCreated,
            @NonNull final SemanticVersion version,
            @NonNull final SwirldTransaction transaction) {
        this.version = requireNonNull(version);
        this.creatorId = requireNonNull(creatorId);
        this.timeCreated = requireNonNull(timeCreated);
        this.transaction = requireNonNull(transaction);
    }

    @Override
    public Iterator<Transaction> transactionIterator() {
        return Collections.singleton((Transaction) transaction).iterator();
    }

    @Override
    public Instant getTimeCreated() {
        return timeCreated;
    }

    @NonNull
    @Override
    public NodeId getCreatorId() {
        return creatorId;
    }

    @NonNull
    @Override
    public SemanticVersion getSoftwareVersion() {
        return requireNonNull(version);
    }
}
