package com.filemover.application.usecase;

import com.filemover.application.port.in.ProcessFileTransferUseCase;
import com.filemover.application.port.out.CompanyValidationGateway;
import com.filemover.application.port.out.FileStorageGateway;
import com.filemover.domain.exception.CompanyValidationException;
import com.filemover.domain.model.CompanyValidationResult;
import com.filemover.domain.model.FileTransferRequest;
import com.filemover.domain.model.FileTransferStatus;
import com.filemover.infrastructure.config.FileMoverProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;

/**
 * Caso de uso central: orquestra a validação da empresa e a movimentação do arquivo.
 * Não possui dependências de frameworks externos — apenas interfaces de domínio.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessFileTransferUseCaseImpl implements ProcessFileTransferUseCase {

    private final CompanyValidationGateway companyValidationGateway;
    private final FileStorageGateway       fileStorageGateway;
    private final FileMoverProperties      properties;

    @Override
    public void execute(FileTransferRequest request) {
        log.info("Starting file transfer process | file={} company={}",
                request.getFileName(), request.getCompanyId());

        // 1. Valida empresa
        CompanyValidationResult result = companyValidationGateway.validate(request.getCompanyId());

        if (!result.isValid()) {
            log.warn("Company validation rejected | companyId={} reason={}",
                    result.getCompanyId(), result.getReason());
            throw new CompanyValidationException(result.getCompanyId(), result.getReason());
        }

        log.info("Company validated successfully | companyId={}", result.getCompanyId());

        // 2. Monta caminho de destino
        String destinationPath = Paths.get(
                properties.getDestinationDirectory(),
                request.getFileName()
        ).toString();

        // 3. Move o arquivo
        log.info("Moving file | source={} destination={}", request.getSourcePath(), destinationPath);
        fileStorageGateway.moveFile(request.getSourcePath(), destinationPath);

        log.info("File transfer completed successfully | file={} status={}",
                request.getFileName(), FileTransferStatus.COMPLETED);
    }
}
