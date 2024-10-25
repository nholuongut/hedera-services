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

package com.swirlds.common.units;

import static com.swirlds.base.units.UnitConstants.MICROSECONDS_TO_MILLISECONDS;
import static com.swirlds.base.units.UnitConstants.MICROSECONDS_TO_NANOSECONDS;
import static com.swirlds.base.units.UnitConstants.MICROSECONDS_TO_SECONDS;
import static com.swirlds.base.units.UnitConstants.MILLISECONDS_TO_MICROSECONDS;
import static com.swirlds.base.units.UnitConstants.MILLISECONDS_TO_NANOSECONDS;
import static com.swirlds.base.units.UnitConstants.MILLISECONDS_TO_SECONDS;
import static com.swirlds.base.units.UnitConstants.NANOSECONDS_TO_MICROSECONDS;
import static com.swirlds.base.units.UnitConstants.NANOSECONDS_TO_MILLISECONDS;
import static com.swirlds.base.units.UnitConstants.NANOSECONDS_TO_SECONDS;
import static com.swirlds.base.units.UnitConstants.SECONDS_TO_MICROSECONDS;
import static com.swirlds.base.units.UnitConstants.SECONDS_TO_MILLISECONDS;
import static com.swirlds.base.units.UnitConstants.SECONDS_TO_NANOSECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UnitConstantsTest {

    @Test
    void testTimeConversions() {
        // given
        final double value = 1.2d;
        final double conversionFactor1 = SECONDS_TO_MICROSECONDS * MICROSECONDS_TO_SECONDS;
        final double conversionFactor2 = SECONDS_TO_MILLISECONDS * MILLISECONDS_TO_SECONDS;
        final double conversionFactor3 = SECONDS_TO_NANOSECONDS * NANOSECONDS_TO_SECONDS;
        final double conversionFactor4 = MILLISECONDS_TO_MICROSECONDS * MICROSECONDS_TO_MILLISECONDS;
        final double conversionFactor5 = MILLISECONDS_TO_NANOSECONDS * NANOSECONDS_TO_MILLISECONDS;
        final double conversionFactor6 = NANOSECONDS_TO_MICROSECONDS * MICROSECONDS_TO_NANOSECONDS;

        // then
        assertThat(value * conversionFactor1).isEqualTo(value);
        assertThat(value * conversionFactor2).isEqualTo(value);
        assertThat(value * conversionFactor3).isEqualTo(value);
        assertThat(value * conversionFactor4).isEqualTo(value);
        assertThat(value * conversionFactor5).isEqualTo(value);
        assertThat(value * conversionFactor6).isEqualTo(value);
    }
}
