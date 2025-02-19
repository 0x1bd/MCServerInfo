plugins {
    kotlin("jvm") version "2.1.10"

    `maven-publish`
}

group = "com.kvxd"
version = "0.3.0"

repositories {
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation("com.google.code.gson:gson:2.12.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("net.kyori:adventure-text-serializer-gson:4.18.0")
    implementation("net.kyori:adventure-text-serializer-legacy:4.18.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    //enabled = false // Disable when building
}

kotlin {
    jvmToolchain(21)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["kotlin"])
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            pom {
                name.set("MCServerInfo")
                description.set("Ping minecraft servers and get information's about them.")
                url.set("https://github.com/0x1bd/MCServerInfo")
            }
        }
    }
    repositories {
        maven {
            url = uri("https://jitpack.io")
        }
    }
}