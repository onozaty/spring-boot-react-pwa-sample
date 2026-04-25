plugins {
    java
    war
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.4.0"
    id("com.github.spotbugs") version "6.4.12"
    id("org.flywaydb.flyway") version "11.14.1"
    jacoco
}

buildscript {
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:11.14.1")
    }
}

group = "com.github.onozaty"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.1")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.microsoft.playwright:playwright:1.52.0")
    testRuntimeOnly("com.microsoft.playwright:driver-bundle:1.52.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    spotbugs("com.github.spotbugs:spotbugs:4.9.8")
}

val frontendDist = project(":frontend").layout.projectDirectory.dir("dist")

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    dependsOn(":frontend:build")
    from(frontendDist) {
        into("BOOT-INF/classes/static")
    }
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootWar>("bootWar") {
    dependsOn(":frontend:build")
    from(frontendDist) {
        into("WEB-INF/classes/static")
    }
    setClasspath(classpath!!.filter { file ->
        val n = file.name
        !n.startsWith("tomcat-embed-") &&
            !n.startsWith("spring-boot-starter-tomcat") &&
            !n.startsWith("spring-boot-tomcat-")
    })
}

// @SpringBootTest では bootJar/bootWar と異なりクラスパスを直接参照するため、
// frontend/dist/ を build/e2e-resources/static/ にコピーしてクラスパスに追加することで
// SpaForwardingConfig の ClassPathResource("static/") から配信できるようにしている
val e2eResourcesDir = layout.buildDirectory.dir("e2e-resources")

val copyFrontendForE2E = tasks.register<Copy>("copyFrontendForE2E") {
    dependsOn(":frontend:build")
    from(frontendDist)
    into(e2eResourcesDir.map { it.dir("static") })
}

fun Test.configureE2E() {
    dependsOn(copyFrontendForE2E)
    useJUnitPlatform()
    include("**/e2e/**")
    testLogging {
        events("passed", "failed", "skipped")
    }
    // testClassesDirs と classpath を明示しないと NO-SOURCE になる
    // (tasks.register<Test> で新規作成したタスクはデフォルト値が設定されないため)
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = files(e2eResourcesDir) + sourceSets["test"].runtimeClasspath
}

val e2eTest = tasks.register<Test>("e2eTest") {
    description = "Run Playwright E2E tests"
    group = "verification"
    configureE2E()
}

// ブラウザを headed 起動して E2E を実行する（WSLg 等で画面表示したい場合に利用）
val e2eTestHeaded = tasks.register<Test>("e2eTestHeaded") {
    description = "Run Playwright E2E tests with headed browser"
    group = "verification"
    configureE2E()
    environment("HEADLESS", "false")
    // 毎回再実行したいので UP-TO-DATE にさせない
    outputs.upToDateWhen { false }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
    // e2eTest / e2eTestHeaded は JaCoCo 対象外とする
    // finalizedBy に jacocoTestReport を含めると dependsOn(tasks.test) が引き込まれ
    // e2e 実行時に test タスクも実行されてしまうため除外している
    if (name != "e2eTest" && name != "e2eTestHeaded") {
        finalizedBy(tasks.jacocoTestReport)
    }
}

// e2e パッケージは e2eTest タスクでのみ実行する
tasks.test {
    exclude("**/e2e/**")
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        html.required = true
    }
}

spotbugs {
    ignoreFailures = false
    effort = com.github.spotbugs.snom.Effort.MAX
    reportLevel = com.github.spotbugs.snom.Confidence.MEDIUM
    excludeFilter = file("config/spotbugs-exclude.xml")
}

spotless {
    java {
        googleJavaFormat("1.35.0")
    }
}

flyway {
    url = "jdbc:postgresql://localhost:5432/sample"
    user = "sample"
    password = "sample"
    cleanDisabled = false
}

tasks.register<org.flywaydb.gradle.task.FlywayCleanTask>("flywayCleanTest") {
    url = "jdbc:postgresql://localhost:5432/sample_test"
    user = "sample"
    password = "sample"
    cleanDisabled = false
}
