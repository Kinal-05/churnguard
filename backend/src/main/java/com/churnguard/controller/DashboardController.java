package com.churnguard.controller;

import com.churnguard.dto.Dtos.AtRiskCustomerResponse;
import com.churnguard.dto.Dtos.DashboardSummaryResponse;
import com.churnguard.security.ChurnGuardUserDetails;
import com.churnguard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @Operation(summary = "Aggregate summary stats — risk distribution and revenue at risk (Redis-cached)")
    public ResponseEntity<DashboardSummaryResponse> getSummary(
            @AuthenticationPrincipal ChurnGuardUserDetails user) {
        return ResponseEntity.ok(dashboardService.getSummary(user.getTenantId()));
    }

    @GetMapping("/at-risk")
    @Operation(summary = "Ranked list of at-risk customers for the CS team dashboard (Redis-cached)")
    public ResponseEntity<List<AtRiskCustomerResponse>> getAtRiskCustomers(
            @AuthenticationPrincipal ChurnGuardUserDetails user,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(
                dashboardService.getAtRiskCustomers(user.getTenantId(), limit));
    }
}