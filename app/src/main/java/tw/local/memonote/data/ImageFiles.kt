package tw.local.memonote.data

import android.content.Context
import android.graphics.*
import android.net.Uri
import java.io.File
import java.util.UUID

object ImageFiles {
    fun import(context: Context,uri: Uri,sticker: Boolean): String {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it,null,bounds) }
        require(bounds.outWidth>0 && bounds.outHeight>0) { "無法讀取這張圖片" }
        val max=if(sticker) 384 else 1200
        var sample=1
        while(bounds.outWidth/sample>max*2 || bounds.outHeight/sample>max*2) sample*=2
        val bitmap=context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=sample }) }
            ?: error("無法讀取這張圖片")
        val orientation=try { context.contentResolver.openInputStream(uri).use { android.media.ExifInterface(it!!).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1) } } catch(e: Exception) { 1 }
        val matrix=Matrix().apply { when(orientation) {
            2 -> setScale(-1f,1f)
            3 -> setRotate(180f)
            4 -> { setRotate(180f); postScale(-1f,1f) }
            5 -> { setRotate(90f); postScale(-1f,1f) }
            6 -> setRotate(90f)
            7 -> { setRotate(-90f); postScale(-1f,1f) }
            8 -> setRotate(-90f)
        } }
        val oriented=if(orientation in 2..8) Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true) else bitmap
        val factor=minOf(1f,max.toFloat()/maxOf(oriented.width,oriented.height))
        val scaled=Bitmap.createScaledBitmap(oriented,(oriented.width*factor).toInt().coerceAtLeast(1),(oriented.height*factor).toInt().coerceAtLeast(1),true)
        val name="img_${UUID.randomUUID()}.png"
        val file=File(context.filesDir,name)
        try { file.outputStream().use { check(scaled.compress(Bitmap.CompressFormat.PNG,100,it)) } }
        catch(e: Exception) { file.delete(); throw e }
        finally { if(scaled!==oriented) scaled.recycle(); if(oriented!==bitmap) oriented.recycle(); bitmap.recycle() }
        return "file:$name"
    }
    fun load(context: Context,ref: String): Bitmap? = try {
        when {
            ref.startsWith("asset:") -> {
                val path = ref.removePrefix("asset:")
                for (candidate in StickerAssets.assetCandidates(path)) {
                    try {
                        context.assets.open(candidate).use { input ->
                            BitmapFactory.decodeStream(input)?.let { return it }
                        }
                    } catch (_: java.io.IOException) {
                        // Try the next stable candidate for notes saved by an older build.
                    }
                }
                null
            }
            ref.startsWith("file:") -> {
                val name=ref.removePrefix("file:")
                if(name.contains('/') || name.contains('\\') || name.contains("..")) null else BitmapFactory.decodeFile(File(context.filesDir,name).path)
            }
            else -> null
        }
    } catch(e: Exception) { null }
}
