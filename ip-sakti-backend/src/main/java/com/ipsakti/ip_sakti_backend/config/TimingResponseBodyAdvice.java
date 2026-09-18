package com.ipsakti.ip_sakti_backend.config;

import java.util.Locale;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Ensures Server-Timing and X-IPSAKTI performance headers are written before
 * the response body is committed.
 */
@ControllerAdvice
public class TimingResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        RequestTiming timing = RequestTiming.current();
        if (timing != null) {
            response.getHeaders().set("Server-Timing", timing.serverTiming());
            response.getHeaders().set("X-IPSAKTI-Provider", timing.provider());
            response.getHeaders().set("X-IPSAKTI-Chunks", Integer.toString(timing.chunks()));
            response.getHeaders().set("X-IPSAKTI-Backend-Total-Ms", String.format(Locale.ROOT, "%.1f", timing.totalMs()));
            response.getHeaders().set("X-IPSAKTI-Route", timing.routeName());
        }
        return body;
    }
}
