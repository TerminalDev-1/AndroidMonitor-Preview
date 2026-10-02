package io.github.androidmonitor.data

import android.content.Context
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.ConcurrentHashMap

/** Caches app names and icons for the process list. */
class AppInfoCache(context: Context) {
    private val packageManager = context.packageManager

    // An empty string means "not an installed package".
    private val labels = ConcurrentHashMap<String, String>()
    private val icons = LruCache<String, ImageBitmap>(256)

    fun label(packageName: String): String? = labels.getOrPut(packageName) {
        try {
            packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            ""
        }
    }.ifEmpty { null }

    fun icon(packageName: String): ImageBitmap? {
        icons.get(packageName)?.let { return it }
        return try {
            packageManager.getApplicationIcon(packageName)
                .toBitmap(ICON_SIZE_PX, ICON_SIZE_PX)
                .asImageBitmap()
                .also { icons.put(packageName, it) }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private companion object {
        const val ICON_SIZE_PX = 96
    }
}
