package io.github.daiaaaa.riskengine.adapter.in.web;

import io.github.daiaaaa.riskengine.application.RiskAssessmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/assessments")
public class RiskAssessmentController {

    private final RiskAssessmentService service;

    public RiskAssessmentController(RiskAssessmentService service) {
        this.service = service;
    }

    @PostMapping
    public AssessmentResponse assess(@Valid @RequestBody AssessmentRequest request) {
        return AssessmentResponse.from(service.assess(request.toDomain()));
    }
}
