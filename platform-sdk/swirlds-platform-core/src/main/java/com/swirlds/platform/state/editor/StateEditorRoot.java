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

package com.swirlds.platform.state.editor;

import picocli.CommandLine;

/**
 * The root pcli command for the state editor.
 */
@CommandLine.Command(name = "state editor", description = "An interactive SignedState.swh editor.")
public class StateEditorRoot implements Runnable {
    @Override
    public void run() {
        // It should be impossible to reach this
        throw new IllegalStateException("No subcommand provided");
    }
}
