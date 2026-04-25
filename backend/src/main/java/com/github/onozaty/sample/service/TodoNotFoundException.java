package com.github.onozaty.sample.service;

public class TodoNotFoundException extends RuntimeException {

  public TodoNotFoundException(Long id) {
    super("Todo not found: id=" + id);
  }
}
