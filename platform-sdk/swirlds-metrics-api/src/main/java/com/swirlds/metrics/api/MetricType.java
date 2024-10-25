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

package com.swirlds.metrics.api;

/**
 * The types of {@code Metric}
 */
@Deprecated
public enum MetricType {

    /**
     * An accumulator is a metric that accumulates results according to the supplied operator.
     */
    ACCUMULATOR,

    /**
     * A counter is a metric that represents a single increasing counter whose value can only increase.
     */
    COUNTER,

    /**
     * A gauge is a metric that represents a single numerical value that can arbitrarily go up and down.
     */
    GAUGE,

    /**
     * A running average is a metric that calculates trends over short periods of time using a set of data.
     */
    RUNNING_AVERAGE,

    /**
     * A speedometer is a metric that represents how many times per unit of time an operation is performed.
     */
    SPEEDOMETER,

    /**
     * A stat entry is a flexible metric which behavior is defined by a provided operation.
     */
    STAT_ENTRY
}
