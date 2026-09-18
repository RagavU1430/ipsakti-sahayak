package com.ipsakti.ip_sakti_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-100)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-ID";
    public static final String MDC_KEY = "request_id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || requestId.isBlank() || requestId.length() > 128) requestId = UUID.randomUUID().toString();
        response.setHeader(HEADER, requestId);
        RequestTiming.start();
        try (MDC.MDCCloseable ignored = MDC.putCloseable(MDC_KEY, requestId)) {
            chain.doFilter(request, response);
        } finally {
            RequestTiming timing = RequestTiming.current();
            if (timing != null) {
                response.setHeader("Server-Timing", timing.serverTiming());
                response.setHeader("X-IPSAKTI-Provider", timing.provider());
                response.setHeader("X-IPSAKTI-Chunks", Integer.toString(timing.chunks()));
                response.setHeader("X-IPSAKTI-Backend-Total-Ms", String.format(java.util.Locale.ROOT, "%.1f", timing.totalMs()));
                response.setHeader("X-IPSAKTI-Route", timing.routeName());
            }
            RequestTiming.clear();
        }
    }
}
