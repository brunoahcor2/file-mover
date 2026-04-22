package com.filemover.infrastructure.camel.route;

import com.filemover.infrastructure.camel.processor.FileTransferProcessor;
import com.filemover.infrastructure.config.FileMoverProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Rota Apache Camel que orquestra o fluxo de movimentação de arquivos.
 *
 * Fluxo:
 *  [source dir] → FileTransferProcessor → [destination dir]
 *                       ↓ (erro)
 *                  [error dir]
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileMoverRoute extends RouteBuilder {

    private static final String ROUTE_ID      = "file-mover-route";
    private static final String DEAD_LETTER   = "file-mover-dlc";

    private final FileMoverProperties  properties;
    private final FileTransferProcessor fileTransferProcessor;

    @Override
    public void configure() {

        errorHandler(
                deadLetterChannel("direct:fileError")
                        .maximumRedeliveries(3)
                        .redeliveryDelay(2000)
                        .backOffMultiplier(2)
                        .useExponentialBackOff()
                        .retryAttemptedLogLevel(LoggingLevel.WARN)
                        .logRetryAttempted(true)
                        .logExhausted(true)
        );

        from("direct:fileError")
                .routeId(DEAD_LETTER)
                .log(LoggingLevel.ERROR,
                        "File processing failed permanently | file=${header.CamelFileName}")
                .to("file:" + properties.getErrorDirectory());

        from("file:" + properties.getSourceDirectory()
                + "?include=.*\\.txt"
                + "&readLock=changed"
                + "&readLockCheckInterval=1000"
                + "&delay=" + properties.getPollDelay()
                + "&delete=true"
                + "&recursive=false")
                .routeId(ROUTE_ID)
                .log(LoggingLevel.INFO,
                        "File detected | name=${header.CamelFileName} size=${header.CamelFileLength}")
                .process(fileTransferProcessor)
                .to("file:" + properties.getDestinationDirectory())
                .log(LoggingLevel.INFO,
                        "File moved successfully | name=${header.CamelFileName}");
    }

    /**
     * Monta a URI do endpoint File de origem com configurações parametrizáveis.
     */
    private String buildSourceUri() {
        return "file:%s?antInclude=%s&readLock=changed&readLockCheckInterval=1000&delay=%d&include=%s"
                .formatted(
                        properties.getSourceDirectory(),
                        properties.getFileFilter(),
                        properties.getPollDelay(),
                        properties.getFileFilter()
                );
    }

    private String buildFileUri(String directory, boolean autoCreate) {
        return "file:%s?autoCreate=%s".formatted(directory, autoCreate);
    }
}
