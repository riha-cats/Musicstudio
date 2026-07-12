import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

// 일부 paper-api 스냅샷은 papermc 저장소에서 이미 정리된 adventure(net.kyori) SNAPSHOT를
// transitive로 요구한다(예: adventure-bom:4.17.0-SNAPSHOT). paper-api의 Gradle 메타데이터가
// 이를 strict 제약으로 박아두어 eachDependency로는 못 누르므로, 선택자 자체를 동일 버전의 정식
// 릴리스(Maven Central)로 치환한다. adventure는 서버가 런타임에 제공하므로 컴파일 전용 영향만 있다
configurations.all {
    resolutionStrategy.dependencySubstitution.all {
        val req = requested
        if (req is ModuleComponentSelector && req.group == "net.kyori"
                && req.version.endsWith("-SNAPSHOT")) {
            useTarget(
                "${req.group}:${req.module}:${req.version.removeSuffix("-SNAPSHOT")}",
                "papermc 저장소에서 제거된 adventure SNAPSHOT를 릴리스로 대체"
            )
        }
    }
}

// 개발/테스트용 기준 API(Java 21 계열). 배포 JAR은 아래 매트릭스가 버전별로 따로 만든다
val primaryApi = "io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT"

// 소스가 @NotNull/@Nullable(org.jetbrains.annotations)을 직접 사용한다. paper-api가 버전에 따라
// 이를 transitive로 노출하지 않으므로(26.x는 미노출) 명시적 compileOnly 의존으로 고정한다
val jetbrainsAnnotations = "org.jetbrains:annotations:24.1.0"

