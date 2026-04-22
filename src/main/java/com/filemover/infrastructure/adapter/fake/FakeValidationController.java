package com.filemover.infrastructure.adapter.fake;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * Controlador REST que simula o serviço externo de validação de empresas.
 * Ativado apenas quando a propriedade "file-mover.fake-server.enabled=true".
 *
 * Empresas bloqueadas: BLOCKED, SUSPENDED, UNKNOWN
 * Todas as demais são aprovadas automaticamente.
 *
 * Endpoint: GET /api/v1/validate/{companyId}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/validate")
@ConditionalOnProperty(name = "file-mover.fake-server.enabled", havingValue = "true", matchIfMissing = true)
public class FakeValidationController {

    private static final Set<String> BLOCKED_COMPANIES = Set.of("BLOCKED", "SUSPENDED", "UNKNOWN");

    @GetMapping("/{companyId}")
    public ResponseEntity<ValidationResponse> validate(@PathVariable String companyId) {
        log.info("[FAKE SERVICE] Validating company | companyId={}", companyId);

        boolean isBlocked = BLOCKED_COMPANIES.contains(companyId.toUpperCase());

        ValidationResponse response = isBlocked
                ? new ValidationResponse(companyId, false, "Company is not authorized for file transfer")
                : new ValidationResponse(companyId, true, "Company approved");

        log.info("[FAKE SERVICE] Validation result | companyId={} valid={}", companyId, response.valid());
        return ResponseEntity.ok(response);
    }

    record ValidationResponse(String companyId, boolean valid, String reason) {}
}
