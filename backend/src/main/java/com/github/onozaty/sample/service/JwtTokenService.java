package com.github.onozaty.sample.service;

import com.github.onozaty.sample.config.CookieProperties;
import com.github.onozaty.sample.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

  public static final String ACCESS_TOKEN_COOKIE_NAME = "ACCESS_TOKEN";
  public static final String REFRESH_TOKEN_COOKIE_NAME = "REFRESH_TOKEN";
  public static final String CLAIM_USER_ID = "uid";
  public static final String CLAIM_SESSION_ID = "sid";

  private static final String REFRESH_TOKEN_COOKIE_PATH = "/api/auth/refresh";

  private final JwtEncoder encoder;
  private final JwtDecoder decoder;
  private final JwtProperties jwtProperties;
  private final CookieProperties cookieProperties;

  public JwtTokenService(JwtProperties jwtProperties, CookieProperties cookieProperties) {
    this.jwtProperties = jwtProperties;
    this.cookieProperties = cookieProperties;

    var key =
        new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    this.encoder =
        new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(key.getEncoded()));
    this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
  }

  public String issueAccessToken(long userId, String username, String sessionId) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(username)
            .claim(CLAIM_USER_ID, userId)
            .claim(CLAIM_SESSION_ID, sessionId)
            .issuedAt(now)
            .expiresAt(now.plus(jwtProperties.accessExpirationMinutes(), ChronoUnit.MINUTES))
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  public JwtDecoder jwtDecoder() {
    return decoder;
  }

  public ResponseCookie buildAccessTokenCookie(String token) {
    return ResponseCookie.from(ACCESS_TOKEN_COOKIE_NAME, token)
        .httpOnly(true)
        .secure(cookieProperties.secure())
        .sameSite("Strict")
        .path("/")
        .maxAge(Duration.ofMinutes(jwtProperties.accessExpirationMinutes()))
        .build();
  }

  public ResponseCookie buildClearAccessTokenCookie() {
    return ResponseCookie.from(ACCESS_TOKEN_COOKIE_NAME, "")
        .httpOnly(true)
        .secure(cookieProperties.secure())
        .sameSite("Strict")
        .path("/")
        .maxAge(0)
        .build();
  }

  public ResponseCookie buildRefreshTokenCookie(String token) {
    return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, token)
        .httpOnly(true)
        .secure(cookieProperties.secure())
        .sameSite("Strict")
        .path(REFRESH_TOKEN_COOKIE_PATH)
        .maxAge(Duration.ofDays(jwtProperties.refreshExpirationDays()))
        .build();
  }

  public ResponseCookie buildClearRefreshTokenCookie() {
    return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
        .httpOnly(true)
        .secure(cookieProperties.secure())
        .sameSite("Strict")
        .path(REFRESH_TOKEN_COOKIE_PATH)
        .maxAge(0)
        .build();
  }
}
