package com.filemover;

import com.filemover.application.port.out.CompanyValidationGateway;
import com.filemover.domain.model.CompanyValidationResult;
import org.apache.camel.CamelContext;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@CamelSpringBootTest
@SpringBootTest
@TestPropertySource(properties = {
    "file-mover.poll-delay=500",
    "camel.springboot.main-run-controller=false"
})
@DisplayName("FileMoverRoute - Integration")
class FileMoverRouteIntegrationTest {

    @TempDir Path sourceDir;
    @TempDir Path destinationDir;
    @TempDir Path errorDir;

    @Autowired CamelContext camelContext;

    @MockBean CompanyValidationGateway companyValidationGateway;

    @Test
    @DisplayName("should move file to destination when company is approved")
    void shouldMoveFileToDestinationWhenApproved() throws IOException, InterruptedException {
        when(companyValidationGateway.validate(anyString()))
                .thenReturn(CompanyValidationResult.approved("ACME"));

        Path file = sourceDir.resolve("ACME_report.txt");
        Files.writeString(file, "test content");

        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> Files.exists(destinationDir.resolve("ACME_report.txt")));
    }
}
