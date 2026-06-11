package com.bank.bian.frauddetection;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Boot + API smoke: ingest → alert → investigate, through HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationTests {

    static final String CR = "/v1/fraud-alert-monitoring-state";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    String url(String path) { return "http://localhost:" + port + path; }

    @Test
    void largeTransactionRaisesAlertWhichCanBeDismissed() {
        var eval = rest.postForEntity(url(CR + "/evaluate"),
                Map.of("accountRef", "CA-API-1", "sourceType", "TRANSACTION",
                        "sourceRef", "TX-9", "amountMinor", 5_000_000, "currency", "INR"),
                Map.class);
        assertThat(eval.getStatusCode().value()).isEqualTo(200);
        assertThat((Boolean) eval.getBody().get("alertRaised")).isTrue();
        String alertId = (String) eval.getBody().get("alertId");

        var resolved = rest.exchange(url(CR + "/" + alertId + "/control"),
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(Map.of("action", "dismiss", "notes", "customer confirmed")),
                Map.class);
        assertThat(resolved.getBody().get("status")).isEqualTo("FALSE_POSITIVE");
    }

    @Test
    void cleanTransactionDoesNotAlert() {
        var eval = rest.postForEntity(url(CR + "/evaluate"),
                Map.of("accountRef", "CA-API-2", "sourceType", "CHEQUE",
                        "sourceRef", "CHQ-1", "amountMinor", 1_234, "currency", "INR"),
                Map.class);
        assertThat((Boolean) eval.getBody().get("alertRaised")).isFalse();
    }
}
