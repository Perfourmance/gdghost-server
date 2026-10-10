package com.gdghost.common.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 에러를 RFC 7807 Problem + code(+ errors[])로 응답한다.
 * instance는 Spring이 요청 경로로 채운다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ProblemDetail> handleBusiness(BusinessException e) {
        return respond(e.code().toProblem(e.getMessage(), e.errors()));
    }

    // 내부 메시지가 밖으로 새지 않게 detail은 고정 문구
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
        log.error("처리하지 못한 예외", e);
        return respond(ErrorCode.INTERNAL_ERROR.toProblem("잠시 후 다시 시도해 주세요.", List.of()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorField> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorField(error.getField(), error.getDefaultMessage()))
                .toList();
        return validationFailed(errors, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorField> errors = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ErrorField(fieldName(result.getMethodParameter().getParameterName(), error),
                                error.getDefaultMessage())))
                .toList();
        return validationFailed(errors, headers);
    }

    /** Spring MVC 기본 예외(404 · 405 · 본문 해석 실패 등)에도 code를 붙인다 */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception e, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail p ? p : ProblemDetail.forStatus(status);
        ErrorCode.fromStatus(status).applyTo(problem);
        return super.handleExceptionInternal(e, problem, headers, status, request);
    }

    private ResponseEntity<Object> validationFailed(List<ErrorField> errors, HttpHeaders headers) {
        ProblemDetail problem = ErrorCode.VALIDATION_FAILED.toProblem(null, errors);
        return ResponseEntity.status(problem.getStatus()).headers(headers).body(problem);
    }

    private static String fieldName(String parameter, MessageSourceResolvable error) {
        return error instanceof FieldError fieldError ? parameter + "." + fieldError.getField() : parameter;
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

}
