plugins {
    id("fabric-loom") version "1.17.12"
    `java-library`
}

group = "com.hackclub"
version = "0.1.1"
description = "Hack Club Minecraft server core for Fabric."

val minecraftVersion = "26.1.2"
val fabricLoaderVersion = "0.19.3"
val fabricApiVersion = "0.153.0+26.1.2"
val slackVersion = "1.38.3"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

loom {
    noIntermediateMappings()
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:intermediary:0.0.0:v2")
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    implementation("com.google.code.gson:gson:2.13.2")
    include("com.google.code.gson:gson:2.13.2")

    implementation("com.slack.api:bolt:$slackVersion")
    include("com.slack.api:bolt:$slackVersion")
    implementation("com.slack.api:bolt-servlet:$slackVersion")
    include("com.slack.api:bolt-servlet:$slackVersion")
    implementation("com.slack.api:bolt-jetty:$slackVersion")
    include("com.slack.api:bolt-jetty:$slackVersion")
    implementation("com.slack.api:slack-api-model:$slackVersion")
    include("com.slack.api:slack-api-model:$slackVersion")
    implementation("com.slack.api:slack-api-client:$slackVersion")
    include("com.slack.api:slack-api-client:$slackVersion")
    implementation("com.slack.api:slack-app-backend:$slackVersion")
    include("com.slack.api:slack-app-backend:$slackVersion")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    include("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okio:okio:3.6.0")
    include("com.squareup.okio:okio:3.6.0")
    implementation("com.squareup.okio:okio-jvm:3.6.0")
    include("com.squareup.okio:okio-jvm:3.6.0")

    implementation("org.apache.commons:commons-text:1.11.0")
    include("org.apache.commons:commons-text:1.11.0")
    implementation("org.apache.commons:commons-lang3:3.19.0")
    include("org.apache.commons:commons-lang3:3.19.0")

    implementation("javax.servlet:javax.servlet-api:3.1.0")
    include("javax.servlet:javax.servlet-api:3.1.0")
    implementation("org.eclipse.jetty:jetty-servlet:9.4.53.v20231009")
    include("org.eclipse.jetty:jetty-servlet:9.4.53.v20231009")
    implementation("org.eclipse.jetty:jetty-security:9.4.53.v20231009")
    include("org.eclipse.jetty:jetty-security:9.4.53.v20231009")
    implementation("org.eclipse.jetty:jetty-server:9.4.53.v20231009")
    include("org.eclipse.jetty:jetty-server:9.4.53.v20231009")
    implementation("org.eclipse.jetty:jetty-http:9.4.53.v20231009")
    include("org.eclipse.jetty:jetty-http:9.4.53.v20231009")
    implementation("org.eclipse.jetty:jetty-io:9.4.53.v20231009")
    include("org.eclipse.jetty:jetty-io:9.4.53.v20231009")
    implementation("org.eclipse.jetty:jetty-util:9.4.53.v20231009")
    include("org.eclipse.jetty:jetty-util:9.4.53.v20231009")
    implementation("org.eclipse.jetty:jetty-util-ajax:9.4.53.v20231009")
    include("org.eclipse.jetty:jetty-util-ajax:9.4.53.v20231009")
    implementation("org.eclipse.jetty:jetty-webapp:9.4.53.v20231009")
    include("org.eclipse.jetty:jetty-webapp:9.4.53.v20231009")
    implementation("org.eclipse.jetty:jetty-xml:9.4.53.v20231009")
    include("org.eclipse.jetty:jetty-xml:9.4.53.v20231009")

    implementation("org.slf4j:slf4j-simple:2.0.5")
    include("org.slf4j:slf4j-simple:2.0.5")
    implementation("org.slf4j:slf4j-api:2.0.17")
    include("org.slf4j:slf4j-api:2.0.17")

    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.10")
    include("org.jetbrains.kotlin:kotlin-stdlib:1.9.10")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-common:1.9.10")
    include("org.jetbrains.kotlin:kotlin-stdlib-common:1.9.10")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.10")
    include("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.10")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.10")
    include("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.10")
    implementation("org.jetbrains:annotations:13.0")
    include("org.jetbrains:annotations:13.0")
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src/main/fabric"))
        resources.setSrcDirs(listOf("src/main/fabricResources"))
    }
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(21)
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()
        inputs.property("version", version)
        filesMatching("fabric.mod.json") {
            expand("version" to version)
        }
    }
}
