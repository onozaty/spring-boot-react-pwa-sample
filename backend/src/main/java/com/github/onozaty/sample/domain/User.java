package com.github.onozaty.sample.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "ユーザー")
public class User {

  @Schema(description = "ユーザーID", requiredMode = Schema.RequiredMode.REQUIRED)
  private long id;

  @Schema(description = "名前", requiredMode = Schema.RequiredMode.REQUIRED, example = "山田太郎")
  private String name;

  @Schema(
      description = "メールアドレス",
      requiredMode = Schema.RequiredMode.REQUIRED,
      example = "yamada@example.com")
  private String email;

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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
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
