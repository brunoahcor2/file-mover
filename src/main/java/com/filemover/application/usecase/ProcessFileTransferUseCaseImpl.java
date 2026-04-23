package com.filemover.application.usecase;

import com.filemover.application.port.in.ProcessFileTransferUseCase;
import com.filemover.application.port.out.CompanyValidationGateway;
import com.filemover.application.port.out.StorageGateway;
import com.filemover.domain.exception.CompanyValidationException;
import com.filemover.domain.exception.FileTransferException;
import com.filemover.domain.model.FileTransferRequest;
import com.filemover.domain.model.FileTransferStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Slf4j
@Service
public class ProcessFileTransferUseCaseImpl implements ProcessFileTransferUseCase {

    private final CompanyValidationGateway validationGateway;
    private final StorageGateway           sourceStorage;
    private final StorageGateway           destinationStorage;

    public ProcessFileTransferUseCaseImpl(
            CompanyValidationGateway validationGateway,
            @Qualifier("sourceStorage")      StorageGateway sourceStorage,
            @Qualifier("destinationStorage") StorageGateway destinationStorage) {

        this.validationGateway  = validationGateway;
        this.sourceStorage      = sourceStorage;
        this.destinationStorage = destinationStorage;
    }

    @Override
    public void execute(FileTransferRequest request) {
        log.info("[USE-CASE] início | file={} company={} source={} dest={}",
                request.getFileName(),
                request.getCompanyId(),
                sourceStorage.providerName(),
                destinationStorage.providerName());

        // ── 1. Validação da empresa ────────────────────────────────────────
        var validation = validationGateway.validate(request.getCompanyId());

        if (!validation.isValid()) {
            log.warn("[USE-CASE] empresa rejeitada | company={} reason={}",
                    request.getCompanyId(), validation.getReason());
            throw new CompanyValidationException(
                    request.getCompanyId(), validation.getReason());
        }

        // ── 2. Leitura do source ───────────────────────────────────────────
        log.info("[USE-CASE] lendo do source | provider={} path={}",
                sourceStorage.providerName(), request.getSourcePath());

        try (InputStream content = sourceStorage.read(request.getSourcePath())) {

            long contentLength = estimateLength(request);

            // ── 3. Gravação no destino ─────────────────────────────────────
            log.info("[USE-CASE] gravando no destino | provider={} file={}",
                    destinationStorage.providerName(), request.getFileName());

            destinationStorage.write(
                    request.getSourcePath(),
                    content,
                    contentLength,
                    request.getFileName());

        } catch (CompanyValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[USE-CASE] falha na transferência | file={} error={}",
                    request.getFileName(), e.getMessage());
            throw new FileTransferException(request.getFileName(), e.getMessage(), e);
        }

        // ── 4. Remoção da origem (somente se destino gravou com sucesso) ───
        try {
            sourceStorage.delete(request.getSourcePath());
            log.info("[USE-CASE] arquivo removido da origem | path={}",
                    request.getSourcePath());
        } catch (Exception e) {
            // Não falha o fluxo — arquivo já está no destino.
            // O Camel com delete=true também tentará remover.
            log.warn("[USE-CASE] falha ao remover da origem (não crítico) | path={} error={}",
                    request.getSourcePath(), e.getMessage());
        }

        log.info("[USE-CASE] concluído | file={} status={}",
                request.getFileName(), FileTransferStatus.TRANSFERRED);
    }

    /**
     * Para storage LOCAL o Camel fornece CamelFileLength no Exchange.
     * Para outros provedores o tamanho pode não estar disponível no request —
     * nesse caso usamos -1 e os SDKs que suportam streaming sem tamanho
     * (S3 com TransferManager, Azure com upload sem length) tratam isso.
     *
     * Se precisar de tamanho exato, adicione contentLength ao FileTransferRequest
     * e preencha-o no FileTransferProcessor via header CamelFileLength.
     */
    private long estimateLength(FileTransferRequest request) {
        return request.getContentLength() != null ? request.getContentLength() : -1L;
    }
}