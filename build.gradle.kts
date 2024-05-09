plugins {
  id("java")
  id("maven-publish")
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
  repositories {
    maven {
      url = uri("https://git.taskwolf.net/api/v4/projects/22/packages/maven")
      credentials(HttpHeaderCredentials::class) {
        name = "Private-Token"
        value = System.getenv("TASKWOLF_GITLAB_PRIVATE_TOKEN") ?:
          findProperty("taskwolfGitlabPrivateToken") as String?
      }
      authentication {
        create("header", HttpHeaderAuthentication::class)
      }
    }
  }
}

repositories {
  mavenCentral()
  maven {
    url = uri("https://git.taskwolf.net/api/v4/projects/8/packages/maven")
    credentials(HttpHeaderCredentials::class) {
      name = "Private-Token"
      value = System.getenv("TASKWOLF_GITLAB_PRIVATE_TOKEN") ?:
        findProperty("taskwolfGitlabPrivateToken") as String?
    }
    authentication {
      create("header", HttpHeaderAuthentication::class)
    }
  }
}

dependencies {
  testCompileOnly(platform("org.junit:junit-bom:5.10.2"))
  testCompileOnly("org.junit.jupiter:junit-jupiter:5.10.2")

  compileOnly("net.taskwolf:core:1.0.0-SNAPSHOT")

  compileOnly("com.google.inject:guice:7.0.0")

  compileOnly("com.google.guava:guava:33.1.0-jre")

  compileOnly("org.projectlombok:lombok:1.18.32")
  annotationProcessor("org.projectlombok:lombok:1.18.32")
  testCompileOnly("org.projectlombok:lombok:1.18.32")
  testAnnotationProcessor("org.projectlombok:lombok:1.18.32")

  compileOnly("org.json:json:20240303")
  compileOnly("commons-io:commons-io:2.16.1")

  compileOnly("org.springframework.boot:spring-boot-starter-web:3.2.5")
}

tasks.test {
  useJUnitPlatform()
}

tasks.jar {
  val dependencies = configurations.runtimeClasspath.get().map(::zipTree)
  from(dependencies)
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}