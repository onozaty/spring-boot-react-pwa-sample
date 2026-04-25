package com.github.onozaty.sample.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.github.onozaty.sample.AppTest;
import com.github.onozaty.sample.domain.UserWithCredential;
import com.github.onozaty.sample.mapper.UserMapper;
import com.github.onozaty.sample.service.JwtTokenService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

/** ログイン時に findByEmailWithCredential が空を返す（認証直後に削除されたなどの競合）ケース */
@AppTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AuthControllerLoginMissingUserTest {

  // "admin" を BCrypt でハッシュしたもの（V2__insert_initial_user.sql と同一）
  private static final String ADMIN_PASSWORD_HASH =
      "$2a$10$pcUixkg46sBY9IxlYdz1UemalgvlwEkUi5T7hofcqVY3DdZsknzvO";

  @LocalServerPort private int port;

  @MockitoBean private UserMapper userMapper;

  private RestClient restClient;

  @BeforeEach
  void setUp() {
    restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
  }

  @Test
  void testLoginReturns401WhenUserNotFoundAfterAuthentication() {
    // Arrange — 認証（UserDetailsService 経由）は通すが、login 内の再取得で空を返す
    var credential = new UserWithCredential();
    credential.setId(1L);
    credential.setName("admin");
    credential.setEmail("admin@example.com");
    credential.setPasswordHash(ADMIN_PASSWORD_HASH);
    when(userMapper.findByEmailWithCredential(anyString()))
        .thenReturn(Optional.of(credential))
        .thenReturn(Optional.empty());

    // Act
    ResponseEntity<Void> response =
        restClient
            .post()
            .uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"email\":\"admin@example.com\",\"password\":\"admin\"}")
            .retrieve()
            .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
            .toBodilessEntity();

    // Assert — 401 + Cookie 失効
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    var setCookieHeaders = response.getHeaders().get("Set-Cookie");
    assertThat(setCookieHeaders).isNotNull();

    String accessCookie =
        setCookieHeaders.stream()
            .filter(v -> v.startsWith(JwtTokenService.ACCESS_TOKEN_COOKIE_NAME + "="))
            .findFirst()
            .orElse(null);
    assertThat(accessCookie).isNotNull();
    assertThat(accessCookie).contains("Max-Age=0");

    String refreshCookie =
        setCookieHeaders.stream()
            .filter(v -> v.startsWith(JwtTokenService.REFRESH_TOKEN_COOKIE_NAME + "="))
            .findFirst()
            .orElse(null);
    assertThat(refreshCookie).isNotNull();
    assertThat(refreshCookie).contains("Max-Age=0");
  }
}
