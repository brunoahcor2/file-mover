package com.filemover.infrastructure.storage;

import com.filemover.application.port.out.StorageGateway;
import com.filemover.infrastructure.config.FileMoverProperties;
import com.filemover.infrastructure.storage.adapter.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cria os beans sourceStorage, destinationStorage e errorStorage
 * baseado na configuração de cada role.
 *
 * Os três são completamente independentes — podem usar provedores
 * diferentes (ex: S3 → LOCAL, erro → SFTP).
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class StorageGatewayFactory {

    private final FileMoverProperties properties;

    @Bean("sourceStorage")
    public StorageGateway sourceStorage() {
        return build("source", properties.getSource());
    }

    @Bean("destinationStorage")
    public StorageGateway destinationStorage() {
        return build("destination", properties.getDestination());
    }

    @Bean("errorStorage")
    public StorageGateway errorStorage() {
        return build("error", properties.getError());
    }

    private StorageGateway build(String role,
                                 FileMoverProperties.StorageConfig config) {
        log.info("[STORAGE] role={} type={}", role, config.getType());
        return switch (config.getType()) {
            case LOCAL      -> new LocalStorageAdapter(properties);
            case S3         -> new S3StorageAdapter(config.getS3());
            case AZURE_BLOB -> new AzureBlobStorageAdapter(config.getAzure());
            case SFTP       -> new SftpStorageAdapter(config.getSftp());
        };
    }
}