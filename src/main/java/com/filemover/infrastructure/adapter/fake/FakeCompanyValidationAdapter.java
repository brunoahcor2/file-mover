package com.filemover.infrastructure.adapter.fake;

import com.filemover.application.port.out.CompanyValidationGateway;
import com.filemover.domain.model.CompanyValidationResult;
import com.filemover.infrastructure.config.FileMoverProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * Adapter que chama o serviço fake de validação de empresa via HTTP.
 * Implementa a porta de saída CompanyValidationGateway.
 *
 * Em caso de falha de comunicação, retorna rejeição com motivo técnico
 * para que o Camel possa acionar o Dead Letter Channel.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FakeCompanyValidationAdapter implements CompanyValidationGateway {

    private final FileMoverProperties properties;
    private final RestClient.Builder  restClientBuilder;

    @Override
    public CompanyValidationResult validate(String companyId) {
        log.debug("Calling validation service | companyId={} url={}",
                companyId, properties.getValidationService().getFullUrl());

        try {
            ValidationResponse response = buildRestClient()
                    .get()
                    .uri("/{companyId}", companyId)
                    .retrieve()
                    .body(ValidationResponse.class);

            if (response == null) {
                log.warn("Validation service returned null response | companyId={}", companyId);
                return CompanyValidationResult.rejected(companyId, "Empty response from validation service");
            }

            log.info("Validation service response | companyId={} valid={} reason={}",
                    companyId, response.valid(), response.reason());

            return response.valid()
                    ? CompanyValidationResult.approved(companyId)
                    : CompanyValidationResult.rejected(companyId, response.reason());

        } catch (RestClientException e) {
            log.error("Validation service call failed | companyId={} error={}", companyId, e.getMessage());
            return CompanyValidationResult.rejected(companyId,
                    "Validation service unavailable: " + e.getMessage());
        }
    }

    private RestClient buildRestClient() {
        FileMoverProperties.ValidationService svc = properties.getValidationService();
        return restClientBuilder
                .baseUrl(svc.getUrl() + svc.getPath())
                .build();
    }

    /**
     * DTO de resposta do serviço de validação.
     */
    record ValidationResponse(String companyId, boolean valid, String reason) {}
}
