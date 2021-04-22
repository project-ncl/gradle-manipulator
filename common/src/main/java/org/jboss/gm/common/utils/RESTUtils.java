package org.jboss.gm.common.utils;

import lombok.experimental.UtilityClass;
import org.commonjava.maven.ext.io.rest.DefaultTranslator;
import org.commonjava.maven.ext.io.rest.Translator;
import org.jboss.gm.common.Configuration;

@UtilityClass
public class RESTUtils {

    private static Translator restCache;

    public static synchronized Translator getTranslator(Configuration configuration) {
        if (restCache == null) {
            restCache = new DefaultTranslator(
                    configuration.daEndpoint(),
                    configuration.restMaxSize(),
                    Translator.CHUNK_SPLIT_COUNT,
                    configuration.restRepositoryGroup(),
                    configuration.versionIncrementalSuffix(),
                    configuration.restHeaders(),
                    configuration.restConnectionTimeout(),
                    configuration.restSocketTimeout(),
                    configuration.restRetryDuration());
        }
        return restCache;
    }
}
