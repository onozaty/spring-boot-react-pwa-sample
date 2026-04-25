package com.github.onozaty.sample.controller;

import static org.assertj.core.api.Assertions.*;

import com.github.onozaty.sample.AppTest;
import com.github.onozaty.sample.domain.User;
import com.github.onozaty.sample.service.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@AppTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AuthControllerTest {

  @LocalServerPort private int port;

  private RestClient restClient;

  @BeforeEach
  void setUp() {
    restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
  }

  @Test
  void testLoginSuccess() {
    // Act
    ResponseEntity<User> response =
        restClient
            .post()
            .uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"email\":\"admin@example.com\",\"password\":\"admin\"}")
            .retrieve()
            .toEntity(User.class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getEmail()).isEqualTo("admin@example.com");

    var setCookieHeaders = response.getHeaders().get("Set-Cookie");
    assertThat(setCookieHeaders).isNotNull();

    String accessCookie =
        setCookieHeaders.stream()
            .filter(v -> v.startsWith(JwtTokenService.ACCESS_TOKEN_COOKIE_NAME + "="))
            .findFirst()
            .orElse(null);
    assertThat(accessCookie).isNotNull();
    assertThat(accessCookie).contains("HttpOnly");
    assertThat(accessCookie).contains("SameSite=Strict");
    assertThat(accessCookie).contains("Path=/");
    assertThat(accessCookie).contains("Max-Age=");

    String refreshCookie =
        setCookieHeaders.stream()
            .filter(v -> v.startsWith(JwtTokenService.REFRESH_TOKEN_COOKIE_NAME + "="))
            .findFirst()
            .orElse(null);
    assertThat(refreshCookie).isNotNull();
    assertThat(refreshCookie).contains("HttpOnly");
    assertThat(refreshCookie).contains("SameSite=Strict");
    assertThat(refreshCookie).contains("Path=/api/auth/refresh");
    assertThat(refreshCookie).contains("Max-Age=");
  }

  @Test
  void testLoginWithWrongPassword() {
    // Act & Assert
    ResponseEntity<Void> response =
        restClient
            .post()
            .uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"email\":\"admin@example.com\",\"password\":\"wrongpassword\"}")
            .retrieve()
            .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
            .toBodilessEntity();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void testLoginWithUnknownEmail() {
    // Act & Assert
    ResponseEntity<Void> response =
        restClient
            .post()
            .uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"email\":\"unknown@example.com\",\"password\":\"admin\"}")
            .retrieve()
            .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
            .toBodilessEntity();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void testLogout() {
    // Arrange
    String accessCookie = loginAndGetAccessCookie();

    // Act
    ResponseEntity<Void> response =
        restClient
            .post()
            .uri("/api/auth/logout")
            .header("Cookie", accessCookie)
            .retrieve()
            .toBodilessEntity();

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    var setCookieHeaders = response.getHeaders().get("Set-Cookie");
    assertThat(setCookieHeaders).isNotNull();

    String clearedAccessCookie =
        setCookieHeaders.stream()
            .filter(v -> v.startsWith(JwtTokenService.ACCESS_TOKEN_COOKIE_NAME + "="))
            .findFirst()
            .orElse(null);
    assertThat(clearedAccessCookie).isNotNull();
    assertThat(clearedAccessCookie).contains("Max-Age=0");

    String clearedRefreshCookie =
        setCookieHeaders.stream()
            .filter(v -> v.startsWith(JwtTokenService.REFRESH_TOKEN_COOKIE_NAME + "="))
            .findFirst()
            .orElse(null);
    assertThat(clearedRefreshCookie).isNotNull();
    assertThat(clearedRefreshCookie).contains("Max-Age=0");
  }

  @Test
  void testLogoutDoesNotAffectOtherSessions() {
    // Arrange — 同じユーザーで 2 セッションを作成し、片方でログアウトする
    String sessionACookie = loginAndGetAccessCookie();
    String sessionBRefreshCookie = loginAndGetRefreshCookie();

    // Act — セッション A だけログアウト
    restClient
        .post()
        .uri("/api/auth/logout")
        .header("Cookie", sessionACookie)
        .retrieve()
        .toBodilessEntity();

    // Assert — セッション B の refresh は引き続き有効
    ResponseEntity<Void> response =
        restClient
            .post()
            .uri("/api/auth/refresh")
            .header("Cookie", sessionBRefreshCookie)
            .retrieve()
            .toBodilessEntity();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void testRefreshSuccess() {
    // Arrange
    var loginResponse =
        restClient
            .post()
            .uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"email\":\"admin@example.com\",\"password\":\"admin\"}")
            .retrieve()
            .toBodilessEntity();

    String refreshCookieHeader =
        loginResponse.getHeaders().get("Set-Cookie").stream()
            .filter(v -> v.startsWith(JwtTokenService.REFRESH_TOKEN_COOKIE_NAME + "="))
            .map(v -> v.split(";")[0])
            .findFirst()
            .orElseThrow();

    // Act
    ResponseEntity<Void> response =
        restClient
            .post()
            .uri("/api/auth/refresh")
            .header("Cookie", refreshCookieHeader)
            .retrieve()
            .toBodilessEntity();

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    var setCookieHeaders = response.getHeaders().get("Set-Cookie");
    assertThat(setCookieHeaders).isNotNull();

    assertThat(
            setCookieHeaders.stream()
                .anyMatch(v -> v.startsWith(JwtTokenService.ACCESS_TOKEN_COOKIE_NAME + "=")))
        .isTrue();
    assertThat(
            setCookieHeaders.stream()
                .anyMatch(v -> v.startsWith(JwtTokenService.REFRESH_TOKEN_COOKIE_NAME + "=")))
        .isTrue();
  }

  @Test
  void testRefreshWithInvalidToken() {
    // Act & Assert
    ResponseEntity<Void> response =
        restClient
            .post()
            .uri("/api/auth/refresh")
            .header("Cookie", JwtTokenService.REFRESH_TOKEN_COOKIE_NAME + "=invalid-token")
            .retrieve()
            .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
            .toBodilessEntity();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void testRefreshWithoutCookie() {
    // Act & Assert
    ResponseEntity<Void> response =
        restClient
            .post()
            .uri("/api/auth/refresh")
            .retrieve()
            .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
            .toBodilessEntity();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void testGetMe() {
    // Arrange
    String cookie = loginAndGetAccessCookie();

    // Act
    ResponseEntity<User> response =
        restClient
            .get()
            .uri("/api/auth/me")
            .header("Cookie", cookie)
            .retrieve()
            .toEntity(User.class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getEmail()).isEqualTo("admin@example.com");
  }

  @Test
  void testGetMeUnauthenticated() {
    // Act & Assert
    ResponseEntity<Void> response =
        restClient
            .get()
            .uri("/api/auth/me")
            .retrieve()
            .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
            .toBodilessEntity();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void testChangePassword() {
    // Arrange — 現在のセッション (current) と他端末セッション (other) を作成
    String currentAccessCookie = loginAndGetAccessCookie();
    String otherRefreshCookie = loginAndGetRefreshCookie();

    // Act
    ResponseEntity<Void> response =
        restClient
            .patch()
            .uri("/api/auth/me/password")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Cookie", currentAccessCookie)
            .body("{\"currentPassword\":\"admin\",\"newPassword\":\"newpassword123\"}")
            .retrieve()
            .toBodilessEntity();

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // 新しいパスワードでログインできる
    ResponseEntity<User> loginResponse =
        restClient
            .post()
            .uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"email\":\"admin@example.com\",\"password\":\"newpassword123\"}")
            .retrieve()
            .toEntity(User.class);
    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    // 他端末のセッション (refresh token) は失効している
    ResponseEntity<Void> otherRefreshResponse =
        restClient
            .post()
            .uri("/api/auth/refresh")
            .header("Cookie", otherRefreshCookie)
            .retrieve()
            .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
            .toBodilessEntity();
    assertThat(otherRefreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void testChangePasswordWithWrongCurrentPassword() {
    // Arrange
    String cookie = loginAndGetAccessCookie();

    // Act & Assert
    ResponseEntity<Void> response =
        restClient
            .patch()
            .uri("/api/auth/me/password")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Cookie", cookie)
            .body("{\"currentPassword\":\"wrongpassword\",\"newPassword\":\"newpassword123\"}")
            .retrieve()
            .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
            .toBodilessEntity();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void testUnauthenticatedAccessToUsers() {
    // Act & Assert
    ResponseEntity<Void> response =
        restClient
            .get()
            .uri("/api/users")
            .retrieve()
            .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
            .toBodilessEntity();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  private String loginAndGetAccessCookie() {
    return loginAndGetCookie(JwtTokenService.ACCESS_TOKEN_COOKIE_NAME);
  }

  private String loginAndGetRefreshCookie() {
    return loginAndGetCookie(JwtTokenService.REFRESH_TOKEN_COOKIE_NAME);
  }

  private String loginAndGetCookie(String cookieName) {
    var response =
        restClient
            .post()
            .uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"email\":\"admin@example.com\",\"password\":\"admin\"}")
            .retrieve()
            .toBodilessEntity();

    return response.getHeaders().get("Set-Cookie").stream()
        .filter(v -> v.startsWith(cookieName + "="))
        .map(v -> v.split(";")[0])
        .findFirst()
        .orElseThrow();
  }
}
