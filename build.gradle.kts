plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"

    `maven-publish`
}

group = "com.kvxd"
version = "0.1.2"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    enabled = false // Disable when building
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