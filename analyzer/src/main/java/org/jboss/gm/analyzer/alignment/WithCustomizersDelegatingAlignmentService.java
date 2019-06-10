package org.jboss.gm.analyzer.alignment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    private final AlignmentService delegate;
    private final List<AlignmentService.RequestCustomizer> requestCustomizers;
    private final List<AlignmentService.ResponseCustomizer> responseCustomizers;

    public WithCustomizersDelegatingAlignmentService(AlignmentService delegate,
            List<RequestCustomizer> requestCustomizers, List<ResponseCustomizer> responseCustomizers) {
        this.delegate = delegate;
        this.requestCustomizers = requestCustomizers != null ? requestCustomizers : new ArrayList<>();
        this.responseCustomizers = responseCustomizers != null ? responseCustomizers : new ArrayList<>();

        this.requestCustomizers.sort(Comparator.comparingInt(RequestCustomizer::order));
        this.responseCustomizers.sort(Comparator.comparingInt(ResponseCustomizer::order));
    }

    @Override
    public Response align(Request request) {
        for (RequestCustomizer requestCustomizer : requestCustomizers) {
            request = requestCustomizer.customize(request);
        }

        Response response = delegate.align(request);

        for (ResponseCustomizer responseCustomizer : responseCustomizers) {
            response = responseCustomizer.customize(response);
        }

        return response;
    }
}
