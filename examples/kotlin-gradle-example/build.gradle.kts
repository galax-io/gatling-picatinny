plugins {
    kotlin("jvm") version "1.9.25"
    id("io.gatling.gradle") version "3.11.5.2"
}

val gatlingVersion = "3.11.5"
val picatinnyVersion = providers.gradleProperty("picatinnyVersion").orElse("0.0.0-ci-local")

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

gatling {
    this.gatlingVersion = gatlingVersion
    scalaVersion = "2.13.18"
    includeMainOutput = false
    includeTestOutput = false
}

dependencies {
    gatling("org.galaxio:gatling-picatinny_2.13:${picatinnyVersion.get()}")
    gatling("io.gatling.highcharts:gatling-charts-highcharts:$gatlingVersion")
    gatling("io.gatling:gatling-test-framework:$gatlingVersion")
}
