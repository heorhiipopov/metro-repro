package lt.neworld.mrepro

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class IsolatedIcRepro {
    private val testKitDir: File by lazy {
        File(System.getProperty("java.io.tmpdir"), "gradle-testkit-standalone").also {
            it.mkdirs()
        }
    }

    private lateinit var tempProjectDir: File

    @BeforeEach
    fun setup() {
        tempProjectDir = File(System.getProperty("java.io.tmpdir"),
            "metro-ic-repro-${System.currentTimeMillis()}")
        tempProjectDir.mkdirs()
        println("Created temp project at: $tempProjectDir")
    }

    @AfterEach
    fun cleanup() {
        println("Temp project location: $tempProjectDir")
    }

    private fun writeFile(path: String, content: String) {
        val file = tempProjectDir.resolve(path)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    private fun setupProject() {
        writeFile("settings.gradle.kts", """
            pluginManagement {
                repositories {
                    mavenLocal()
                    mavenCentral()
                    google()
                    gradlePluginPortal()
                    maven("https://oss.sonatype.org/content/repositories/snapshots")
                }
            }

            dependencyResolutionManagement {
                repositories {
                    mavenLocal()
                    mavenCentral()
                    google()
                    maven("https://oss.sonatype.org/content/repositories/snapshots")
                }
            }

            rootProject.name = "metro-ic-reproducer"
            include(":core")
            include(":api")
            include(":feature")
            include(":app")
        """.trimIndent())

        writeFile("gradle.properties", """
            org.gradle.jvmargs=-Xmx2g
        """.trimIndent())

        writeFile("build.gradle.kts", """
            plugins {
                kotlin("jvm") version "2.3.0" apply false
                id("dev.zacsweers.metro") version "0.10.2" apply false
            }
        """.trimIndent())

        writeFile("core/build.gradle.kts", """
            plugins {
                kotlin("jvm")
                id("dev.zacsweers.metro") version "0.10.2" apply false
            }

            dependencies {
                implementation("dev.zacsweers.metro:runtime:0.10.2")
            }
        """.trimIndent())

        writeFile("core/src/main/kotlin/com/example/core/Scopes.kt", """
            package com.example.core

            import dev.zacsweers.metro.Scope

            @Scope
            @Retention(AnnotationRetention.RUNTIME)
            annotation class AppScope

            @Scope
            @Retention(AnnotationRetention.RUNTIME)
            annotation class OtherScope
        """.trimIndent())

        writeFile("api/build.gradle.kts", """
            plugins {
                kotlin("jvm")
                id("dev.zacsweers.metro") version "0.10.2" apply false
            }
        """.trimIndent())

        writeFile("api/src/main/kotlin/com/example/api/Types.kt", """
            package com.example.api

            interface UserApi {
                fun getCurrentUser(): String
            }
        """.trimIndent())

        writeFile("feature/build.gradle.kts", """
            plugins {
                kotlin("jvm")
                id("dev.zacsweers.metro")
            }

            dependencies {
                implementation(project(":core"))
                implementation(project(":api"))
            }
        """.trimIndent())

        writeFile("feature/src/main/kotlin/com/example/feature/UserService.kt", """
            package com.example.feature

            interface UserService {
                fun doWork(): String
            }
        """.trimIndent())

        writeFile("feature/src/main/kotlin/com/example/feature/UserServiceImpl.kt", """
            package com.example.feature

            import com.example.api.UserApi
            import com.example.core.AppScope
            import dev.zacsweers.metro.Inject

            @AppScope
            class UserServiceImpl @Inject constructor(
                private val userApi: UserApi
            ) : UserService {
                override fun doWork() = userApi.getCurrentUser()
            }
        """.trimIndent())

        writeFile("feature/src/main/kotlin/com/example/feature/SessionModule.kt", """
            package com.example.feature

            import com.example.core.AppScope
            import dev.zacsweers.metro.Binds
            import dev.zacsweers.metro.BindingContainer
            import dev.zacsweers.metro.ContributesTo

            @BindingContainer
            @ContributesTo(AppScope::class)
            abstract class SessionModule {
                @Binds
                abstract fun bindUserService(impl: UserServiceImpl): UserService
            }
        """.trimIndent())

        writeFile("app/build.gradle.kts", """
            plugins {
                kotlin("jvm")
                id("dev.zacsweers.metro")
            }

            dependencies {
                implementation(project(":core"))
                implementation(project(":api"))
                implementation(project(":feature"))
            }
        """.trimIndent())

        writeFile("app/src/main/kotlin/com/example/app/AppGraph.kt", """
            package com.example.app

            import com.example.core.AppScope
            import com.example.feature.UserService
            import dev.zacsweers.metro.DependencyGraph

            @AppScope
            @DependencyGraph(scope = AppScope::class)
            interface AppGraph {
                val userService: UserService
            }
        """.trimIndent())
    }

    private fun writeModuleFile(content: String) {
        writeFile("feature/src/main/kotlin/com/example/feature/UserApiModule.kt", content)
    }

    private val moduleOriginal = """
        package com.example.feature

        import com.example.api.UserApi
        import com.example.core.AppScope
        import dev.zacsweers.metro.BindingContainer
        import dev.zacsweers.metro.ContributesTo
        import dev.zacsweers.metro.Provides

        @BindingContainer
        @ContributesTo(AppScope::class)
        object UserApiModule {
            @Provides
            fun provideUserApi(): UserApi {
                return object : UserApi {
                    override fun getCurrentUser() = "user"
                }
            }
        }
    """.trimIndent()

    private val moduleOtherScope = """
        package com.example.feature

        import com.example.api.UserApi
        import com.example.core.OtherScope
        import dev.zacsweers.metro.BindingContainer
        import dev.zacsweers.metro.ContributesTo
        import dev.zacsweers.metro.Provides

        @BindingContainer
        @ContributesTo(OtherScope::class)
        object UserApiModule {
            @Provides
            fun provideUserApi(): UserApi {
                return object : UserApi {
                    override fun getCurrentUser() = "user"
                }
            }
        }
    """.trimIndent()

    private val moduleNoContributes = """
        package com.example.feature

        import com.example.api.UserApi
        import dev.zacsweers.metro.BindingContainer
        import dev.zacsweers.metro.Provides

        @BindingContainer
        object UserApiModule {
            @Provides
            fun provideUserApi(): UserApi {
                return object : UserApi {
                    override fun getCurrentUser() = "user"
                }
            }
        }
    """.trimIndent()

    private fun runBuild(vararg args: String, incrementalCompilation: Boolean = true): BuildResult {
        val icArg = "-Pkotlin.incremental=$incrementalCompilation"
        return GradleRunner.create()
            .withProjectDir(tempProjectDir)
            .withTestKitDir(testKitDir)
            .withArguments(*args, icArg, "--stacktrace")
            .forwardOutput()
            .build()
    }

    private fun runBuildAllowFailure(vararg args: String, incrementalCompilation: Boolean = true): BuildResult? {
        return try {
            runBuild(*args, incrementalCompilation = incrementalCompilation)
        } catch (e: Exception) {
            println("Build failed: ${e.message?.take(300)}")
            null
        }
    }

    @Test
    fun `standalone minimal - reproduces IC bug`() {

        setupProject()
        println("IC ENABLED")

        writeModuleFile(moduleOriginal)
        println("Step 1: Clean build with @ContributesTo(AppScope)")
        runBuild("clean", incrementalCompilation = true)
        val cleanResult = runBuild(":app:compileKotlin", incrementalCompilation = true)
        println("Clean build result: ${cleanResult.task(":app:compileKotlin")?.outcome}")

        println("Step 2: Change to @ContributesTo(OtherScope)")
        writeModuleFile(moduleOtherScope)
        val step2Result = runBuildAllowFailure(":app:compileKotlin", incrementalCompilation = true)
        println("Step 2 result: ${if (step2Result != null) "SUCCESS" else "FAILED (expected)"}")

        println("Step 3: Remove @ContributesTo entirely")
        writeModuleFile(moduleNoContributes)
        val icEnabledResult = runBuildAllowFailure(":app:compileKotlin", incrementalCompilation = true)
        val icEnabledStatus = if (icEnabledResult != null) "SUCCESS" else "FAILED"
        println("Step 3 result: $icEnabledStatus")

        println("IC DISABLED")

        writeModuleFile(moduleOriginal)
        println("Step 1: Clean build with @ContributesTo(AppScope)")
        runBuild("clean", incrementalCompilation = false)
        runBuild(":app:compileKotlin", incrementalCompilation = false)
        println("Clean build succeeded")

        println("Step 2: Change to @ContributesTo(OtherScope)")
        writeModuleFile(moduleOtherScope)
        runBuildAllowFailure(":app:compileKotlin", incrementalCompilation = false)
        println("Step 2: (expected to fail)")

        println("Step 3: Remove @ContributesTo entirely")
        writeModuleFile(moduleNoContributes)
        val icDisabledResult = runBuildAllowFailure(":app:compileKotlin", incrementalCompilation = false)
        val icDisabledStatus = if (icDisabledResult != null) "SUCCESS" else "FAILED"
        println("Step 3 result: $icDisabledStatus")

        println("COMPARISON")
        println("IC ENABLED: $icEnabledStatus")
        println("IC DISABLED: $icDisabledStatus")

        if (icEnabledStatus != icDisabledStatus) {
            println("BUG REPRODUCED!")
            println("Temp project location: $tempProjectDir")
        } else {
            println("Behavior is the same - bug NOT reproduced")
        }
    }

    @Test
    fun `standalone no interface - no bug reproduction`() {
        setupProjectNoInterface()

        println("IC ENABLED")

        writeModuleFile(moduleOriginal)
        runBuild("clean", incrementalCompilation = true)
        runBuild(":app:compileKotlin", incrementalCompilation = true)
        println("Step 1: Clean build - SUCCESS")

        writeModuleFile(moduleOtherScope)
        runBuildAllowFailure(":app:compileKotlin", incrementalCompilation = true)
        println("Step 2: Change scope - (expected fail)")

        writeModuleFile(moduleNoContributes)
        val icEnabledResult = runBuildAllowFailure(":app:compileKotlin", incrementalCompilation = true)
        val icEnabledStatus = if (icEnabledResult != null) "SUCCESS" else "FAILED"
        println("Step 3: Remove @ContributesTo - $icEnabledStatus")

        println("IC DISABLED")

        writeModuleFile(moduleOriginal)
        runBuild("clean", incrementalCompilation = false)
        runBuild(":app:compileKotlin", incrementalCompilation = false)
        println("Step 1: Clean build - SUCCESS")

        writeModuleFile(moduleOtherScope)
        runBuildAllowFailure(":app:compileKotlin", incrementalCompilation = false)
        println("Step 2: Change scope - (expected fail)")

        writeModuleFile(moduleNoContributes)
        val icDisabledResult = runBuildAllowFailure(":app:compileKotlin", incrementalCompilation = false)
        val icDisabledStatus = if (icDisabledResult != null) "SUCCESS" else "FAILED"
        println("Step 3: Remove @ContributesTo - $icDisabledStatus")

        println("COMPARISON")
        println("IC ENABLED: $icEnabledStatus")
        println("IC DISABLED: $icDisabledStatus")

        if (icEnabledStatus == icDisabledStatus) {
            println("Behavior is the same - no bug (expected)")
        } else {
            println("Unexpected: Bug reproduced without interface!")
        }
    }

    private fun setupProjectNoInterface() {
        writeFile("settings.gradle.kts", """
            pluginManagement {
                repositories {
                    mavenLocal()
                    mavenCentral()
                    google()
                    gradlePluginPortal()
                    maven("https://oss.sonatype.org/content/repositories/snapshots")
                }
            }

            dependencyResolutionManagement {
                repositories {
                    mavenLocal()
                    mavenCentral()
                    google()
                    maven("https://oss.sonatype.org/content/repositories/snapshots")
                }
            }

            rootProject.name = "metro-ic-reproducer"
            include(":core")
            include(":api")
            include(":feature")
            include(":app")
        """.trimIndent())

        writeFile("gradle.properties", "org.gradle.jvmargs=-Xmx2g")

        writeFile("build.gradle.kts", """
            plugins {
                kotlin("jvm") version "2.3.0" apply false
                id("dev.zacsweers.metro") version "0.10.2" apply false
            }
        """.trimIndent())

        // core module
        writeFile("core/build.gradle.kts", """
            plugins {
                kotlin("jvm")
                id("dev.zacsweers.metro")
            }
            dependencies {
                implementation("dev.zacsweers.metro:runtime:0.10.2")
            }
        """.trimIndent())

        writeFile("core/src/main/kotlin/com/example/core/Scopes.kt", """
            package com.example.core
            import dev.zacsweers.metro.Scope
            @Scope @Retention(AnnotationRetention.RUNTIME) annotation class AppScope
            @Scope @Retention(AnnotationRetention.RUNTIME) annotation class OtherScope
        """.trimIndent())

        writeFile("api/build.gradle.kts", "plugins { kotlin(\"jvm\") }")
        writeFile("api/src/main/kotlin/com/example/api/Types.kt", """
            package com.example.api
            interface UserApi { fun getCurrentUser(): String }
        """.trimIndent())

        writeFile("feature/build.gradle.kts", """
            plugins {
                kotlin("jvm")
                id("dev.zacsweers.metro")
            }
            dependencies {
                implementation(project(":core"))
                implementation(project(":api"))
            }
        """.trimIndent())

        writeFile("feature/src/main/kotlin/com/example/feature/UserService.kt", """
            package com.example.feature
            import com.example.api.UserApi
            import com.example.core.AppScope
            import dev.zacsweers.metro.Inject
            @AppScope
            class UserService @Inject constructor(private val userApi: UserApi) {
                fun doWork() = userApi.getCurrentUser()
            }
        """.trimIndent())

        writeFile("app/build.gradle.kts", """
            plugins {
                kotlin("jvm")
                id("dev.zacsweers.metro")
            }
            dependencies {
                implementation(project(":core"))
                implementation(project(":api"))
                implementation(project(":feature"))
            }
        """.trimIndent())

        writeFile("app/src/main/kotlin/com/example/app/AppGraph.kt", """
            package com.example.app
            import com.example.core.AppScope
            import com.example.feature.UserService
            import dev.zacsweers.metro.DependencyGraph
            @AppScope
            @DependencyGraph(scope = AppScope::class)
            interface AppGraph { val userService: UserService }
        """.trimIndent())
    }
}