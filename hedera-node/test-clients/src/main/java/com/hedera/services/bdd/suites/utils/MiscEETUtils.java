/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.utils;

import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class MiscEETUtils {
    @SuppressWarnings("java:S2245") // using java.util.Random in tests is fine
    private static final Random r = new Random(298238L);

    private MiscEETUtils() {}

    public static byte[] genRandomBytes(int numBytes) {
        byte[] contents = new byte[numBytes];
        r.nextBytes(contents);
        return contents;
    }

    public static List<ByteString> batchOfSize(int size) {
        var batch = new ArrayList<ByteString>();
        for (int i = 0; i < size; i++) {
            batch.add(metadata("memo" + i));
        }
        return batch;
    }

    public static ByteString metadataOfLength(int length) {
        return ByteString.copyFrom(genRandomBytes(length));
    }

    public static ByteString metadata(String contents) {
        return ByteString.copyFromUtf8(contents);
    }
}
