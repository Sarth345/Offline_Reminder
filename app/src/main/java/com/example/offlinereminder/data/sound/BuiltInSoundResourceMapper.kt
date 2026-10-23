package com.example.offlinereminder.data.sound

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.annotation.RawRes
import com.example.offlinereminder.R

/**
 * Android data-layer mapper connecting domain sound keys with packaged R.raw resources.
 * Keeps pure domain models free from Android framework references.
 */
object BuiltInSoundResourceMapper {

    @RawRes
    fun getRawResourceForKey(key: String?): Int? = when (key) {
        "planerize_bell" -> R.raw.planerize_bell
        "soft_bell" -> R.raw.soft_bell
        "gentle_chime" -> R.raw.gentle_chime
        "digital_alert" -> R.raw.digital_alert
        "short_beep" -> R.raw.short_beep
        "planerize_alarm" -> R.raw.planerize_alarm
        "classic_alarm" -> R.raw.classic_alarm
        "digital_alarm" -> R.raw.digital_alarm
        "rising_alert" -> R.raw.rising_alert
        "strong_alarm" -> R.raw.strong_alarm
        else -> null
    }

    fun getUriForRawResource(context: Context, @RawRes resId: Int): Uri {
        return Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/$resId")
    }

    fun getSoundUriForKey(context: Context, key: String?): Uri? {
        val resId = getRawResourceForKey(key) ?: return null
        return getUriForRawResource(context, resId)
    }
}
