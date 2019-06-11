package org.jboss.gm.analyzer.alignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.Test;

public class WithCustomizersDelegatingAlignmentServiceTest {

    @Test
    public void nullCustomizersShouldBeAllowed() {
        final AlignmentService delegate = mock(AlignmentService.class);
        final AlignmentService.Request request = mock(AlignmentService.Request.class);
        final AlignmentService.Response response = mock(AlignmentService.Response.class);

        when(delegate.align(request)).thenReturn(response);

        final AlignmentService sut = new WithCustomizersDelegatingAlignmentService(delegate, null, null);

        assertThat(sut.align(request)).isSameAs(response);
    }

    @Test
    public void requestCustomizersShouldBeInvokedInOrder() {
        final AlignmentService delegate = mock(AlignmentService.class);
        final AlignmentService.RequestCustomizer reqCustomizer1 = mock(AlignmentService.RequestCustomizer.class);
        final AlignmentService.RequestCustomizer reqCustomizer2 = mock(AlignmentService.RequestCustomizer.class);
        final AlignmentService.Request originalReq = mock(AlignmentService.Request.class);
        final AlignmentService.Request customizedReq1 = mock(AlignmentService.Request.class);
        final AlignmentService.Request customizedReq2 = mock(AlignmentService.Request.class);
        final AlignmentService.Response response = mock(AlignmentService.Response.class);

        when(reqCustomizer1.customize(originalReq)).thenReturn(customizedReq1);
        when(reqCustomizer1.order()).thenReturn(0);
        when(reqCustomizer2.order()).thenReturn(10);
        when(reqCustomizer2.customize(customizedReq1)).thenReturn(customizedReq2);
        when(delegate.align(customizedReq2)).thenReturn(response);

        final AlignmentService sut = new WithCustomizersDelegatingAlignmentService(delegate,
                Arrays.asList(reqCustomizer2, reqCustomizer1), null);

        assertThat(sut.align(originalReq)).isSameAs(response);
    }

    @Test
    public void responseCustomizersShouldBeInvokedInOrder() {
        final AlignmentService delegate = mock(AlignmentService.class);
        final AlignmentService.Request request = mock(AlignmentService.Request.class);
        final AlignmentService.ResponseCustomizer respCustomizer1 = mock(AlignmentService.ResponseCustomizer.class);
        final AlignmentService.ResponseCustomizer respCustomizer2 = mock(AlignmentService.ResponseCustomizer.class);
        final AlignmentService.Response originalResponse = mock(AlignmentService.Response.class);
        final AlignmentService.Response customizedResp1 = mock(AlignmentService.Response.class);
        final AlignmentService.Response customizedResp2 = mock(AlignmentService.Response.class);

        when(delegate.align(request)).thenReturn(originalResponse);
        when(respCustomizer1.customize(originalResponse)).thenReturn(customizedResp1);
        when(respCustomizer2.customize(customizedResp1)).thenReturn(customizedResp2);
        when(respCustomizer1.order()).thenReturn(0);
        when(respCustomizer2.order()).thenReturn(10);

        final AlignmentService sut = new WithCustomizersDelegatingAlignmentService(delegate,
                null, Arrays.asList(respCustomizer1, respCustomizer2));

        assertThat(sut.align(request)).isSameAs(customizedResp2);
    }
}
