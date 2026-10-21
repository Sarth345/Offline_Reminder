package com.example.offlinereminder

import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Phase 13 Architecture Verification Test.
 * Ensures strict Clean Architecture compliance:
 * 1. Domain layer must have zero dependencies on Android, Compose, or Room.
 * 2. Domain layer must remain pure Kotlin / standard Java time.
 */
class Phase13ArchitectureTest {

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
}
