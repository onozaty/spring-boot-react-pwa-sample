package com.github.onozaty.sample;

import com.github.onozaty.sample.service.JwtTokenService;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** テスト用ログインヘルパー。admin で認証して Cookie 文字列を得る */
public class LoginHelper {

  private LoginHelper() {}

  /** admin ユーザーでログインし、Set-Cookie ヘッダの値（Cookie ヘッダとして送れる形式）を返す。 例: "ACCESS_TOKEN=eyJ..." */
  public static String getAuthCookie(String baseUrl) {
    var client = RestClient.builder().baseUrl(baseUrl).build();
    var response =
        client
            .post()
            .uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"email\":\"admin@example.com\",\"password\":\"admin\"}")
            .retrieve()
            .toBodilessEntity();

    return response.getHeaders().get("Set-Cookie").stream()
        .filter(v -> v.startsWith(JwtTokenService.ACCESS_TOKEN_COOKIE_NAME + "="))
        .map(v -> v.split(";")[0])
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("ACCESS_TOKEN cookie not found in login response"));
  }
}
