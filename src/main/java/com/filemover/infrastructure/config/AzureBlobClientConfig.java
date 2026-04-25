package com.filemover.infrastructure.config;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.filemover.infrastructure.config.FileMoverProperties.StorageConfig;
import com.filemover.infrastructure.storage.StorageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra BlobServiceClient como beans Spring para cada role Azure.
 * O Camel referencia esses beans via #azureBlobServiceClient_destination etc.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AzureBlobClientConfig {

    private final FileMoverProperties properties;

    @Bean("azureBlobServiceClient_source")
    public BlobServiceClient sourceBlobServiceClient() {
        return buildIfAzure("source", properties.getSource());
    }

    @Bean("azureBlobServiceClient_destination")
    public BlobServiceClient destinationBlobServiceClient() {
        return buildIfAzure("destination", properties.getDestination());
    }

    @Bean("azureBlobServiceClient_error")
    public BlobServiceClient errorBlobServiceClient() {
        return buildIfAzure("error", properties.getError());
    }

    private BlobServiceClient buildIfAzure(String role, StorageConfig config) {
        if (config.getType() != StorageType.AZURE_BLOB) {
            log.debug("[AZURE-CLIENT] role={} type={} — bean vazio não criado.", role, config.getType());
            return null;
        }
        String connStr = config.getAzure().getConnectionString();
        log.info("[AZURE-CLIENT] role={} criando BlobServiceClient.", role);
        return new BlobServiceClientBuilder()
                .connectionString(connStr)
                .buildClient();
    }
}