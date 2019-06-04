package org.jboss.gm.common.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class FileUtils {

    /**
     * Read the n-th line of a file (n starts from 1)
     * Returns null of the line does not exist
     */
    public static String getNthLine(File target, int n) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(target))) {
            String line = null;
            int i = 0;
            while ((i < n) && ((line = br.readLine()) != null)) {
                i++;
            }
            if (i == n) {
                return line;
            }
            return null;
        }
    }
}
