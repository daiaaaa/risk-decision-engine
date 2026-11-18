package io.github.daiaaaa.riskengine.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end through HTTP, with the real providers and zero simulated latency.
 */
@SpringBootTest(properties = {
        "risk.signals.deny-list.latency=0ms",
        "risk.signals.device.latency=0ms",
        "risk.signals.history.latency=0ms"
})
@AutoConfigureMockMvc
class RiskAssessmentApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void knownDeviceAndUsualAmountIsApproved() throws Exception {
        assess("cust-001", "device-abc", "300.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVE"))
                .andExpect(jsonPath("$.score").value(8))
                .andExpect(jsonPath("$.signals", hasSize(3)));
    }

    @Test
    void unknownDeviceGoesToReview() throws Exception {
        assess("cust-001", "device-new", "300.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("REVIEW"))
                .andExpect(jsonPath("$.score").value(40));
    }

    @Test
    void unknownDeviceAndUnusualAmountIsDenied() throws Exception {
        assess("cust-001", "device-new", "3000.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.score").value(78));
    }

    @Test
    void denyListedCustomerIsDeniedRegardlessOfScore() throws Exception {
        assess("cust-blocked", "device-abc", "10.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.signals[?(@.source == 'deny-list')].blocking").value(true));
    }

    @Test
    void invalidRequestReturnsProblemDetail() throws Exception {
        mvc.perform(post("/assessments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transactionId":"","customerId":"cust-001",
                                 "deviceId":"device-abc","amount":-5,"type":"PIX"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private ResultActions assess(String customerId, String deviceId, String amount) throws Exception {
        return mvc.perform(post("/assessments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"transactionId":"tx-1","customerId":"%s","deviceId":"%s",
                         "amount":%s,"type":"PIX"}
                        """.formatted(customerId, deviceId, amount)));
    }
}
