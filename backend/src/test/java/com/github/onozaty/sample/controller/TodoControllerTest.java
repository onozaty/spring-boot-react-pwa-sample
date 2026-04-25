package com.github.onozaty.sample.controller;

import static org.assertj.core.api.Assertions.*;

import com.github.onozaty.sample.AppTest;
import com.github.onozaty.sample.LoginHelper;
import com.github.onozaty.sample.domain.Todo;
import com.github.onozaty.sample.domain.TodoCreateInput;
import com.github.onozaty.sample.domain.TodoUpdateInput;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@AppTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class TodoControllerTest {

  private static final ParameterizedTypeReference<Map<String, Object>> PROBLEM_DETAIL_TYPE =
      new ParameterizedTypeReference<>() {};

  @LocalServerPort private int port;

  private RestClient restClient;

  @BeforeEach
  void setUp() {
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
    var input = createInput("買い物をする");

    // Act
    ResponseEntity<Todo> response =
        restClient
            .post()
            .uri("/api/todos")
            .contentType(MediaType.APPLICATION_JSON)
            .body(input)
            .retrieve()
            .toEntity(Todo.class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    var created = response.getBody();
    assertThat(created.getId()).isNotNull();
    assertThat(created.getText()).isEqualTo("買い物をする");
    assertThat(created.isDone()).isFalse();
    assertThat(created.getCreatedAt()).isNotNull();
    assertThat(created.getUpdatedAt()).isNotNull();
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().toString())
        .endsWith("/api/todos/" + created.getId());
  }

  @Test
  void testFindAll() {
    // Arrange
    createTodo("TODO 1");
    createTodo("TODO 2");

    // Act
    ResponseEntity<Todo[]> response =
        restClient.get().uri("/api/todos").retrieve().toEntity(Todo[].class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    var todos = response.getBody();
    assertThat(todos).hasSize(2);
    assertThat(todos[0].getText()).isEqualTo("TODO 1");
    assertThat(todos[1].getText()).isEqualTo("TODO 2");
  }

  @Test
  void testFindAllEmpty() {
    // Act
    ResponseEntity<Todo[]> response =
        restClient.get().uri("/api/todos").retrieve().toEntity(Todo[].class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEmpty();
  }

  @Test
  void testUpdate() {
    // Arrange
    var created = createTodo("元のテキスト");
    var input = updateInput("更新後のテキスト", true);

    // Act
    ResponseEntity<Todo> response =
        restClient
            .put()
            .uri("/api/todos/{id}", created.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .body(input)
            .retrieve()
            .toEntity(Todo.class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    var updated = response.getBody();
    assertThat(updated.getId()).isEqualTo(created.getId());
    assertThat(updated.getText()).isEqualTo("更新後のテキスト");
    assertThat(updated.isDone()).isTrue();
    assertThat(updated.getUpdatedAt()).isNotNull();
  }

  @Test
  void testUpdateNotFound() {
    // Arrange
    var input = updateInput("テキスト", false);

    // Act & Assert
    ResponseEntity<Void> response =
        restClient
            .put()
            .uri("/api/todos/{id}", 999L)
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
    var created = createTodo("削除するTODO");

    // Act
    ResponseEntity<Void> deleteResponse =
        restClient.delete().uri("/api/todos/{id}", created.getId()).retrieve().toBodilessEntity();

    // Assert
    assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // 削除後は一覧に存在しない
    var todos = restClient.get().uri("/api/todos").retrieve().body(Todo[].class);
    assertThat(todos).isEmpty();
  }

  @Test
  void testDeleteNotFound() {
    // Act & Assert
    ResponseEntity<Void> response =
        restClient
            .delete()
            .uri("/api/todos/{id}", 999L)
            .retrieve()
            .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
            .toBodilessEntity();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void testCannotAccessOtherUsersTodos() {
    // Arrange — admin でTODOを作成
    var created = createTodo("adminのTODO");

    // 別ユーザーを作成してそのユーザーでログイン
    var otherUserClient = createOtherUserClient("other@example.com");

    // Act — 別ユーザーで更新を試みる
    var updateInput = updateInput("改ざん", true);
    ResponseEntity<Void> updateResponse =
        otherUserClient
            .put()
            .uri("/api/todos/{id}", created.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .body(updateInput)
            .retrieve()
            .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
            .toBodilessEntity();

    assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    // Act — 別ユーザーで削除を試みる
    ResponseEntity<Void> deleteResponse =
        otherUserClient
            .delete()
            .uri("/api/todos/{id}", created.getId())
            .retrieve()
            .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
            .toBodilessEntity();

    assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    // admin のTODOはそのまま残っている
    var todos = restClient.get().uri("/api/todos").retrieve().body(Todo[].class);
    assertThat(todos).hasSize(1);
    assertThat(todos[0].getText()).isEqualTo("adminのTODO");
  }

  @Test
  void testOtherUserTodosNotVisible() {
    // Arrange — admin でTODOを作成
    createTodo("adminのTODO");

    // 別ユーザーを作成してそのユーザーでログイン
    var otherUserClient = createOtherUserClient("other2@example.com");
    // 別ユーザーでもTODOを作成
    otherUserClient
        .post()
        .uri("/api/todos")
        .contentType(MediaType.APPLICATION_JSON)
        .body(createInput("otherのTODO"))
        .retrieve()
        .body(Todo.class);

    // Act — admin で一覧取得
    var todos = restClient.get().uri("/api/todos").retrieve().body(Todo[].class);

    // Assert — admin 自身のTODOのみ見える
    assertThat(todos).hasSize(1);
    assertThat(todos[0].getText()).isEqualTo("adminのTODO");
  }

  @Test
  void testCreateWithBlankText() {
    // Arrange
    var input = createInput("");

    // Act
    ResponseEntity<Map<String, Object>> response =
        restClient
            .post()
            .uri("/api/todos")
            .contentType(MediaType.APPLICATION_JSON)
            .body(input)
            .retrieve()
            .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
            .toEntity(PROBLEM_DETAIL_TYPE);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void testCreateWithTooLongText() {
    // Arrange — 501文字
    var input = createInput("a".repeat(501));

    // Act
    ResponseEntity<Map<String, Object>> response =
        restClient
            .post()
            .uri("/api/todos")
            .contentType(MediaType.APPLICATION_JSON)
            .body(input)
            .retrieve()
            .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
            .toEntity(PROBLEM_DETAIL_TYPE);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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

  private Todo createTodo(String text) {
    return restClient
        .post()
        .uri("/api/todos")
        .contentType(MediaType.APPLICATION_JSON)
        .body(createInput(text))
        .retrieve()
        .body(Todo.class);
  }

  private RestClient createOtherUserClient(String email) {
    // admin クライアントで別ユーザーを作成
    var userInput = new com.github.onozaty.sample.domain.UserCreateInput();
    userInput.setName("Other User");
    userInput.setEmail(email);
    userInput.setPassword("password123");
    restClient
        .post()
        .uri("/api/users")
        .contentType(MediaType.APPLICATION_JSON)
        .body(userInput)
        .retrieve()
        .toBodilessEntity();

    // 別ユーザーとしてログイン
    var loginResponse =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .build()
            .post()
            .uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"email\":\"" + email + "\",\"password\":\"password123\"}")
            .retrieve()
            .toBodilessEntity();

    String cookie =
        loginResponse.getHeaders().get("Set-Cookie").stream()
            .filter(
                v ->
                    v.startsWith(
                        com.github.onozaty.sample.service.JwtTokenService.ACCESS_TOKEN_COOKIE_NAME
                            + "="))
            .map(v -> v.split(";")[0])
            .findFirst()
            .orElseThrow();

    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultRequest(spec -> spec.header("Cookie", cookie))
        .build();
  }
}
