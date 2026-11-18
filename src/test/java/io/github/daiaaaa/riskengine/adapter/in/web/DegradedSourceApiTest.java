package io.github.daiaaaa.riskengine.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.lessThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The customer-history source is made slower than the timeout, simulating an outage.
 * The API must still answer quickly, flag the response as degraded and never auto-approve.
 */
@SpringBootTest(properties = {
        "risk.signals.deny-list.latency=0ms",
        "risk.signals.device.latency=0ms",
        "risk.signals.history.latency=2s",
        "risk.resilience.timeout=100ms"
})
@AutoConfigureMockMvc
class DegradedSourceApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void slowSourceDoesNotBreakTheAssessment() throws Exception {
        // Known device and usual amount: with every source healthy this would be APPROVE.
        mvc.perform(post("/assessments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transactionId":"tx-1","customerId":"cust-001","deviceId":"device-abc",
                                 "amount":300.00,"type":"PIX"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("REVIEW"))
                .andExpect(jsonPath("$.degraded").value(true))
                .andExpect(jsonPath("$.signals[?(@.source == 'customer-history')].degraded").value(true))
                .andExpect(jsonPath("$.processingTimeMs").value(lessThan(1000)));
    }
}
