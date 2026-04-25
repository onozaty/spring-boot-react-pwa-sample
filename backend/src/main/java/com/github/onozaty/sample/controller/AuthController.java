package com.github.onozaty.sample.controller;

import com.github.onozaty.sample.domain.User;
import com.github.onozaty.sample.security.UserPrincipal;
import com.github.onozaty.sample.service.AuthService;
import com.github.onozaty.sample.service.JwtTokenService;
import com.github.onozaty.sample.service.LoginResult;
import com.github.onozaty.sample.service.RefreshResult;
import com.github.onozaty.sample.service.UserService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

  private final JwtTokenService jwtTokenService;
  private final UserService userService;
  private final AuthService authService;

  public AuthController(
      JwtTokenService jwtTokenService, UserService userService, AuthService authService) {
    this.jwtTokenService = jwtTokenService;
    this.userService = userService;
    this.authService = authService;
  }

  @PostMapping("/login")
  @Operation(summary = "ログイン", description = "メールアドレスとパスワードで認証し、JWT Cookie を発行します")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "ログイン成功"),
    @ApiResponse(responseCode = "401", description = "認証失敗")
  })
  public ResponseEntity<User> login(@Valid @RequestBody LoginRequest request) {
    LoginResult result = authService.login(request.email(), request.password());

    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE,
            jwtTokenService.buildAccessTokenCookie(result.accessToken()).toString())
        .header(
            HttpHeaders.SET_COOKIE,
            jwtTokenService.buildRefreshTokenCookie(result.refreshToken()).toString())
        .body(result.user());
  }

  @PostMapping("/logout")
  @Operation(summary = "ログアウト", description = "現在の端末のセッションのみを破棄し、JWT Cookie を削除します")
  @ApiResponse(responseCode = "204", description = "ログアウト成功")
  public ResponseEntity<Void> logout(@AuthenticationPrincipal UserPrincipal principal) {
    authService.revokeSession(principal.sessionId());

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

    RefreshResult result = authService.refresh(plainRefreshToken).orElse(null);
    if (result == null) {
      return ResponseEntity.status(401)
          .header(HttpHeaders.SET_COOKIE, jwtTokenService.buildClearAccessTokenCookie().toString())
          .header(HttpHeaders.SET_COOKIE, jwtTokenService.buildClearRefreshTokenCookie().toString())
          .build();
    }

    return ResponseEntity.noContent()
        .header(
            HttpHeaders.SET_COOKIE,
            jwtTokenService.buildAccessTokenCookie(result.accessToken()).toString())
        .header(
            HttpHeaders.SET_COOKIE,
            jwtTokenService.buildRefreshTokenCookie(result.refreshToken()).toString())
        .build();
  }

  @GetMapping("/me")
  @Operation(summary = "ログインユーザー取得", description = "現在ログイン中のユーザー情報を返します")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "取得成功"),
    @ApiResponse(responseCode = "401", description = "未認証")
  })
  public ResponseEntity<User> me(@AuthenticationPrincipal UserPrincipal principal) {
    User user =
        userService
            .findById(principal.userId())
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
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody PasswordChangeRequest request) {
    authService.changePassword(
        principal.userId(),
        principal.sessionId(),
        request.currentPassword(),
        request.newPassword());

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
