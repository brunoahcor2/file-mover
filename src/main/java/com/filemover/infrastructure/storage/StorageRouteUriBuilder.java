package com.filemover.infrastructure.storage;

import com.filemover.infrastructure.config.FileMoverProperties;
import com.filemover.infrastructure.config.FileMoverProperties.StorageConfig;
import com.filemover.infrastructure.config.FileMoverProperties.StorageConfig.S3Config;
import com.filemover.infrastructure.config.FileMoverProperties.StorageConfig.SftpConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Monta URIs do Camel em runtime baseado no tipo de storage configurado.
 *
 * Cada provider tem seu próprio componente Camel com poll nativo:
 *   LOCAL → file://
 *   S3    → aws2-s3://
 *   SFTP  → sftp://
 *   AZURE → azure-storage-blob://
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageRouteUriBuilder {

    private final FileMoverProperties properties;

    public String buildSourceUri() {
        String uri = buildUri(properties.getSource(), "source", properties.getPollDelay());
        log.info("[URI] source={}", uri);
        return uri;
    }

    public String buildDestinationUri() {
        String uri = buildUri(properties.getDestination(), "destination", null);
        log.info("[URI] destination={}", uri);
        return uri;
    }

    public String buildErrorUri() {
        String uri = buildUri(properties.getError(), "error", null);
        log.info("[URI] error={}", uri);
        return uri;
    }

    private String buildUri(StorageConfig config, String role, Long pollDelay) {
        return switch (config.getType()) {
            case LOCAL      -> buildLocalUri(config, role, pollDelay);
            case S3         -> buildS3Uri(config.getS3(), role, pollDelay);
            case SFTP       -> buildSftpUri(config.getSftp(), role, pollDelay);
            case AZURE_BLOB -> buildAzureUri(config.getAzure(), role, pollDelay);
        };
    }

    // ── LOCAL ──────────────────────────────────────────────────────────────

    private String buildLocalUri(StorageConfig config, String role, Long pollDelay) {
        String dir = switch (role) {
            case "source"      -> properties.getSourceDirectory();
            case "destination" -> properties.getDestinationDirectory();
            default            -> properties.getErrorDirectory();
        };

        if (pollDelay != null) {
            // source: poll ativo com filtro e readLock
            return "file:%s?include=.*\\.txt&readLock=changed&readLockCheckInterval=500&readLockMinAge=500&delete=true&delay=%d"
                    .formatted(dir, pollDelay);
        }
        // destination / error: apenas gravar
        return "file:%s?autoCreate=true".formatted(dir);
    }

    // ── S3 ─────────────────────────────────────────────────────────────────

    private String buildS3Uri(S3Config s3, String role, Long pollDelay) {
        StringBuilder uri = new StringBuilder("aws2-s3://")
                .append(s3.getBucketName());

        uri.append("?region=").append(s3.getRegion());
        uri.append("&useDefaultCredentialsProvider=false");
        uri.append("&accessKey=").append(s3.getAccessKeyId());
        uri.append("&secretKey=").append(s3.getSecretAccessKey());

        if (s3.getEndpoint() != null && !s3.getEndpoint().isBlank()) {
            uri.append("&overrideEndpoint=true");
            uri.append("&uriEndpointOverride=").append(s3.getEndpoint());
            uri.append("&forcePathStyle=true");
        }

        if (s3.getKeyPrefix() != null && !s3.getKeyPrefix().isBlank()) {
            uri.append("&prefix=").append(s3.getKeyPrefix());
        }

        if (pollDelay != null) {
            // source: poll, move objeto após consumo
            uri.append("&deleteAfterRead=true");
            uri.append("&delay=").append(pollDelay);
            uri.append("&maxMessagesPerPoll=10");
        } else {
            // destination / error: apenas gravar
            uri.append("&deleteAfterWrite=false");
        }

        return uri.toString();
    }

    // ── SFTP ───────────────────────────────────────────────────────────────

    private String buildSftpUri(SftpConfig sftp, String role, Long pollDelay) {
        String path = sftp.getRemoteDir();

        StringBuilder uri = new StringBuilder("sftp://")
                .append(sftp.getUsername()).append("@")
                .append(sftp.getHost()).append(":").append(sftp.getPort())
                .append(path);

        uri.append("?password=").append(sftp.getPassword());
        uri.append("&strictHostKeyChecking=no");
        uri.append("&disconnect=true");

        if (sftp.getPrivateKeyPath() != null && !sftp.getPrivateKeyPath().isBlank()) {
            uri.append("&privateKeyFile=").append(sftp.getPrivateKeyPath());
        }

        if (pollDelay != null) {
            uri.append("&include=.*\\.txt");
            uri.append("&delete=true");
            uri.append("&delay=").append(pollDelay);
        }

        return uri.toString();
    }

    // ── AZURE BLOB ─────────────────────────────────────────────────────────

    private String buildAzureUri(FileMoverProperties.StorageConfig.AzureConfig azure,
                                 String role, Long pollDelay) {
        StringBuilder uri = new StringBuilder("azure-storage-blob://")
                .append(azure.getContainerName())
                .append("?credentials=#azureCredentials");

        if (azure.getKeyPrefix() != null && !azure.getKeyPrefix().isBlank()) {
            uri.append("&prefix=").append(azure.getKeyPrefix());
        }

        if (pollDelay != null) {
            uri.append("&delay=").append(pollDelay);
            uri.append("&deleteAfterRead=true");
            uri.append("&maxResultsPerPage=10");
        }

        return uri.toString();
    }
}