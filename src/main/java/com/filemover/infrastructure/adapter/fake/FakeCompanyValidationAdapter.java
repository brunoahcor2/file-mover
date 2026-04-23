package com.filemover.infrastructure.adapter.fake;

import com.filemover.application.port.out.CompanyValidationGateway;
import com.filemover.domain.model.CompanyValidationResult;
import com.filemover.infrastructure.config.FileMoverProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adapter que chama o serviço fake de validação via HTTP.
 *
 * O RestClient.Builder é injetado com timeouts pré-configurados
 * em RestClientConfig — sem isso, uma lentidão no servidor fake
 * pode travar o processamento indefinidamente.
 *
 * Logs [TIMING] emitem o tempo de cada chamada HTTP para diagnóstico.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FakeCompanyValidationAdapter implements CompanyValidationGateway {

    private final FileMoverProperties  properties;
    private final RestClient.Builder   restClientBuilder;

    @Override
    public CompanyValidationResult validate(String companyId) {
        long t0  = System.currentTimeMillis();
        String url = properties.getValidationService().getUrl()
                + properties.getValidationService().getPath();

        log.info("[TIMING] fase=validation-start companyId={} url={}", companyId, url);

        try {
            ValidationResponse response = buildRestClient()
                    .get()
                    .uri("/{companyId}", companyId)
                    .retrieve()
                    .body(ValidationResponse.class);

            long elapsed = System.currentTimeMillis() - t0;

            if (response == null) {
                log.warn("[TIMING] fase=validation-end companyId={} elapsed={}ms resultado=NULL_RESPONSE",
                        companyId, elapsed);
                return CompanyValidationResult.rejected(companyId, "Empty response from validation service");
            }

            log.info("[TIMING] fase=validation-end companyId={} valid={} elapsed={}ms",
                    companyId, response.valid(), elapsed);

            return response.valid()
                    ? CompanyValidationResult.approved(companyId)
                    : CompanyValidationResult.rejected(companyId, response.reason());

        } catch (RestClientException e) {
            long elapsed = System.currentTimeMillis() - t0;
            log.error("[TIMING] fase=validation-error companyId={} elapsed={}ms erro={}",
                    companyId, elapsed, e.getMessage());

            // Retorna rejeição em vez de propagar — o DLC decide o destino
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

    record ValidationResponse(String companyId, boolean valid, String reason) {}
}