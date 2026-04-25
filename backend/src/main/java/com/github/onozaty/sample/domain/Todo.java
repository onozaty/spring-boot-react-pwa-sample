package com.github.onozaty.sample.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "TODO")
public class Todo {

  @Schema(description = "TODO ID", requiredMode = Schema.RequiredMode.REQUIRED)
  private long id;

  @Schema(description = "ユーザーID", requiredMode = Schema.RequiredMode.REQUIRED)
  private long userId;

  @Schema(description = "テキスト", requiredMode = Schema.RequiredMode.REQUIRED)
  private String text;

  @Schema(description = "完了フラグ", requiredMode = Schema.RequiredMode.REQUIRED)
  private boolean done;

  @Schema(description = "作成日時", requiredMode = Schema.RequiredMode.REQUIRED)
  private OffsetDateTime createdAt;

  @Schema(description = "更新日時", requiredMode = Schema.RequiredMode.REQUIRED)
  private OffsetDateTime updatedAt;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getUserId() {
    return userId;
  }

  public void setUserId(long userId) {
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
