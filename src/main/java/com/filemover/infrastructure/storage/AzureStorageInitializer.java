package com.filemover.infrastructure.storage;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.filemover.infrastructure.config.FileMoverProperties;
import com.filemover.infrastructure.config.FileMoverProperties.StorageConfig;
import com.filemover.infrastructure.config.FileMoverProperties.StorageConfig.AzureConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Garante que os containers Azure Blob existam antes do Camel iniciar.
 *
 * Executado automaticamente no startup via @PostConstruct.
 * Só age nos roles configurados como AZURE_BLOB — ignora LOCAL, S3 e SFTP.
 * Usa createBlobContainerIfNotExists — idempotente, seguro rodar múltiplas vezes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AzureStorageInitializer {

    private final FileMoverProperties properties;

    // Leitura direta das env vars — bypass do binding YAML para evitar
    // corrupção de caracteres especiais (=, +) na connection string
    @Value("${DEST_AZURE_CONN_STR:}")
    private String destAzureConnStrDirect;

    @Value("${SOURCE_AZURE_CONN_STR:}")
    private String sourceAzureConnStrDirect;

    @Value("${ERROR_AZURE_CONN_STR:}")
    private String errorAzureConnStrDirect;

    @PostConstruct
    public void init() {
        log.info("[AZURE-INIT] Verificando containers Azure...");
        ensureContainer("source",      properties.getSource(),      sourceAzureConnStrDirect);
        ensureContainer("destination", properties.getDestination(), destAzureConnStrDirect);
        ensureContainer("error",       properties.getError(),       errorAzureConnStrDirect);
        log.info("[AZURE-INIT] Verificacao concluida.");
    }

    private void ensureContainer(String role, StorageConfig config, String connStrDirect) {
        if (config.getType() != StorageType.AZURE_BLOB) {
            log.debug("[AZURE-INIT] role={} type={} — ignorado.", role, config.getType());
            return;
        }

        AzureConfig azure = config.getAzure();

        // Usa a env var direta como primeira opção — evita corrupção do binding YAML
        // Fallback para o valor do properties caso a env var não esteja definida
        String connStr = (connStrDirect != null && !connStrDirect.isBlank())
                ? connStrDirect
                : azure.getConnectionString();

        // ── Diagnóstico ───────────────────────────────────────────────────
        log.info("[AZURE-INIT] role={} connStr='{}'", role, connStr);
        if (connStr != null) {
            log.info("[AZURE-INIT] role={} connStr.length={}", role, connStr.length());
            for (String part : connStr.split(";")) {
                if (part.startsWith("AccountKey=")) {
                    String key = part.substring("AccountKey=".length());
                    log.info("[AZURE-INIT] role={} AccountKey='{}' length={}", role, key, key.length());
                }
            }
        }
        // ── Fim diagnóstico ───────────────────────────────────────────────

        if (connStr == null || connStr.isBlank()) {
            log.warn("[AZURE-INIT] role={} — connection string não configurada, pulando.", role);
            return;
        }

        if (azure.getContainerName() == null || azure.getContainerName().isBlank()) {
            log.warn("[AZURE-INIT] role={} — container name não configurado, pulando.", role);
            return;
        }

        try {
            BlobServiceClient client = new BlobServiceClientBuilder()
                    .connectionString(connStr)
                    .buildClient();

            var containerClient = client.createBlobContainerIfNotExists(azure.getContainerName());

            if (containerClient != null) {
                log.info("[AZURE-INIT] role={} container={} criado.",
                        role, azure.getContainerName());
            } else {
                log.info("[AZURE-INIT] role={} container={} ja existe.",
                        role, azure.getContainerName());
            }

        } catch (Exception e) {
            log.error("[AZURE-INIT] role={} container={} ERRO={} — verifique a connection string.",
                    role, azure.getContainerName(), e.getMessage());
            throw new IllegalStateException(
                    "[AZURE-INIT] Falha ao garantir container Azure para role=" + role, e);
        }
    }
}