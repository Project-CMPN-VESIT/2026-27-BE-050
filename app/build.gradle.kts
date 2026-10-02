import java.util.Properties

fun String.kotlinStringLiteral(): String = replace("\\", "\\\\").replace("\"", "\\\"")

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val groqApiKey = (localProperties.getProperty("groq_api_key") ?: System.getenv("GROQ_API_KEY") ?: "").trim()
val weatherstackApiKey = (localProperties.getProperty("weatherstack_api_key") ?: System.getenv("WEATHERSTACK_API_KEY") ?: "").trim()
val mediastackApiKey = (localProperties.getProperty("mediastack_api_key") ?: System.getenv("MEDIASTACK_API_KEY") ?: "").trim()
val xBearerToken = (localProperties.getProperty("x_bearer_token") ?: System.getenv("X_BEARER_TOKEN") ?: "").trim()
val youtubeApiKey = (localProperties.getProperty("youtube_api_key") ?: System.getenv("YOUTUBE_API_KEY") ?: "").trim()
val redditClientId = (localProperties.getProperty("reddit_client_id") ?: System.getenv("REDDIT_CLIENT_ID") ?: "").trim()
val redditClientSecret = (localProperties.getProperty("reddit_client_secret") ?: System.getenv("REDDIT_CLIENT_SECRET") ?: "").trim()
val redditUserAgent = (localProperties.getProperty("reddit_user_agent") ?: System.getenv("REDDIT_USER_AGENT") ?: "").trim()
val redditRefreshToken = (localProperties.getProperty("reddit_refresh_token") ?: System.getenv("REDDIT_REFRESH_TOKEN") ?: "").trim()
val imdApiKey = (localProperties.getProperty("imd_api_key") ?: System.getenv("IMD_API_KEY") ?: "").trim()
val backendBaseUrl = (localProperties.getProperty("backend_base_url") ?: System.getenv("BACKEND_BASE_URL") ?: "http://10.0.2.2:3000/").trim()

plugins {
    alias(libs.plugins.android.application)

    id("org.jetbrains.kotlin.android")

    // REMOVE kapt
    // id("org.jetbrains.kotlin.kapt")

    // ADD KSP
    id("com.google.devtools.ksp")

    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.capstone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.capstone"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "GROQ_API_KEY", "\"${groqApiKey.kotlinStringLiteral()}\"")
        buildConfigField("String", "WEATHERSTACK_API_KEY", "\"${weatherstackApiKey.kotlinStringLiteral()}\"")
        buildConfigField("String", "MEDIASTACK_API_KEY", "\"${mediastackApiKey.kotlinStringLiteral()}\"")
        buildConfigField("String", "X_BEARER_TOKEN", "\"${xBearerToken.kotlinStringLiteral()}\"")
        buildConfigField("String", "YOUTUBE_API_KEY", "\"${youtubeApiKey.kotlinStringLiteral()}\"")
        buildConfigField("String", "REDDIT_CLIENT_ID", "\"${redditClientId.kotlinStringLiteral()}\"")
        buildConfigField("String", "REDDIT_CLIENT_SECRET", "\"${redditClientSecret.kotlinStringLiteral()}\"")
        buildConfigField("String", "REDDIT_USER_AGENT", "\"${redditUserAgent.kotlinStringLiteral()}\"")
        buildConfigField("String", "REDDIT_REFRESH_TOKEN", "\"${redditRefreshToken.kotlinStringLiteral()}\"")
        buildConfigField("String", "IMD_API_KEY", "\"${imdApiKey.kotlinStringLiteral()}\"")
        buildConfigField("String", "BACKEND_BASE_URL", "\"${backendBaseUrl.kotlinStringLiteral()}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
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
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("androidx.preference:preference-ktx:1.2.1")

    // Network
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Video playback
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // Maps
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.8.1"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // WorkManager for background tasks
    implementation("androidx.work:work-runtime-ktx:2.8.1")
    // Nearby Connections for mesh networking (Phase 5)
    implementation("com.google.android.gms:play-services-nearby:18.0.0")

    // Room for Phase 5 local mesh persistence
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")

    ksp("androidx.room:room-compiler:2.7.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}