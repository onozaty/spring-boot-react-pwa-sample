package com.github.onozaty.sample.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "TODO作成入力")
public class TodoCreateInput {

  @NotBlank
  @Size(max = 500)
  @Schema(description = "テキスト", requiredMode = Schema.RequiredMode.REQUIRED)
  private String text;

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }
}
