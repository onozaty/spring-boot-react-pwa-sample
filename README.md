# spring-boot-react-pwa-sample

Spring Boot と React を組み合わせた PWA サンプルプロジェクトです。
以下の機能を実装しています。

- 認証（ログイン / ログアウト / パスワード変更）
- ユーザー管理（一覧 / 登録 / 編集 / 削除）
- TODO 管理（一覧 / 登録 / 更新 / 削除）
- PWA 対応（Service Worker による静的アセットのキャッシュ、ホーム画面追加）
- オフライン対応（ネットワーク切断時の TODO 操作を IndexedDB にキューイングし、復帰時にバックエンドへ同期）

認証は JWT を httpOnly + SameSite=Strict な Cookie に格納するステートレス方式で、短命なアクセストークン（既定 15 分）と長命なリフレッシュトークン（既定 7 日）の組み合わせによるサイレントリフレッシュに対応しています。

## 構成

```
spring-boot-react-pwa-sample/
├── backend/          # Spring Boot バックエンド (Java 25)
├── frontend/         # React フロントエンド (TypeScript + Vite, PWA)
└── .devcontainer/    # Dev Container 設定
```

マルチプロジェクト構成の Gradle でビルドを管理しています。
開発環境は Dev Container で提供しており、PostgreSQL データベースと pgAdmin4 も含まれています。

## 技術スタック

### バックエンド

