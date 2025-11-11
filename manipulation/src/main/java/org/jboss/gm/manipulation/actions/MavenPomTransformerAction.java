package org.jboss.gm.manipulation.actions;

import static org.jboss.gm.common.versioning.ProjectVersionFactory.withGAV;

import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectRef;
import org.gradle.api.Action;
import org.gradle.api.XmlProvider;
import org.jboss.gm.common.model.ManipulationModel;
import org.jboss.gm.manipulation.ResolvedDependenciesRepository;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Overrides data in a maven POM.
 */
public class MavenPomTransformerAction
        implements Action<XmlProvider> {

    private static final String VERSION = "version";
    private static final String DEPENDENCIES = "dependencies";
    private static final String DEPENDENCY = "dependency";
    private static final String GROUPID = "groupId";
    private static final String ARTIFACTID = "artifactId";

    private final ManipulationModel alignmentConfiguration;
    private final ResolvedDependenciesRepository resolvedDependenciesRepository;

    /**
     * Creates a new maven POM transformer action
     *
     * @param alignmentConfiguration the alignment configuration
     * @param resolvedDependenciesRepository the resolved dependencies repository
     */
    public MavenPomTransformerAction(
            ManipulationModel alignmentConfiguration,
            ResolvedDependenciesRepository resolvedDependenciesRepository) {
        this.alignmentConfiguration = alignmentConfiguration;
        this.resolvedDependenciesRepository = resolvedDependenciesRepository;
    }

    @Override
    public void execute(XmlProvider xmlProvider) {
        transformDependencies(xmlProvider);
    }

    private void transformDependencies(XmlProvider xmlProvider) {
        // find <dependencies> child
        Node dependenciesNode = null;
        NodeList childNodes = xmlProvider.asElement().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && DEPENDENCIES.equals(child.getNodeName())) {
                dependenciesNode = child;
                break;
            }
        }

        if (dependenciesNode == null) {
            return;
        }

        // go through dependencies
        NodeList dependencyNodes = ((Element) dependenciesNode).getElementsByTagName(DEPENDENCY);
        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Node dependencyNode = dependencyNodes.item(i);

            // collect GAV
            String group = null;
            String name = null;
            String version = null;
            Node versionNode = null;
            for (int j = 0; j < dependencyNode.getChildNodes().getLength(); j++) {
                Node child = dependencyNode.getChildNodes().item(j);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    switch (child.getNodeName()) {
                        case GROUPID:
                            group = child.getTextContent();
                            break;
                        case ARTIFACTID:
                            name = child.getTextContent();
                            break;
                        case VERSION:
                            version = child.getTextContent();
                            versionNode = child;
                            break;
                    }
                }
            }
            if (group == null || name == null) {
                continue;
            }
            if (version == null) {
                version = resolvedDependenciesRepository.get(new SimpleProjectRef(group, name));
                if (version == null) {
                    continue;
                }
            }

            // modify version
            final ProjectVersionRef initial = withGAV(group, name, version);
            final ProjectVersionRef aligned = alignmentConfiguration.getAlignedDependencies().get(initial.toString());
            if (aligned != null) {
                if (versionNode == null) {
                    versionNode = dependencyNode.getOwnerDocument().createElement("version");
                    dependencyNode.appendChild(versionNode);
                }
                versionNode.setTextContent(aligned.getVersionString());
            }
        }
    }
}
