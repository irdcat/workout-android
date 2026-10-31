import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.github.irdcat.workout"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.github.irdcat.workout"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Starts HiltTestApplication so device tests get the test bindings.
        testInstrumentationRunner = "com.github.irdcat.workout.HiltTestRunner"

        // OAuth 2.0 *Web application* client ID from Google Cloud Console, used for Sign in with Google.
        // Kept out of the repository: set google.serverClientId in local.properties.
        resValue("string", "google_server_client_id", googleServerClientId())
    }

    buildTypes {
        debug {
            // JaCoCo report for the JVM tests: ./gradlew createDebugUnitTestCoverageReport
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time (LocalDate) on API 24–25.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        // google_server_client_id comes from local.properties via resValue.
        resValues = true
    }

    sourceSets {
        // Fakes and the navigation tests run both on the JVM (Robolectric) and on a device.
        getByName("test").kotlin.srcDir("src/sharedTest/kotlin")
        getByName("androidTest").kotlin.srcDir("src/sharedTest/kotlin")
    }

    testOptions {
        // Robolectric reads the merged manifest (the Compose test activity) and the app's fonts.
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // Robolectric loads classes through its own class loader; without this JaCoCo misses them.
            test.extensions.configure(JacocoTaskExtension::class) {
                isIncludeNoLocationClasses = true
                excludes = listOf("jdk.internal.*")
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Google sign-in (Credential Manager, AuthorizationClient) and the Sheets API
    implementation(libs.androidx.credentials)
    // Credential Manager's provider on devices with Play Services; without it getCredential fails at runtime
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.client.gson)
    implementation(libs.google.http.client.gson)
    implementation(libs.google.api.services.sheets)

    // Storage: Room caches the plan and the session log; DataStore keeps settings and the session
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    // JVM tests; Compose UI tests run under Robolectric
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // Navigation tests start the real activity with test bindings in place of the Hilt modules.
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    // The activity Compose UI tests host their content in.
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Device tests
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}

fun googleServerClientId(): String {
    val properties = Properties()
    rootProject.file("local.properties").takeIf { it.isFile }?.reader()?.use(properties::load)
    return properties.getProperty("google.serverClientId").orEmpty().also {
        if (it.isEmpty()) logger.warn("google.serverClientId is not set in local.properties; Sign in with Google will not work.")
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}
