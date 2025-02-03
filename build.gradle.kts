plugins {
    kotlin("jvm") version "2.1.10"

    `maven-publish`
}

group = "com.kvxd"
version = "0.1.4.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.12.1")

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