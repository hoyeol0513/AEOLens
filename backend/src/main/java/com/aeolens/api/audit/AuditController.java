package com.aeolens.api.audit;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import com.aeolens.api.history.AuditHistoryService;
import com.aeolens.api.history.AuditSummary;
import com.aeolens.api.history.GuestSessionService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/audits")
public class AuditController {

    private final AuditService auditService;
    private final AuditHistoryService auditHistoryService;
    private final GuestSessionService guestSessionService;

    public AuditController(
            AuditService auditService,
            AuditHistoryService auditHistoryService,
            GuestSessionService guestSessionService
    ) {
        this.auditService = auditService;
        this.auditHistoryService = auditHistoryService;
        this.guestSessionService = guestSessionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AuditResponse> analyze(
            @Valid @RequestBody AuditRequest request,
            ServerWebExchange exchange
    ) {
        return guestSessionService.resolve(exchange)
                .flatMap(guest -> auditService.analyze(request.url())
                        .flatMap(response -> auditHistoryService.save(guest.id(), response)));
    }

    @GetMapping
    public Mono<List<AuditSummary>> list(ServerWebExchange exchange) {
        return guestSessionService.resolve(exchange)
                .flatMap(guest -> auditHistoryService.list(guest.id()));
    }

    @GetMapping("/{auditId}")
    public Mono<AuditResponse> find(
            @PathVariable UUID auditId,
            ServerWebExchange exchange
    ) {
        return guestSessionService.resolve(exchange)
                .flatMap(guest -> auditHistoryService.find(guest.id(), auditId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Audit history entry not found")));
    }

    @DeleteMapping("/{auditId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(
            @PathVariable UUID auditId,
            ServerWebExchange exchange
    ) {
        return guestSessionService.resolve(exchange)
                .flatMap(guest -> auditHistoryService.delete(guest.id(), auditId))
                .flatMap(deleted -> deleted
                        ? Mono.empty()
                        : Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Audit history entry not found")));
    }
}
