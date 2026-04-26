package com.github.onozaty.sample.config;

import com.github.onozaty.sample.security.CookieBearerTokenResolver;
import com.github.onozaty.sample.security.UserPrincipal;
import com.github.onozaty.sample.service.JwtTokenService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final JwtTokenService jwtTokenService;
  private final CookieBearerTokenResolver cookieBearerTokenResolver;

  public SecurityConfig(
      JwtTokenService jwtTokenService, CookieBearerTokenResolver cookieBearerTokenResolver) {
    this.jwtTokenService = jwtTokenService;
    this.cookieBearerTokenResolver = cookieBearerTokenResolver;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/health")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    // SPA のルート / 静的リソース / Swagger はすべて permitAll
                    // （未認証時のルートガードは SPA 側の beforeLoad が担う）
                    .anyRequest()
                    .permitAll())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(
                        jwt ->
                            jwt.decoder(jwtTokenService.jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                    .bearerTokenResolver(cookieBearerTokenResolver))
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                    (request, response, authException) -> {
                      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                      response.getWriter().write("{\"error\":\"Unauthorized\"}");
                    }))
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable());

    return http.build();
  }

  @Bean
  Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
    return jwt -> {
      long userId = (Long) jwt.getClaim(JwtTokenService.CLAIM_USER_ID);
      String email = jwt.getSubject();
      String sessionId = jwt.getClaim(JwtTokenService.CLAIM_SESSION_ID);
      UserPrincipal principal = new UserPrincipal(userId, email, sessionId);
      return new UsernamePasswordAuthenticationToken(principal, jwt, Collections.emptyList());
    };
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthenticationManager authenticationManager(
      UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    var provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }
}
