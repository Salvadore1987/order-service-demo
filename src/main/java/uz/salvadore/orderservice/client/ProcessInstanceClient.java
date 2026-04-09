package uz.salvadore.orderservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import uz.salvadore.orderservice.config.OAuth2TokenProvider;
import uz.salvadore.orderservice.config.RestClientLoggingInterceptor;

import java.util.Map;

@Component
public class ProcessInstanceClient {

    private static final Logger log = LoggerFactory.getLogger(ProcessInstanceClient.class);

    private final RestClient restClient;

    public ProcessInstanceClient(@Value("${process-engine.worker.engine-url}") String baseUrl,
                                 OAuth2TokenProvider tokenProvider,
                                 RestClientLoggingInterceptor loggingInterceptor) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory()))
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(tokenProvider.getToken());
                    return execution.execute(request, body);
                })
                .requestInterceptor(loggingInterceptor)
                .build();
    }

    public Map<String, Object> createInstance(String definitionKey, String businessKey, Map<String, Object> variables) {
        Map<String, Object> requestBody = Map.of(
                "definitionKey", definitionKey,
                "businessKey", businessKey,
                "variables", variables
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/api/v1/instances")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        log.info("Created process instance: {}", response);
        return response;
    }
}
