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

package com.swirlds.common.utility.throttle;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.utility.CompareTo.isLessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.swirlds.base.test.fixtures.time.FakeTime;
import java.time.Duration;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("RateLimiter Tests")
class RateLimiterTests {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 100})
    @DisplayName("Period Test")
    void periodTest(final int periodMs) {
        final Random random = getRandomPrintSeed();

        final FakeTime time = new FakeTime(Duration.ofNanos(1));
        final RateLimiter rateLimiter = new RateLimiter(time, Duration.ofMillis(periodMs));

        long count = 0;
        long denied = 0;

        final Duration limit = Duration.ofSeconds(1);
        while (isLessThan(time.elapsed(), limit)) {
            assertEquals(denied, rateLimiter.getDeniedRequests(), "invalid number of denied requests");

            // Check and see if the rate limiter will allow the action to be triggered.
            final boolean requestAccepted = rateLimiter.request();
            if (!requestAccepted) {
                denied++;
            } else {
                denied = 0;
            }

            assertEquals(denied, rateLimiter.getDeniedRequests(), "invalid number of denied requests");

            if (random.nextBoolean()) {
                if (rateLimiter.request()) {
                    rateLimiter.trigger();
                    count++;
                } else {
                    denied++;
                }
            } else if (rateLimiter.requestAndTrigger()) {
                count++;
                denied = 0;
            } else {
                denied++;
            }

            // If we successfully triggered above, we should now be denied until time advances. If we did not
            // successfully trigger, we should also be denied.
            assertFalse(rateLimiter.request());
            denied++;

            time.tick(Duration.ofNanos(1_000));
        }

        assertEquals(limit.toMillis() / periodMs, count);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 100})
    @DisplayName("Frequency Test")
    void frequencyTest(final int periodMs) {
        final Random random = getRandomPrintSeed();

        final FakeTime time = new FakeTime(Duration.ofNanos(1));
        final RateLimiter rateLimiter = new RateLimiter(time, 1000.0 / periodMs);

        long count = 0;
        long denied = 0;

        final Duration limit = Duration.ofSeconds(1);
        while (isLessThan(time.elapsed(), limit)) {
            assertEquals(denied, rateLimiter.getDeniedRequests(), "invalid number of denied requests");

            // Check and see if the rate limiter will allow the action to be triggered.
            final boolean requestAccepted = rateLimiter.request();
            if (!requestAccepted) {
                denied++;
            } else {
                denied = 0;
            }

            assertEquals(denied, rateLimiter.getDeniedRequests(), "invalid number of denied requests");

            if (random.nextBoolean()) {
                if (rateLimiter.request()) {
                    rateLimiter.trigger();
                    count++;
                } else {
                    denied++;
                }
            } else if (rateLimiter.requestAndTrigger()) {
                count++;
                denied = 0;
            } else {
                denied++;
            }

            // If we successfully triggered above, we should now be denied until time advances. If we did not
            // successfully trigger, we should also be denied.
            assertFalse(rateLimiter.request());
            denied++;

            time.tick(Duration.ofNanos(1_000));
        }

        assertEquals(limit.toMillis() / periodMs, count);
    }
}
