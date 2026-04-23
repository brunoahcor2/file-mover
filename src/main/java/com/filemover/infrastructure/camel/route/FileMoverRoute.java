package com.filemover.infrastructure.camel.route;

import com.filemover.infrastructure.camel.processor.FileTransferProcessor;
import com.filemover.infrastructure.storage.StorageRouteUriBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Rota Apache Camel que orquestra o fluxo de movimentação de arquivos.
 *
 * As URIs de source, destination e error são montadas dinamicamente
 * pelo StorageRouteUriBuilder baseado no tipo de provider configurado.
 *
 * Fluxo:
 *   source (LOCAL/S3/SFTP/Azure) → FileTransferProcessor → destination
 *                                          ↓ (erro após retries)
 *                                        error
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileMoverRoute extends RouteBuilder {

    private static final String ROUTE_ID    = "file-mover-route";
    private static final String DEAD_LETTER = "file-mover-dlc";

    private final StorageRouteUriBuilder uriBuilder;
    private final FileTransferProcessor  fileTransferProcessor;

    @Override
    public void configure() {

        errorHandler(
                deadLetterChannel("direct:fileError")
                        .maximumRedeliveries(3)
                        .redeliveryDelay(2_000)
                        .backOffMultiplier(2)
                        .useExponentialBackOff()
                        .retryAttemptedLogLevel(LoggingLevel.WARN)
                        .logRetryAttempted(true)
                        .logExhausted(true)
        );

        // ── Dead Letter Channel → error storage ────────────────────────────
        from("direct:fileError")
                .routeId(DEAD_LETTER)
                .log(LoggingLevel.ERROR,
                        "File processing failed permanently | file=${header.CamelFileName}")
                .to(uriBuilder.buildErrorUri());

        // ── Rota principal: source → processor → destination ───────────────
        from(uriBuilder.buildSourceUri())
                .routeId(ROUTE_ID)
                .log(LoggingLevel.INFO,
                        "File detected | name=${header.CamelFileName} size=${header.CamelFileLength}")
                .process(fileTransferProcessor)
                .to(uriBuilder.buildDestinationUri())
                .log(LoggingLevel.INFO,
                        "File moved successfully | name=${header.CamelFileName}");
    }
}