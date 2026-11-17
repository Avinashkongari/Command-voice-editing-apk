package com.example.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.domain.model.MediaAsset
import com.example.domain.model.MediaAssetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MediaMetadataHelper(private val context: Context) {

    suspend fun importUriAsAsset(
        projectId: String,
        uri: Uri,
        forcedType: MediaAssetType? = null
    ): Result<MediaAsset> = withContext(Dispatchers.IO) {
        runCatching {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            val mimeType = context.contentResolver.getType(uri).orEmpty()
            var displayName = "Media_${System.currentTimeMillis() % 10000}"
            var fileSize = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        displayName = cursor.getString(nameIdx) ?: displayName
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        fileSize = cursor.getLong(sizeIdx)
                    }
                }
            }

            val detectedType = forcedType ?: when {
                mimeType.startsWith("video/") -> MediaAssetType.VIDEO
                mimeType.startsWith("audio/") -> MediaAssetType.AUDIO
                mimeType.startsWith("image/") -> MediaAssetType.IMAGE
                displayName.endsWith(".mp3", true) ||
                    displayName.endsWith(".wav", true) ||
                    displayName.endsWith(".m4a", true) ||
                    displayName.endsWith(".aac", true) -> MediaAssetType.AUDIO
                displayName.endsWith(".mp4", true) ||
                    displayName.endsWith(".mov", true) ||
                    displayName.endsWith(".mkv", true) -> MediaAssetType.VIDEO
                else -> MediaAssetType.IMAGE
            }

            val assetId = UUID.randomUUID().toString()
            var durationMs = 0L
            var width = 0
            var height = 0
            var thumbnailPath: String? = null

            when (detectedType) {
                MediaAssetType.IMAGE -> {
                    val bounds = readImageBounds(uri)
                    width = bounds.first
                    height = bounds.second
                    thumbnailPath = createImageThumbnail(uri, assetId)
                }
                MediaAssetType.VIDEO -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull() ?: 5000L
                        width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                            ?.toIntOrNull() ?: 1920
                        height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                            ?.toIntOrNull() ?: 1080
                        val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        if (frame != null) {
                            thumbnailPath = saveBitmapThumb(frame, assetId)
                            frame.recycle()
                        }
                    } finally {
                        runCatching { retriever.release() }
                    }
                }
                MediaAssetType.AUDIO, MediaAssetType.VOICEOVER -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull() ?: 0L
                    } finally {
                        runCatching { retriever.release() }
                    }
                }
            }

            MediaAsset(
                id = assetId,
                projectId = projectId,
                uriString = uri.toString(),
                displayName = displayName,
                type = detectedType,
                mimeType = mimeType,
                durationMs = durationMs,
                width = width,
                height = height,
                fileSizeBytes = fileSize,
                tags = displayName.substringBeforeLast('.').replace('_', ' '),
                thumbnailPath = thumbnailPath
            )
        }
    }

    suspend fun extractFreezeFrame(
        projectId: String,
        sourceUri: Uri,
        timeMs: Long,
        labelPrefix: String
    ): Result<MediaAsset> = withContext(Dispatchers.IO) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            val frame = try {
                retriever.setDataSource(context, sourceUri)
                retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: throw IllegalStateException("Could not extract video frame at ${timeMs}ms.")
            } finally {
                runCatching { retriever.release() }
            }

            val dir = File(context.filesDir, "freeze_frames").apply { mkdirs() }
            val outFile = File(dir, "freeze_${UUID.randomUUID()}.png")
            FileOutputStream(outFile).use { fos ->
                frame.compress(Bitmap.CompressFormat.PNG, 95, fos)
            }
            val w = frame.width
            val h = frame.height
            frame.recycle()

            MediaAsset(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                uriString = Uri.fromFile(outFile).toString(),
                displayName = "${labelPrefix}_freeze_${timeMs / 1000}s.png",
                type = MediaAssetType.IMAGE,
                mimeType = "image/png",
                durationMs = 0L,
                width = w,
                height = h,
                fileSizeBytes = outFile.length(),
                tags = "freeze frame",
                thumbnailPath = outFile.absolutePath
            )
        }
    }

    fun ensureBlackFrameImageFile(): File {
        val file = File(context.cacheDir, "voxcut_black_canvas.png")
        if (!file.exists()) {
            val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.BLACK)
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
        }
        return file
    }

    private fun readImageBounds(uri: Uri): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        return opts.outWidth.coerceAtLeast(0) to opts.outHeight.coerceAtLeast(0)
    }

    private fun createImageThumbnail(uri: Uri, assetId: String): String? {
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        val path = saveBitmapThumb(bmp, assetId)
        bmp.recycle()
        return path
    }

    private fun saveBitmapThumb(source: Bitmap, assetId: String): String {
        val dir = File(context.cacheDir, "thumbs").apply { mkdirs() }
        val outFile = File(dir, "$assetId.jpg")
        val maxDim = 256
        val ratio = minOf(maxDim.toFloat() / source.width.coerceAtLeast(1), maxDim.toFloat() / source.height.coerceAtLeast(1), 1f)
        val scaled = Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true
        )
        FileOutputStream(outFile).use { fos ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, fos)
        }
        if (scaled !== source) scaled.recycle()
        return outFile.absolutePath
    }
}
