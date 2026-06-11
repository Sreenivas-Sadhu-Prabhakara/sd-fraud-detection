package com.bank.bian.frauddetection;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test: the context boots and the BIAN semantic API answers.
 * Phase 2 adds real domain tests per service domain.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationTests {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Test
    void serviceDomainMetadataIsServed() {
        @SuppressWarnings("unchecked")
        Map<String, String> meta = rest.getForObject("http://localhost:" + port + "/v1/service-domain", Map.class);
        assertThat(meta).containsEntry("serviceDomain", "Fraud Detection");
        assertThat(meta).containsEntry("functionalPattern", "Monitor");
    }

    @Test
    void controlRecordLifecycleWorks() {
        var created = rest.postForEntity(
                "http://localhost:" + port + "/v1/fraud-alert-monitoring-state/initiate",
                Map.of("note", "smoke"), Map.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getBody()).containsKey("controlRecordId");
    }
}
