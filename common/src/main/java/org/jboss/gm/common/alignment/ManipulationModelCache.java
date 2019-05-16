package org.jboss.gm.common.alignment;

public interface ManipulationModelCache {

    ManipulationModel get(String name);

    void put(String name, ManipulationModel manipulationModel);

    ManipulationModelCache NOOP = new ManipulationModelCache() {
        @Override
        public ManipulationModel get(String name) {
            return null;
        }

        @Override
        public void put(String name, ManipulationModel manipulationModel) {

        }
    };

}
