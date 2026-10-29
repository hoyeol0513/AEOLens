package com.aeolens.api.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;

import com.aeolens.api.audit.AuditResponse;
import com.aeolens.api.audit.StaticAuditAnalyzer;

@SpringBootTest
@ActiveProfiles("test")
class AnonymousAuditHistoryTests {

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private GuestSessionService guestSessionService;

    @Autowired
    private AuditHistoryService auditHistoryService;

    @Autowired
    private StaticAuditAnalyzer staticAuditAnalyzer;

    @BeforeEach
    void clearHistory() {
        databaseClient.sql("DELETE FROM audits").fetch().rowsUpdated()
                .then(databaseClient.sql("DELETE FROM guest_sessions").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM guest_users").fetch().rowsUpdated())
                .block();
    }

    @Test
    void issuesGuestCookieAndOwnsSavedAuditLifecycle() {
        MockServerWebExchange firstExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/audits"));
        GuestIdentity guest = guestSessionService.resolve(firstExchange).block();

        ResponseCookie cookie = firstExchange.getResponse().getCookies().getFirst("aeolens_guest");
        assertThat(guest).isNotNull();
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getMaxAge()).isPositive();

        MockServerWebExchange returningExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/audits")
                        .cookie(new HttpCookie(cookie.getName(), cookie.getValue())));
        GuestIdentity returningGuest = guestSessionService.resolve(returningExchange).block();
        assertThat(returningGuest).isEqualTo(guest);

        AuditResponse audit = staticAuditAnalyzer.analyze(
                URI.create("https://example.com"),
                "<html lang=\"en\"><body><main><h1>Example</h1></main></body></html>",
                false);
        auditHistoryService.save(guest.id(), audit).block();

        MockServerWebExchange otherExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/audits"));
        GuestIdentity otherGuest = guestSessionService.resolve(otherExchange).block();

        assertThat(auditHistoryService.list(guest.id()).block())
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.auditId()).isEqualTo(audit.auditId());
                    assertThat(summary.url()).isEqualTo("https://example.com");
                });
        assertThat(auditHistoryService.find(guest.id(), audit.auditId()).block())
                .isEqualTo(audit);
        assertThat(auditHistoryService.find(otherGuest.id(), audit.auditId()).block())
                .isNull();
        assertThat(auditHistoryService.delete(otherGuest.id(), audit.auditId()).block())
                .isFalse();
        assertThat(auditHistoryService.delete(guest.id(), audit.auditId()).block())
                .isTrue();
        assertThat(auditHistoryService.find(guest.id(), audit.auditId()).block())
                .isNull();
    }
}
