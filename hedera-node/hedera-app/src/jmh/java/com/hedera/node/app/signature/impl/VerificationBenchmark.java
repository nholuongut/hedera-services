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

package com.hedera.node.app.signature.impl;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.node.app.fixtures.AppTestBase;
import com.hedera.node.app.signature.ExpandedSignaturePair;
import com.hedera.node.app.spi.fixtures.Scenarios;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/** Measures the amount of time to prepare expanded signatures and call the crypto engine */
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class VerificationBenchmark extends AppTestBase implements Scenarios {
    @Param({"1", "2", "5", "10"})
    public int numSigPairs;

    private Set<ExpandedSignaturePair> sigPairs;
    private Bytes fakeSignedBytes;
    private SignatureVerifierImpl subject;

    @Setup(Level.Invocation)
    public void setUp() {
        sigPairs = createSigPairs(numSigPairs);
        final var fakeCryptoEngine = new DoNothingCryptoEngine();
        fakeSignedBytes = Bytes.wrap(new byte[] {1, 2, 3, 4, 5});
        subject = new SignatureVerifierImpl(fakeCryptoEngine);
    }

    @Benchmark
    public void verifyBench(Blackhole blackhole) {
        blackhole.consume(subject.verify(fakeSignedBytes, sigPairs));
    }

    private Set<ExpandedSignaturePair> createSigPairs(int numSigPairs) {
        final var pairs = new HashSet<ExpandedSignaturePair>();
        for (int i = 0; i < numSigPairs; i++) {
            final var keyBytes = randomBytes(32);
            final var sigPair = SignaturePair.newBuilder()
                    .ed25519(keyBytes)
                    .pubKeyPrefix(keyBytes.slice(0, 10))
                    .build();
            pairs.add(
                    new ExpandedSignaturePair(Key.newBuilder().ed25519(keyBytes).build(), keyBytes, null, sigPair));
        }
        return pairs;
    }
}
