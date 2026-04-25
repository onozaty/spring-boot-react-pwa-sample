package com.github.onozaty.sample.security;

import com.github.onozaty.sample.service.JwtTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.stereotype.Component;

@Component
public class CookieBearerTokenResolver implements BearerTokenResolver {

  @Override
  public String resolve(HttpServletRequest request) {
    if (request.getCookies() == null) {
      return null;
    }
    for (Cookie cookie : request.getCookies()) {
      if (JwtTokenService.ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
