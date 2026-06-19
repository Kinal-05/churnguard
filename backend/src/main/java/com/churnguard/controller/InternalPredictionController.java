package com.churnguard.controller;

import com.churnguard.dto.Dtos.ChurnPredictionResponse;
import com.churnguard.dto.Dtos.PredictionCallbackRequest;
import com.churnguard.exception.UnauthorizedException;
import com.churnguard.security.ChurnGuardUserDetails;
import com.churnguard.service.PredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Predictions")
public class InternalPredictionController {

    private final PredictionService predictionService;

    @Value("${churnguard.internal.service-token}")
    private String internalServiceToken;

    /**
     * Called by the ML service after scoring a customer.
     * Protected by a shared secret token (X-Internal-Token header)
     * rather than JWT — service-to-service auth pattern.
     */
    @PostMapping("/internal/predictions")
    @Operation(summary = "[ML Service] Callback to store a completed churn prediction")
    public ResponseEntity<Void> receivePrediction(
            @RequestHeader("X-Internal-Token") String token,
            @Valid @RequestBody PredictionCallbackRequest request) {

        if (!internalServiceToken.equals(token)) {
            throw new UnauthorizedException("Invalid internal service token");
        }

        predictionService.savePrediction(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/v1/customers/{customerId}/predictions")
    @Operation(summary = "Get full prediction history for a customer")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<ChurnPredictionResponse>> getPredictionHistory(
            @AuthenticationPrincipal ChurnGuardUserDetails user,
            @PathVariable UUID customerId) {
        return ResponseEntity.ok(predictionService.getPredictionHistory(customerId));
    }
}