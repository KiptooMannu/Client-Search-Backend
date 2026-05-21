package com.kazikonnect.backend.core.config;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice
public class AcceptedStatusModifierAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(@NonNull MethodParameter returnType, @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    @Nullable
    public Object beforeBodyWrite(@Nullable Object body, @NonNull MethodParameter returnType, @NonNull MediaType selectedContentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response) {
        if (response instanceof ServletServerHttpResponse servletResponse) {
            int status = servletResponse.getServletResponse().getStatus();
            // If the response is successful (2xx), change it to 202 Accepted.
            if (status >= 200 && status < 300) {
                servletResponse.setStatusCode(HttpStatus.ACCEPTED);
            }
        }
        return body;
    }
}
