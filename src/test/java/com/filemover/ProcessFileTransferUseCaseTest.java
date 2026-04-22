package com.filemover;

import com.filemover.application.port.out.CompanyValidationGateway;
import com.filemover.application.port.out.FileStorageGateway;
import com.filemover.application.usecase.ProcessFileTransferUseCaseImpl;
import com.filemover.domain.exception.CompanyValidationException;
import com.filemover.domain.model.CompanyValidationResult;
import com.filemover.domain.model.FileTransferRequest;
import com.filemover.domain.model.FileTransferStatus;
import com.filemover.infrastructure.config.FileMoverProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessFileTransferUseCase")
class ProcessFileTransferUseCaseTest {

    @Mock CompanyValidationGateway companyValidationGateway;
    @Mock FileStorageGateway       fileStorageGateway;
    @Mock FileMoverProperties      properties;

    @InjectMocks
    ProcessFileTransferUseCaseImpl useCase;

    private FileTransferRequest baseRequest;

    @BeforeEach
    void setUp() {
        baseRequest = FileTransferRequest.builder()
                .fileName("ACME_report.txt")
                .sourcePath("/tmp/source/ACME_report.txt")
                .companyId("ACME")
                .receivedAt(Instant.now())
                .status(FileTransferStatus.RECEIVED)
                .build();

        FileMoverProperties.ValidationService svc = new FileMoverProperties.ValidationService();
        when(properties.getDestinationDirectory()).thenReturn("/tmp/destination");
    }

    @Test
    @DisplayName("should move file when company is valid")
    void shouldMoveFileWhenCompanyIsValid() {
        when(companyValidationGateway.validate("ACME"))
                .thenReturn(CompanyValidationResult.approved("ACME"));

        useCase.execute(baseRequest);

        verify(fileStorageGateway, times(1))
                .moveFile(eq("/tmp/source/ACME_report.txt"), anyString());
    }

    @Test
    @DisplayName("should throw CompanyValidationException when company is rejected")
    void shouldThrowWhenCompanyIsRejected() {
        when(companyValidationGateway.validate("ACME"))
                .thenReturn(CompanyValidationResult.rejected("ACME", "Company suspended"));

        assertThatThrownBy(() -> useCase.execute(baseRequest))
                .isInstanceOf(CompanyValidationException.class)
                .hasMessageContaining("ACME")
                .hasMessageContaining("Company suspended");

        verify(fileStorageGateway, never()).moveFile(anyString(), anyString());
    }

    @Test
    @DisplayName("should never call fileStorage when validation fails")
    void shouldNeverCallFileStorageWhenValidationFails() {
        when(companyValidationGateway.validate(anyString()))
                .thenReturn(CompanyValidationResult.rejected("ACME", "Blocked"));

        assertThatThrownBy(() -> useCase.execute(baseRequest))
                .isInstanceOf(CompanyValidationException.class);

        verifyNoInteractions(fileStorageGateway);
    }
}
