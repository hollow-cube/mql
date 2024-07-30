plugins {
    `java-library`

    `maven-publish`
    signing
    alias(libs.plugins.nexuspublish)
}

group = "dev.hollowcube"
version = System.getenv("TAG_VERSION") ?: "dev"
description = "An interpreter and JIT compiler for a subset of Molang"

repositories {
    mavenCentral()
}

dependencies {
    api(libs.jetbrainsAnnotations)
    implementation(libs.bundles.asm)

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java {
    withSourcesJar()
    withJavadocJar()

    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.test {
    useJUnitPlatform()

//    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

nexusPublishing {
    this.packageGroup.set("dev.hollowcube")

    repositories.sonatype {
        nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
        snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))

        if (System.getenv("SONATYPE_USERNAME") != null) {
            username.set(System.getenv("SONATYPE_USERNAME"))
            password.set(System.getenv("SONATYPE_PASSWORD"))
        }
    }
}

publishing.publications.create<MavenPublication>("maven") {
    groupId = "dev.hollowcube"
    artifactId = "mql"
    version = project.version.toString()

    from(project.components["java"])

    pom {
        name.set(artifactId)
        description.set(project.description)
        url.set("https://github.com/hollow-cube/mql")

        licenses {
            license {
                name.set("MIT")
                url.set("https://github.com/hollow-cube/mql/blob/main/LICENSE")
            }
        }

        developers {
            developer {
                id.set("mworzala")
                name.set("Matt Worzala")
                email.set("matt@hollowcube.dev")
            }
        }

        issueManagement {
            system.set("GitHub")
            url.set("https://github.com/hollow-cube/mql/issues")
        }

        scm {
            connection.set("scm:git:git://github.com/hollow-cube/mql.git")
            developerConnection.set("scm:git:git@github.com:hollow-cube/mql.git")
            url.set("https://github.com/hollow-cube/mql")
            tag.set(System.getenv("TAG_VERSION") ?: "HEAD")
        }

        ciManagement {
            system.set("Github Actions")
            url.set("https://github.com/hollow-cube/mql/actions")
        }
    }
}

signing {
    isRequired = System.getenv("CI") != null

    val privateKey = System.getenv("GPG_PRIVATE_KEY")
    val keyPassphrase = System.getenv()["GPG_PASSPHRASE"]
    useInMemoryPgpKeys(privateKey, keyPassphrase)

    sign(publishing.publications)
}