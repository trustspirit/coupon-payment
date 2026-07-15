package com.example.board.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode ec = e.getErrorCode();
        return ResponseEntity.status(ec.getStatus())
                .body(new ErrorResponse(ec.getCode(), ec.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse(ErrorCode.VALIDATION_FAILED.getMessage());
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(new ErrorResponse(ErrorCode.VALIDATION_FAILED.getCode(), detail));
    }
}
