package com.filemover.infrastructure.config;

import com.filemover.infrastructure.storage.StorageType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Propriedades parametrizáveis do File Mover.
 * Todas as variáveis podem ser sobrescritas via environment variables.
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "file-mover")
public class FileMoverProperties {

    @NotBlank
    private String sourceDirectory;

    @NotBlank
    private String destinationDirectory;

    @NotBlank
    private String errorDirectory;

    @Min(1000)
    private long pollDelay = 5000;

    private boolean moveFailed = true;

    private String fileFilter = ".*\\.txt";

    @Valid
    private ValidationService validationService = new ValidationService();

    @Valid
    private StorageConfig source      = new StorageConfig();

    @Valid
    private StorageConfig destination = new StorageConfig();

    @Valid
    private StorageConfig error       = new StorageConfig();

    // ── ValidationService ──────────────────────────────────────────────────

    @Data
    public static class ValidationService {

        @NotBlank
        private String url = "http://localhost:8081";

        @NotBlank
        private String path = "/api/v1/validate";

        @Min(1000)
        private int timeoutMs = 5000;

        @Min(500)
        private int connectTimeoutMs = 2000;

        public String getFullUrl() {
            return url + path;
        }
    }

    // ── StorageConfig ──────────────────────────────────────────────────────

    @Data
    @Validated
    public static class StorageConfig {

        @NotNull
        private StorageType type = StorageType.LOCAL;

        private S3Config    s3    = new S3Config();
        private AzureConfig azure = new AzureConfig();
        private SftpConfig  sftp  = new SftpConfig();

        @Data
        public static class S3Config {
            private String bucketName;
            private String region        = "us-east-1";
            private String endpoint;
            private String accessKeyId;
            private String secretAccessKey;
            private String keyPrefix     = "";
        }

        @Data
        public static class AzureConfig {
            private String connectionString;
            private String containerName;
            private String keyPrefix = "";
        }

        @Data
        public static class SftpConfig {
            private String host;
            private int    port           = 22;
            private String username;
            private String password;
            private String privateKeyPath;
            private String remoteDir      = "/upload";
        }
    }
}