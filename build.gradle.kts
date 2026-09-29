plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.spring") version "2.4.10"
    kotlin("plugin.jpa") version "2.4.10"
    kotlin("kapt") version "2.4.10"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("jacoco")
    id("org.sonarqube") version "7.4.0.8496"
}

group = "team.incube"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation(Libs.THE_SDK)
    implementation(Libs.DATAGSM_OAUTH_SDK)
    implementation(Libs.JJWT_API)
    runtimeOnly(Libs.JJWT_IMPL)
    runtimeOnly(Libs.JJWT_JACKSON)
    implementation(Libs.SPRING_BOOT_JPA)
    implementation(Libs.SPRING_BOOT_REDIS)
    implementation(Libs.SPRING_BOOT_GRAPHQL)
    implementation(Libs.GRAPHQL_EXTENDED_SCALARS)
    implementation(Libs.AWS_SDK_S3)
    implementation(Libs.APACHE_POI)
    implementation(Libs.SPRING_BOOT_SECURITY)
    implementation(Libs.SPRING_BOOT_WEB)
    implementation(Libs.JACKSON_KOTLIN)
    implementation(Libs.KOTLIN_REFLECT)
    implementation(Libs.QUERYDSL_JPA)

    kapt(Libs.QUERYDSL_KAPT)

    implementation(Libs.SPRINGDOC_OPENAPI)

    compileOnly(Libs.LOMBOK)
    kapt(Libs.LOMBOK)

    runtimeOnly(Libs.MYSQL_CONNECTOR)
    implementation(Libs.SPRING_BOOT_FLYWAY)
    implementation(Libs.FLYWAY_MYSQL)

    testImplementation(Libs.SPRING_BOOT_TEST)
    testImplementation(Libs.KOTLIN_TEST_JUNIT5)
    testImplementation(Libs.SPRING_GRAPHQL_TEST)
    testImplementation(Libs.SPRING_SECURITY_TEST)
    testImplementation(Libs.MOCKK)
    testImplementation(Libs.KOTEST_RUNNER)
    testImplementation(Libs.KOTEST_ASSERTIONS)
    testCompileOnly(Libs.LOMBOK)
    kaptTest(Libs.LOMBOK)

    testRuntimeOnly(Libs.JUNIT_PLATFORM_LAUNCHER)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

ktlint {
    version.set("1.8.0")
}

kapt {
    correctErrorTypes = true
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

ktlint {
    filter {
        exclude("**/generated/**")
    }
}

jacoco {
    toolVersion = "0.8.14"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

sonar {
    properties {
        property("sonar.projectKey", "team-incube_GSMC-server-V4")
        property("sonar.organization", "team-incube")
        property("sonar.host.url", "https://sonarcloud.io")
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            "${layout.buildDirectory.get()}/reports/jacoco/test/jacocoTestReport.xml",
        )
        property("sonar.exclusions", "src/main/resources/db/migration/**")
        property("sonar.issue.ignore.multicriteria", "portInFunctionalInterface")
        property("sonar.issue.ignore.multicriteria.portInFunctionalInterface.ruleKey", "kotlin:S6517")
        property(
            "sonar.issue.ignore.multicriteria.portInFunctionalInterface.resourceKey",
            "src/main/kotlin/**/port/in/**/*.kt",
        )
    }
}
