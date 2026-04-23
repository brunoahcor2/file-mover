package com.filemover.domain.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Entidade de domínio que representa uma requisição de transferência de arquivo.
 * Imutável por design — use o builder para construção.
 */
@Value
@Builder
public class FileTransferRequest {

    String           fileName;
    String           sourcePath;
    String           companyId;
    Instant          receivedAt;
    FileTransferStatus status;
    Long             contentLength;   // null quando o provider não fornece o tamanho

    public FileTransferRequest withStatus(FileTransferStatus newStatus) {
        return FileTransferRequest.builder()
                .fileName(this.fileName)
                .sourcePath(this.sourcePath)
                .companyId(this.companyId)
                .receivedAt(this.receivedAt)
                .status(newStatus)
                .contentLength(this.contentLength)
                .build();
    }
}