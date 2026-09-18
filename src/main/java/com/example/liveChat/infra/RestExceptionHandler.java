package com.example.liveChat.infra;


import com.example.liveChat.exceptions.UserAlreadyExistsException;
import com.example.liveChat.exceptions.UserNotFoundException;
import com.example.liveChat.exceptions.ServerNotFoundException;
import com.example.liveChat.exceptions.ServerInviteNotFoundException;
import com.example.liveChat.exceptions.InvalidRequestException;
import com.example.liveChat.exceptions.ResourceConflictException;
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

    @ExceptionHandler(Exception.class)
    private ResponseEntity<RestErrorMessage> genericHandler(Exception exception){
        RestErrorMessage threatResponse = new RestErrorMessage(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong :( try again later");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(threatResponse);
    }

}
