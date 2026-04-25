package com.github.onozaty.sample.controller;

import com.github.onozaty.sample.domain.User;
import com.github.onozaty.sample.mapper.UserMapper;
import com.github.onozaty.sample.service.AuthService;
import com.github.onozaty.sample.service.InvalidRefreshTokenException;
import com.github.onozaty.sample.service.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "認証API")
public class AuthController {

  private final AuthenticationManager authenticationManager;
  private final JwtTokenService jwtTokenService;
  private final UserMapper userMapper;
  private final AuthService authService;

  public AuthController(
      AuthenticationManager authenticationManager,
      JwtTokenService jwtTokenService,
      UserMapper userMapper,
      AuthService authService) {
    this.authenticationManager = authenticationManager;
    this.jwtTokenService = jwtTokenService;
    this.userMapper = userMapper;
    this.authService = authService;
  }

  @PostMapping("/login")
  @Operation(summary = "ログイン", description = "メールアドレスとパスワードで認証し、JWT Cookie を発行します")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "ログイン成功"),
    @ApiResponse(responseCode = "401", description = "認証失敗")
  })
  public ResponseEntity<User> login(@Valid @RequestBody LoginRequest request) {
    var authToken = new UsernamePasswordAuthenticationToken(request.email(), request.password());
    var authentication = authenticationManager.authenticate(authToken);

    var email = authentication.getName();
    var user =
        userMapper
            .findByEmail(email)
            .orElseThrow(
                () ->
                    new AuthenticationCredentialsNotFoundException("Authenticated user not found"));

    String sessionId = authService.createSession(user.getId());
    String accessToken = jwtTokenService.issueAccessToken(user.getId(), user.getEmail(), sessionId);
    String refreshToken = authService.issueRefreshToken(sessionId);

    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE, jwtTokenService.buildAccessTokenCookie(accessToken).toString())
        .header(
            HttpHeaders.SET_COOKIE,
            jwtTokenService.buildRefreshTokenCookie(refreshToken).toString())
        .body(user);
  }

  @PostMapping("/logout")
  @Operation(summary = "ログアウト", description = "現在の端末のセッションのみを破棄し、JWT Cookie を削除します")
  @ApiResponse(responseCode = "204", description = "ログアウト成功")
  public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
    String sessionId = jwt.getClaim(JwtTokenService.CLAIM_SESSION_ID);
    authService.revokeSession(sessionId);

    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, jwtTokenService.buildClearAccessTokenCookie().toString())
        .header(HttpHeaders.SET_COOKIE, jwtTokenService.buildClearRefreshTokenCookie().toString())
        .build();
  }

  @PostMapping("/refresh")
  @Operation(summary = "トークンリフレッシュ", description = "リフレッシュトークンを使って新しいアクセストークンを発行します")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "リフレッシュ成功"),
    @ApiResponse(responseCode = "401", description = "リフレッシュトークンが無効または期限切れ")
  })
  public ResponseEntity<Void> refresh(HttpServletRequest request) {
    String plainRefreshToken = extractRefreshTokenCookie(request);
    if (plainRefreshToken == null) {
      return ResponseEntity.status(401)
          .header(HttpHeaders.SET_COOKIE, jwtTokenService.buildClearAccessTokenCookie().toString())
          .header(HttpHeaders.SET_COOKIE, jwtTokenService.buildClearRefreshTokenCookie().toString())
          .build();
    }

    try {
      String sessionId = authService.validateAndRotateRefreshToken(plainRefreshToken);
      var session =
          authService
              .findSession(sessionId)
              .orElseThrow(
                  () ->
                      new AuthenticationCredentialsNotFoundException(
                          "Authenticated session not found"));
      var user =
          userMapper
              .findById(session.getUserId())
              .orElseThrow(
                  () ->
                      new AuthenticationCredentialsNotFoundException(
                          "Authenticated user not found"));

      String newAccessToken =
          jwtTokenService.issueAccessToken(user.getId(), user.getEmail(), sessionId);
      String newRefreshToken = authService.issueRefreshToken(sessionId);

      return ResponseEntity.noContent()
          .header(
              HttpHeaders.SET_COOKIE,
              jwtTokenService.buildAccessTokenCookie(newAccessToken).toString())
          .header(
              HttpHeaders.SET_COOKIE,
              jwtTokenService.buildRefreshTokenCookie(newRefreshToken).toString())
          .build();

    } catch (InvalidRefreshTokenException e) {
      return ResponseEntity.status(401)
          .header(HttpHeaders.SET_COOKIE, jwtTokenService.buildClearAccessTokenCookie().toString())
          .header(HttpHeaders.SET_COOKIE, jwtTokenService.buildClearRefreshTokenCookie().toString())
          .build();
    }
  }

  @GetMapping("/me")
  @Operation(summary = "ログインユーザー取得", description = "現在ログイン中のユーザー情報を返します")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "取得成功"),
    @ApiResponse(responseCode = "401", description = "未認証")
  })
  public ResponseEntity<User> me(@AuthenticationPrincipal Jwt jwt) {
    Long userId = jwt.getClaim(JwtTokenService.CLAIM_USER_ID);
    var user =
        userMapper
            .findById(userId)
            .orElseThrow(
                () ->
                    new AuthenticationCredentialsNotFoundException("Authenticated user not found"));
    return ResponseEntity.ok(user);
  }

  @PatchMapping("/me/password")
  @Operation(summary = "パスワード変更", description = "ログイン中ユーザーのパスワードを変更します。他端末のセッションは失効します。")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "変更成功"),
    @ApiResponse(responseCode = "400", description = "現在のパスワードが正しくない")
  })
  public ResponseEntity<Void> changePassword(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordChangeRequest request) {
    Long userId = jwt.getClaim(JwtTokenService.CLAIM_USER_ID);
    String sessionId = jwt.getClaim(JwtTokenService.CLAIM_SESSION_ID);
    authService.changePassword(userId, sessionId, request.currentPassword(), request.newPassword());
    return ResponseEntity.noContent().build();
  }

  private String extractRefreshTokenCookie(HttpServletRequest request) {
    if (request.getCookies() == null) {
      return null;
    }
    for (Cookie cookie : request.getCookies()) {
      if (JwtTokenService.REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
