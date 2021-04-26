package org.jboss.gm.common.utils;

import lombok.experimental.UtilityClass;

import org.apache.commons.beanutils.ContextClassLoaderLocal;
import org.commonjava.maven.ext.io.rest.DefaultTranslator;
import org.commonjava.maven.ext.io.rest.Translator;
import org.jboss.gm.common.Configuration;

@UtilityClass
public class RESTUtils {

    private static final ContextClassLoaderLocalWithConfiguration cache = new ContextClassLoaderLocalWithConfiguration();

    public static Translator getTranslator(Configuration config) {
        cache.configuration = config;
        return cache.get();
    }

    private static class ContextClassLoaderLocalWithConfiguration extends ContextClassLoaderLocal<Translator> {
        Configuration configuration = null;

        @Override
        protected Translator initialValue() {
            return new DefaultTranslator(
                    configuration.daEndpoint(),
                    configuration.restMaxSize(),
                    Translator.CHUNK_SPLIT_COUNT,
                    configuration.restRepositoryGroup(),
                    configuration.restBrewPullActive(),
                    configuration.restMode(),
                    configuration.versionIncrementalSuffix(),
                    configuration.restHeaders(),
                    configuration.restConnectionTimeout(),
                    configuration.restSocketTimeout(),
                    configuration.restRetryDuration());
        }
    }
}
