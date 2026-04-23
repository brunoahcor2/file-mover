package com.filemover.infrastructure.camel.processor;

import com.filemover.application.port.in.ProcessFileTransferUseCase;
import com.filemover.domain.model.FileTransferRequest;
import com.filemover.domain.model.FileTransferStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Processor Camel com timing granular por fase e suporte a múltiplos providers.
 *
 * Headers mapeados por provider:
 *   LOCAL → CamelFileName, CamelFileAbsolutePath, CamelFileLength
 *   S3    → CamelAwsS3Key, CamelAwsS3ContentLength
 *   SFTP  → CamelFileName, CamelFileAbsolutePath, CamelFileLength
 *   Azure → CamelAzureStorageBlobName, CamelAzureStorageBlobSize
 */
@Slf4j
@Component
public class FileTransferProcessor implements Processor {

    private final ProcessFileTransferUseCase processFileTransferUseCase;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Timer   processingTimer;

    public FileTransferProcessor(ProcessFileTransferUseCase processFileTransferUseCase,
                                 MeterRegistry meterRegistry) {
        this.processFileTransferUseCase = processFileTransferUseCase;

        this.successCounter = Counter.builder("file_mover_transfers_total")
                .tag("status", "success")
                .description("Total de arquivos transferidos com sucesso")
                .register(meterRegistry);

        this.failureCounter = Counter.builder("file_mover_transfers_total")
                .tag("status", "failure")
                .description("Total de arquivos com falha na transferência")
                .register(meterRegistry);

        this.processingTimer = Timer.builder("file_mover_processing_duration_seconds")
                .description("Tempo de processamento por arquivo")
                .register(meterRegistry);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        long t0        = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);

        // ── Fase 1: extração de metadados (multi-provider) ─────────────────
        long t1 = System.currentTimeMillis();

        String fileName   = resolveFileName(exchange);
        String sourcePath = resolveSourcePath(exchange);
        Long   contentLength = resolveContentLength(exchange);
        String companyId  = extractCompanyId(fileName, exchange);

        log.info("[TIMING] fase=metadata file={} companyId={} contentLength={} traceId={} elapsed={}ms",
                fileName, companyId, contentLength, traceId, System.currentTimeMillis() - t1);

        // ── Fase 2: construção do request ──────────────────────────────────
        long t2 = System.currentTimeMillis();
        FileTransferRequest request = FileTransferRequest.builder()
                .fileName(fileName)
                .sourcePath(sourcePath)
                .companyId(companyId)
                .contentLength(contentLength)
                .receivedAt(Instant.now())
                .status(FileTransferStatus.RECEIVED)
                .build();
        log.info("[TIMING] fase=build-request elapsed={}ms traceId={}",
                System.currentTimeMillis() - t2, traceId);

        log.info("Processing file | traceId={} fileName={} companyId={} bytes={}",
                traceId, fileName, companyId, contentLength);

