package com.github.onozaty.sample.service;

import static org.assertj.core.api.Assertions.*;

import com.github.onozaty.sample.AppTest;
import com.github.onozaty.sample.domain.User;
import com.github.onozaty.sample.domain.UserCreateInput;
import com.github.onozaty.sample.domain.UserUpdateInput;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@AppTest
class UserServiceTest {

  @Autowired private UserService userService;

  @Test
  void testCreateAndFindById() {
    // Arrange
    UserCreateInput input = new UserCreateInput();
    input.setName("Test User");
    input.setEmail("test@example.com");
    input.setPassword("password123");

    // Act
    User created = userService.create(input);
    User found = userService.findById(created.getId());

    // Assert
    assertThat(created.getId()).isNotNull();
    assertThat(created.getName()).isEqualTo("Test User");
    assertThat(created.getEmail()).isEqualTo("test@example.com");
    assertThat(found.getId()).isEqualTo(created.getId());
    assertThat(found.getName()).isEqualTo("Test User");
  }

  @Test
  void testFindAll() {
    // Arrange
    UserCreateInput input1 = new UserCreateInput();
    input1.setName("User 1");
    input1.setEmail("user1@example.com");
    input1.setPassword("password123");
    userService.create(input1);

    UserCreateInput input2 = new UserCreateInput();
    input2.setName("User 2");
    input2.setEmail("user2@example.com");
    input2.setPassword("password123");
    userService.create(input2);

    // Act
    var users = userService.findAll();

    // Assert — admin (migration seed) + User 1 + User 2 の 3 件
    assertThat(users).hasSizeGreaterThanOrEqualTo(2);
  }

  @Test
  void testUpdate() {
    // Arrange
    UserCreateInput input = new UserCreateInput();
    input.setName("Original Name");
    input.setEmail("original@example.com");
    input.setPassword("password123");
    User created = userService.create(input);

    UserUpdateInput updatedInput = new UserUpdateInput();
    updatedInput.setName("Updated Name");
    updatedInput.setEmail("updated@example.com");

    // Act
    User result = userService.update(created.getId(), updatedInput);

    // Assert
    assertThat(result.getId()).isEqualTo(created.getId());
    assertThat(result.getName()).isEqualTo("Updated Name");
    assertThat(result.getEmail()).isEqualTo("updated@example.com");
  }

  @Test
  void testDelete() {
    // Arrange
    UserCreateInput input = new UserCreateInput();
    input.setName("To Delete");
    input.setEmail("delete@example.com");
    input.setPassword("password123");
    User created = userService.create(input);

    // Act
    userService.delete(created.getId());

    // Assert
    assertThatThrownBy(() -> userService.findById(created.getId()))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void testFindByIdNotFound() {
    // Act & Assert
    assertThatThrownBy(() -> userService.findById(999L)).isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void testUpdateNotFound() {
    // Arrange
    UserUpdateInput input = new UserUpdateInput();
    input.setName("Test");
    input.setEmail("test@example.com");

    // Act & Assert
    assertThatThrownBy(() -> userService.update(999L, input))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void testDeleteNotFound() {
    // Act & Assert
    assertThatThrownBy(() -> userService.delete(999L)).isInstanceOf(UserNotFoundException.class);
  }
}
