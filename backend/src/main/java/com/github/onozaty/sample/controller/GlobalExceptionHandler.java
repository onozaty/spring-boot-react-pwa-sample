package com.github.onozaty.sample.controller;

import com.github.onozaty.sample.service.InvalidCurrentPasswordException;
import com.github.onozaty.sample.service.JwtTokenService;
import com.github.onozaty.sample.service.TodoNotFoundException;
import com.github.onozaty.sample.service.UserNotFoundException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final JwtTokenService jwtTokenService;

  public GlobalExceptionHandler(JwtTokenService jwtTokenService) {
    this.jwtTokenService = jwtTokenService;
  }

  @ExceptionHandler(UserNotFoundException.class)
  public ResponseEntity<Void> handleUserNotFound(UserNotFoundException e) {
    return ResponseEntity.notFound().build();
  }

  @ExceptionHandler(TodoNotFoundException.class)
  public ResponseEntity<Void> handleTodoNotFound(TodoNotFoundException e) {
    return ResponseEntity.notFound().build();
  }

  @ExceptionHandler(InvalidCurrentPasswordException.class)
  public ResponseEntity<ProblemDetail> handleInvalidCurrentPassword(
      InvalidCurrentPasswordException e) {
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    problemDetail.setTitle("Bad Request");
    problemDetail.setDetail("現在のパスワードが正しくありません。");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
  }

  @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleAuthenticationCredentialsNotFound(
      AuthenticationCredentialsNotFoundException e) {
    logger.info("認証情報が失効しています。: {}", e.getMessage());
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
    problemDetail.setTitle("Unauthorized");
    problemDetail.setDetail("認証情報が失効しています。再度ログインしてください。");
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .header(HttpHeaders.SET_COOKIE, jwtTokenService.buildClearAccessTokenCookie().toString())
        .header(HttpHeaders.SET_COOKIE, jwtTokenService.buildClearRefreshTokenCookie().toString())
        .body(problemDetail);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException e) {
    logger.info("認証に失敗しました。: {}", e.getMessage());
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
    problemDetail.setTitle("Unauthorized");
    problemDetail.setDetail("メールアドレスまたはパスワードが正しくありません。");
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problemDetail);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
      DataIntegrityViolationException e) {
    logger.info("データの整合性制約に違反しています。: {}", e.getMessage());
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
    problemDetail.setTitle("Conflict");
    problemDetail.setDetail("データの整合性制約に違反しています。");
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
    logger.error("予期せぬエラーが発生しました。", e);
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    problemDetail.setTitle("Internal Server Error");
    problemDetail.setDetail("予期せぬエラーが発生しました。");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    logger.info("バリデーションエラーが発生しました。: {}", ex.getMessage());
    List<ValidationProblemDetail.FieldError> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                fe -> new ValidationProblemDetail.FieldError(fe.getField(), fe.getDefaultMessage()))
            .toList();

    ProblemDetail problemDetail = ex.getBody();
    var body =
        new ValidationProblemDetail(
            problemDetail.getTitle(),
            status.value(),
            problemDetail.getDetail(),
            ((ServletWebRequest) request).getRequest().getRequestURI(),
            errors);
    return ResponseEntity.status(status).headers(headers).body(body);
  }
}
