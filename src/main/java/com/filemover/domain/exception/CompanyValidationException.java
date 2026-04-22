package com.filemover.domain.exception;

public class CompanyValidationException extends RuntimeException {

    private final String companyId;
    private final String reason;

    public CompanyValidationException(String companyId, String reason) {
        super("Validation failed for company [%s]: %s".formatted(companyId, reason));
        this.companyId = companyId;
        this.reason = reason;
    }

    public String getCompanyId() { return companyId; }
    public String getReason()    { return reason; }
}
