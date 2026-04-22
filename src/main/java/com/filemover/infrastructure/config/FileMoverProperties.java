package com.filemover.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
}
