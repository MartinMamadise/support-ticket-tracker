package za.co.mamadise.support_ticket_tracker.error;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import za.co.mamadise.support_ticket_tracker.auth.AuthenticationFailedException;
import za.co.mamadise.support_ticket_tracker.ticket.InvalidTicketQueryException;
import za.co.mamadise.support_ticket_tracker.ticket.TicketNotFoundException;
import za.co.mamadise.support_ticket_tracker.ticket.TicketWorkflowException;
import za.co.mamadise.support_ticket_tracker.user.DuplicateUserException;
import za.co.mamadise.support_ticket_tracker.user.InvalidUserException;
import za.co.mamadise.support_ticket_tracker.user.RoleChangeNotAllowedException;
import za.co.mamadise.support_ticket_tracker.user.UserNotFoundException;

/** Converts exceptions into consistent JSON error responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TicketNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(TicketNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), Map.of());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleGenericNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), Map.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleGenericConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), Map.of());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), Map.of());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getFieldErrors());
    }

    /** Upload larger than spring.servlet.multipart limits. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return build(HttpStatus.CONTENT_TOO_LARGE, "The file is too large", Map.of());
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ApiError> handleBadMultipart(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "A file must be sent as multipart/form-data in the part 'file'", Map.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex) {
        return build(HttpStatus.BAD_REQUEST, "Missing parameter '" + ex.getParameterName() + "'", Map.of());
    }

    /** The request is well-formed but not allowed in the ticket's current state. */
    @ExceptionHandler(TicketWorkflowException.class)
    public ResponseEntity<ApiError> handleWorkflow(TicketWorkflowException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), Map.of());
    }

    /** The ticket was changed by someone else since the client loaded it. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleConflict(OptimisticLockingFailureException ex) {
        return build(HttpStatus.CONFLICT,
                "The ticket was changed by someone else. Reload it and try again.", Map.of());
    }

    /** Bad paging, sorting, filter or search parameters on the ticket list. */
    @ExceptionHandler(InvalidTicketQueryException.class)
    public ResponseEntity<ApiError> handleInvalidQuery(InvalidTicketQueryException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getFieldErrors());
    }

    /** Failed sign-in or a session that is no longer valid. The message is fixed text, never account details. */
    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiError> handleAuthenticationFailed(AuthenticationFailedException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), Map.of());
    }

    @ExceptionHandler(za.co.mamadise.support_ticket_tracker.auth.TooManyAttemptsException.class)
    public ResponseEntity<ApiError> handleTooManyAttempts(za.co.mamadise.support_ticket_tracker.auth.TooManyAttemptsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "300")
                .body(new ApiError(Instant.now(), 429, HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(), ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(InvalidUserException.class)
    public ResponseEntity<ApiError> handleInvalidUser(InvalidUserException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getFieldErrors());
    }

    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<ApiError> handleDuplicateUser(DuplicateUserException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), ex.getFieldErrors());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(UserNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), Map.of());
    }

    @ExceptionHandler(RoleChangeNotAllowedException.class)
    public ResponseEntity<ApiError> handleRoleChange(RoleChangeNotAllowedException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), Map.of());
    }

    /**
     * Method security ({@code @AdminOnly} and friends) throws from inside the controller, where this advice would
     * otherwise turn it into a 500. Signed-in but not allowed is 403; a caller that is not signed in is 401 (the URL
     * rules normally stop those earlier). Fixed text: nothing about the rule or the role is revealed.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean anonymous = authentication == null || authentication instanceof AnonymousAuthenticationToken
                || new AuthenticationTrustResolverImpl().isAnonymous(authentication);
        return anonymous ? build(HttpStatus.UNAUTHORIZED, "Authentication required", Map.of())
                : build(HttpStatus.FORBIDDEN, "Access denied", Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> fieldErrors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Request body is missing or not valid JSON", Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        // The rejected value is not echoed back: it is client input and could be long or contain markup.
        return build(HttpStatus.BAD_REQUEST, "Invalid value for '" + ex.getName() + "'", Map.of());
    }

    /** Fallback: keeps Spring's own 4xx errors (e.g. 405) and turns anything else into a 500. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            return build(status, status.getReasonPhrase(), Map.of());
        }
        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", Map.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, Map<String, String> fieldErrors) {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
