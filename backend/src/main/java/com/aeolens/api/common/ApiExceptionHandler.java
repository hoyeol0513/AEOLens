package com.aeolens.api.common;

import java.net.URI;
import java.util.concurrent.TimeoutException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.aeolens.api.audit.InvalidAuditTargetException;
import com.aeolens.api.audit.TargetFetchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidAuditTargetException.class)
    ProblemDetail handleInvalidTarget(InvalidAuditTargetException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid audit target", exception.getMessage());
    }

    @ExceptionHandler(WebExchangeBindException.class)
    ProblemDetail handleValidation(WebExchangeBindException exception) {
        String detail = exception.getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "Invalid request" : error.getDefaultMessage())
                .orElse("Invalid request");
        return problem(HttpStatus.BAD_REQUEST, "Request validation failed", detail);
    }

    @ExceptionHandler(TargetFetchException.class)
    ProblemDetail handleUnusableTarget(TargetFetchException exception) {
        return problem(HttpStatus.BAD_GATEWAY, "Target fetch failed", exception.getMessage());
    }

    @ExceptionHandler(WebClientResponseException.class)
    ProblemDetail handleRemoteStatus(WebClientResponseException exception) {
        return problem(HttpStatus.BAD_GATEWAY, "Target fetch failed",
                "The target returned HTTP " + exception.getStatusCode().value());
    }

    @ExceptionHandler(WebClientRequestException.class)
    ProblemDetail handleUnreachableTarget(WebClientRequestException exception) {
        if (hasCause(exception, io.netty.handler.timeout.TimeoutException.class)) {
            return timeoutProblem();
        }
        return problem(HttpStatus.BAD_GATEWAY, "Target unreachable",
                "The target could not be reached. Check that the site is online and publicly accessible.");
    }

    @ExceptionHandler(TimeoutException.class)
    ProblemDetail handleTimeout(TimeoutException exception) {
        return timeoutProblem();
    }

    private ProblemDetail timeoutProblem() {
        return problem(HttpStatus.GATEWAY_TIMEOUT, "Target fetch timed out",
                "The target did not respond within the configured crawl timeout");
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://aeolens.dev/problems/" + status.value()));
        return problem;
    }
}
