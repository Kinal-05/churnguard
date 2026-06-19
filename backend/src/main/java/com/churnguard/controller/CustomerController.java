package com.churnguard.controller;

import com.churnguard.dto.Dtos.*;
import com.churnguard.security.ChurnGuardUserDetails;
import com.churnguard.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customers")
@SecurityRequirement(name = "bearerAuth")
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    @Operation(summary = "Create or update a customer (upsert by externalRef)")
    public ResponseEntity<CustomerResponse> upsertCustomer(
            @AuthenticationPrincipal ChurnGuardUserDetails user,
            @Valid @RequestBody UpsertCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(customerService.upsertCustomer(user.getTenantId(), request));
    }

    @GetMapping
    @Operation(summary = "List all customers for the tenant (paginated)")
    public ResponseEntity<PagedCustomerResponse> listCustomers(
            @AuthenticationPrincipal ChurnGuardUserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                customerService.listCustomers(user.getTenantId(), page, size));
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "Get a single customer with their latest churn prediction")
    public ResponseEntity<CustomerResponse> getCustomer(
            @AuthenticationPrincipal ChurnGuardUserDetails user,
            @PathVariable UUID customerId) {
        return ResponseEntity.ok(
                customerService.getCustomer(user.getTenantId(), customerId));
    }
}