package com.clinic.doc_appointment.exception;

import com.clinic.doc_appointment.dto.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * Translates exceptions into the {@link ApiResponse} envelope with an appropriate status.
 *
 * <p>The guiding rule: a client mistake must never surface as a 500. Anything that reaches
 * {@link #handleGenericException} is by definition a server fault we did not anticipate, so every
 * predictable failure below has an explicit handler — including the domain exceptions that used to
 * fall through (a slot overlap, a booked slot, an unparseable path variable, malformed JSON, an
 * oversized upload), each of which reported "An unexpected error occurred" for what was really a
 * 400, 409 or 413.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Constraint names the schema declares; see the Flyway migration that adds them.
    private static final String UNIQUE_APPOINTMENT_SLOT = "uk_appointment_slot";
    private static final String UNIQUE_SLOT_PER_DOCTOR_TIME = "uk_slot_doctor_date_start";

    // ===================== authentication / authorization =====================

    /** Wrong email/password on login. */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid email or password"));
    }

    /**
     * Any other authentication failure — disabled account, locked account, unsupported token.
     * Without this, only {@code BadCredentialsException} was covered and the rest became 500s.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication rejected ({}): {}", ex.getClass().getSimpleName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication failed"));
    }

    /** Unusable refresh tokens (expired, revoked, unknown, replayed). */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        // The specific reason stays server-side: returning it would let a caller probe which
        // tokens exist, and tell an attacker their replay was the thing that got detected.
        log.warn("Refresh rejected: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid or expired refresh token"));
    }

    /** Ownership check failed in a service or access guard. */
    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbiddenOperation(ForbiddenOperationException ex) {
        log.warn("Forbidden operation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /** {@code @PreAuthorize} denial — must map to 403, not the RuntimeException catch-all's 400. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("You are not authorized to perform this action."));
    }

    // ===================== not found / duplicates =====================

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateResource(DuplicateResourceException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // ===================== domain state and conflicts =====================

    /** Invalid state transition, or an action the resource's history forbids. */
    @ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidState(InvalidStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /** Slot inputs the client can correct: a past date, or an end time before a start time. */
    @ExceptionHandler({InvalidSlotDateException.class, InvalidSlotTimeException.class})
    public ResponseEntity<ApiResponse<Void>> handleInvalidSlotInput(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /** Slot conflicts: overlapping an existing slot, or acting on one that is already booked. */
    @ExceptionHandler({SlotOverlapException.class, BookedSlotException.class})
    public ResponseEntity<ApiResponse<Void>> handleSlotConflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler({SlotAlreadyBookedException.class, SlotNotAvailableException.class})
    public ResponseEntity<ApiResponse<Void>> handleSlotUnavailable(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /** Booking conflict after all retries are exhausted. */
    @ExceptionHandler(BookingConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleBookingConflict(BookingConflictException ex) {
        log.warn("Booking conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler({
            OptimisticLockingFailureException.class,
            ObjectOptimisticLockingFailureException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLocking(Exception ex) {
        log.warn("Optimistic locking conflict: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        "The resource was modified by another user. Please refresh and try again."));
    }

    /**
     * Database constraint violations.
     *
     * <p>The constraint names matched here are the ones the schema actually declares. Matching on a
     * name that does not exist is worse than not matching at all: the branch silently never fires and
     * a genuine double-book reports itself as a generic "data conflict".
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.error("Data integrity violation: {}", ex.getMessage());

        String detail = String.valueOf(ex.getMostSpecificCause().getMessage()).toLowerCase();
        String message = "Operation failed due to data conflict.";

        if (detail.contains(UNIQUE_APPOINTMENT_SLOT)) {
            message = "This slot is already booked. Please select a different slot.";
        } else if (detail.contains(UNIQUE_SLOT_PER_DOCTOR_TIME)) {
            message = "A slot already exists at that time. Please choose a different time.";
        }

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(message));
    }

    // ===================== request validation =====================

    /** Bean-validation failures on an {@code @Valid} request body or model attribute. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        for (ObjectError error : ex.getBindingResult().getAllErrors()) {
            // getAllErrors() yields ObjectError for class-level constraints, which is not a
            // FieldError. An unconditional cast here would turn a validation failure into a 500.
            String key = (error instanceof FieldError fieldError) ? fieldError.getField() : error.getObjectName();
            errors.put(key, error.getDefaultMessage());
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Validation failed")
                        .data(errors)
                        .build());
    }

    /** Constraint violations on method parameters — {@code @Min}/{@code @Max} on a request param. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(
            ConstraintViolationException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                errors.put(String.valueOf(violation.getPropertyPath()), violation.getMessage()));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Validation failed")
                        .data(errors)
                        .build());
    }

    /** An unparseable path variable or query param — a bad date, or an unknown enum constant. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Unparseable value for '{}': {}", ex.getName(), ex.getValue());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Invalid value for '" + ex.getName() + "'."));
    }

    /** Malformed JSON, or a value the body cannot be bound from (e.g. a bad enum). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Unreadable request body: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Request body is malformed or contains an unsupported value."));
    }

    /** Upload beyond {@code spring.servlet.multipart.max-file-size} — 413, not 500. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("Upload rejected as too large: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("The uploaded file is too large."));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooManyRequests(TooManyRequestsException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // ===================== routing =====================
    //
    // Spring MVC raises these itself for a request that never reaches a handler. Without explicit
    // handlers they fall to the Exception catch-all below, so an unknown path, a wrong HTTP method
    // or an unsupported content type all answered 500 - reporting a server fault for a request the
    // client simply got wrong, and hiding genuine 500s among them.

    /** No handler matched the path. */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNoHandler(Exception ex) {
        log.warn("No handler for request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("The requested endpoint does not exist."));
    }

    /** The path exists but not for this method. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error("This endpoint does not support " + ex.getMethod() + " requests."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        log.warn("Unsupported media type: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error("Unsupported content type for this endpoint."));
    }

    /** A required query parameter or multipart part was absent. */
    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestPart(Exception ex) {
        log.warn("Missing required request input: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("A required request parameter is missing."));
    }

    // ===================== server faults =====================

    /**
     * Storage failed. A genuinely missing file is a {@link ResourceNotFoundException} and reports 404
     * above; reaching here means writing, reading or deleting broke, which is ours to fix.
     */
    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileStorage(FileStorageException ex) {
        log.error("File storage failure", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("The file could not be processed. Please try again later."));
    }

    /** Untyped/unexpected errors are server faults (500). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error: ", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred. Please try again later."));
    }
}
