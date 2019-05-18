package org.jboss.gm.analyzer.alignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.jboss.gm.common.model.ManipulationModel;
import org.junit.Test;

/**
 * @author <a href="claprun@redhat.com">Christophe Laprun</a>
 */
public class ManipulationModelTest {

    @Test
    public void findCorrespondingChildWithName() {
        ManipulationModel model = new ManipulationModel("root", "bar");
        final ManipulationModel child = new ManipulationModel("child1", "bar");
        model.addChild(child);
        model.addChild(new ManipulationModel("child2", "bar"));
        final ManipulationModel child11 = new ManipulationModel("child11", "bar");
        child.addChild(child11);

        assertThat(model.findCorrespondingChild(model.getName())).isEqualTo(model);
        assertThat(model.findCorrespondingChild("child1")).isEqualTo(child);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> model.findCorrespondingChild("child11"))
                .withMessage("ManipulationModel child11 does not exist");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> model.findCorrespondingChild(""))
                .withMessage("Supplied child name cannot be empty");

        assertThat(child.findCorrespondingChild("child11")).isEqualTo(child11);
    }

    @Test
    public void findCorrespondingChildWithPath() {
        ManipulationModel model = new ManipulationModel("root", "bar");
        final ManipulationModel child = new ManipulationModel("child1", "bar");
        model.addChild(child);
        model.addChild(new ManipulationModel("child2", "bar"));
        final ManipulationModel child11 = new ManipulationModel("child11", "bar");
        child.addChild(child11);
        final ManipulationModel child111 = new ManipulationModel("child111", "bar");
        child11.addChild(child111);

        assertThat(model.findCorrespondingChild(":")).isEqualTo(model);

        assertThat(model.findCorrespondingChild(":child1")).isEqualTo(child);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> model.findCorrespondingChild(":child11"))
                .withMessage("ManipulationModel child11 does not exist");

        assertThat(child.findCorrespondingChild(":child1:child11")).isEqualTo(child11);
        assertThat(child.findCorrespondingChild(":child1:child11:child111")).isEqualTo(child111);
    }
}
