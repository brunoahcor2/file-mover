package com.filemover.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Configura o RestClient com timeouts explícitos e um interceptor de timing.
 *
 * Sem essa configuração, chamadas HTTP usam o timeout infinito do SO —
 * principal causa de processamentos que travam por 90+ segundos.
 *
 * Localização: infrastructure/config/RestClientConfig.java
 */
@Slf4j
@Configuration
public class RestClientConfig {

    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS    = 5_000;

    @Bean
    public RestClient.Builder restClientBuilder() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MS));
        factory.setReadTimeout(Duration.ofMillis(READ_TIMEOUT_MS));

        return RestClient.builder()
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    long start = System.currentTimeMillis();
                    log.info("[HTTP-OUT] {} {} | início",
                            request.getMethod(), request.getURI());

                    try {
                        var response = execution.execute(request, body);
                        log.info("[HTTP-OUT] {} {} → {} | {}ms",
                                request.getMethod(),
                                request.getURI(),
                                response.getStatusCode(),
                                System.currentTimeMillis() - start);
                        return response;
                    } catch (Exception e) {
                        log.error("[HTTP-OUT] {} {} → FALHOU após {}ms | erro={}",
                                request.getMethod(),
                                request.getURI(),
                                System.currentTimeMillis() - start,
                                e.getMessage());
                        throw e;
                    }
                });
    }
}