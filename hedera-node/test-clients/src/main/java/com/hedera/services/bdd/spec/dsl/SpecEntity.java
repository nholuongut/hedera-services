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

package com.hedera.services.bdd.spec.dsl;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.hedera.HederaNetwork;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Encapsulates logic to manage a Hedera entity (e.g., account, file, or contract) in the
 * context of a {@link HapiSpec}.
 *
 * <p>Hides details of interacting with the {@link HapiSpecRegistry} from {@link HapiTest}
 * authors, who can simply inject POJO entities into their test classes and methods and
 * call methods on those objects to perform HAPI operations scoped to the managed entities.
 */
public interface SpecEntity {
    /**
     * Returns the {@link HapiSpecRegistry} name of this entity.
     *
     * @return the name of the entity
     */
    String name();

    /**
     * Returns the logic to register with a spec all information about this entity on a
     * given {@link HederaNetwork}, if it exists there.
     *
     * @param network the network to get the registrar for
     * @return the entity's registrar for the network, or {@code null} if it does not exist there
     */
    @Nullable
    SpecEntityRegistrar registrarFor(@NonNull HederaNetwork network);

    /**
     * If this entity is already created on the spec's target {@link HederaNetwork}, registers
     * its record (e.g., entity id, controlling key, and memo) with the given {@link HapiSpec}'s
     * registry.
     *
     * <p>If the entity is not already created on the network, blocks until it is created using
     * the given spec; then registers its record.
     *
     * @param spec the spec to use to create the entity if it is not already created
     */
    default void registerOrCreateWith(@NonNull final HapiSpec spec) {
        requireNonNull(spec);
        Optional.ofNullable(registrarFor(spec.targetNetworkOrThrow()))
                .orElseGet(() -> createWith(spec))
                .registerWith(spec);
    }

    /**
     * Creates this entity with the given {@link HapiSpec}, returning the registrar
     * for the spec's target network.
     *
     * @param spec the spec to use to create the entity
     */
    SpecEntityRegistrar createWith(@NonNull HapiSpec spec);

    /**
     * Locks the entity's model, preventing further modification.
     */
    void lock();

    /**
     * Returns a list of entities that must be created before this entity can be created.
     *
     * @return the prerequisite entities
     */
    default List<SpecEntity> prerequisiteEntities() {
        return emptyList();
    }
}
