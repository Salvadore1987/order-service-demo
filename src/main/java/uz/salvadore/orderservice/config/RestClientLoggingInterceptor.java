package uz.salvadore.orderservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class RestClientLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RestClientLoggingInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request,
                                        byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        logRequest(request, body);
        ClientHttpResponse response = execution.execute(request, body);
        logResponse(request, response);
        return response;
    }

    private void logRequest(HttpRequest request, byte[] body) {
        log.info(">>> {} {}", request.getMethod(), request.getURI());
        if (body.length > 0) {
            log.info(">>> Request body: {}", new String(body, StandardCharsets.UTF_8));
        }
    }

    private void logResponse(HttpRequest request, ClientHttpResponse response) throws IOException {
        byte[] responseBody = StreamUtils.copyToByteArray(response.getBody());
        log.info("<<< {} {} — Status: {}",
                request.getMethod(), request.getURI(), response.getStatusCode());
        if (responseBody.length > 0) {
            log.info("<<< Response body: {}", new String(responseBody, StandardCharsets.UTF_8));
        }
    }
}
