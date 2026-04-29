# spring-boot-react-pwa-sample

Spring Boot と React を組み合わせた PWA サンプルプロジェクトです。
以下の機能を実装しています。

- 認証（ログイン / ログアウト / パスワード変更）
- ユーザー管理（一覧 / 登録 / 編集 / 削除）
- TODO 管理（一覧 / 登録 / 更新 / 削除）
- PWA 対応（Service Worker による静的アセットのキャッシュ、ホーム画面追加）
- オフライン対応（ネットワーク切断時の TODO 操作を IndexedDB にキューイングし、復帰時にバックエンドへ同期）

## 認証方式

httpOnly + SameSite=Strict な Cookie に認証情報を格納するステートレス方式で、短命なアクセストークンと長命なリフレッシュトークンの組み合わせによるサイレントリフレッシュに対応しています。

| 種別                       | Cookie 名       | Path                 | 形式                              | 既定の有効期間 | 保存先                                         |
| -------------------------- | --------------- | -------------------- | --------------------------------- | -------------- | ---------------------------------------------- |
| アクセストークン           | `ACCESS_TOKEN`  | `/`                  | JWT (HS256, Nimbus JOSE)          | 15 分          | クライアント Cookie のみ（DB には保持しない）  |
| リフレッシュトークン       | `REFRESH_TOKEN` | `/api/auth/refresh`  | 不透明トークン（`SecureRandom` 32 バイトの Base64URL） | 7 日           | DB には SHA-256 ハッシュのみを保存             |

実装上のポイント:

- リフレッシュトークンは平文を Cookie でクライアントに渡し、サーバー側は SHA-256 ハッシュのみを保存する（DB 流出時にトークン本体が漏れない）。
- リフレッシュトークン Cookie は `Path=/api/auth/refresh` に限定し、通常 API には送信されない。
- `/api/auth/refresh` 利用時にトークンをローテーション（旧トークンを `DELETE` し新トークンを発行）。並行リクエスト時は `DELETE` の影響行数で二重ローテーションを検出し、再利用された側は 401 にする。
- セッションは `sessions` テーブルで管理し、パスワード変更時は現在のセッションを除き全失効、ログアウト時は当該セッションを失効させる（リフレッシュトークンは `ON DELETE CASCADE` で連動削除）。
- フロントエンド (`src/lib/api-client.ts`) では openapi-fetch のミドルウェアで 401 を捕捉し、`/api/auth/refresh` を 1 回だけ呼んで成功時に元リクエストを再送する。並行する 401 は同じ refresh 結果を共有する。

## オフライン対応

PWA としてのキャッシュは [vite-plugin-pwa](https://vite-pwa-org.netlify.app/) (Workbox) が静的アセットの precache を担当し、TODO データは IndexedDB を一次ストアとして扱い、UI は常にここを参照する独自の同期エンジンで管理しています。

### サーバー到達可能性の判定（ヘルスチェック）

`navigator.onLine` だけでは「ネットワーク接続あり/サーバー死んでる」「キャプティブポータル」などを判別できないため、`/api/health` への HEAD 相当の GET を組み合わせて判定しています（`src/hooks/use-reachability.ts`）。

- `navigator.onLine === false` のときは即 `reachable=false`（ヘルスチェックは停止）。
- `navigator.onLine === true` のときは `/api/health` を **30 秒間隔**でポーリング（TanStack Query の `refetchInterval`）。
- 非アクティブタブではポーリングを停止し、`visibilitychange` でアクティブに戻った瞬間に即時再チェック。
- 初期値は楽観的に `true`（ヘルスチェック完了前でも API を試す）。
- `false → true` 遷移を検知したタイミングで sync-queue の消化を起動する。

### IndexedDB によるローカル保持

[idb](https://github.com/jakearchibald/idb) を使い、DB 名 `pwa-sample` に以下 2 ストアを持ちます（`src/lib/todo-store.ts`）。

| ストア       | キー      | 用途                                                                                                       |
| ------------ | --------- | ---------------------------------------------------------------------------------------------------------- |
| `todos`      | `localId` | TODO レコード（`syncStatus: 'synced' \| 'pending'` を持ち、UI は常にこのストアを描画する）                 |
| `sync-queue` | `seq`     | オフライン中に積まれた create / update / delete オペレーション（`autoIncrement` で採番、enqueue 順を保証） |

同期エンジン（`src/lib/todo-sync.ts`）の挙動:

- オンライン時の mutation は **API → IDB** の順に反映する。
- `reachable === false` のときは IDB に楽観的に書き、`sync-queue` に op を積む。サーバー側未採番の create は `localId` を仮 ID として保持し、`syncStatus: 'pending'` で UI に区別表示する。
- `reachable` が `false → true` に遷移したとき、`sync-queue` を作成順に消化する。
  - 成功 → dequeue。create はサーバー採番 ID で再 upsert し、仮 localId のレコードを削除する。
  - HTTP エラー（4xx / 5xx）／ネットワーク失敗（fetch 自体の throw）いずれも op をキューに残したまま消化を停止する。後続 op は前の op に依存しうるため、最初の失敗で止めてユーザーの変更を失わないようにしている。
  - 失敗時の通知はライブラリ層では行わず、呼び出し元（自動同期 / 手動再試行）が文脈に応じて出し分ける。

これにより、ブラウザを閉じてもオフライン中の変更は IndexedDB に保持され、次回起動時にサーバーへ反映されます。

### PWA 動作確認時の注意

`vite-plugin-pwa` はデフォルトで Service Worker を本番ビルド時にのみ登録します。`pnpm dev` (`./gradlew :frontend:dev`) で起動した開発サーバーでは Service Worker が登録されず、オフライン状態でリロードするとページの読み込みに失敗します。PWA としてのオフライン動作を確認する場合は、ビルド成果物を `vite preview` で配信してください。`pnpm preview:build` でビルド → 配信を一括実行できます。

```bash
# Gradle から実行する場合
./gradlew :frontend:preview_build

# pnpm から直接実行する場合
cd frontend
pnpm preview:build
```

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

> **PWA / オフライン動作の確認について**: 開発サーバーでは Service Worker が登録されないため、オフライン状態のリロードなど PWA の挙動は確認できません。確認する場合は本番ビルドして `vite preview` で配信してください。詳細は [PWA 動作確認時の注意](#pwa-動作確認時の注意) を参照してください。

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