dependencies {
    compileOnly(primaryApi)
    compileOnly(jetbrainsAnnotations)

    // 테스트: 순수 로직(MiniMessage 파싱, italic, 플레이스홀더, 음높이 계산) 검증
    testImplementation(primaryApi)
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks {
    runServer {
        minecraftVersion("1.21.6")
        jvmArgs("-Xms2G", "-Xmx2G")

        // 26.x로 띄우려면(검증 완료) 아래 3줄 주석 해제. 26.x 서버는 Java 25 최소 요구라
        // 서버 JVM을 JDK 25로 지정하고, 기존 run/ world와 분리된 디렉토리를 쓴다
        // minecraftVersion("26.1.2")
        // runDirectory.set(layout.projectDirectory.dir("run-26x"))
        // javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    }

    processResources {
        // plugin.yml의 한글은 파일에 직접 기입(UTF-8). properties 경유 시 Gradle이
        // ISO-8859-1로 읽어 깨지므로 description placeholder는 사용하지 않는다
        filteringCharset = "UTF-8"
        val props = mapOf("version" to version, "apiVersion" to "1.21")
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}

// 멀티버전 빌드 매트릭스
// 하나의 소스를 각 paper-api 버전에 대해 컴파일하고, plugin.yml의 api-version을 버전에 맞춰
// 주입한 뒤 build/libs/Upload/<마크버전>/ 에 JAR을 만든다. 각 JAR은 Paper/Purpur 공용
// (Purpur는 Paper 포크라 Paper API JAR이 그대로 동작; 이 플러그인은 Purpur 전용 API 미사용)
//
// release : 산출 바이트코드(서버 최소 Java). 1.20~1.20.4=Java17, 1.20.5+=Java21
// jdk     : 컴파일에 쓸 JDK. 26.x paper-api는 Java25 바이트코드라 JDK25로만 읽힌다
data class Target(
    val label: String,       // 폴더명 = 마인크래프트 버전
    val coord: String,       // paper-api 의존 좌표
    val apiVersion: String,  // plugin.yml api-version
    val release: Int,        // 출력 바이트코드 버전
    val jdk: Int             // 컴파일 JDK 툴체인
)

val targets = listOf(
    Target("1.20",    "io.papermc.paper:paper-api:1.20-R0.1-SNAPSHOT",     "1.20", 17, 21),
    Target("1.20.1",  "io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT",   "1.20", 17, 21),
    Target("1.20.2",  "io.papermc.paper:paper-api:1.20.2-R0.1-SNAPSHOT",   "1.20", 17, 21),
    Target("1.20.3",  "io.papermc.paper:paper-api:1.20.3-R0.1-SNAPSHOT",   "1.20", 17, 21),
    Target("1.20.4",  "io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT",   "1.20", 17, 21),
    // 1.20.5 paper-api POM이 삭제된 adventure-bom:4.17.0-SNAPSHOT를 import해 해소 불가
    // POM 파싱 단계라 dependencySubstitution이 못 잡으므로, API 호환 이웃(1.20.6, 동일 api-version
    // 1.20·동일 Java21)의 헤더로 컴파일한다. 결과 JAR은 1.20.5 서버에서 정상 동작
    Target("1.20.5",  "io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT",   "1.20", 21, 21),
    Target("1.20.6",  "io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT",   "1.20", 21, 21),
    Target("1.21",    "io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT",     "1.21", 21, 21),
    Target("1.21.1",  "io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT",   "1.21", 21, 21),
    Target("1.21.3",  "io.papermc.paper:paper-api:1.21.3-R0.1-SNAPSHOT",   "1.21", 21, 21),
    Target("1.21.4",  "io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT",   "1.21", 21, 21),
    Target("1.21.5",  "io.papermc.paper:paper-api:1.21.5-R0.1-SNAPSHOT",   "1.21", 21, 21),
    Target("1.21.6",  "io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT",   "1.21", 21, 21),
    Target("1.21.7",  "io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT",   "1.21", 21, 21),
    Target("1.21.8",  "io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT",   "1.21", 21, 21),
    // 1.21.9 paper-api POM이 삭제된 adventure-bom:4.25.0-SNAPSHOT를 import해 해소 불가
    // API 호환 이웃(1.21.10)의 헤더로 컴파일한다(동일 api-version 1.21·동일 Java21)
    Target("1.21.9",  "io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT",  "1.21", 21, 21),
    Target("1.21.10", "io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT",  "1.21", 21, 21),
    Target("1.21.11", "io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT",  "1.21", 21, 21),
    Target("26.1.2",  "io.papermc.paper:paper-api:26.1.2.build.69-stable", "1.21", 21, 25),
)

val uploadRoot = layout.buildDirectory.dir("libs/Upload")
val toolchainService = javaToolchains
val mainSource = sourceSets.main.get()

val buildAllVersions = tasks.register("buildAllVersions") {
    group = "build"
    description = "모든 마인크래프트 버전용 JAR을 build/libs/Upload/<버전>/ 에 생성"
}

for (t in targets) {
    val safe = t.label.replace('.', '_')

    val api = configurations.create("paperApi_$safe") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
    dependencies.add(api.name, t.coord)
    dependencies.add(api.name, jetbrainsAnnotations)

    val classesDir = layout.buildDirectory.dir("matrix/${t.label}/classes")
    val resourcesDir = layout.buildDirectory.dir("matrix/${t.label}/resources")

    val compile = tasks.register<JavaCompile>("compile_$safe") {
        group = "build matrix"
        description = "MusicStudio 컴파일 → ${t.label} (api ${t.apiVersion}, Java${t.release}, JDK${t.jdk})"
        source(mainSource.java)
        classpath = api
        destinationDirectory.set(classesDir)
        options.release.set(t.release)
        options.encoding = "UTF-8"
        javaCompiler.set(toolchainService.compilerFor {
            languageVersion.set(JavaLanguageVersion.of(t.jdk))
        })
    }

    val resources = tasks.register<Copy>("resources_$safe") {
        group = "build matrix"
        from(mainSource.resources)
        into(resourcesDir)
        filteringCharset = "UTF-8"
        val props = mapOf("version" to version.toString(), "apiVersion" to t.apiVersion)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    val jar = tasks.register<Jar>("jar_$safe") {
        group = "build matrix"
        description = "MusicStudio JAR → Upload/${t.label}/"
        from(compile.flatMap { it.destinationDirectory })
        from(resources)
        archiveBaseName.set("MusicStudio")
        archiveVersion.set("${version}-mc${t.label}")
        destinationDirectory.set(uploadRoot.map { it.dir(t.label) })
    }

    buildAllVersions.configure { dependsOn(jar) }
}
