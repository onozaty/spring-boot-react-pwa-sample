package com.github.onozaty.sample.service;

import static org.assertj.core.api.Assertions.*;

import com.github.onozaty.sample.AppTest;
import com.github.onozaty.sample.domain.Todo;
import com.github.onozaty.sample.domain.TodoCreateInput;
import com.github.onozaty.sample.domain.TodoUpdateInput;
import com.github.onozaty.sample.domain.UserCreateInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@AppTest
class TodoServiceTest {

  @Autowired private TodoService todoService;
  @Autowired private UserService userService;

  private Long userId;

  @BeforeEach
  void setUp() {
    var input = new UserCreateInput();
    input.setName("Test User");
    input.setEmail("testuser@example.com");
    input.setPassword("password123");
    userId = userService.create(input).getId();
  }

  @Test
  void testCreateAndFindAll() {
    // Arrange
    var input = createInput("買い物をする");

    // Act
    Todo created = todoService.create(userId, input);
    var todos = todoService.findAll(userId);

    // Assert
    assertThat(created.getId()).isNotNull();
    assertThat(created.getUserId()).isEqualTo(userId);
    assertThat(created.getText()).isEqualTo("買い物をする");
    assertThat(created.isDone()).isFalse();
    assertThat(created.getCreatedAt()).isNotNull();
    assertThat(created.getUpdatedAt()).isNotNull();
    assertThat(todos).hasSize(1);
    assertThat(todos.get(0).getId()).isEqualTo(created.getId());
  }

  @Test
  void testFindAllEmpty() {
    // Act
    var todos = todoService.findAll(userId);

    // Assert
    assertThat(todos).isEmpty();
  }

  @Test
  void testFindAllMultiple() {
    // Arrange
    todoService.create(userId, createInput("TODO 1"));
    todoService.create(userId, createInput("TODO 2"));
    todoService.create(userId, createInput("TODO 3"));

    // Act
    var todos = todoService.findAll(userId);

    // Assert
    assertThat(todos).hasSize(3);
    assertThat(todos).extracting(Todo::getText).containsExactly("TODO 1", "TODO 2", "TODO 3");
  }

  @Test
  void testFindAllIsolatedByUser() {
    // Arrange
    var otherInput = new UserCreateInput();
    otherInput.setName("Other User");
    otherInput.setEmail("other@example.com");
    otherInput.setPassword("password123");
    Long otherUserId = userService.create(otherInput).getId();

    todoService.create(userId, createInput("自分のTODO"));
    todoService.create(otherUserId, createInput("他人のTODO"));

    // Act
    var todos = todoService.findAll(userId);

    // Assert — 自分のTODOのみ返る
    assertThat(todos).hasSize(1);
    assertThat(todos.get(0).getText()).isEqualTo("自分のTODO");
  }

  @Test
  void testUpdate() {
    // Arrange
    Todo created = todoService.create(userId, createInput("元のテキスト"));
    var updateInput = updateInput("更新後のテキスト", true);

    // Act
    Todo updated = todoService.update(userId, created.getId(), updateInput);

    // Assert
    assertThat(updated.getId()).isEqualTo(created.getId());
    assertThat(updated.getText()).isEqualTo("更新後のテキスト");
    assertThat(updated.isDone()).isTrue();
    assertThat(updated.getUpdatedAt()).isNotNull();
  }

  @Test
  void testUpdateNotFound() {
    // Act & Assert
    assertThatThrownBy(() -> todoService.update(userId, 999L, updateInput("テキスト", false)))
        .isInstanceOf(TodoNotFoundException.class);
  }

  @Test
  void testUpdateCannotAccessOtherUsersTodo() {
    // Arrange
    var otherInput = new UserCreateInput();
    otherInput.setName("Other User");
    otherInput.setEmail("other@example.com");
    otherInput.setPassword("password123");
    Long otherUserId = userService.create(otherInput).getId();
    Todo other = todoService.create(otherUserId, createInput("他人のTODO"));

    // Act & Assert — 別ユーザーのTODOは更新できない
    assertThatThrownBy(() -> todoService.update(userId, other.getId(), updateInput("改ざん", true)))
        .isInstanceOf(TodoNotFoundException.class);
  }

  @Test
  void testDelete() {
    // Arrange
    Todo created = todoService.create(userId, createInput("削除するTODO"));

    // Act
    todoService.delete(userId, created.getId());

    // Assert
    assertThat(todoService.findAll(userId)).isEmpty();
  }

  @Test
  void testDeleteNotFound() {
    // Act & Assert
    assertThatThrownBy(() -> todoService.delete(userId, 999L))
        .isInstanceOf(TodoNotFoundException.class);
  }

  @Test
  void testDeleteCannotAccessOtherUsersTodo() {
    // Arrange
    var otherInput = new UserCreateInput();
    otherInput.setName("Other User");
    otherInput.setEmail("other@example.com");
    otherInput.setPassword("password123");
    Long otherUserId = userService.create(otherInput).getId();
    Todo other = todoService.create(otherUserId, createInput("他人のTODO"));

    // Act & Assert — 別ユーザーのTODOは削除できない
    assertThatThrownBy(() -> todoService.delete(userId, other.getId()))
        .isInstanceOf(TodoNotFoundException.class);

    // 他人のTODOは残っている
    assertThat(todoService.findAll(otherUserId)).hasSize(1);
  }

  private static TodoCreateInput createInput(String text) {
    var input = new TodoCreateInput();
    input.setText(text);
    return input;
  }

  private static TodoUpdateInput updateInput(String text, boolean done) {
    var input = new TodoUpdateInput();
    input.setText(text);
    input.setDone(done);
    return input;
  }
}
