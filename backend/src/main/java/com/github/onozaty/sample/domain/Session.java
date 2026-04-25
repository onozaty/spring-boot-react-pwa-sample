package com.github.onozaty.sample.domain;

import java.time.OffsetDateTime;

public class Session {

  private String id;
  private Long userId;
  private OffsetDateTime createdAt;
  private OffsetDateTime lastUsedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public OffsetDateTime getLastUsedAt() {
    return lastUsedAt;
  }

  public void setLastUsedAt(OffsetDateTime lastUsedAt) {
    this.lastUsedAt = lastUsedAt;
  }
}
