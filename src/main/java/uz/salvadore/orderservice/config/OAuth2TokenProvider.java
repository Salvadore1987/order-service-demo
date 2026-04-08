package uz.salvadore.orderservice.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;

@Component
public class OAuth2TokenProvider {

    private static final Logger log = LoggerFactory.getLogger(OAuth2TokenProvider.class);
    private static final int EXPIRY_MARGIN_SECONDS = 10;

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    private String cachedToken;
    private Instant expiresAt;

    public OAuth2TokenProvider(@Value("${process-engine.worker.auth.token-uri}") String tokenUri,
                               @Value("${process-engine.worker.auth.client-id}") String clientId,
                               @Value("${process-engine.worker.auth.client-secret}") String clientSecret) {
        this.restClient = RestClient.builder().baseUrl(tokenUri).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.expiresAt = Instant.MIN;
    }

    public synchronized String getToken() {
        if (cachedToken == null || Instant.now().isAfter(expiresAt)) {
            refreshToken();
        }
        return cachedToken;
    }

    private void refreshToken() {
        log.info("Requesting new OAuth2 token from Keycloak");

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        TokenResponse response = restClient.post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);

        cachedToken = response.accessToken();
        expiresAt = Instant.now().plusSeconds(response.expiresIn() - EXPIRY_MARGIN_SECONDS);

        log.info("OAuth2 token obtained, expires in {} seconds", response.expiresIn());
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("token_type") String tokenType
    ) {
    }
}
