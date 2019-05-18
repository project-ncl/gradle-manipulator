package org.jboss.gm.common;

import org.jboss.gm.common.model.ManipulationModel;

public interface ManipulationModelCache {

    ManipulationModel get(String name);

    void put(String name, ManipulationModel manipulationModel);

}
