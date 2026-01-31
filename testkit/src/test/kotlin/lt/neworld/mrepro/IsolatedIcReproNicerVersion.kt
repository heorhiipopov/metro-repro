package lt.neworld.mrepro

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

class IsolatedIcReproNicerVersion {
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
    }

    @AfterEach
    fun cleanup() {
        println("Temp project location: $tempProjectDir")
    }

    @Test
    fun testIcBehavior() {
        setupProject()
        runScenario(incremental = true)
    }

    @Test
    fun testComparisonWithControl() {
        setupProject()

        // 1. Control
        val nonIncrementalStatus = runScenario(incremental = false)
        // 2. Test
        val incrementalStatus = runScenario(incremental = true)

        println("Comparison")
        println("NON-INCREMENTAL (Control): $nonIncrementalStatus")
        println("INCREMENTAL (Test): $incrementalStatus")

        assertEquals(nonIncrementalStatus,incrementalStatus)
    }

    @Test
    fun testNoInterfaceControl() {
        setupProjectNoInterface()

        println("Running control: no interface")

        // 1. Control (Non-IC)
        val nonIncrementalStatus = runScenario(incremental = false)
        // 2. Test (IC)
        val incrementalStatus = runScenario(incremental = true)

        println("Final control comparison:")
        println("NON-INCREMENTAL: $nonIncrementalStatus")
        println("INCREMENTAL: $incrementalStatus")

        assertEquals(nonIncrementalStatus, incrementalStatus)
    }

    private fun runScenario(incremental: Boolean): String {
        val mode = if (incremental) "INCREMENTAL" else "NON-INCREMENTAL"
        println("Starting Scenario: $mode")

        // Step 1: Baseline Clean Build
        writeModuleFile(moduleOriginal)
        runBuild("clean", incrementalCompilation = incremental)
        val result1 = runBuild(":app:compileKotlin", incrementalCompilation = incremental)
        println("Step 1 (Original): ${result1.task(":app:compileKotlin")?.outcome}")

        // Step 2: Change to OtherScope (Expect Failure)
        writeModuleFile(moduleOtherScope)
        val result2 = runBuildAllowFailure(":app:compileKotlin", incrementalCompilation = incremental)
        println("Step 2 (OtherScope): ${if (result2 != null) "SUCCESS" else "FAILED (expected)"}")

        // Step 3: Remove @ContributesTo (Expect Failure)
        writeModuleFile(moduleNoContributes)
        val result3 = runBuildAllowFailure(":app:compileKotlin", incrementalCompilation = incremental)
        val status = if (result3 != null) "SUCCESS" else "FAILED"
        println("Step 3 (No Contributes): $status")

        return status
    }


    private fun writeFile(path: String, content: String) {
        val file = tempProjectDir.resolve(path)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    private fun writeModuleFile(content: String) =
        writeFile("feature/src/main/kotlin/com/example/feature/UserApiModule.kt", content)

    private fun setupProject() {
        writeFile("settings.gradle.kts", """
            pluginManagement {
                repositories {
                    mavenLocal()
                    mavenCentral()
                    google()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositories {
                    mavenLocal()
                    mavenCentral()
                    google()
                }
            }
            rootProject.name = "metro-ic-reproducer"
            include(":core", ":api", ":feature", ":app")
        """.trimIndent())

        writeFile("gradle.properties", "org.gradle.jvmargs=-Xmx2g")

        writeFile("build.gradle.kts", """
            plugins {
                kotlin("jvm") version "2.3.0" apply false
                id("dev.zacsweers.metro") version "0.10.2" apply false
            }
        """.trimIndent())

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
            
            @Scope 
            @Retention(AnnotationRetention.RUNTIME) 
            annotation class AppScope
            
            @Scope 
            @Retention(AnnotationRetention.RUNTIME)
            annotation class OtherScope
        """.trimIndent())

        writeFile("api/build.gradle.kts", "plugins { kotlin(\"jvm\") }")
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
            class UserServiceImpl @Inject constructor(private val userApi: UserApi) : UserService {
                override fun doWork() = userApi.getCurrentUser()
            }
        """.trimIndent())

        writeFile("feature/src/main/kotlin/com/example/feature/SessionModule.kt", """
            package com.example.feature
            import com.example.core.AppScope
            import dev.zacsweers.metro.*
            
            @BindingContainer 
            @ContributesTo(AppScope::class)
            abstract class SessionModule {
                @Binds 
                abstract fun bindUserService(impl: UserServiceImpl): UserService
            }
        """.trimIndent())

        writeFile("app/build.gradle.kts", """
            plugins { kotlin("jvm"); id("dev.zacsweers.metro") }
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

    private fun setupProjectNoInterface() {
        setupProject()

        writeFile("feature/src/main/kotlin/com/example/feature/UserService.kt", """
        package com.example.feature
        import com.example.api.UserApi
        import com.example.core.AppScope
        import dev.zacsweers.metro.Inject
        
        @AppScope
        class UserService @Inject constructor(
            private val userApi: UserApi
        ) {
            fun doWork() = userApi.getCurrentUser()
        }
    """.trimIndent())

        val implFile = tempProjectDir.resolve("feature/src/main/kotlin/com/example/feature/UserServiceImpl.kt")
        if (implFile.exists()){
            implFile.delete()
        }

        val sessionModuleFile = tempProjectDir.resolve("feature/src/main/kotlin/com/example/feature/SessionModule.kt")
        if (sessionModuleFile.exists()) {
            sessionModuleFile.delete()
        }
    }

    private val moduleOriginal = """
        package com.example.feature
        import com.example.api.UserApi
        import com.example.core.AppScope
        import dev.zacsweers.metro.*
        
        @BindingContainer 
        @ContributesTo(AppScope::class)
        object UserApiModule {
            @Provides 
            fun provideUserApi(): UserApi = object : UserApi { override fun getCurrentUser() = "user" }
        }
    """.trimIndent()

    private val moduleOtherScope = """
        package com.example.feature
        import com.example.api.UserApi
        import com.example.core.OtherScope
        import dev.zacsweers.metro.*
        
        @BindingContainer 
        @ContributesTo(OtherScope::class)
        object UserApiModule {
            @Provides 
            fun provideUserApi(): UserApi = object : UserApi { override fun getCurrentUser() = "user" }
        }
    """.trimIndent()

    private val moduleNoContributes = """
        package com.example.feature
        import com.example.api.UserApi
        import dev.zacsweers.metro.*
        
        @BindingContainer
        object UserApiModule {
            @Provides 
            fun provideUserApi(): UserApi = object : UserApi { override fun getCurrentUser() = "user" }
        }
    """.trimIndent()

    private fun runBuild(vararg args: String, incrementalCompilation: Boolean): BuildResult {
        return GradleRunner.create()
            .withProjectDir(tempProjectDir)
            .withTestKitDir(testKitDir)
            .withArguments(*args, "-Pkotlin.incremental=$incrementalCompilation", "--stacktrace")
            .forwardOutput()
            .build()
    }

    private fun runBuildAllowFailure(vararg args: String, incrementalCompilation: Boolean): BuildResult? {
        return try {
            runBuild(*args, incrementalCompilation = incrementalCompilation)
        } catch (_: Exception) {
            null
        }
    }
}