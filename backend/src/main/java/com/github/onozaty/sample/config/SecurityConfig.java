package com.github.onozaty.sample.config;

import com.github.onozaty.sample.security.CookieBearerTokenResolver;
import com.github.onozaty.sample.service.JwtTokenService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
                auth.requestMatchers("/api/auth/login", "/api/auth/refresh")
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
                    .jwt(jwt -> jwt.decoder(jwtTokenService.jwtDecoder()))
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
