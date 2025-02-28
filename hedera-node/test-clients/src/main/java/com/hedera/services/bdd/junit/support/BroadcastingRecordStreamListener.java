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

package com.hedera.services.bdd.junit.support;

import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.isRecordFile;
import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.isSidecarFile;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A small utility class that listens for record stream files and provides them to any subscribed
 * listeners.
 */
public class BroadcastingRecordStreamListener extends FileAlterationListenerAdaptor {
    private static final Logger log = LogManager.getLogger(BroadcastingRecordStreamListener.class);

    private static final int NUM_RETRIES = 32;
    private static final long RETRY_BACKOFF_MS = 500L;

    private final List<StreamDataListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Subscribes a listener to receive record stream items.
     *
     * @param listener the listener to subscribe
     * @return a runnable that can be used to unsubscribe the listener
     */
    public Runnable subscribe(final StreamDataListener listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    enum FileType {
        RECORD_STREAM_FILE,
        SIDE_CAR_FILE,
        OTHER
    }

    @Override
    public void onFileCreate(final File file) {
        switch (typeOf(file)) {
            case RECORD_STREAM_FILE -> retryExposingVia(this::exposeItems, "record", file);
            case SIDE_CAR_FILE -> retryExposingVia(this::exposeSidecars, "sidecar", file);
            case OTHER -> {
                // Nothing to expose
            }
        }
    }

    private void retryExposingVia(
            @NonNull final Consumer<File> exposure, @NonNull final String fileType, @NonNull final File f) {
        var retryCount = 0;
        while (true) {
            retryCount++;
            try {
                exposure.accept(f);
                log.info(
                        "Listener@{} gave validators access to {} file {}",
                        System.identityHashCode(this),
                        fileType,
                        f.getAbsolutePath());
                return;
            } catch (UncheckedIOException e) {
                if (retryCount < NUM_RETRIES) {
                    try {
                        MILLISECONDS.sleep(RETRY_BACKOFF_MS);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    log.error("Could not expose contents of {} file {}", fileType, f.getAbsolutePath(), e);
                    throw new IllegalStateException();
                }
            }
        }
    }

    private void exposeSidecars(final File file) {
        final var contents = RecordStreamAccess.ensurePresentSidecarFile(file.getAbsolutePath());
        contents.getSidecarRecordsList().forEach(sidecar -> listeners.forEach(l -> l.onNewSidecar(sidecar)));
    }

    private void exposeItems(final File file) {
        final var contents = RecordStreamAccess.ensurePresentRecordFile(file.getAbsolutePath());
        contents.getRecordStreamItemsList().forEach(item -> listeners.forEach(l -> l.onNewItem(item)));
    }

    public int numListeners() {
        return listeners.size();
    }

    private FileType typeOf(final File file) {
        if (isRecordFile(file.getName())) {
            return FileType.RECORD_STREAM_FILE;
        } else if (isSidecarFile(file.getName())) {
            return FileType.SIDE_CAR_FILE;
        } else {
            return FileType.OTHER;
        }
    }
}
