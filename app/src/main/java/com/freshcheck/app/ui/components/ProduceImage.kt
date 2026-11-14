package com.freshcheck.app.ui.components
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local images only. Decode off main thread and downsample; failures keep the labelled illustration.
 * Member 2/3 owns URI grants and retention. Remote URLs are intentionally not fetched.
 */
@Composable
fun ProduceImage(uri: String?, description: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, uri) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching {
                val parsed = uri?.let(Uri::parse) ?: return@runCatching null
                if (parsed.scheme !in setOf("content", "file")) return@runCatching null
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, options) }
                var sample = 1
                while (options.outWidth / sample > 1024 || options.outHeight / sample > 1024) sample *= 2
                options.inJustDecodeBounds = false; options.inSampleSize = sample
                context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, options) }
            }.getOrNull()
        }
    }
    Box(modifier.clearAndSetSemantics { contentDescription = if (bitmap == null) "$description · illustration placeholder" else description }) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), description, Modifier.matchParentSize(), contentScale = ContentScale.Fit)
        else ProduceArt(Modifier.matchParentSize())
    }
}
