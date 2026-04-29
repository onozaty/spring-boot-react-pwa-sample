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

  @Test
  void testOfflineCreateThenSyncOnReconnectNoDuplicates() {
    // Arrange — オフラインで TODO を3件追加し、オンライン復帰後に重複登録されないことを確認する。
    // useReachabilityEffects を __root.tsx でのみ呼ぶ仕様の回帰テスト。複数箇所で呼ばれて
    // processSyncQueue が多重起動すると、各 op が複数回送信されてサーバーに重複が作られる。

    // オフライン化 (setUp 完了時点で reachable=true 状態)
    page.context().setOffline(true);
    // ヘルスチェックが失敗して reachable=false (オフラインバナー表示) になるまで待つ
    assertThat(page.getByText("オフラインモードです").first()).isVisible();

    // Act 1: オフラインで3件追加
    addTodo("オフラインTODO 1");
    addTodo("オフラインTODO 2");
    addTodo("オフラインTODO 3");

    // この時点ではキューに積まれているだけ (同期待ちバッジ表示)
    assertThat(page.getByText("同期待ち")).hasCount(3);

    // Act 2: オンライン復帰
    page.context().setOffline(false);

    // Assert — 同期完了後、3件のみ存在する (重複していない)
    // 同期待ちバッジが消えるのを待つ
    assertThat(page.getByText("同期待ち")).hasCount(0);
    assertThat(page.getByText("オフラインTODO 1")).hasCount(1);
    assertThat(page.getByText("オフラインTODO 2")).hasCount(1);
    assertThat(page.getByText("オフラインTODO 3")).hasCount(1);
  }

  @Test
  void testOfflineReloadKeepsAppFunctional() {
    // Arrange — オンラインで TODO を作成し、SW がアクティブになるまで一度リロードしておく。
    // SW は初回訪問では fetch を制御しないため、オフラインリロード前にオンラインで
    // 1 度リロードして SW の制御下に入った状態を作る。
    addTodo("リロードしても見えるはずのTODO");
    page.reload();
    assertThat(page.getByText("リロードしても見えるはずのTODO")).isVisible();

    // Act — オフライン化してリロード
    page.context().setOffline(true);
    page.reload();

    // Assert — エラー UI に飛ばず、ヘッダのユーザー名と既存 TODO が表示される
    assertThat(page.getByText("Something went wrong")).not().isVisible();
    assertThat(page.locator("header").getByText("admin")).isVisible();
    assertThat(page.getByText("リロードしても見えるはずのTODO")).isVisible();
  }

  private void addTodo(String text) {
    page.getByPlaceholder("新しいTODOを入力...").fill(text);
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("追加")).click();
    assertThat(page.getByText(text)).isVisible();
  }
}
