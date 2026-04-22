package com.filemover.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configuração do RestClient com timeouts parametrizáveis.
 */
@Configuration
@RequiredArgsConstructor
public class RestClientConfig {

    private final FileMoverProperties properties;

    @Bean
    public RestClient.Builder restClientBuilder() {
        FileMoverProperties.ValidationService svc = properties.getValidationService();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(svc.getConnectTimeoutMs());
        factory.setReadTimeout(svc.getTimeoutMs());

        return RestClient.builder()
                .requestFactory(factory);
    }
}
