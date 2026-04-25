package com.github.onozaty.sample.controller;

import com.github.onozaty.sample.domain.User;
import com.github.onozaty.sample.domain.UserCreateInput;
import com.github.onozaty.sample.domain.UserUpdateInput;
import com.github.onozaty.sample.service.UserNotFoundException;
import com.github.onozaty.sample.service.UserService;
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
@RequestMapping("/api/users")
@Tag(name = "Users", description = "ユーザー管理API")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  @Operation(summary = "ユーザー一覧取得", description = "登録されている全ユーザーを取得します")
  @ApiResponse(responseCode = "200", description = "取得成功")
  public List<User> findAll() {
    return userService.findAll();
  }

  @GetMapping("/{id}")
  @Operation(summary = "ユーザー取得", description = "指定したIDのユーザーを取得します")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "取得成功"),
    @ApiResponse(responseCode = "404", description = "ユーザーが存在しない")
  })
  public ResponseEntity<User> findById(
      @Parameter(description = "ユーザーID", required = true) @PathVariable Long id) {
    return ResponseEntity.ok(
        userService.findById(id).orElseThrow(() -> new UserNotFoundException(id)));
  }

  @PostMapping
  @Operation(summary = "ユーザー作成", description = "新しいユーザーを登録します")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "作成成功"),
    @ApiResponse(
        responseCode = "400",
        description = "バリデーションエラー",
        content = @Content(schema = @Schema(implementation = ValidationProblemDetail.class))),
    @ApiResponse(responseCode = "409", description = "データの整合性制約違反")
  })
  public ResponseEntity<User> create(@Valid @RequestBody UserCreateInput input) {
    User created = userService.create(input);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.getId())
            .toUri();
    return ResponseEntity.created(location).body(created);
  }

  @PutMapping("/{id}")
  @Operation(summary = "ユーザー更新", description = "指定したIDのユーザー情報を更新します")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "更新成功"),
    @ApiResponse(
        responseCode = "400",
        description = "バリデーションエラー",
        content = @Content(schema = @Schema(implementation = ValidationProblemDetail.class))),
    @ApiResponse(responseCode = "404", description = "ユーザーが存在しない"),
    @ApiResponse(responseCode = "409", description = "データの整合性制約違反")
  })
  public ResponseEntity<User> update(
      @Parameter(description = "ユーザーID", required = true) @PathVariable Long id,
      @Valid @RequestBody UserUpdateInput input) {
    return ResponseEntity.ok(
        userService.update(id, input).orElseThrow(() -> new UserNotFoundException(id)));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "ユーザー削除", description = "指定したIDのユーザーを削除します")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "削除成功"),
    @ApiResponse(responseCode = "404", description = "ユーザーが存在しない")
  })
  public ResponseEntity<Void> delete(
      @Parameter(description = "ユーザーID", required = true) @PathVariable Long id) {
    if (!userService.delete(id)) {
      throw new UserNotFoundException(id);
    }
    return ResponseEntity.noContent().build();
  }
}
