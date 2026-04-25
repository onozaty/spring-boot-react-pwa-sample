package com.github.onozaty.sample.service;

public class TodoNotFoundException extends RuntimeException {

  public TodoNotFoundException(long id) {
    super("Todo not found: id=" + id);
  }
}
