plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.nexus.publish)
}

group = "top.ntutn"

val mavenCentralUsername = providers.gradleProperty("mavenCentralUsername")
    .orElse(providers.environmentVariable("MAVEN_CENTRAL_USERNAME"))
val mavenCentralPassword = providers.gradleProperty("mavenCentralPassword")
    .orElse(providers.environmentVariable("MAVEN_CENTRAL_PASSWORD"))
val signingKey = providers.gradleProperty("signingKey")
    .orElse(providers.environmentVariable("SIGNING_KEY"))

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            username.set(mavenCentralUsername)
            password.set(mavenCentralPassword)
        }
    }
    packageGroup.set("top.ntutn")
}

tasks.register("checkMavenCentralCredentials") {
    group = "publishing"
    description = "Checks that Maven Central and signing credentials are configured."

    inputs.property(
        "mavenCentralUsernameConfigured",
        mavenCentralUsername.map { it.isNotBlank() }.orElse(false),
    )
    inputs.property(
        "mavenCentralPasswordConfigured",
        mavenCentralPassword.map { it.isNotBlank() }.orElse(false),
    )
    inputs.property(
        "signingKeyConfigured",
        signingKey.map { it.isNotBlank() }.orElse(false),
    )

    doLast {
        val configured = inputs.properties
        val missing = buildList {
            if (configured["mavenCentralUsernameConfigured"] != true) add("mavenCentralUsername")
            if (configured["mavenCentralPasswordConfigured"] != true) add("mavenCentralPassword")
            if (configured["signingKeyConfigured"] != true) add("signingKey")
        }
        check(missing.isEmpty()) {
            "Missing Maven Central credentials: ${missing.joinToString()}"
        }
    }
}
