# CLAUDE.md

## プロジェクト概要

Spring Boot + React の PWA サンプルアプリ。JWT Cookie 認証・TODO 機能・オフライン同期・Service Worker を実装している。

- **backend**: Spring Boot 4.0 / Java 25 / MyBatis / PostgreSQL / Flyway
- **frontend**: React 19 / TypeScript / Vite / TanStack Router・Query / Tailwind CSS / shadcn/ui / Vitest
- **E2E**: Playwright（backend の test タスクとは分離）

### アーキテクチャ概要

```
frontend (React PWA)
  └─ openapi-fetch (型安全な API クライアント、src/generated/api.d.ts)
backend REST API (/api/...)
  ├─ Controller → Service → Mapper (MyBatis) → PostgreSQL
  └─ JWT Cookie 認証 (アクセストークン + リフレッシュトークン)
```

### 主要ドメイン

| ドメイン | Controller | Service |
|---|---|---|
| 認証 | `AuthController` | `AuthService`, `JwtTokenService` |
| ユーザー管理 | `UserController` | `UserService` |
| TODO | `TodoController` | `TodoService` |

## 修正完了時の手順

コードの修正が完了したら、必ず以下を順番に実行すること。backend / frontend の両方をまとめて対象とする集約タスクをルートプロジェクトに登録してある。

### 1. フォーマット

```bash
./gradlew format
```

### 2. 静的解析 (lint / typecheck / SpotBugs)

```bash
./gradlew lint
```

### 3. テスト

```bash
./gradlew test
```

### 4. E2E テスト (必要に応じて)

```bash
./gradlew e2eTest
```

E2E テストは backend の `e2e` パッケージに配置し、通常の `test` タスクとは分離している。

## ライブラリ追加時の注意

ライブラリを追加する際は、以下を調査してから使用すること。

- **最新バージョン**: Maven Central の metadata や GitHub releases を確認する
- **メンテナンス状況**: GitHub のコミット・リリース頻度、issue 対応状況を確認し、放棄されていないことを確認する
- **利用実績**: ダウンロード数やスター数など、実際に使われているかを確認する
- **対象バージョンとの互換性**: 使用している Spring Boot / Java のバージョンと互換があることを確認する

## コーディング規約

### レイヤー規約（backend）

- Controller は Service 経由でデータアクセスすること。Controller が Mapper（MyBatis）を直接 `@Autowired` / コンストラクタインジェクションすることは禁止。
- Service のメソッドは存在しないリソースを例外でなく `Optional` で返すこと。`UserNotFoundException` などのドメイン例外は Controller 側でスローする。
- TODO はユーザー所有リソースであり、Service 層で `userId` による所有権チェックを行う。

### API 型生成（frontend）

バックエンドの OpenAPI スキーマから型を生成して使用する。スキーマ変更後は再生成すること。

```bash
# バックエンドを起動した状態で実行
cd frontend && npm run generate
```

生成先: `src/generated/api.d.ts`

### テストコード

テストの記述規約・依存の扱い・プロジェクト固有のルールは `.claude/skills/unit-test/SKILL.md` に従うこと。

### DB マイグレーション

Flyway を使用。マイグレーションファイルは `backend/src/main/resources/db/migration/` に配置。

```bash
# ローカル DB (sample) にマイグレーション適用
./gradlew :backend:flywayMigrate

# テスト DB (sample_test) クリーン
./gradlew :backend:flywayCleanTest
```
