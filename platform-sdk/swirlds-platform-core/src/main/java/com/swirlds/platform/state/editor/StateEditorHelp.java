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

package com.swirlds.platform.state.editor;

import com.swirlds.cli.utility.CommandBuilder;
import com.swirlds.cli.utility.SubcommandOf;
import picocli.CommandLine;

@CommandLine.Command(
        name = "help",
        aliases = {"h"},
        helpCommand = true,
        description = "Show the state editor usage information.")
@SubcommandOf(StateEditorRoot.class)
public class StateEditorHelp extends StateEditorOperation {

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @Override
    @SuppressWarnings("java:S106")
    public void run() {
        spec.commandLine().getParent().usage(System.out, CommandBuilder.getColorScheme());
    }
}
