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

package com.hedera.node.app.state.merkle;

import static com.hedera.node.app.fixtures.AppTestBase.DEFAULT_CONFIG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.spi.fixtures.state.TestSchema;
import com.hedera.node.config.data.HederaConfig;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.constructable.RuntimeConstructable;
import com.swirlds.common.io.utility.LegacyTemporaryFileBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.sources.SimpleConfigSource;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.state.merkle.disk.OnDiskReadableKVState;
import com.swirlds.state.merkle.disk.OnDiskWritableKVState;
import com.swirlds.state.spi.MigrationContext;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableQueueState;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.Schema;
import com.swirlds.state.spi.StateDefinition;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableQueueState;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.config.VirtualMapConfig;
import com.swirlds.virtualmap.config.VirtualMapConfig_;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SerializationTest extends MerkleTestBase {
    private Path dir;
    private Configuration config;
    private NetworkInfo networkInfo;

    @Mock
    private HederaLifecycles lifecycles;

    @BeforeEach
    void setUp() throws IOException {
        setupConstructableRegistry();

        this.dir = LegacyTemporaryFileBuilder.buildTemporaryDirectory();
        this.config = new TestConfigBuilder()
                .withSource(new SimpleConfigSource()
                        .withValue(VirtualMapConfig_.FLUSH_INTERVAL, 1 + "")
                        .withValue(VirtualMapConfig_.COPY_FLUSH_THRESHOLD, 1 + ""))
                .withConfigDataType(VirtualMapConfig.class)
                .withConfigDataType(HederaConfig.class)
                .getOrCreateConfig();
        this.networkInfo = mock(NetworkInfo.class);
    }

    Schema createV1Schema() {
        return new TestSchema(1) {
            @NonNull
            @Override
            @SuppressWarnings("rawtypes")
            public Set<StateDefinition> statesToCreate() {
                final var fruitDef = StateDefinition.inMemory(FRUIT_STATE_KEY, STRING_CODEC, STRING_CODEC);
                final var animalDef = StateDefinition.onDisk(ANIMAL_STATE_KEY, STRING_CODEC, STRING_CODEC, 100);
                final var countryDef = StateDefinition.singleton(COUNTRY_STATE_KEY, STRING_CODEC);
                final var steamDef = StateDefinition.queue(STEAM_STATE_KEY, STRING_CODEC);
                return Set.of(fruitDef, animalDef, countryDef, steamDef);
            }

            @Override
            public void migrate(@NonNull final MigrationContext ctx) {
                final var newStates = ctx.newStates();
                final WritableKVState<String, String> fruit = newStates.get(FRUIT_STATE_KEY);
                fruit.put(A_KEY, APPLE);
                fruit.put(B_KEY, BANANA);
                fruit.put(C_KEY, CHERRY);
                fruit.put(D_KEY, DATE);
                fruit.put(E_KEY, EGGPLANT);
                fruit.put(F_KEY, FIG);
                fruit.put(G_KEY, GRAPE);

                final OnDiskWritableKVState<String, String> animals =
                        (OnDiskWritableKVState<String, String>) (OnDiskWritableKVState) newStates.get(ANIMAL_STATE_KEY);
                animals.put(A_KEY, AARDVARK);
                animals.put(B_KEY, BEAR);
                animals.put(C_KEY, CUTTLEFISH);
                animals.put(D_KEY, DOG);
                animals.put(E_KEY, EMU);
                animals.put(F_KEY, FOX);
                animals.put(G_KEY, GOOSE);
                animals.commit();

                final WritableSingletonState<String> country = newStates.getSingleton(COUNTRY_STATE_KEY);
                country.put(CHAD);

                final WritableQueueState<String> steam = newStates.getQueue(STEAM_STATE_KEY);
                steam.add(ART);
                steam.add(BIOLOGY);
                steam.add(CHEMISTRY);
                steam.add(DISCIPLINE);
                steam.add(ECOLOGY);
                steam.add(FIELDS);
                steam.add(GEOMETRY);
            }
        };
    }

    private void forceFlush(ReadableKVState<?, ?> state) {
        if (state instanceof OnDiskReadableKVState<?, ?>) {
            try {
                Field vmField = OnDiskReadableKVState.class.getDeclaredField("virtualMap");
                vmField.setAccessible(true);
                VirtualMap<?, ?> vm = (VirtualMap<?, ?>) vmField.get(state);

                final VirtualRootNode<?, ?> root = vm.getRight();
                if (!vm.isEmpty()) {
                    root.enableFlush();
                    vm.release();
                    root.waitUntilFlushed();
                }
            } catch (IllegalAccessException | NoSuchFieldException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * In this test scenario, we have a genesis setup where we create FRUIT and ANIMALS and COUNTRY,
     * save them to disk, and then load them back in, and verify everything was loaded correctly.
     * <br/>
     * This tests has two modes: one where we force flush the VMs to disk and one where we don't.
     * When it forces disk flush, it makes sure that the data gets to the datasource from cache and persisted in the table.
     * When the flush is not forced, the data remains in cache which has its own serialization mechanism
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void simpleReadAndWrite(boolean forceFlush) throws IOException, ConstructableRegistryException {
        final var schemaV1 = createV1Schema();
        final var originalTree = createMerkleHederaState(schemaV1);

        // When we serialize it to bytes and deserialize it back into a tree
        MerkleHederaState copy = originalTree.copy(); // make a copy to make VM flushable
        final byte[] serializedBytes;
        if (forceFlush) {
            // Force flush the VMs to disk to test serialization and deserialization
            forceFlush(originalTree.getReadableStates(FIRST_SERVICE).get(ANIMAL_STATE_KEY));
            copy.copy(); // make a fast copy because we can only write to disk an immutable copy
            CRYPTO.digestTreeSync(copy);
            serializedBytes = writeTree(copy, dir);
        } else {
            CRYPTO.digestTreeSync(copy);
            serializedBytes = writeTree(originalTree, dir);
        }

        final MerkleHederaState loadedTree = loadeMerkleTree(schemaV1, serializedBytes);

        assertTree(loadedTree);
    }

    /**
     * This test scenario is trickier, and it's designed to reproduce <a href="https://github.com/hashgraph/hedera-services/issues/13335">#13335: OnDiskKeySerializer uses wrong classId for OnDiskKey.</a>
     * This issue can be reproduced only if at first it gets flushed to disk, then it gets loaded back in, and this time it remains in cache.
     * After it gets saved to disk again, and then loaded back in, it results in ClassCastException due to incorrect classId.
     */
    @Test
    void dualReadAndWrite() throws IOException, ConstructableRegistryException {
        final var schemaV1 = createV1Schema();
        final var originalTree = createMerkleHederaState(schemaV1);

        MerkleHederaState copy = originalTree.copy(); // make a copy to make VM flushable
        ;

        forceFlush(originalTree.getReadableStates(FIRST_SERVICE).get(ANIMAL_STATE_KEY));
        copy.copy(); // make a fast copy because we can only write to disk an immutable copy
        CRYPTO.digestTreeSync(copy);
        final byte[] serializedBytes = writeTree(copy, dir);

        MerkleHederaState loadedTree = loadeMerkleTree(schemaV1, serializedBytes);
        ((OnDiskReadableKVState) originalTree.getReadableStates(FIRST_SERVICE).get(ANIMAL_STATE_KEY)).reset();
        populateVmCache(loadedTree);

        loadedTree.copy(); // make a copy to store it to disk

        CRYPTO.digestTreeSync(loadedTree);
        // refreshing the dir
        dir = LegacyTemporaryFileBuilder.buildTemporaryDirectory();
        final byte[] serializedBytesWithCache = writeTree(loadedTree, dir);

        // let's load it again and see if it works
        MerkleHederaState loadedTreeWithCache = loadeMerkleTree(schemaV1, serializedBytesWithCache);
        ((OnDiskReadableKVState)
                        loadedTreeWithCache.getReadableStates(FIRST_SERVICE).get(ANIMAL_STATE_KEY))
                .reset();

        assertTree(loadedTreeWithCache);
    }

    private MerkleHederaState loadeMerkleTree(Schema schemaV1, byte[] serializedBytes)
            throws ConstructableRegistryException, IOException {
        final var newRegistry =
                new MerkleSchemaRegistry(registry, FIRST_SERVICE, DEFAULT_CONFIG, new SchemaApplications());
        newRegistry.register(schemaV1);

        // Register the MerkleHederaState so, when found in serialized bytes, it will register with
        // our migration callback, etc. (normally done by the Hedera main method)
        final Supplier<RuntimeConstructable> constructor = () -> new MerkleHederaState(lifecycles);
        final var pair = new ClassConstructorPair(MerkleHederaState.class, constructor);
        registry.registerConstructable(pair);

        final MerkleHederaState loadedTree = parseTree(serializedBytes, dir);
        newRegistry.migrate(
                loadedTree,
                schemaV1.getVersion(),
                schemaV1.getVersion(),
                config,
                networkInfo,
                mock(Metrics.class),
                mock(WritableEntityIdStore.class),
                new HashMap<>());
        loadedTree.migrate(1);

        return loadedTree;
    }

    private MerkleHederaState createMerkleHederaState(Schema schemaV1) {
        final var v1 = version(1, 0, 0);
        final var originalTree = new MerkleHederaState(lifecycles);
        final var originalRegistry =
                new MerkleSchemaRegistry(registry, FIRST_SERVICE, DEFAULT_CONFIG, new SchemaApplications());
        originalRegistry.register(schemaV1);
        originalRegistry.migrate(
                originalTree,
                null,
                v1,
                config,
                networkInfo,
                mock(Metrics.class),
                mock(WritableEntityIdStore.class),
                new HashMap<>());
        return originalTree;
    }

    private static void populateVmCache(MerkleHederaState loadedTree) {
        final var states = loadedTree.getWritableStates(FIRST_SERVICE);
        final WritableKVState<String, String> animalState = states.get(ANIMAL_STATE_KEY);
        assertThat(animalState.getForModify(A_KEY)).isEqualTo(AARDVARK);
        assertThat(animalState.getForModify(B_KEY)).isEqualTo(BEAR);
        assertThat(animalState.getForModify(C_KEY)).isEqualTo(CUTTLEFISH);
        assertThat(animalState.getForModify(D_KEY)).isEqualTo(DOG);
        assertThat(animalState.getForModify(E_KEY)).isEqualTo(EMU);
        assertThat(animalState.getForModify(F_KEY)).isEqualTo(FOX);
        assertThat(animalState.getForModify(G_KEY)).isEqualTo(GOOSE);
    }

    private static void assertTree(MerkleHederaState loadedTree) {
        final var states = loadedTree.getReadableStates(FIRST_SERVICE);
        final ReadableKVState<String, String> fruitState = states.get(FRUIT_STATE_KEY);
        assertThat(fruitState.get(A_KEY)).isEqualTo(APPLE);
        assertThat(fruitState.get(B_KEY)).isEqualTo(BANANA);
        assertThat(fruitState.get(C_KEY)).isEqualTo(CHERRY);
        assertThat(fruitState.get(D_KEY)).isEqualTo(DATE);
        assertThat(fruitState.get(E_KEY)).isEqualTo(EGGPLANT);
        assertThat(fruitState.get(F_KEY)).isEqualTo(FIG);
        assertThat(fruitState.get(G_KEY)).isEqualTo(GRAPE);

        final ReadableKVState<String, String> animalState = states.get(ANIMAL_STATE_KEY);
        assertThat(animalState.get(A_KEY)).isEqualTo(AARDVARK);
        assertThat(animalState.get(B_KEY)).isEqualTo(BEAR);
        assertThat(animalState.get(C_KEY)).isEqualTo(CUTTLEFISH);
        assertThat(animalState.get(D_KEY)).isEqualTo(DOG);
        assertThat(animalState.get(E_KEY)).isEqualTo(EMU);
        assertThat(animalState.get(F_KEY)).isEqualTo(FOX);
        assertThat(animalState.get(G_KEY)).isEqualTo(GOOSE);

        final ReadableSingletonState<String> countryState = states.getSingleton(COUNTRY_STATE_KEY);
        assertThat(countryState.get()).isEqualTo(CHAD);

        final ReadableQueueState<String> steamState = states.getQueue(STEAM_STATE_KEY);
        assertThat(steamState.iterator())
                .toIterable()
                .containsExactly(ART, BIOLOGY, CHEMISTRY, DISCIPLINE, ECOLOGY, FIELDS, GEOMETRY);
    }
}
