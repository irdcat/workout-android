import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import com.android.build.gradle.internal.tasks.factory.dependsOn

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProps = gradleLocalProperties(rootProject.rootDir, providers)
val spreadsheetId = localProps.getProperty("spreadsheetId")
val range = "A1:H200"
tasks.register("buildStringResources") {
    group = "resources"
    description = "Builds strings.xml file with provided spreadsheetId and range"

    doLast {
        val stringsFile = file(project.layout.projectDirectory
            .file("src/main/res/values/strings.xml"))
        if (stringsFile.exists()) {
            stringsFile.delete()
        }
        stringsFile.createNewFile()
        val xml = """
            <resources>
                <string name="app_name">Workout</string>
                <string name="spreadsheet_id">${spreadsheetId}</string>
                <string name="spreadsheet_range">${range}</string>
            </resources>
        """.trimIndent()
        stringsFile.writeText(xml)
    }
}

tasks.named("build")
    .dependsOn("buildStringResources")

android {
    namespace = "com.github.irdcat.workout"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.github.irdcat.workout"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            pickFirsts += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    // Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Android Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.composetheme)
    implementation(libs.composetheme.material3)

    // Google APIs
    implementation(libs.googleid)
    implementation(libs.play.services.auth)
    implementation(libs.google.api.client.android)
    implementation(libs.google.http.client.gson)
    implementation(libs.google.api.services.sheets)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}