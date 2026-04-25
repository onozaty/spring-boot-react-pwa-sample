package com.github.onozaty.sample.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "TODO")
public class Todo {

  @Schema(description = "TODO ID", requiredMode = Schema.RequiredMode.REQUIRED)
  private Long id;

  @Schema(description = "ユーザーID", requiredMode = Schema.RequiredMode.REQUIRED)
  private Long userId;

  @Schema(description = "テキスト", requiredMode = Schema.RequiredMode.REQUIRED)
  private String text;

  @Schema(description = "完了フラグ", requiredMode = Schema.RequiredMode.REQUIRED)
  private boolean done;

  @Schema(description = "作成日時", requiredMode = Schema.RequiredMode.REQUIRED)
  private OffsetDateTime createdAt;

  @Schema(description = "更新日時", requiredMode = Schema.RequiredMode.REQUIRED)
  private OffsetDateTime updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public boolean isDone() {
    return done;
  }

  public void setDone(boolean done) {
    this.done = done;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
