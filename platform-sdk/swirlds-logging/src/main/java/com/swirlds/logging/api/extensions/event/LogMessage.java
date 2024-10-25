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

package com.swirlds.logging.api.extensions.event;

import com.swirlds.logging.api.extensions.provider.LogProvider;
import com.swirlds.logging.api.internal.event.ParameterizedLogMessage;
import com.swirlds.logging.api.internal.event.SimpleLogMessage;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A log message that is part of a {@link LogEvent}. A message can be a simple String (see {@link SimpleLogMessage}) or
 * a parameterized String (see {@link ParameterizedLogMessage}). {@link LogProvider} can provide custom implementations of this interface.
 *
 * @see SimpleLogMessage
 * @see ParameterizedLogMessage
 */
public interface LogMessage {

    /**
     * Returns the message as a String. If the message is parameterized, the parameters are resolved.
     *
     * @return the message as a String
     */
    @NonNull
    String getMessage();
}
