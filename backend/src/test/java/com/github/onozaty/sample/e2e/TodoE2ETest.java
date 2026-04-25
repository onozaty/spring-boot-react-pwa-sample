package com.github.onozaty.sample.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.github.onozaty.sample.AppTest;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

@AppTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class TodoE2ETest {

  @LocalServerPort private int port;

  private static Playwright playwright;
  private static Browser browser;
  private Page page;

  @BeforeAll
  static void launchBrowser() {
    playwright = Playwright.create();
    boolean headless = !"false".equalsIgnoreCase(System.getenv("HEADLESS"));
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
  }

  @AfterAll
  static void closeBrowser() {
    browser.close();
    playwright.close();
  }

  @BeforeEach
  void setUp() {
    page = browser.newPage();
    page.navigate("http://localhost:" + port + "/login");
    page.getByLabel("メールアドレス").fill("admin@example.com");
    page.getByLabel("パスワード").fill("admin");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("ログイン")).click();
    // ログイン後トップページに遷移するまで待機
    assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("ホーム")))
        .isVisible();
    // TODOページに移動
    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("TODO")).click();
    assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("TODO")))
        .isVisible();
  }

  @AfterEach
  void closePage() {
    page.close();
  }

  @Test
  void testEmptyTodoListMessage() {
    // Assert — 初期状態では「TODOがありません」が表示される
    assertThat(page.getByText("TODOがありません")).isVisible();
  }

  @Test
  void testAddButtonDisabledWhenEmpty() {
    // Assert — 入力が空のときは追加ボタンが無効
    assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("追加")))
        .isDisabled();
  }

  @Test
  void testCreateTodo() {
    // Arrange
    page.getByPlaceholder("新しいTODOを入力...").fill("買い物をする");

    // Act
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("追加")).click();

    // Assert
    assertThat(page.getByText("買い物をする")).isVisible();
    assertThat(page.getByPlaceholder("新しいTODOを入力...")).hasValue("");
  }

  @Test
  void testCreateMultipleTodos() {
    // Arrange & Act
    addTodo("タスク1");
    addTodo("タスク2");
    addTodo("タスク3");

    // Assert
    assertThat(page.getByText("タスク1")).isVisible();
    assertThat(page.getByText("タスク2")).isVisible();
    assertThat(page.getByText("タスク3")).isVisible();
  }

  @Test
  void testToggleTodoDone() {
    // Arrange
    addTodo("完了するTODO");
    Locator item = page.locator("li").filter(new Locator.FilterOptions().setHasText("完了するTODO"));
    Locator checkbox = item.locator("input[type=checkbox]");

    // Act — チェックボックスをクリックして完了にする
    checkbox.click();

    // Assert
    assertThat(checkbox).isChecked();
  }

  @Test
  void testToggleTodoDoneAndUndone() {
    // Arrange
    addTodo("切り替えるTODO");
    Locator item = page.locator("li").filter(new Locator.FilterOptions().setHasText("切り替えるTODO"));
    Locator checkbox = item.locator("input[type=checkbox]");

    // Act — 完了にする
    checkbox.click();
    assertThat(checkbox).isChecked();

    // Act — 未完了に戻す
    checkbox.click();

    // Assert
    assertThat(checkbox).not().isChecked();
  }

  @Test
  void testDeleteTodo() {
    // Arrange
    addTodo("削除するTODO");

    // Act
    page.locator("li")
        .filter(new Locator.FilterOptions().setHasText("削除するTODO"))
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("削除"))
        .click();

    // Assert
    assertThat(page.getByText("削除するTODO")).not().isVisible();
    assertThat(page.getByText("TODOがありません")).isVisible();
  }

  @Test
  void testDeleteOneTodoOfMany() {
    // Arrange
    addTodo("残すTODO");
    addTodo("削除するTODO");

    // Act
    page.locator("li")
        .filter(new Locator.FilterOptions().setHasText("削除するTODO"))
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("削除"))
        .click();

    // Assert
    assertThat(page.getByText("削除するTODO")).not().isVisible();
    assertThat(page.getByText("残すTODO")).isVisible();
  }

  private void addTodo(String text) {
    page.getByPlaceholder("新しいTODOを入力...").fill(text);
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("追加")).click();
    assertThat(page.getByText(text)).isVisible();
  }
}
