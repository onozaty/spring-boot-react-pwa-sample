package com.github.onozaty.sample.controller;

import com.github.onozaty.sample.domain.Todo;
import com.github.onozaty.sample.domain.TodoCreateInput;
import com.github.onozaty.sample.domain.TodoUpdateInput;
import com.github.onozaty.sample.service.JwtTokenService;
import com.github.onozaty.sample.service.TodoNotFoundException;
import com.github.onozaty.sample.service.TodoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/todos")
@Tag(name = "Todos", description = "TODOリストAPI")
public class TodoController {

  private final TodoService todoService;

  public TodoController(TodoService todoService) {
    this.todoService = todoService;
  }

  @GetMapping
  @Operation(summary = "TODO一覧取得", description = "ログインユーザーのTODO一覧を取得します")
  @ApiResponse(responseCode = "200", description = "取得成功")
  public List<Todo> findAll(@AuthenticationPrincipal Jwt jwt) {
    Long userId = jwt.getClaim(JwtTokenService.CLAIM_USER_ID);
    return todoService.findAll(userId);
  }

  @PostMapping
  @Operation(summary = "TODO作成", description = "新しいTODOを作成します")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "作成成功"),
    @ApiResponse(
        responseCode = "400",
        description = "バリデーションエラー",
        content = @Content(schema = @Schema(implementation = ValidationProblemDetail.class)))
  })
  public ResponseEntity<Todo> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TodoCreateInput input) {
    Long userId = jwt.getClaim(JwtTokenService.CLAIM_USER_ID);
    Todo created = todoService.create(userId, input);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.getId())
            .toUri();
    return ResponseEntity.created(location).body(created);
  }

  @PutMapping("/{id}")
  @Operation(summary = "TODO更新", description = "指定したIDのTODOを更新します")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "更新成功"),
    @ApiResponse(
        responseCode = "400",
        description = "バリデーションエラー",
        content = @Content(schema = @Schema(implementation = ValidationProblemDetail.class))),
    @ApiResponse(responseCode = "404", description = "TODOが存在しない")
  })
  public ResponseEntity<Todo> update(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "TODO ID", required = true) @PathVariable Long id,
      @Valid @RequestBody TodoUpdateInput input) {
    Long userId = jwt.getClaim(JwtTokenService.CLAIM_USER_ID);
    Todo updated =
        todoService.update(userId, id, input).orElseThrow(() -> new TodoNotFoundException(id));
    return ResponseEntity.ok(updated);
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "TODO削除", description = "指定したIDのTODOを削除します")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "削除成功"),
    @ApiResponse(responseCode = "404", description = "TODOが存在しない")
  })
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "TODO ID", required = true) @PathVariable Long id) {
    Long userId = jwt.getClaim(JwtTokenService.CLAIM_USER_ID);
    if (!todoService.delete(userId, id)) {
      throw new TodoNotFoundException(id);
    }
    return ResponseEntity.noContent().build();
  }
}
