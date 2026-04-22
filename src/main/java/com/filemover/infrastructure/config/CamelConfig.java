package com.filemover.infrastructure.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.ManagementStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * Configuração do Apache Camel: cria diretórios necessários e
 * habilita instrumentação de métricas.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CamelConfig {

    private final FileMoverProperties properties;

    /**
     * Garante que os diretórios de source, destination e error existam
     * antes que o Camel inicie as rotas.
     */
    @Bean
    public Boolean ensureDirectoriesExist() {
        createIfAbsent(properties.getSourceDirectory(),      "source");
        createIfAbsent(properties.getDestinationDirectory(), "destination");
        createIfAbsent(properties.getErrorDirectory(),       "error");
        return Boolean.TRUE;
    }

    private void createIfAbsent(String path, String label) {
        File dir = new File(path);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                log.info("Directory created | label={} path={}", label, path);
            } else {
                log.warn("Could not create directory | label={} path={}", label, path);
            }
        } else {
            log.debug("Directory already exists | label={} path={}", label, path);
        }
    }
}
