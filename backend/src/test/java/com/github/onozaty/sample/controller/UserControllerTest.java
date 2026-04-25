package com.github.onozaty.sample.controller;

import static org.assertj.core.api.Assertions.*;

import com.github.onozaty.sample.AppTest;
import com.github.onozaty.sample.LoginHelper;
import com.github.onozaty.sample.domain.User;
import com.github.onozaty.sample.domain.UserCreateInput;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@AppTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class UserControllerTest {

  private static final ParameterizedTypeReference<Map<String, Object>> PROBLEM_DETAIL_TYPE =
      new ParameterizedTypeReference<>() {};

  @LocalServerPort private int port;

  private RestClient restClient;

  @BeforeEach
  void setUp() {
    // admin ユーザーは V2 migration で挿入済み（email: admin@example.com, password: admin）
    restClient =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultRequest(
                spec ->
                    spec.header("Cookie", LoginHelper.getAuthCookie("http://localhost:" + port)))
            .build();
  }

  @Test
  void testCreate() {
    // Arrange
    var input = userInput("Test User", "test@example.com");

    // Act
    ResponseEntity<User> response =
        restClient
            .post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .body(input)
            .retrieve()
            .toEntity(User.class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    var created = response.getBody();
    assertThat(created.getId()).isNotNull();
    assertThat(created.getName()).isEqualTo("Test User");
    assertThat(created.getEmail()).isEqualTo("test@example.com");
    assertThat(created.getCreatedAt()).isNotNull();
    assertThat(created.getUpdatedAt()).isNotNull();
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().toString())
        .endsWith("/api/users/" + created.getId());
  }

  @Test
  void testFindAll() {
    // Arrange
    createUser("User 1", "user1@example.com");
    createUser("User 2", "user2@example.com");

    // Act
    ResponseEntity<User[]> response =
        restClient.get().uri("/api/users").retrieve().toEntity(User[].class);

    // Assert — admin (migration seed) + User 1 + User 2 の 3 件
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    var users = response.getBody();
    assertThat(users).hasSizeGreaterThanOrEqualTo(2);
    assertThat(users)
        .anyMatch(u -> "User 1".equals(u.getName()) && "user1@example.com".equals(u.getEmail()));
    assertThat(users)
        .anyMatch(u -> "User 2".equals(u.getName()) && "user2@example.com".equals(u.getEmail()));
  }

  @Test
  void testFindById() {
    // Arrange
    var created = createUser("Test User", "test@example.com");

    // Act
    ResponseEntity<User> response =
        restClient.get().uri("/api/users/{id}", created.getId()).retrieve().toEntity(User.class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    var found = response.getBody();
    assertThat(found.getId()).isEqualTo(created.getId());
    assertThat(found.getName()).isEqualTo("Test User");
    assertThat(found.getEmail()).isEqualTo("test@example.com");
  }

  @Test
  void testFindByIdNotFound() {
    // handleUserNotFound はボディ無しで 404 を返す仕様
    // Act & Assert
    ResponseEntity<Void> response =
        restClient
            .get()
            .uri("/api/users/{id}", 999L)
            .retrieve()
            .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
            .toBodilessEntity();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void testUpdate() {
    // Arrange
    var created = createUser("Original", "original@example.com");
    var input = userInput("Updated", "updated@example.com");

    // Act
    ResponseEntity<User> response =
        restClient
            .put()
            .uri("/api/users/{id}", created.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .body(input)
            .retrieve()
            .toEntity(User.class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    var updated = response.getBody();
    assertThat(updated.getId()).isEqualTo(created.getId());
    assertThat(updated.getName()).isEqualTo("Updated");
    assertThat(updated.getEmail()).isEqualTo("updated@example.com");
    assertThat(updated.getUpdatedAt()).isNotNull();
  }

  @Test
  void testUpdateNotFound() {
    // Arrange
    var input = userInput("Test", "test@example.com");

    // Act & Assert
    ResponseEntity<Void> response =
        restClient
            .put()
            .uri("/api/users/{id}", 999L)
            .contentType(MediaType.APPLICATION_JSON)
            .body(input)
            .retrieve()
            .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
            .toBodilessEntity();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void testDelete() {
    // Arrange
    var created = createUser("To Delete", "delete@example.com");

    // Act
    ResponseEntity<Void> deleteResponse =
        restClient.delete().uri("/api/users/{id}", created.getId()).retrieve().toBodilessEntity();

    // Assert
    assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    ResponseEntity<Void> getResponse =
        restClient
            .get()
            .uri("/api/users/{id}", created.getId())
            .retrieve()
            .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
            .toBodilessEntity();
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void testDeleteNotFound() {
    // Act & Assert
    ResponseEntity<Void> response =
        restClient
            .delete()
            .uri("/api/users/{id}", 999L)
            .retrieve()
            .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
            .toBodilessEntity();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void testCreateWithBlankName() {
    // Arrange
    var input = userInput("", "test@example.com");

    // Act
    ResponseEntity<Map<String, Object>> response = postExpectingError("/api/users", input);

    // Assert
    assertValidationProblem(response, "/api/users");
    assertThat(fieldErrors(response))
        .anyMatch(e -> "name".equals(e.get("field")) && hasNonBlankMessage(e));
  }

  @Test
  void testCreateWithInvalidEmail() {
    // Arrange
    var input = userInput("Test User", "not-an-email");

    // Act
    ResponseEntity<Map<String, Object>> response = postExpectingError("/api/users", input);

    // Assert
    assertValidationProblem(response, "/api/users");
    assertThat(fieldErrors(response))
        .anyMatch(e -> "email".equals(e.get("field")) && hasNonBlankMessage(e));
  }

  @Test
  void testCreateWithMultipleValidationErrors() {
    // Arrange — name が空 + email が不正形式
    var input = userInput("", "not-an-email");

    // Act
    ResponseEntity<Map<String, Object>> response = postExpectingError("/api/users", input);

    // Assert
    assertValidationProblem(response, "/api/users");
    var errors = fieldErrors(response);
    assertThat(errors).anyMatch(e -> "name".equals(e.get("field")) && hasNonBlankMessage(e));
    assertThat(errors).anyMatch(e -> "email".equals(e.get("field")) && hasNonBlankMessage(e));
  }

  @Test
  void testUpdateWithBlankName() {
    // Arrange
    var created = createUser("Original", "original@example.com");
    var input = userInput("", "updated@example.com");

    // Act
    ResponseEntity<Map<String, Object>> response =
        putExpectingError("/api/users/{id}", input, created.getId());

    // Assert
    assertValidationProblem(response, "/api/users/" + created.getId());
    assertThat(fieldErrors(response))
        .anyMatch(e -> "name".equals(e.get("field")) && hasNonBlankMessage(e));
  }

  @Test
  void testUpdateWithInvalidEmail() {
    // Arrange
    var created = createUser("Original", "original@example.com");
    var input = userInput("Updated", "not-an-email");

    // Act
    ResponseEntity<Map<String, Object>> response =
        putExpectingError("/api/users/{id}", input, created.getId());

    // Assert
    assertValidationProblem(response, "/api/users/" + created.getId());
    assertThat(fieldErrors(response))
        .anyMatch(e -> "email".equals(e.get("field")) && hasNonBlankMessage(e));
  }

  @Test
  void testCreateWithDuplicateEmail() {
    // Arrange
    createUser("User 1", "duplicate@example.com");
    var input = userInput("User 2", "duplicate@example.com");

    // Act
    ResponseEntity<ProblemDetail> response =
        restClient
            .post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .body(input)
            .retrieve()
            .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
            .toEntity(ProblemDetail.class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    var problem = response.getBody();
    assertThat(problem.getTitle()).isEqualTo("Conflict");
    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getDetail()).isEqualTo("データの整合性制約に違反しています。");
  }

  @Test
  void testUpdateWithDuplicateEmail() {
    // Arrange
    createUser("User 1", "existing@example.com");
    var created = createUser("User 2", "original@example.com");
    var input = userInput("User 2", "existing@example.com");

    // Act
    ResponseEntity<ProblemDetail> response =
        restClient
            .put()
            .uri("/api/users/{id}", created.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .body(input)
            .retrieve()
            .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
            .toEntity(ProblemDetail.class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    var problem = response.getBody();
    assertThat(problem.getTitle()).isEqualTo("Conflict");
    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getDetail()).isEqualTo("データの整合性制約に違反しています。");
  }

  private static UserCreateInput userInput(String name, String email) {
    var input = new UserCreateInput();
    input.setName(name);
    input.setEmail(email);
    input.setPassword("password123");
    return input;
  }

  private User createUser(String name, String email) {
    return restClient
        .post()
        .uri("/api/users")
        .contentType(MediaType.APPLICATION_JSON)
        .body(userInput(name, email))
        .retrieve()
        .body(User.class);
  }

  private ResponseEntity<Map<String, Object>> postExpectingError(
      String uri, UserCreateInput input) {
    return restClient
        .post()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .body(input)
        .retrieve()
        .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
        .toEntity(PROBLEM_DETAIL_TYPE);
  }

  private ResponseEntity<Map<String, Object>> putExpectingError(
      String uri, UserCreateInput input, Object... uriVars) {
    return restClient
        .put()
        .uri(uri, uriVars)
        .contentType(MediaType.APPLICATION_JSON)
        .body(input)
        .retrieve()
        .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
        .toEntity(PROBLEM_DETAIL_TYPE);
  }

  private static void assertValidationProblem(
      ResponseEntity<Map<String, Object>> response, String expectedInstance) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    var body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.get("title")).isEqualTo("Bad Request");
    assertThat(body.get("status")).isEqualTo(400);
    assertThat(body.get("instance")).isEqualTo(expectedInstance);
  }

  private static boolean hasNonBlankMessage(Map<String, String> error) {
    var message = error.get("message");
    return message != null && !message.isBlank();
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, String>> fieldErrors(ResponseEntity<Map<String, Object>> response) {
    return (List<Map<String, String>>) response.getBody().get("errors");
  }
}
