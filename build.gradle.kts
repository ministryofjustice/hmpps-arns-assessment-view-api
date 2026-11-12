plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "11.0.15"
  kotlin("plugin.spring") version "2.4.21"
  kotlin("plugin.jpa") version "2.4.21"
  id("org.jetbrains.kotlinx.kover") version "0.9.11"
}

sourceSets {
  create("e2eTest") {
    compileClasspath += sourceSets["main"].output + sourceSets["test"].output
    runtimeClasspath += sourceSets["main"].output + sourceSets["test"].output
  }
}

configurations.named("e2eTestImplementation") {
  extendsFrom(configurations.testImplementation.get())
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:3.1.0")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:7.4.1")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-webclient")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql")

  developmentOnly("org.springframework.boot:spring-boot-devtools")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:3.1.0")
  testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
  testImplementation("org.wiremock:wiremock-standalone:3.13.2")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.48") {
    exclude(group = "io.swagger.core.v3")
  }
}

kotlin {
  jvmToolchain(25)
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
  }

  test {
    exclude("**/integration/**")
    exclude("**/src/integrationTest/**")
  }

  register<Test>("integrationTest") {
    include("**/integration/**")
    useJUnitPlatform()
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
  }

  named("check") {
    dependsOn("integrationTest")
  }
}

tasks.register<Test>("e2eTest") {
  description = "Runs end-to-end tests."
  group = "verification"

  testClassesDirs = sourceSets["e2eTest"].output.classesDirs
  classpath = sourceSets["e2eTest"].runtimeClasspath

  // Optional: Force tests to run even if outputs haven't changed
  outputs.upToDateWhen { false }

  useJUnitPlatform()
}

tasks.named("e2eTest") {
  onlyIf {
    !gradle.startParameter.taskNames.any { it.contains("koverHtmlReport") }
  }
}

tasks.check {
  dependsOn(tasks.named("e2eTest"))
}

kover {
  reports {
    total {
      xml { xmlFile.set(layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml")) }
      log { onCheck = true }
    }
  }
}
