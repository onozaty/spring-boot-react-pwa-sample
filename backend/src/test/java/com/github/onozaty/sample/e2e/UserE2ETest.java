package com.github.onozaty.sample.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.github.onozaty.sample.AppTest;
import com.github.onozaty.sample.domain.User;
import com.github.onozaty.sample.domain.UserCreateInput;
import com.github.onozaty.sample.mapper.UserCredentialMapper;
import com.github.onozaty.sample.mapper.UserMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

@AppTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class UserE2ETest {

  @LocalServerPort private int port;

  @Autowired private UserMapper userMapper;
  @Autowired private UserCredentialMapper credentialMapper;
  @Autowired private PasswordEncoder passwordEncoder;

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
    // ログインページに移動してログイン
    page.navigate("http://localhost:" + port + "/login");
    page.getByLabel("メールアドレス").fill("admin@example.com");
    page.getByLabel("パスワード").fill("admin");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("ログイン")).click();
    // ログイン後トップページに遷移するまで待機
    assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("ホーム")))
        .isVisible();
  }

  @AfterEach
  void closePage() {
    page.close();
  }

  @Test
  void testNavigateToUsers() {
    // Arrange
    // (トップページに既にナビゲート済み)

    // Act
    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("ユーザー管理")).click();

    // Assert
    assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("ユーザー管理")))
        .isVisible();
  }

  @Test
  void testUserListDisplay() {
    // Arrange
    createUser("山田太郎", "yamada@example.com");
    createUser("鈴木花子", "suzuki@example.com");

    // Act
    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("ユーザー管理")).click();

    // Assert — admin は常に存在するが、追加した 2 ユーザーが表示される
    assertThat(page.getByRole(AriaRole.CELL, new Page.GetByRoleOptions().setName("山田太郎")))
        .isVisible();
    assertThat(page.getByRole(AriaRole.CELL, new Page.GetByRoleOptions().setName("鈴木花子")))
        .isVisible();
    assertThat(
            page.getByRole(
                AriaRole.CELL, new Page.GetByRoleOptions().setName("yamada@example.com")))
        .isVisible();
  }

  @Test
  void testCreateUser() {
    // Arrange
    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("ユーザー管理")).click();
    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("新規作成")).click();

    // Act
    page.getByLabel("名前").fill("テストユーザー");
    page.getByLabel("メールアドレス").fill("test@example.com");
    page.getByLabel("パスワード").fill("password123");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("作成")).click();

    // Assert
    assertThat(page.getByText("ユーザーを作成しました。")).isVisible();
    assertThat(page.getByRole(AriaRole.CELL, new Page.GetByRoleOptions().setName("テストユーザー")))
        .isVisible();
    var users = userMapper.findAll();
    // admin + テストユーザー の 2 件
    Assertions.assertThat(users).hasSize(2);
    var testUser =
        users.stream().filter(u -> "テストユーザー".equals(u.getName())).findFirst().orElseThrow();
    Assertions.assertThat(testUser.getName()).isEqualTo("テストユーザー");
    Assertions.assertThat(testUser.getEmail()).isEqualTo("test@example.com");
  }

  @Test
  void testCreateUserDuplicateEmail() {
    // Arrange
    createUser("既存ユーザー", "duplicate@example.com");
    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("ユーザー管理")).click();
    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("新規作成")).click();

    // Act
    page.getByLabel("名前").fill("別のユーザー");
    page.getByLabel("メールアドレス").fill("duplicate@example.com");
    page.getByLabel("パスワード").fill("password123");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("作成")).click();

    // Assert
    assertThat(page.getByText("このメールアドレスはすでに使用されています。")).isVisible();
  }

  @Test
  void testEditUserFormPrefilled() {
    // Arrange
    createUser("編集確認ユーザー", "prefill@example.com");
    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("ユーザー管理")).click();
    Locator row =
        page.getByRole(
            AriaRole.ROW, new Page.GetByRoleOptions().setName(Pattern.compile(".*編集確認ユーザー.*")));

    // Act
    row.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("編集")).click();

    // Assert
    assertThat(page.getByLabel("名前")).hasValue("編集確認ユーザー");
    assertThat(page.getByLabel("メールアドレス")).hasValue("prefill@example.com");
    assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("更新")))
        .isVisible();
  }

  @Test
  void testEditUser() {
    // Arrange
    createUser("編集前ユーザー", "before@example.com");
    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("ユーザー管理")).click();
    Locator row =
        page.getByRole(
            AriaRole.ROW, new Page.GetByRoleOptions().setName(Pattern.compile(".*編集前ユーザー.*")));
    row.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("編集")).click();

    // Act
    page.getByLabel("名前").fill("編集後ユーザー");
    page.getByLabel("メールアドレス").fill("after@example.com");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("更新")).click();

    // Assert
    assertThat(page.getByText("ユーザーを更新しました。")).isVisible();
    assertThat(page.getByRole(AriaRole.CELL, new Page.GetByRoleOptions().setName("編集後ユーザー")))
        .isVisible();
    assertThat(
            page.getByRole(AriaRole.CELL, new Page.GetByRoleOptions().setName("after@example.com")))
        .isVisible();
    var users = userMapper.findAll();
    var editedUser =
        users.stream().filter(u -> "編集後ユーザー".equals(u.getName())).findFirst().orElseThrow();
    Assertions.assertThat(editedUser.getName()).isEqualTo("編集後ユーザー");
    Assertions.assertThat(editedUser.getEmail()).isEqualTo("after@example.com");
  }

  @Test
  void testDeleteUserShowsDialog() {
    // Arrange
    createUser("削除対象ユーザー", "delete-me@example.com");
    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("ユーザー管理")).click();
    Locator row =
        page.getByRole(
            AriaRole.ROW, new Page.GetByRoleOptions().setName(Pattern.compile(".*削除対象ユーザー.*")));

    // Act
    row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("削除")).click();

    // Assert
    assertThat(page.getByRole(AriaRole.ALERTDIALOG)).isVisible();
    assertThat(page.getByText("ユーザーを削除しますか？")).isVisible();
    assertThat(page.getByText("削除対象ユーザー を削除します。この操作は取り消せません。")).isVisible();
  }

  @Test
  void testDeleteUserCancel() {
    // Arrange
    createUser("キャンセルユーザー", "cancel@example.com");
    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("ユーザー管理")).click();
    Locator row =
        page.getByRole(
            AriaRole.ROW, new Page.GetByRoleOptions().setName(Pattern.compile(".*キャンセルユーザー.*")));
    row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("削除")).click();
    assertThat(page.getByRole(AriaRole.ALERTDIALOG)).isVisible();

    // Act
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("キャンセル")).click();

    // Assert
    assertThat(page.getByRole(AriaRole.ALERTDIALOG)).not().isVisible();
    assertThat(page.getByRole(AriaRole.CELL, new Page.GetByRoleOptions().setName("キャンセルユーザー")))
        .isVisible();
    Assertions.assertThat(
            userMapper.findAll().stream().filter(u -> "キャンセルユーザー".equals(u.getName())).count())
        .isEqualTo(1);
  }

  @Test
  void testDeleteUser() {
    // Arrange
    createUser("削除完了ユーザー", "done@example.com");
    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("ユーザー管理")).click();
    Locator row =
        page.getByRole(
            AriaRole.ROW, new Page.GetByRoleOptions().setName(Pattern.compile(".*削除完了ユーザー.*")));
    row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("削除")).click();
    assertThat(page.getByRole(AriaRole.ALERTDIALOG)).isVisible();

    // Act
    page.getByRole(AriaRole.ALERTDIALOG)
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("削除"))
        .click();

    // Assert
    assertThat(page.getByText("ユーザーを削除しました。")).isVisible();
    assertThat(page.getByRole(AriaRole.CELL, new Page.GetByRoleOptions().setName("削除完了ユーザー")))
        .not()
        .isVisible();
    Assertions.assertThat(
            userMapper.findAll().stream().filter(u -> "削除完了ユーザー".equals(u.getName())).count())
        .isEqualTo(0);
  }

  @Test
  void testUnauthenticatedRedirectsToLogin() {
    // Arrange — 新規ページ（Cookie なし）で /users にアクセス
    var newPage = browser.newPage();

    // Act
    newPage.navigate("http://localhost:" + port + "/users");

    // Assert
    assertThat(newPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("ログイン")))
        .isVisible();
    newPage.close();
  }

  @Test
  void testChangePassword() {
    // Arrange
    page.navigate("http://localhost:" + port + "/users");

    // Act — ヘッダーからパスワード変更画面に遷移
    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("パスワード変更")).click();
    page.getByLabel("現在のパスワード").fill("admin");
    page.getByLabel("新しいパスワード（8文字以上）").fill("newpassword123");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("変更する")).click();

    // Assert
    assertThat(page.getByText("パスワードを変更しました。")).isVisible();
  }

  @Test
  void testLogout() {
    // Arrange
    page.navigate("http://localhost:" + port + "/users");

    // Act
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("ログアウト")).click();

    // Assert — ログインページに戻る
    assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("ログイン")))
        .isVisible();
  }

  private User createUser(String name, String email) {
    var input = new UserCreateInput();
    input.setName(name);
    input.setEmail(email);
    User created = userMapper.insert(input);
    credentialMapper.insert(created.getId(), passwordEncoder.encode("password123"));
    return created;
  }
}
