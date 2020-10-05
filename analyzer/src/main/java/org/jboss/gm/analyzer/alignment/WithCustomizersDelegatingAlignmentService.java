package org.jboss.gm.analyzer.alignment;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.commonjava.maven.ext.common.ManipulationException;
import org.gradle.api.logging.Logger;
import org.jboss.gm.common.logging.GMLogger;

/**
 * An implementation of {@link org.jboss.gm.analyzer.alignment.AlignmentService} that
 * delegates to another {@link org.jboss.gm.analyzer.alignment.AlignmentService}.
 * The request the delegate receives is not the original this class receives, but
 * is the product of the list of {@link org.jboss.gm.analyzer.alignment.AlignmentService.RequestCustomizer} this class
 * was created with.
 * Moreover, the response returned is not what the delegate returned, but is the result of the list
 * of {@link org.jboss.gm.analyzer.alignment.AlignmentService.ResponseCustomizer} this class was created with
 *
 * @see org.jboss.gm.analyzer.alignment.AlignmentServiceFactory
 */
public class WithCustomizersDelegatingAlignmentService implements AlignmentService {

    private final Logger logger = GMLogger.getLogger(getClass());

    private final DAAlignmentService delegate;
    private final List<AlignmentService.RequestCustomizer> requestCustomizers;
    private final List<AlignmentService.ResponseCustomizer> responseCustomizers;

    public WithCustomizersDelegatingAlignmentService(DAAlignmentService delegate,
            List<RequestCustomizer> requestCustomizers,
            List<ResponseCustomizer> responseCustomizers) {
        this.delegate = delegate;
        this.requestCustomizers = requestCustomizers != null
                ? requestCustomizers.stream().sorted(Comparator.comparingInt(RequestCustomizer::order))
                        .collect(Collectors.toList())
                : Collections.emptyList();
        this.responseCustomizers = responseCustomizers != null
                ? responseCustomizers.stream().sorted(Comparator.comparingInt(ResponseCustomizer::order))
                        .collect(Collectors.toList())
                : Collections.emptyList();
    }

    @Override
    public Response align(Request request) throws ManipulationException {

        logger.debug("Invoking request customizers...");
        for (RequestCustomizer requestCustomizer : requestCustomizers) {
            request = requestCustomizer.customize(request);
        }

        logger.debug("Invoking DA...");
        Response response = delegate.align(request);

        logger.debug("Invoking response customizers...");
        for (ResponseCustomizer responseCustomizer : responseCustomizers) {
            response = responseCustomizer.customize(response);
        }

        return response;
    }
}
