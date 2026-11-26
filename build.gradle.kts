plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.spring") version "2.4.10"
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("dev.detekt") version "2.0.0-alpha.6"
    jacoco
}

// Single source of truth for the PrintScript engine (CNC) version: gradle.properties (cncVersion),
// overridable with -PcncVersion=X.Y.Z. It is also exposed at runtime as `engineVersion`.
val cncVersion: String = (findProperty("cncVersion") as String?) ?: error("cncVersion is not defined")

repositories {
    // Local builds: `./gradlew -PuseMavenLocal=true ...` resolves CNC from ~/.m2 (publishToMavenLocal from a CNC checkout).
    if (providers.gradleProperty("useMavenLocal").orNull == "true") {
        mavenLocal()
    }
    mavenCentral()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/TP1-2-INGSIS/CNC")
        credentials {
            username = (findProperty("gpr.user") as String?) ?: System.getenv("GITHUB_ACTOR")
            password = (findProperty("gpr.key") as String?) ?: System.getenv("GPR_TOKEN") ?: System.getenv("GITHUB_TOKEN")
        }
        content {
            includeGroup("com.github.TP1-2-INGSIS")
        }
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.github.TP1-2-INGSIS:printscript:$cncVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

group = "ingsis"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        javaParameters = true
        allWarningsAsErrors = true
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

ktlint {
    version.set("1.5.0")
    android.set(false)
    outputToConsole.set(true)
    ignoreFailures.set(false)
}

detekt {
    // Full config built on detekt's default one; our deliberate changes are commented in the file.
    config.setFrom(files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    // Pre-existing findings are accepted via the baseline so new code is checked from now on.
    baseline = file("config/detekt/detekt-baseline.xml")
}

tasks.register<Exec>("installGitHooks") {
    description = "Configures git to use .githooks directory"
    group = "git hooks"
    commandLine("git", "config", "core.hooksPath", ".githooks")
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                minimum = "0.8".toBigDecimal()
            }
        }
    }
}

tasks.processResources {
    inputs.property("cncVersion", cncVersion)
    filesMatching("engine.properties") {
        expand("cncVersion" to cncVersion)
    }
}

tasks.jar {
    enabled = false
}

tasks.bootJar {
    archiveFileName.set("app.jar")
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
