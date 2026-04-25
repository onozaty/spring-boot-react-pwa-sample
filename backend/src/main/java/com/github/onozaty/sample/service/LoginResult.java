package com.github.onozaty.sample.service;

import com.github.onozaty.sample.domain.User;

public record LoginResult(User user, String accessToken, String refreshToken) {}
