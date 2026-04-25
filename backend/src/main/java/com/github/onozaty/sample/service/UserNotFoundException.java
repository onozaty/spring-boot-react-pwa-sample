package com.github.onozaty.sample.service;

public class UserNotFoundException extends RuntimeException {

  public UserNotFoundException(long id) {
    super("User not found: id=" + id);
  }
}
