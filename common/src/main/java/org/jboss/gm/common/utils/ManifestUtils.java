package org.jboss.gm.common.utils;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.Manifest;

import lombok.experimental.UtilityClass;

import org.commonjava.maven.ext.common.ManipulationUncheckedException;

@UtilityClass
public class ManifestUtils {
    /**
     * Retrieves the Version and SHA this was built with.
     *
     * @return the Version and GIT sha of this codebase.
     */
    public static String getManifestInformation() {
        String result = "";
        try {
            final Enumeration<URL> resources = ManifestUtils.class.getClassLoader()
                    .getResources("META-INF/MANIFEST.MF");

            while (resources.hasMoreElements()) {
                final URL jarUrl = resources.nextElement();

                if (jarUrl.getFile().contains("/cli-") || jarUrl.getFile().contains("/analyzer-")
                        || jarUrl.getFile().contains("/manipulation-")) {
                    final Manifest manifest = new Manifest(jarUrl.openStream());

                    result = manifest.getMainAttributes()
                            .getValue("Implementation-Version");
                    result += " ( SHA: " + manifest.getMainAttributes()
                            .getValue("Scm-Revision") + " ) ";
                    break;
                }
            }
        } catch (final IOException e) {
            throw new ManipulationUncheckedException("Error retrieving information from manifest", e);
        }

        return result;
    }
}
