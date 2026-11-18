package io.github.daiaaaa.riskengine.adapter.in.web;

import io.github.daiaaaa.riskengine.domain.model.Decision;
import io.github.daiaaaa.riskengine.domain.model.RiskAssessment;
import io.github.daiaaaa.riskengine.domain.model.RiskSignal;

import java.time.Instant;
import java.util.List;

/**
 * Output contract of the API. Kept separate from the domain so the domain can change
 * without breaking API clients.
 */
public record AssessmentResponse(
        String assessmentId,
        String transactionId,
        Decision decision,
        int score,
        List<SignalResponse> signals,
        Instant assessedAt,
        long processingTimeMs) {

    public record SignalResponse(String source, int score, int weight, boolean blocking, String reason) {

        static SignalResponse from(RiskSignal signal) {
            return new SignalResponse(signal.source(), signal.score(), signal.weight(),
                    signal.blocking(), signal.reason());
        }
    }

    static AssessmentResponse from(RiskAssessment assessment) {
        return new AssessmentResponse(
                assessment.assessmentId(),
                assessment.transactionId(),
                assessment.decision(),
                assessment.score(),
                assessment.signals().stream().map(SignalResponse::from).toList(),
                assessment.assessedAt(),
                assessment.processingTime().toMillis());
    }
}
