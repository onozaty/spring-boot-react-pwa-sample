package com.github.onozaty.sample.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
    // HS256 は 32 バイト以上の鍵を必要とする
    @NotBlank @Size(min = 32) String secret,
    @Min(1) int accessExpirationMinutes,
    @Min(1) int refreshExpirationDays) {}
