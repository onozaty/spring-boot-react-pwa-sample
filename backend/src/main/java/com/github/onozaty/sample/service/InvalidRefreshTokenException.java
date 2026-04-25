package com.github.onozaty.sample.service;

public class InvalidRefreshTokenException extends RuntimeException {

  public InvalidRefreshTokenException() {
    super("Invalid or expired refresh token");
  }
}
