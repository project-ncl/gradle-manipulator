package org.jboss.gm.common.utils;

import lombok.experimental.UtilityClass;

import org.aeonbits.owner.ConfigCache;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.logging.FilteringCustomLogger;
import org.jboss.gm.common.logging.GMLogger;

import com.redhat.resilience.otel.OTelCLIHelper;

@UtilityClass
public class OTELUtils {

    private final Logger logger = GMLogger.getLogger(OTELUtils.class);

    private final LogLevel originalLevel = FilteringCustomLogger.getContext().getLevel();

    public void startOTel() {
        Configuration configuration = ConfigCache.getOrCreate(Configuration.class);
        String endpoint = System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT");
        String service = System.getenv("OTEL_SERVICE_NAME");

        if (endpoint != null && !configuration.disableOTEL()) {
            if (service == null) {
                service = "gradle-manipulator";
            }
            logger.info("Enabling OpenTelemetry collection on {} with service name {}", endpoint, service);
            try {
                if (originalLevel != LogLevel.DEBUG) {
                    FilteringCustomLogger.getContext().setLevel(LogLevel.DEBUG);
                }
                OTelCLIHelper.startOTel(service, "alignment-plugin",
                        OTelCLIHelper.defaultSpanProcessor(OTelCLIHelper.defaultSpanExporter(endpoint)));
            } finally {
                FilteringCustomLogger.getContext().setLevel(originalLevel);
            }
        }
    }

    public void stopOTel() {
        try {
            if (originalLevel != LogLevel.DEBUG) {
                FilteringCustomLogger.getContext().setLevel(LogLevel.DEBUG);
            }
            OTelCLIHelper.stopOTel();
        } finally {
            FilteringCustomLogger.getContext().setLevel(originalLevel);
        }
    }
}
