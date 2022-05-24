/*
 * Copyright (C) 2018 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.gm.cli;

import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.util.ManifestUtils;

import picocli.CommandLine;

public class ManifestVersionProvider
        implements CommandLine.IVersionProvider {
    /**
     * Returns version information for a command.
     *
     * @return version information (each string in the array is displayed on a separate line)
     */
    @Override
    public String[] getVersion() throws ManipulationException {
        return new String[] { "Gradle Analyser CLI " + ManifestUtils.getManifestInformation(Main.class) };
    }
}
