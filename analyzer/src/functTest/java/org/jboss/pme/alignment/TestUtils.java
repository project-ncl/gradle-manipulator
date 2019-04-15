package org.jboss.pme.alignment;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;

public final class TestUtils {

	private TestUtils() {
	}

	public static void copyDirectory(String classpathResource, File target) throws URISyntaxException, IOException {
		FileUtils.copyDirectory(Paths
				.get(TestUtils.class.getClassLoader().getResource(classpathResource).toURI()).toFile(), target);
	}
}
