package com.filemover.infrastructure.storage.adapter;

import com.filemover.application.port.out.StorageGateway;
import com.filemover.infrastructure.config.FileMoverProperties.StorageConfig.S3Config;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.net.URI;

@Slf4j
public class S3StorageAdapter implements StorageGateway {

    private final S3Client s3;
    private final S3Config config;

    public S3StorageAdapter(S3Config config) {
        this.config = config;

        var builder = S3Client.builder()
                .region(Region.of(config.getRegion()));

        // endpoint override explícito (prioridade sobre AWS_ENDPOINT_URL)
        if (config.getEndpoint() != null && !config.getEndpoint().isBlank()) {
            log.info("[S3-INIT] usando endpoint override: {}", config.getEndpoint());
            builder.endpointOverride(URI.create(config.getEndpoint()))
                    .serviceConfiguration(
                            S3Configuration.builder()
                                    .pathStyleAccessEnabled(true)
                                    .build()
                    );
        }

        // credenciais: se não configuradas explicitamente, o SDK lê
        // AWS_ACCESS_KEY_ID e AWS_SECRET_ACCESS_KEY do ambiente automaticamente
        if (config.getAccessKeyId() != null && !config.getAccessKeyId().isBlank()) {
            log.info("[S3-INIT] usando credenciais explícitas | accessKey={}",
                    config.getAccessKeyId());
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                            config.getAccessKeyId(),
                            config.getSecretAccessKey())));
        } else {
            log.info("[S3-INIT] usando DefaultCredentialsProvider (IAM/env)");
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        log.info("[S3-INIT] S3Client criado | bucket={} region={} endpoint={}",
                config.getBucketName(), config.getRegion(), config.getEndpoint());

        this.s3 = builder.build();
    }

    @Override
    public void write(String path, InputStream content, long contentLength, String fileName) {
        String key = config.getKeyPrefix() + fileName;
        log.info("[S3] upload | bucket={} key={}", config.getBucketName(), key);

        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(config.getBucketName())
                        .key(key)
                        .build(),
                RequestBody.fromInputStream(content, contentLength)
        );
    }

    @Override
    public InputStream read(String path) {
        return s3.getObject(GetObjectRequest.builder()
                .bucket(config.getBucketName())
                .key(path)
                .build());
    }

    @Override
    public void delete(String path) {
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(config.getBucketName())
                .key(path)
                .build());
    }

    @Override
    public String providerName() { return "S3"; }
}