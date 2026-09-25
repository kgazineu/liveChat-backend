package com.example.liveChat.infra;


import com.example.liveChat.exceptions.UserAlreadyExistsException;
import com.example.liveChat.exceptions.UserNotFoundException;
import com.example.liveChat.exceptions.ServerNotFoundException;
import com.example.liveChat.exceptions.ServerInviteNotFoundException;
import com.example.liveChat.exceptions.InvalidRequestException;
import com.example.liveChat.exceptions.ResourceConflictException;
import com.example.liveChat.exceptions.DirectChannelNotFoundException;
import com.example.liveChat.exceptions.ServerChannelNotFoundException;
import com.example.liveChat.exceptions.MediaInfrastructureUnavailableException;
import com.example.liveChat.exceptions.RateLimitExceededException;
import com.example.liveChat.infra.ratelimit.RateLimitInfrastructureException;
import com.example.liveChat.infra.storage.AttachmentObjectNotFoundException;
import com.example.liveChat.infra.storage.AttachmentStorageException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(AuthenticationException.class)
    private ResponseEntity<RestErrorMessage> authenticationHandler(AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new RestErrorMessage(HttpStatus.UNAUTHORIZED, exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    private ResponseEntity<RestErrorMessage> accessDeniedHandler(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new RestErrorMessage(HttpStatus.FORBIDDEN, exception.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    private ResponseEntity<RestErrorMessage> userNotFoundHandler(UserNotFoundException exception){
        RestErrorMessage threatResponse = new RestErrorMessage(HttpStatus.NOT_FOUND, exception.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(threatResponse);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    private ResponseEntity<RestErrorMessage> userAlreadyExistsHandler(UserAlreadyExistsException exception){
        RestErrorMessage threatResponse = new RestErrorMessage(HttpStatus.CONFLICT, exception.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(threatResponse);
    }

    @ExceptionHandler(ServerNotFoundException.class)
    private ResponseEntity<RestErrorMessage> serverNotFoundHandler(ServerNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new RestErrorMessage(HttpStatus.NOT_FOUND, exception.getMessage()));
    }

    @ExceptionHandler(ServerInviteNotFoundException.class)
    private ResponseEntity<RestErrorMessage> serverInviteNotFoundHandler(ServerInviteNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new RestErrorMessage(HttpStatus.NOT_FOUND, exception.getMessage()));
    }

    @ExceptionHandler(DirectChannelNotFoundException.class)
    private ResponseEntity<RestErrorMessage> directChannelNotFoundHandler(DirectChannelNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new RestErrorMessage(HttpStatus.NOT_FOUND, exception.getMessage()));
    }

    @ExceptionHandler(ServerChannelNotFoundException.class)
    private ResponseEntity<RestErrorMessage> serverChannelNotFoundHandler(ServerChannelNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new RestErrorMessage(HttpStatus.NOT_FOUND, exception.getMessage()));
    }

    @ExceptionHandler(ResourceConflictException.class)
    private ResponseEntity<RestErrorMessage> resourceConflictHandler(ResourceConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new RestErrorMessage(HttpStatus.CONFLICT, exception.getMessage()));
    }

    @ExceptionHandler(InvalidRequestException.class)
    private ResponseEntity<RestErrorMessage> invalidRequestHandler(InvalidRequestException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new RestErrorMessage(HttpStatus.BAD_REQUEST, exception.getMessage()));
    }

    @ExceptionHandler(MediaInfrastructureUnavailableException.class)
    private ResponseEntity<RestErrorMessage> mediaInfrastructureUnavailableHandler(
            MediaInfrastructureUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new RestErrorMessage(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    private ResponseEntity<RestErrorMessage> rateLimitExceededHandler(RateLimitExceededException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()))
                .body(new RestErrorMessage(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage()));
    }

    @ExceptionHandler(RateLimitInfrastructureException.class)
    private ResponseEntity<RestErrorMessage> rateLimitInfrastructureHandler(
            RateLimitInfrastructureException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new RestErrorMessage(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage()));
    }

    @ExceptionHandler(AttachmentObjectNotFoundException.class)
    private ResponseEntity<RestErrorMessage> attachmentNotFoundHandler(AttachmentObjectNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new RestErrorMessage(HttpStatus.CONFLICT, exception.getMessage()));
    }

    @ExceptionHandler(AttachmentStorageException.class)
    private ResponseEntity<RestErrorMessage> attachmentStorageHandler(AttachmentStorageException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new RestErrorMessage(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    private ResponseEntity<RestErrorMessage> genericHandler(Exception exception){
        RestErrorMessage threatResponse = new RestErrorMessage(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong :( try again later");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(threatResponse);
    }

}
