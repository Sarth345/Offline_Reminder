package com.example.offlinereminder.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies strict Phase 12C clean architecture boundaries:
 * - Domain layer is 100% pure Kotlin with zero Android/framework dependencies.
 * - No DAO, Room, Compose, AlarmManager, or MediaPlayer imports in domain.
 */
class Phase12CArchitectureTest {

    @Test
    fun domainLayer_isCompletelyFreeOfAndroidAndFrameworkDependencies() {
        // Resolve domain source root
        val possiblePaths = listOf(
            File("app/src/main/java/com/example/offlinereminder/domain"),
            File("src/main/java/com/example/offlinereminder/domain"),
            File("../app/src/main/java/com/example/offlinereminder/domain")
        )
        val domainDir = possiblePaths.firstOrNull { it.exists() && it.isDirectory }
            ?: throw IllegalStateException("Could not locate domain directory")

        val disallowedImports = listOf(
            "import android.",
            "import androidx.room.",
            "import androidx.compose.",
            "import com.example.offlinereminder.data.local.dao.",
            "import com.example.offlinereminder.data.local.AppDatabase",
            "import android.app.AlarmManager",
            "import android.media.MediaPlayer"
        )

        val violations = mutableListOf<String>()

        domainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val lines = file.readLines()
                lines.forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    for (disallowed in disallowedImports) {
                        if (trimmed.startsWith(disallowed)) {
                            violations.add("${file.name}:${index + 1} -> $trimmed")
                        }
                    }
                }
            }

        assertTrue(
            "Domain layer must remain 100% free of Android, Room, Compose, and Media framework dependencies. Violations found:\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }
}
