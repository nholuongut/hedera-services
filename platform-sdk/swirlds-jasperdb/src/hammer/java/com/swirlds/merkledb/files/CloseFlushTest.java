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

package com.swirlds.merkledb.files;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.utility.LegacyTemporaryFileBuilder;
import com.swirlds.merkledb.MerkleDbDataSourceBuilder;
import com.swirlds.merkledb.test.fixtures.ExampleByteArrayVirtualValue;
import com.swirlds.merkledb.test.fixtures.ExampleFixedSizeVirtualValue;
import com.swirlds.merkledb.test.fixtures.ExampleLongKeyFixedSize;
import com.swirlds.merkledb.test.fixtures.TestType;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualLongKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import com.swirlds.virtualmap.datasource.VirtualHashRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * This is a regression test for swirlds/swirlds-platform/issues/6151, but
 * it can be used to find many different issues with VirtualMap.
 *
 * The test creates a virtual map and makes its copies in a loop, until it gets flushed to
 * disk. Right after a flush is started, the last map is released, which triggers virtual
 * pipeline shutdown. The test then makes sure the flush completes without exceptions.
 */
public class CloseFlushTest {

    private static Path tmpFileDir;

    @BeforeAll
    public static void setup() throws IOException {
        tmpFileDir = LegacyTemporaryFileBuilder.buildTemporaryFile();
        Configurator.setRootLevel(Level.WARN);
    }

    @AfterAll
    public static void cleanUp() {
        Configurator.reconfigure();
    }

    @Test
    public void closeFlushTest() throws Exception {
        final int count = 100000;
        final ExecutorService exec = Executors.newSingleThreadExecutor();
        final AtomicReference<Exception> exception = new AtomicReference<>();
        for (int j = 0; j < 100; j++) {
            final Path storeDir = tmpFileDir.resolve("closeFlushTest-" + j);
            final VirtualDataSource<VirtualLongKey, ExampleByteArrayVirtualValue> dataSource =
                    TestType.fixed_fixed.dataType().createDataSource(storeDir, "closeFlushTest", count, 0, false, true);
            // Create a custom data source builder, which creates a custom data source to capture
            // all exceptions happened in saveRecords()
            final VirtualDataSourceBuilder<VirtualLongKey, ExampleByteArrayVirtualValue> builder =
                    new CustomDataSourceBuilder<>(dataSource, exception);
            VirtualMap<VirtualLongKey, ExampleByteArrayVirtualValue> map = new VirtualMap<>("closeFlushTest", builder);
            for (int i = 0; i < count; i++) {
                final ExampleLongKeyFixedSize key = new ExampleLongKeyFixedSize(i);
                final ExampleFixedSizeVirtualValue value = new ExampleFixedSizeVirtualValue(i);
                map.put(key, value);
            }
            VirtualMap<VirtualLongKey, ExampleByteArrayVirtualValue> copy;
            final CountDownLatch shutdownLatch = new CountDownLatch(1);
            for (int i = 0; i < 100; i++) {
                copy = map.copy();
                map.release();
                map = copy;
            }
            copy = map.copy();
            final VirtualRootNode<VirtualLongKey, ExampleByteArrayVirtualValue> root = map.getRight();
            root.enableFlush();
            final VirtualMap<VirtualLongKey, ExampleByteArrayVirtualValue> lastMap = map;
            final Future<?> job = exec.submit(() -> {
                try {
                    Thread.sleep(new Random().nextInt(500));
                    lastMap.release();
                } catch (final Exception z) {
                    throw new RuntimeException(z);
                } finally {
                    shutdownLatch.countDown();
                }
            });
            copy.release();
            shutdownLatch.await();
            if (exception.get() != null) {
                exception.get().printStackTrace();
                break;
            }
            job.get();
        }
        Assertions.assertNull(exception.get(), "No exceptions expected, but caught " + exception.get());
    }

    public static class CustomDataSourceBuilder<K extends VirtualKey, V extends VirtualValue>
            extends MerkleDbDataSourceBuilder<K, V> {

        private VirtualDataSource<K, V> delegate = null;
        private AtomicReference<Exception> exceptionSink = null;

        // Provided for deserialization
        public CustomDataSourceBuilder() {}

        public CustomDataSourceBuilder(final VirtualDataSource<K, V> delegate, AtomicReference<Exception> sink) {
            this.delegate = delegate;
            this.exceptionSink = sink;
        }

        @Override
        public long getClassId() {
            return super.getClassId() + 1;
        }

        @Override
        public VirtualDataSource<K, V> build(final String label, final boolean withDbCompactionEnabled) {
            return new VirtualDataSource<>() {
                @Override
                public void close() throws IOException {
                    delegate.close();
                }

                @Override
                public void saveRecords(
                        final long firstLeafPath,
                        final long lastLeafPath,
                        @NonNull final Stream<VirtualHashRecord> pathHashRecordsToUpdate,
                        @NonNull final Stream<VirtualLeafRecord<K, V>> leafRecordsToAddOrUpdate,
                        @NonNull final Stream<VirtualLeafRecord<K, V>> leafRecordsToDelete,
                        final boolean isReconnectContext) {
                    try {
                        delegate.saveRecords(
                                firstLeafPath,
                                lastLeafPath,
                                pathHashRecordsToUpdate,
                                leafRecordsToAddOrUpdate,
                                leafRecordsToDelete,
                                isReconnectContext);
                    } catch (final Exception e) {
                        exceptionSink.set(e);
                    }
                }

                @Override
                public VirtualLeafRecord<K, V> loadLeafRecord(final K key) throws IOException {
                    return delegate.loadLeafRecord(key);
                }

                @Override
                public VirtualLeafRecord<K, V> loadLeafRecord(final long path) throws IOException {
                    return delegate.loadLeafRecord(path);
                }

                @Override
                public long findKey(final K key) throws IOException {
                    return delegate.findKey(key);
                }

                @Override
                public Hash loadHash(final long path) throws IOException {
                    return delegate.loadHash(path);
                }

                @Override
                public void snapshot(final Path snapshotDirectory) throws IOException {
                    delegate.snapshot(snapshotDirectory);
                }

                @Override
                public void copyStatisticsFrom(final VirtualDataSource<K, V> that) {
                    delegate.copyStatisticsFrom(that);
                }

                @Override
                public void registerMetrics(final Metrics metrics) {
                    delegate.registerMetrics(metrics);
                }

                @Override
                public long estimatedSize(final long dirtyInternals, final long dirtyLeaves) {
                    return delegate.estimatedSize(dirtyInternals, dirtyLeaves);
                }

                public long getFirstLeafPath() {
                    return delegate.getFirstLeafPath();
                }

                public long getLastLeafPath() {
                    return delegate.getLastLeafPath();
                }

                @Override
                public void enableBackgroundCompaction() {
                    delegate.enableBackgroundCompaction();
                }

                @Override
                public void stopAndDisableBackgroundCompaction() {
                    delegate.stopAndDisableBackgroundCompaction();
                }
            };
        }
    }
}
