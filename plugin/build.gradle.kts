plugins {
    java
}

group = "io.github.xcollxn"
version = "1.15.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.opencollab.dev/main/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.255-stable")
    compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT")
    compileOnly("org.geysermc.geyser:api:2.12.1-SNAPSHOT")   // Geyser API (emote event), provided by Geyser at runtime
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.processResources {
    filteringCharset = "UTF-8"
    // Bedrock packs + Geyser block mapping (built by bedrock-paket/*.py) – installed into Geyser by BedrockPakete
    from("bedrock-paket") {
        include("TeamPing-Bedrock.mcpack", "TeamPing-Bedrock-Fenster.mcpack", "teamping_mappings.json")
        into("bedrock")
    }
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
