package com.example.offlinereminder.ui.theme

import androidx.compose.ui.graphics.Color

// Primary Material 3 Color Palette - Blue / Teal Accent
val PrimaryLight = Color(0xFF0061A4)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFD1E4FF)
val OnPrimaryContainerLight = Color(0xFF001D36)

val SecondaryLight = Color(0xFF535F70)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFD7E3F7)
val OnSecondaryContainerLight = Color(0xFF101C2B)

val TertiaryLight = Color(0xFF6B5778)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFF2DAFF)
val OnTertiaryContainerLight = Color(0xFF251431)

val BackgroundLight = Color(0xFFFDFCFF)
val OnBackgroundLight = Color(0xFF1A1C1E)
val SurfaceLight = Color(0xFFFDFCFF)
val OnSurfaceLight = Color(0xFF1A1C1E)
val SurfaceVariantLight = Color(0xFFDFE2EB)
val OnSurfaceVariantLight = Color(0xFF43474E)

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

// Dark Theme Palette
val PrimaryDark = Color(0xFF9ECAFF)
val OnPrimaryDark = Color(0xFF003258)
val PrimaryContainerDark = Color(0xFF00497D)
val OnPrimaryContainerDark = Color(0xFFD1E4FF)

val SecondaryDark = Color(0xFFBBC7DB)
val OnSecondaryDark = Color(0xFF253140)
val SecondaryContainerDark = Color(0xFF3B4858)
val OnSecondaryContainerDark = Color(0xFFD7E3F7)

val TertiaryDark = Color(0xFFD7BEE4)
val OnTertiaryDark = Color(0xFF3B2948)
val TertiaryContainerDark = Color(0xFF523F5F)
val OnTertiaryContainerDark = Color(0xFFF2DAFF)

val BackgroundDark = Color(0xFF1A1C1E)
val OnBackgroundDark = Color(0xFFE2E2E6)
val SurfaceDark = Color(0xFF1A1C1E)
val OnSurfaceDark = Color(0xFFE2E2E6)
val SurfaceVariantDark = Color(0xFF43474E)
val OnSurfaceVariantDark = Color(0xFFC3C7D0)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

// Domain Priority & Status Colors
object PriorityColors {
    val Low = Color(0xFF2E7D32)
    val LowContainer = Color(0xFFC8E6C9)
    val Normal = Color(0xFF0277BD)
    val NormalContainer = Color(0xFFB3E5FC)
    val High = Color(0xFFEF6C00)
    val HighContainer = Color(0xFFFFE0B2)
    val Critical = Color(0xFFC62828)
    val CriticalContainer = Color(0xFFFFCDD2)
}

object StatusColors {
    val Active = Color(0xFF2E7D32)
    val Completed = Color(0xFF455A64)
    val Archived = Color(0xFF616161)
    val Trashed = Color(0xFFC62828)
}
