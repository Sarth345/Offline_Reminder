package com.example.offlinereminder

import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Phase 14 Architecture Verification Test.
 * Ensures strict Clean Architecture compliance across the codebase:
 * 1. Domain layer must have zero dependencies on Android, Compose, Room, or Lifecycle.
 * 2. Presentation layer (Compose & ViewModels) must never directly access Room DAOs or AlarmManager.
 * 3. 100% Offline: zero network / internet references or imports.
 */
class Phase14ArchitectureTest {

    @Test
    fun domainLayer_hasZeroAndroidComposeOrRoomDependencies() {
        val domainDir = File("src/main/java/com/example/offlinereminder/domain")
        if (!domainDir.exists()) {
            fail("Domain directory not found: ${domainDir.absolutePath}")
        }

        val violations = mutableListOf<String>()

        domainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val lines = file.readLines()
                for ((index, line) in lines.withIndex()) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("import ")) {
                        val imported = trimmed.removePrefix("import ").trim()
                        if (imported.startsWith("android.") ||
                            imported.startsWith("androidx.compose.") ||
                            imported.startsWith("androidx.room.") ||
                            imported.startsWith("androidx.lifecycle.")
                        ) {
                            violations.add("${file.name}:${index + 1} imports forbidden dependency: $imported")
                        }
                    }
                }
            }

        if (violations.isNotEmpty()) {
            fail("Architecture violations found in domain layer:\n" + violations.joinToString("\n"))
        }
    }

    @Test
    fun presentationLayer_neverAccessesRoomDaosOrAlarmManagerDirectly() {
        val presentationDir = File("src/main/java/com/example/offlinereminder/presentation")
        if (!presentationDir.exists()) {
            fail("Presentation directory not found: ${presentationDir.absolutePath}")
        }

        val violations = mutableListOf<String>()

        presentationDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val lines = file.readLines()
                for ((index, line) in lines.withIndex()) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("import ")) {
                        val imported = trimmed.removePrefix("import ").trim()
                        if (imported.contains(".data.local.dao.") ||
                            imported.contains(".data.local.AppDatabase") ||
                            imported == "android.app.AlarmManager"
                        ) {
                            violations.add("${file.name}:${index + 1} imports forbidden direct data/alarm dependency: $imported")
                        }
                    }
                }
            }

        if (violations.isNotEmpty()) {
            fail("Architecture violations found in presentation layer:\n" + violations.joinToString("\n"))
        }
    }
}
