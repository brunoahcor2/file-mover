package com.filemover.infrastructure.storage.adapter;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.filemover.application.port.out.StorageGateway;
import com.filemover.infrastructure.config.FileMoverProperties.StorageConfig.AzureConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;

@Slf4j
public class AzureBlobStorageAdapter implements StorageGateway {

    private final BlobServiceClient client;
    private final AzureConfig       config;

    public AzureBlobStorageAdapter(AzureConfig config) {
        this.config = config;
        this.client = new BlobServiceClientBuilder()
                .connectionString(config.getConnectionString())
                .buildClient();
    }

    @Override
    public void write(String path, InputStream content, long contentLength, String fileName) {
        String blobName = config.getKeyPrefix() + fileName;
        log.info("[AZURE] upload | container={} blob={}", config.getContainerName(), blobName);

        client.getBlobContainerClient(config.getContainerName())
                .getBlobClient(blobName)
                .upload(content, contentLength, true);
    }

    @Override
    public InputStream read(String path) {
        return client.getBlobContainerClient(config.getContainerName())
                .getBlobClient(path)
                .openInputStream();
    }

    @Override
    public void delete(String path) {
        client.getBlobContainerClient(config.getContainerName())
                .getBlobClient(path)
                .delete();
    }

    @Override
    public String providerName() { return "AZURE_BLOB"; }
}