package org.jboss.gm.common.utils;

import java.util.Map;
import java.util.Set;

import lombok.experimental.UtilityClass;

import org.apache.commons.beanutils.ContextClassLoaderLocal;
import org.commonjava.maven.ext.core.state.RESTState;
import org.commonjava.maven.ext.core.util.PropertiesUtils;
import org.commonjava.maven.ext.io.rest.DefaultTranslator;
import org.commonjava.maven.ext.io.rest.Translator;
import org.jboss.da.model.rest.Strategy;
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
            Map<String, String> restRanks = PropertiesUtils.getPropertiesByPrefix(configuration.getProperties(),
                    RESTState.REST_DEPENDENCY_RANKS);
            Map<String, String> restAllows = PropertiesUtils.getPropertiesByPrefix(configuration.getProperties(),
                    RESTState.REST_DEPENDENCY_ALLOW_LIST);
            Map<String, String> restDenies = PropertiesUtils.getPropertiesByPrefix(configuration.getProperties(),
                    RESTState.REST_DEPENDENCY_DENY_LIST);

            Set<Strategy> strategies = RESTState.constructStrategies(
                    configuration.restGlobalDependencyRanks(),
                    configuration.restGlobalDependencyAllowList(),
                    configuration.restGlobalDependencyDenyList(),
                    restRanks,
                    restAllows,
                    restDenies,
                    configuration.restDependencyRankDelimiter());

            return new DefaultTranslator(
                    configuration.daEndpoint(),
                    configuration.restMaxSize(),
                    Translator.CHUNK_SPLIT_COUNT,
                    configuration.restBrewPullActive(),
                    configuration.restMode(),
                    configuration.restHeaders(),
                    configuration.restConnectionTimeout(),
                    configuration.restSocketTimeout(),
                    configuration.restRetryDuration(),
                    strategies);
        }
    }
}
