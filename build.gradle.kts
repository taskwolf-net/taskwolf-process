plugins {
  id("java")
  id("maven-publish")
  id("io.freefair.lombok") version "9.8.0"
}

group = "net.taskwolf"
version = "1.0.0-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_21
java.targetCompatibility = JavaVersion.VERSION_21

publishing {
  publications {
    create<MavenPublication>("library") {
      from(components["java"])
    }
  }
}

repositories {
  mavenCentral()
  mavenLocal()
}

dependencies {
  testCompileOnly(platform("org.junit:junit-bom:6.1.3"))
  testCompileOnly("org.junit.jupiter:junit-jupiter:6.1.3")

  compileOnly("net.taskwolf:core:1.0.0-SNAPSHOT")
  compileOnly("net.taskwolf:workflow:1.0.0-SNAPSHOT")

  compileOnly("com.google.inject:guice:7.0.0")

  compileOnly("com.google.guava:guava:33.7.2-jre")

  compileOnly("org.projectlombok:lombok:1.18.48")
  annotationProcessor("org.projectlombok:lombok:1.18.48")
  testCompileOnly("org.projectlombok:lombok:1.18.48")
  testAnnotationProcessor("org.projectlombok:lombok:1.18.48")

  compileOnly("org.json:json:20260814")
  compileOnly("commons-io:commons-io:2.22.0")

  compileOnly("org.springframework.boot:spring-boot-starter-web:4.1.1")

  compileOnly("com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20260924.2")
}

tasks.test {
  useJUnitPlatform()
}

tasks.jar {
  val dependencies = configurations.runtimeClasspath.get().map(::zipTree)
  from(dependencies)
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}