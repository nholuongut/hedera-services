/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.internal.reconnect;

import static com.swirlds.common.test.fixtures.junit.tags.TestQualifierTags.TIMING_SENSITIVE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(TIMING_SENSITIVE)
public class ConcurrentBlockingIteratorTest {
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    @Test
    @DisplayName("non-positive queue sizes throw")
    void badBufferSizes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ConcurrentBlockingIterator<Integer>(-1, 1, SECONDS),
                "Should have thrown IllegalArgumentException");
        assertThrows(
                IllegalArgumentException.class,
                () -> new ConcurrentBlockingIterator<Integer>(0, 1, SECONDS),
                "Should have thrown IllegalArgumentException");
    }

    @Test
    @DisplayName("hasNext on empty, closed iterator returns false")
    void hasNextOnEmptyWhenClosed() {
        final var itr = new ConcurrentBlockingIterator<Integer>(100, 1, SECONDS);
        itr.close();
        assertFalse(itr.hasNext(), "Should be false on empty iterator");
    }

    @Test
    @DisplayName("hasNext on non-empty, open iterator returns true")
    void hasNextOnFull() throws InterruptedException {
        final var itr = new ConcurrentBlockingIterator<Integer>(100, 1, SECONDS);
        itr.supply(123);
        assertTrue(itr.hasNext(), "Should be true on non-empty iterator");
    }

    @Test
    @DisplayName("hasNext twice on good element is OK")
    void hasTwice() throws InterruptedException {
        final var itr = new ConcurrentBlockingIterator<Integer>(100, 1, SECONDS);
        itr.supply(123);
        //noinspection ResultOfMethodCallIgnored
        itr.hasNext();
        assertTrue(itr.hasNext(), "Should be true on non-empty iterator");
    }

    @Test
    @DisplayName("hasNext on non-empty, closed iterator returns true")
    void hasNextOnFullWhenClosed() throws InterruptedException {
        final var itr = new ConcurrentBlockingIterator<Integer>(100, 1, SECONDS);
        itr.supply(123);
        itr.close();
        assertTrue(itr.hasNext(), "Should be true on non-empty iterator");
    }

    @Test
    @DisplayName("next on empty iterator throws when closed")
    void nextOnEmptyThrowsWhenClosed() {
        final var itr = new ConcurrentBlockingIterator<Integer>(100, 1, SECONDS);
        itr.close();
        assertThrows(NoSuchElementException.class, itr::next, "Should throw on empty");
    }

    @Test
    @DisplayName("next on empty iterator throws if subsequently closed")
    void nextOnEmptyThrowsWhenFinallyClosed() throws InterruptedException, ExecutionException {
        final var itr = new ConcurrentBlockingIterator<Integer>(100, 1, SECONDS);
        final Future<Class<NoSuchElementException>> thrown = thrownByCall(itr::next);
        MILLISECONDS.sleep(10);
        itr.close();
        final Class<NoSuchElementException> clazz = thrown.get();
        assertEquals(NoSuchElementException.class, clazz, "Should throw on empty");
    }

    @Test
    @DisplayName("next on consumed iterator throws when closed")
    void nextOnConsumedThrowsWhenClosed() throws InterruptedException {
        final var itr = new ConcurrentBlockingIterator<Integer>(100, 1, SECONDS);
        itr.supply(123);
        itr.close();
        itr.next();
        assertThrows(NoSuchElementException.class, itr::next, "Should throw on empty");
    }

    @Test
    @DisplayName("next on consumed iterator throws if subsequently closed")
    void nextOnConsumedThrowsWhenFinallyClosed() throws InterruptedException, ExecutionException {
        final var itr = new ConcurrentBlockingIterator<Integer>(100, 1, SECONDS);
        itr.supply(123);
        final AtomicBoolean firstNextWasFine = new AtomicBoolean(false);
        final Future<Class<NoSuchElementException>> thrown = thrownByCall(() -> {
            itr.next(); // should be fine
            firstNextWasFine.set(true);
            itr.next(); // should throw
        });
        MILLISECONDS.sleep(10);
        itr.close();
        final Class<NoSuchElementException> clazz = thrown.get();
        assertEquals(NoSuchElementException.class, clazz, "Should throw on empty");
        assertTrue(firstNextWasFine.get(), "The first call to 'next' should have worked");
    }

    @Test
    @DisplayName("next on iterator with elements returns")
    void nextOnFullOk() throws InterruptedException {
        final var itr = new ConcurrentBlockingIterator<Integer>(100, 1, SECONDS);
        itr.supply(1);
        itr.supply(2);
        itr.supply(3);
        assertTrue(itr.hasNext(), "Should have next");
        assertEquals(1, itr.next(), "First element should be 1");
        assertTrue(itr.hasNext(), "Should have next");
        assertEquals(2, itr.next(), "Second element should be 2");
        assertTrue(itr.hasNext(), "Should have next");
        assertEquals(3, itr.next(), "Third element should be 3");
    }

    @Test
    @DisplayName("can iterate even after closed")
    void iterateAfterClose() throws InterruptedException {
        final var itr = new ConcurrentBlockingIterator<Integer>(100, 1, SECONDS);
        for (int i = 0; i < 10; i++) {
            itr.supply(i);
        }

        itr.close();

        for (int i = 0; i < 10; i++) {
            assertTrue(itr.hasNext(), "Should have more elements");
            final int next = itr.next();
            assertEquals(i, next, "Unexpected value " + next);
        }
    }

    @Test
    @DisplayName("cannot add elements after close")
    void cannotAddElementsAfterClosed() throws InterruptedException {
        final var itr = new ConcurrentBlockingIterator<Integer>(100, 1, SECONDS);
        itr.supply(1);
        itr.close();
        assertThrows(IllegalStateException.class, () -> itr.supply(2), "Should have thrown IllegalStateException");
    }

    @Test
    @DisplayName("submit and iteration on different threads")
    void multiThreadIteration() throws ExecutionException, InterruptedException {
        final int max = 100_000;
        final var itr = new ConcurrentBlockingIterator<Integer>(100, 1, SECONDS);

        final Future<?> submission = threadPool.submit(() -> {
            for (int i = 0; i < max; i++) {
                try {
                    itr.supply(i);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            itr.close();
        });

        final Future<Integer> iteration = threadPool.submit(() -> {
            int lastValue = -1;
            while (itr.hasNext()) {
                int value = itr.next();
                if (value != lastValue + 1) {
                    throw new RuntimeException("Expected " + (lastValue + 1) + " but was " + value);
                }
                lastValue = value;
            }
            return lastValue;
        });

        try {
            iteration.get();
        } catch (Exception e) {
            fail("Failed to iterate over all items", e);
        }

        assertEquals(max - 1, iteration.get(), "Not all elements were handled by the iterator!");

        try {
            submission.get();
        } catch (Exception e) {
            fail("Failed to submit all items", e);
        }
    }

    @RepeatedTest(1000)
    @DisplayName("yield all elements even after closed")
    void supplyBeforeClose() throws InterruptedException {
        final ConcurrentBlockingIterator<Integer> iterator = new ConcurrentBlockingIterator<>(4, 10, SECONDS);
        final Thread supplier = new Thread(() -> {
            try {
                iterator.supply(1);
                iterator.supply(2);
                iterator.close();
            } catch (final InterruptedException e) {
                fail("Supplier interrupted");
            }
        });
        supplier.start();
        assertTrue(iterator.hasNext(), "Iterator must have more than zero elements");
        assertEquals(1, iterator.next());
        assertTrue(iterator.hasNext(), "Iterator must have more than one element");
        assertEquals(2, iterator.next());
        assertFalse(iterator.hasNext(), "Iterator must not have more than two elements");
        supplier.join();
    }

    private <T> Future<Class<T>> thrownByCall(Runnable lambda) {
        return threadPool.submit(() -> {
            try {
                lambda.run();
            } catch (Exception e) {
                //noinspection unchecked
                return (Class<T>) e.getClass();
            }
            return null;
        });
    }
}
