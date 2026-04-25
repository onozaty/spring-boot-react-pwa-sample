package com.github.onozaty.sample.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.github.onozaty.sample.AppTest;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

@AppTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class TokenRefreshE2ETest {

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
    assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("ホーム")))
        .isVisible();
  }

  @AfterEach
  void closePage() {
    page.close();
  }

  @Test
  void testTransparentRefreshOnExpiredToken() {
    // Arrange — /api/todos への最初のリクエストだけ 401 を返し、AT 期限切れをシミュレート。
    // フロントエンドの api-client.ts が 401 を受けて /api/auth/refresh を呼び出し、
    // 新しい AT を取得してから元リクエストをリトライするはず。
    AtomicInteger todoCallCount = new AtomicInteger(0);
    AtomicInteger refreshCallCount = new AtomicInteger(0);

    page.route(
        "**/api/todos",
        route -> {
          if (todoCallCount.getAndIncrement() == 0) {
            // 1回目だけ 401 を返す（AT 期限切れを模倣）
            route.fulfill(new Route.FulfillOptions().setStatus(401));
          } else {
            // 2回目以降は実際のサーバーに転送
            route.resume();
          }
        });

    page.route(
        "**/api/auth/refresh",
        route -> {
          refreshCallCount.incrementAndGet();
          route.resume();
        });

    // Act — TODOページに遷移（GET /api/todos が発行される）
    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("TODO")).click();

    // Assert — リフレッシュが1回走り、TODOページが正常に表示される
    assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("TODO")))
        .isVisible();
    // ローディングが終わり、入力欄が表示される（リトライ成功でTODOページが機能している）
    assertThat(page.getByPlaceholder("新しいTODOを入力...")).isVisible();

    Assertions.assertThat(refreshCallCount.get()).as("/api/auth/refresh が1回呼ばれること").isEqualTo(1);
    Assertions.assertThat(todoCallCount.get())
        .as("/api/todos が2回呼ばれること（1回目:401、2回目:リトライ）")
        .isEqualTo(2);
  }

  @Test
  void testRedirectToLoginWhenRefreshFails() {
    // Arrange — /api/todos と /api/auth/refresh の両方に 401 を返す。
    // RT も期限切れの状況を模倣。フロントエンドはログインページにリダイレクトするはず。
    page.route("**/api/todos", route -> route.fulfill(new Route.FulfillOptions().setStatus(401)));
    page.route(
        "**/api/auth/refresh", route -> route.fulfill(new Route.FulfillOptions().setStatus(401)));

    // Act — TODOページに遷移
    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("TODO")).click();

    // Assert — ログインページにリダイレクトされる
    assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("ログイン")))
        .isVisible();
  }

  @Test
  void testConcurrentRequestsRefreshOnce() {
    // Arrange — /api/todos と /api/auth/me が同時に 401 を返したとき、
    // /api/auth/refresh が1回だけ呼ばれることを確認（二重リフレッシュ防止）。
    // キャッシュの影響を受けないよう新しいページで実行する。
    var freshPage = browser.newPage();
    try {
      AtomicInteger refreshCallCount = new AtomicInteger(0);
      AtomicInteger todosCallCount = new AtomicInteger(0);
      AtomicInteger meCallCount = new AtomicInteger(0);

      freshPage.route(
          "**/api/todos",
          route -> {
            if (todosCallCount.getAndIncrement() == 0) {
              route.fulfill(new Route.FulfillOptions().setStatus(401));
            } else {
              route.resume();
            }
          });

      freshPage.route(
          "**/api/auth/me",
          route -> {
            if (meCallCount.getAndIncrement() == 0) {
              route.fulfill(new Route.FulfillOptions().setStatus(401));
            } else {
              route.resume();
            }
          });

      freshPage.route(
          "**/api/auth/refresh",
          route -> {
            refreshCallCount.incrementAndGet();
            route.resume();
          });

      // Act — ログインしてトップページからTODOページへ遷移
      // （/api/auth/me と /api/todos が並行して 401 を返す）
      freshPage.navigate("http://localhost:" + port + "/login");
      freshPage.getByLabel("メールアドレス").fill("admin@example.com");
      freshPage.getByLabel("パスワード").fill("admin");
      // ログイン POST は route の対象外なので通常通り通る
      freshPage.route(
          "**/api/auth/login",
          route -> {
            meCallCount.set(0); // ログイン後の /api/auth/me を再びインターセプト対象にリセット
            route.resume();
          });
      freshPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("ログイン")).click();
      assertThat(freshPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("ホーム")))
          .isVisible();
      freshPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("TODO")).click();
      assertThat(freshPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("TODO")))
          .isVisible();
      assertThat(freshPage.getByPlaceholder("新しいTODOを入力...")).isVisible();

      // Assert — リフレッシュは高々1回（二重呼び出しがない）
      Assertions.assertThat(refreshCallCount.get())
          .as("/api/auth/refresh の呼び出しが1回以下であること（二重リフレッシュ防止）")
          .isLessThanOrEqualTo(1);
    } finally {
      freshPage.close();
    }
  }
}
