package org.jboss.gm.common.utils;

import java.io.File;
import java.io.IOException;

import lombok.Getter;
import lombok.experimental.UtilityClass;

import org.commonjava.maven.ext.common.ManipulationException;
import org.gradle.internal.jvm.JavaInfo;
import org.gradle.internal.jvm.Jvm;

@UtilityClass
public class JavaUtils {

    private static final JavaInfo javaInfo = Jvm.current();

    @Getter
    private static final File javaHome = javaInfo.getJavaHome();

    public static boolean compareJavaHome(File newHome) throws ManipulationException {
        try {
            if (newHome.getCanonicalPath().equals(javaHome.getCanonicalPath())) {
                return true;
            }
        } catch (IOException e) {
            throw new ManipulationException("Unable to compare java home locations", e);
        }
        return false;
    }
}
