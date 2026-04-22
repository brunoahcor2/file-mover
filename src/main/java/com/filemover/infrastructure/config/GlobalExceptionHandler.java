package com.filemover.infrastructure.config;

import com.filemover.domain.exception.CompanyValidationException;
import com.filemover.domain.exception.FileTransferException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

/**
 * Tratamento global de exceções seguindo RFC 9457 (Problem Details for HTTP APIs).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CompanyValidationException.class)
    public ProblemDetail handleCompanyValidation(CompanyValidationException ex) {
        log.warn("Company validation rejected | companyId={} reason={}", ex.getCompanyId(), ex.getReason());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setType(URI.create("https://filemover/errors/company-validation-failed"));
        pd.setTitle("Company Validation Failed");
        pd.setProperty("companyId", ex.getCompanyId());
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(FileTransferException.class)
    public ProblemDetail handleFileTransfer(FileTransferException ex) {
        log.error("File transfer error | fileName={}", ex.getFileName(), ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        pd.setType(URI.create("https://filemover/errors/file-transfer-failed"));
        pd.setTitle("File Transfer Failed");
        pd.setProperty("fileName", ex.getFileName());
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred");
        pd.setType(URI.create("https://filemover/errors/internal-error"));
        pd.setTitle("Internal Server Error");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
