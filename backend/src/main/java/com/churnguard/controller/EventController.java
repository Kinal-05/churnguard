package com.churnguard.controller;

import com.churnguard.dto.Dtos.EventResponse;
import com.churnguard.dto.Dtos.IngestEventRequest;
import com.churnguard.security.ChurnGuardUserDetails;
import com.churnguard.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Events")
@SecurityRequirement(name = "bearerAuth")
public class EventController {

    private final EventService eventService;

    @PostMapping("/events")
    @Operation(summary = "Ingest a customer event — also publishes async to Kafka for ML scoring")
    public ResponseEntity<EventResponse> ingestEvent(
            @AuthenticationPrincipal ChurnGuardUserDetails user,
            @Valid @RequestBody IngestEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventService.ingestEvent(user.getTenantId(), request));
    }

    @GetMapping("/customers/{customerId}/events")
    @Operation(summary = "List recent events for a customer")
    public ResponseEntity<List<EventResponse>> getCustomerEvents(
            @AuthenticationPrincipal ChurnGuardUserDetails user,
            @PathVariable UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(
                eventService.getCustomerEvents(user.getTenantId(), customerId, page, size));
    }
}