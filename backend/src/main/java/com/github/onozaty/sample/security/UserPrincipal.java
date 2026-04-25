package com.github.onozaty.sample.security;

public record UserPrincipal(long userId, String email, String sessionId) {}