        // ── Fase 3: use case (validação + movimentação) ────────────────────
        try {
            long t3 = System.currentTimeMillis();
            processingTimer.record(() -> processFileTransferUseCase.execute(request));
            log.info("[TIMING] fase=use-case elapsed={}ms traceId={}",
                    System.currentTimeMillis() - t3, traceId);

            successCounter.increment();
            log.info("[TIMING] fase=TOTAL file={} elapsed={}ms traceId={} status=SUCCESS",
                    fileName, System.currentTimeMillis() - t0, traceId);
            log.info("File transfer completed | traceId={} fileName={}", traceId, fileName);

        } catch (Exception e) {
            failureCounter.increment();
            log.error("[TIMING] fase=TOTAL file={} elapsed={}ms traceId={} status=FAILURE error={}",
                    fileName, System.currentTimeMillis() - t0, traceId, e.getMessage());
            log.error("File transfer failed | traceId={} fileName={} error={}",
                    traceId, fileName, e.getMessage());
            throw e;
        } finally {
            MDC.remove("traceId");
        }
    }

    // ── Resolvers de header por provider ───────────────────────────────────

    /**
     * Resolve o nome do arquivo independente do provider.
     *
     * LOCAL/SFTP → CamelFileName         ex: "ACME_relatorio.txt"
     * S3         → CamelAwsS3Key         ex: "incoming/ACME_relatorio.txt" → extrai só o nome
     * Azure      → CamelAzureStorageBlobName  ex: "incoming/ACME_relatorio.txt" → extrai só o nome
     */
    private String resolveFileName(Exchange exchange) {
        // LOCAL e SFTP
        String fileName = exchange.getIn().getHeader("CamelFileName", String.class);
        if (fileName != null && !fileName.isBlank()) {
            return fileName;
        }

        // S3
        String s3Key = exchange.getIn().getHeader("CamelAwsS3Key", String.class);
        if (s3Key != null && !s3Key.isBlank()) {
            return s3Key.contains("/")
                    ? s3Key.substring(s3Key.lastIndexOf('/') + 1)
                    : s3Key;
        }

        // Azure Blob
        String blobName = exchange.getIn().getHeader("CamelAzureStorageBlobName", String.class);
        if (blobName != null && !blobName.isBlank()) {
            return blobName.contains("/")
                    ? blobName.substring(blobName.lastIndexOf('/') + 1)
                    : blobName;
        }

        return null;
    }

    /**
     * Resolve o caminho de origem (path lógico) independente do provider.
     *
     * LOCAL/SFTP → CamelFileAbsolutePath
     * S3         → CamelAwsS3Key  (key completa, ex: "incoming/ACME_relatorio.txt")
     * Azure      → CamelAzureStorageBlobName
     */
    private String resolveSourcePath(Exchange exchange) {
        // LOCAL e SFTP
        String absolutePath = exchange.getIn().getHeader("CamelFileAbsolutePath", String.class);
        if (absolutePath != null && !absolutePath.isBlank()) {
            return absolutePath;
        }

        // S3
        String s3Key = exchange.getIn().getHeader("CamelAwsS3Key", String.class);
        if (s3Key != null && !s3Key.isBlank()) {
            return s3Key;
        }

        // Azure Blob
        String blobName = exchange.getIn().getHeader("CamelAzureStorageBlobName", String.class);
        if (blobName != null && !blobName.isBlank()) {
            return blobName;
        }

        return null;
    }

    /**
     * Resolve o tamanho do conteúdo independente do provider.
     *
     * LOCAL/SFTP → CamelFileLength
     * S3         → CamelAwsS3ContentLength
     * Azure      → CamelAzureStorageBlobSize
     */
    private Long resolveContentLength(Exchange exchange) {
        // LOCAL e SFTP
        Long fileLength = exchange.getIn().getHeader("CamelFileLength", Long.class);
        if (fileLength != null) {
            return fileLength;
        }

        // S3
        Long s3Length = exchange.getIn().getHeader("CamelAwsS3ContentLength", Long.class);
        if (s3Length != null) {
            return s3Length;
        }

        // Azure Blob
        Long blobSize = exchange.getIn().getHeader("CamelAzureStorageBlobSize", Long.class);
        if (blobSize != null) {
            return blobSize;
        }

        return null;
    }

    /**
     * Extrai companyId do prefixo do nome do arquivo ("ACME_report.txt" → "ACME")
     * ou do header customizado "X-Company-Id".
     */
    private String extractCompanyId(String fileName, Exchange exchange) {
        String headerCompanyId = exchange.getIn().getHeader("X-Company-Id", String.class);
        if (headerCompanyId != null && !headerCompanyId.isBlank()) {
            return headerCompanyId;
        }
        if (fileName != null && fileName.contains("_")) {
            return fileName.split("_")[0];
        }
        return "UNKNOWN";
    }
}