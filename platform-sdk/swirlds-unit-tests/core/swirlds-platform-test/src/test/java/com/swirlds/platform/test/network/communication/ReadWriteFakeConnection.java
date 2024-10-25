/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.network.communication;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.utility.IOConsumer;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.gossip.sync.SyncInputStream;
import com.swirlds.platform.gossip.sync.SyncOutputStream;
import com.swirlds.platform.test.network.FakeConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ReadWriteFakeConnection extends FakeConnection {
    private final SyncInputStream in;
    private final SyncOutputStream out;

    private final Configuration configuration =
            new TestConfigBuilder().withValue("socket.gzipCompression", false).getOrCreateConfig();

    private final PlatformContext platformContext =
            TestPlatformContextBuilder.create().withConfiguration(configuration).build();

    public ReadWriteFakeConnection(final InputStream in, final OutputStream out) {
        super();
        this.in = SyncInputStream.createSyncInputStream(platformContext, in, 100);
        this.out = SyncOutputStream.createSyncOutputStream(platformContext, out, 100);
    }

    /**
     * Create a fake connection with a function that is called on every byte sent to the output stream.
     *
     * @param in
     * 		the input stream
     * @param out
     * 		the output stream
     * @param outputInterceptor
     * 		a function called on each byte sent to the output stream via the write() method, ignored if null
     */
    public ReadWriteFakeConnection(
            final InputStream in, final OutputStream out, final IOConsumer<Integer> outputInterceptor) {

        super();
        this.in = SyncInputStream.createSyncInputStream(platformContext, in, 100);
        final OutputStream baseOutput = new OutputStream() {
            @Override
            public void write(final int b) throws IOException {
                if (outputInterceptor != null) {
                    outputInterceptor.accept(b);
                }
                out.write(b);
            }
        };
        this.out = SyncOutputStream.createSyncOutputStream(platformContext, baseOutput, 100);
    }

    @Override
    public SyncInputStream getDis() {
        return in;
    }

    @Override
    public SyncOutputStream getDos() {
        return out;
    }
}
