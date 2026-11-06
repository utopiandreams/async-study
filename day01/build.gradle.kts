plugins {
    kotlin("jvm")
    kotlin("plugin.spring") version "2.4.21"
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
}
