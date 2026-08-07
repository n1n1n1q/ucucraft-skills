plugins {
    java
}

// Adjust `paperApiVersion` to the exact published Paper API build for your server.
val paperApiVersion = "26.2.build.92-stable"
val javaVersion = 25

group = "com.ucucraft"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    // Soft dependency: the Countries API (thief lockpicking). Provided at runtime by the Countries
    // plugin; compiled against the bundled api jar only. ucucraft-items is a pure PDC soft-dep.
    compileOnly(files("libs/countries-1.0.0.jar"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val props = mapOf("version" to version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.compileJava {
    options.encoding = "UTF-8"
}
