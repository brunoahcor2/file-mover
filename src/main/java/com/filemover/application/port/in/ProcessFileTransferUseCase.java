package com.filemover.application.port.in;

import com.filemover.domain.model.FileTransferRequest;

/**
 * Porta de entrada (Driving Port) — define o contrato do caso de uso
 * de transferência de arquivo. Implementada pelos Use Cases.
 */
public interface ProcessFileTransferUseCase {

    /**
     * Executa o fluxo completo: valida empresa → move arquivo.
     *
     * @param request dados do arquivo a ser processado
     */
    void execute(FileTransferRequest request);
}
