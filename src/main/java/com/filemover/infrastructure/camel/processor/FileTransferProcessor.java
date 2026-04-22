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
 * Processor Camel: extrai metadados do Exchange, monta o FileTransferRequest
 * e delega ao use case. Também instrumenta métricas e MDC para rastreabilidade.
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
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);

        String fileName   = exchange.getIn().getHeader("CamelFileName",         String.class);
        String sourcePath = exchange.getIn().getHeader("CamelFileAbsolutePath", String.class);
        // companyId pode vir no nome do arquivo (ex: COMPANY123_report.txt) ou em um header customizado
        String companyId  = extractCompanyId(fileName, exchange);

        log.info("Processing file | traceId={} fileName={} companyId={}", traceId, fileName, companyId);

        FileTransferRequest request = FileTransferRequest.builder()
                .fileName(fileName)
                .sourcePath(sourcePath)
                .companyId(companyId)
                .receivedAt(Instant.now())
                .status(FileTransferStatus.RECEIVED)
                .build();

        try {
            processingTimer.record(() -> processFileTransferUseCase.execute(request));
            successCounter.increment();
            log.info("File transfer completed | traceId={} fileName={}", traceId, fileName);
        } catch (Exception e) {
            failureCounter.increment();
            log.error("File transfer failed | traceId={} fileName={} error={}", traceId, fileName, e.getMessage());
            throw e;  // re-lança para o errorHandler do Camel tratar
        } finally {
            MDC.remove("traceId");
        }
    }

    /**
     * Extrai o companyId a partir do nome do arquivo (prefixo antes do '_')
     * ou de um header customizado "X-Company-Id".
     *
     * Exemplo: "ACME_report_2024.txt" → companyId = "ACME"
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
