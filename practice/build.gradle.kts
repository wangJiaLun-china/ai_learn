plugins {
    java
    application
}

group = "com.wangjialun.ai"
version = "1.0"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

repositories {
    mavenCentral()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

application {
    mainClass.set("com.wangjialun.ai.practice.PracticeRunner")
}

// 交互式练习：./gradlew -q run  或  ./gradlew -q run --args='step05'
tasks.named<JavaExec>("run") {
    workingDir = projectDir
    standardInput = System.`in`
}
