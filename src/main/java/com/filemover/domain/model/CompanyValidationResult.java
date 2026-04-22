package com.filemover.domain.model;

import lombok.Builder;
import lombok.Value;

/**
 * Resultado da validação de empresa retornado pelo serviço externo.
 */
@Value
@Builder
public class CompanyValidationResult {

    String companyId;
    boolean valid;
    String reason;

    public static CompanyValidationResult approved(String companyId) {
        return CompanyValidationResult.builder()
                .companyId(companyId)
                .valid(true)
                .reason("Company approved for file transfer")
                .build();
    }

    public static CompanyValidationResult rejected(String companyId, String reason) {
        return CompanyValidationResult.builder()
                .companyId(companyId)
                .valid(false)
                .reason(reason)
                .build();
    }
}
