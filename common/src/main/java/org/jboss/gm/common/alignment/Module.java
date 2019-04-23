/**
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 * <p>
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.gm.common.alignment;

import java.util.HashMap;
import java.util.Map;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author <a href="claprun@redhat.com">Christophe Laprun</a>
 */
public class Module {
    @JsonProperty
    private String name;

    @JsonProperty
    private String newVersion;

    private Map<String, ProjectVersionRef> alignedDependencies = new HashMap<>();

    public Module() {
    }

    public Module(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Map<String, ProjectVersionRef> getAlignedDependencies() {
        return alignedDependencies;
    }

    public String getNewVersion() {
        return newVersion;
    }

    public void setNewVersion(String newProjectVersion) {
        this.newVersion = newProjectVersion;
    }
}
