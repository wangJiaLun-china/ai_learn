plugins {
    java
    application
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.wangjialun.ai"
version = "1.0"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

repositories {
    mavenCentral()
}

val agentscopeVersion = "2.0.4"

dependencies {
    implementation(platform("io.agentscope:agentscope-bom:$agentscopeVersion"))
    implementation("io.agentscope:agentscope-core")
    implementation("io.agentscope:agentscope-harness")
    implementation("io.agentscope:agentscope-extensions-model-openai")
    implementation("io.agentscope:agentscope-extensions-a2a-client")
    implementation("io.agentscope:agentscope-extensions-a2a-server")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

application {
    mainClass.set("com.wangjialun.ai.toyagentscope.Application")
}

tasks.shadowJar {
    archiveFileName.set("ToyAgentScope.jar")
    mergeServiceFiles()
}

// 下载依赖源码，便于对照 AgentScope 2.0.4 的真实 API
tasks.register("downloadSources") {
    doLast {
        val ids = configurations.runtimeClasspath.get().incoming.resolutionResult.allComponents
            .mapNotNull { it.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier }
            .filter { it.group == "io.agentscope" && !it.module.endsWith("-bom") }
        val dep = ids.map { project.dependencies.create("${it.group}:${it.module}:${it.version}:sources") }
        val conf = configurations.detachedConfiguration(*dep.toTypedArray())
        conf.isTransitive = false
        conf.resolve().forEach { println("SRC " + it.absolutePath) }
    }
}

// Step25 的终端形态：./gradlew -q runCli  或  ./gradlew -q runCli --args='-p "运行：ls"'
tasks.register<JavaExec>("runCli") {
    group = "application"
    description = "运行 Step25 CLI 智能体（REPL / -p 单命令模式）"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.wangjialun.ai.toyagentscope.agent.step25.CliAgent")
    standardInput = System.`in`
}