| カテゴリ         | ライブラリ / フレームワーク                                                                                           |
| ---------------- | --------------------------------------------------------------------------------------------------------------------- |
| フレームワーク   | [Spring Boot](https://spring.io/projects/spring-boot) 4.0                                                             |
| 言語             | Java 25                                                                                                               |
| 認証 / 認可      | [Spring Security](https://spring.io/projects/spring-security) + OAuth2 Resource Server (JWT / HS256, Nimbus JOSE)     |
| DB アクセス      | [MyBatis](https://mybatis.org/) 4.0                                                                                   |
| マイグレーション | [Flyway](https://flywaydb.org/)                                                                                       |
| API ドキュメント | [springdoc-openapi](https://springdoc.org/) 3.0                                                                       |
| データベース     | [PostgreSQL](https://www.postgresql.org/) 17                                                                          |
| フォーマット     | [Spotless](https://github.com/diffplug/spotless) + [google-java-format](https://github.com/google/google-java-format) |
| 静的解析         | [SpotBugs](https://spotbugs.github.io/)                                                                               |
| カバレッジ       | [JaCoCo](https://www.jacoco.org/jacoco/)                                                                              |
| E2E テスト       | [Playwright](https://playwright.dev/java/) for Java                                                                   |
| ビルドツール     | [Gradle](https://gradle.org/)                                                                                         |

### フロントエンド

| カテゴリ             | ライブラリ / フレームワーク                                                                                 |
| -------------------- | ----------------------------------------------------------------------------------------------------------- |
| フレームワーク       | [React](https://react.dev/) 19                                                                              |
| 言語                 | TypeScript 6                                                                                                |
| ビルドツール         | [Vite](https://vite.dev/) 8                                                                                 |
| ルーティング         | [TanStack Router](https://tanstack.com/router)                                                              |
| データフェッチ       | [TanStack Query](https://tanstack.com/query) + [openapi-fetch](https://openapi-ts.dev/openapi-fetch/)       |
| UI コンポーネント    | [shadcn/ui](https://ui.shadcn.com/) ([Radix UI](https://www.radix-ui.com/) ベース)                          |
| 通知トースト         | [Sonner](https://sonner.emilkowal.ski/)                                                                     |
| スタイリング         | [Tailwind CSS](https://tailwindcss.com/) 4                                                                  |
| PWA / Service Worker | [vite-plugin-pwa](https://vite-pwa-org.netlify.app/) + [Workbox](https://developer.chrome.com/docs/workbox) |
| ローカルストレージ   | [idb](https://github.com/jakearchibald/idb)（IndexedDB ラッパー、オフライン同期キュー用）                   |
| ユニットテスト       | [Vitest](https://vitest.dev/) + [Testing Library](https://testing-library.com/) + [MSW](https://mswjs.io/)  |
| Lint                 | [ESLint](https://eslint.org/)                                                                               |
| フォーマット         | [Prettier](https://prettier.io/)                                                                            |
| パッケージマネージャ | [pnpm](https://pnpm.io/)                                                                                    |

## 動作環境

Dev Container を使用するため、以下が必要です。

- [Docker](https://www.docker.com/)
- [VS Code](https://code.visualstudio.com/) + [Dev Containers 拡張機能](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers)

## 起動方法

### 1. Dev Container の起動

VS Code でリポジトリを開き、コマンドパレットから **Dev Containers: Reopen in Container** を実行します。

コンテナ起動後、pgAdmin4 が利用可能になります。

| サービス | URL                   |
| -------- | --------------------- |
| pgAdmin4 | http://localhost:5050 |

### 2. バックエンドの起動

```bash
./gradlew :backend:bootRun
```

起動後、以下の URL でアクセスできます。

| サービス         | URL                                   |
| ---------------- | ------------------------------------- |
| バックエンド API | http://localhost:8080                 |
| Swagger UI       | http://localhost:8080/swagger-ui.html |

### 3. フロントエンドの起動

初回はパッケージのインストールが必要です。

```bash
# Gradle から実行する場合
./gradlew :frontend:pnpmInstall

# pnpm から直接実行する場合
cd frontend
pnpm install
```

続いて、開発サーバーを起動します。

```bash
# Gradle から実行する場合
./gradlew :frontend:dev

# pnpm から直接実行する場合
cd frontend
pnpm dev
```

起動後、http://localhost:5173 でアクセスできます。

### 初期ユーザー

Flyway のマイグレーションで初期ユーザーが登録されています。

| メールアドレス      | パスワード |
| ------------------- | ---------- |
| `admin@example.com` | `admin`    |

## 設定

認証関連の設定は [application.properties](backend/src/main/resources/application.properties) で定義しており、環境変数で上書きできます。本番環境では必ず `JWT_SECRET` と `COOKIE_SECURE` を上書きしてください。

| プロパティ                          | 環境変数        | デフォルト     | 説明                                                                   |
| ----------------------------------- | --------------- | -------------- | ---------------------------------------------------------------------- |
| `app.jwt.secret`                    | `JWT_SECRET`    | 開発用の固定値 | JWT 署名鍵（HS256、32 バイト以上）。**本番では必ず上書きすること**     |
| `app.jwt.access-expiration-minutes` | -               | `15`           | アクセストークンの有効期間（分）                                       |
| `app.jwt.refresh-expiration-days`   | -               | `7`            | リフレッシュトークンの有効期間（日）。`/api/auth/refresh` で再発行する |
| `app.cookie.secure`                 | `COOKIE_SECURE` | `false`        | Cookie の Secure 属性。HTTPS 環境では `true` にすること                |

## ビルド

### 集約タスク

backend / frontend をまとめて実行する集約タスクをルートプロジェクトに用意しています。

```bash
# フォーマット
./gradlew format

# 静的解析（lint / typecheck / SpotBugs）
./gradlew lint

# テスト（E2E を除く）
./gradlew test

# E2E テスト
./gradlew e2eTest

# E2E テスト（ブラウザ表示あり）
./gradlew e2eTestHeaded
```

### 実行可能 JAR / WAR（フロントエンド込み）

```bash
# JAR
./gradlew :backend:bootJar

# WAR
./gradlew :backend:bootWar
```

フロントエンドのビルド → パッケージングが自動で実行されます。
生成先: `backend/build/libs/backend-0.0.1-SNAPSHOT.jar` または `.war`

```bash
# JAR で起動
java -jar backend/build/libs/backend-0.0.1-SNAPSHOT.jar
```

### バックエンド

```bash
# ビルド
./gradlew :backend:build

# テスト
./gradlew :backend:test

# E2E テスト（Playwright）
./gradlew :backend:e2eTest

# E2E テスト（ブラウザ表示あり）
./gradlew :backend:e2eTestHeaded

# フォーマット
./gradlew :backend:spotlessApply

# 静的解析
./gradlew :backend:spotbugsMain :backend:spotbugsTest
```

E2E テストはフロントエンドのビルド（`frontend/dist/`）を必要とします。初回および frontend 変更後は自動でビルドが実行されます。

`e2eTestHeaded` はブラウザを headed 起動するため、Windows + WSL2 環境では WSLg 経由でブラウザウィンドウが表示されます。`devcontainer.json` で WSLg のソケットと `DISPLAY` 環境変数をコンテナに引き込む設定を入れているため、追加設定は不要です。

### フロントエンド

`frontend/build.gradle.kts` で `package.json` の scripts を Gradle タスクとして自動登録しています（`:` は `_` に置換）。`pnpm` を直接使うことも可能です。

```bash
# 型チェック
./gradlew :frontend:typecheck
# または: cd frontend && pnpm typecheck

# Lint
./gradlew :frontend:lint
# または: cd frontend && pnpm lint

# フォーマット
./gradlew :frontend:format
# または: cd frontend && pnpm format

# ユニットテスト (Vitest)
./gradlew :frontend:test
# または: cd frontend && pnpm test

# カバレッジ付きテスト
./gradlew :frontend:test_coverage
# または: cd frontend && pnpm test:coverage

# プロダクションビルド
./gradlew :frontend:build
# または: cd frontend && pnpm build
```

## API クライアントの生成

生成済みの型定義 `frontend/src/generated/api.d.ts` はリポジトリに含まれているため、通常は生成不要です。
バックエンドの API 変更に合わせて再生成する場合は、バックエンドを起動した状態で以下を実行します。

```bash
# Gradle から実行する場合
./gradlew :frontend:generate

# pnpm から直接実行する場合
cd frontend
pnpm generate
```
