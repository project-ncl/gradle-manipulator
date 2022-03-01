package org.jboss.gm.analyzer.alignment;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.experimental.UtilityClass;

import org.gradle.api.Project;
import org.jboss.gm.analyzer.alignment.AlignmentService.Manipulator;
import org.jboss.gm.common.Configuration;

import static java.util.Comparator.comparingInt;

/**
 * This is what {@value org.jboss.gm.analyzer.alignment.AlignmentTask#NAME} task uses to retrieve a fully wired
 * {@link org.jboss.gm.analyzer.alignment.AlignmentService}
 */
@UtilityClass
final class AlignerFactory {
    static AlignmentService getAlignmentService(Configuration configuration) {
        return new DAAlignmentService(configuration);
    }

    static List<Manipulator> getManipulators(Configuration configuration, Project root) {
        return Stream.of(
                new UpdateProjectVersionCustomizer(configuration, root),
                new DependencyOverrideCustomizer(configuration, root.getAllprojects()))
                .sorted(comparingInt(AlignmentService.Manipulator::order)).collect(Collectors.toList());
    }
}
