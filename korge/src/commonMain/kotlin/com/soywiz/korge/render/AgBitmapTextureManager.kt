package com.soywiz.korge.render

import com.soywiz.kds.*
import com.soywiz.korag.*
import com.soywiz.korge.annotations.*
import com.soywiz.korge.internal.*
import com.soywiz.korim.bitmap.*
import com.soywiz.korma.geom.Rectangle

/**
 * Class in charge of automatically handling [AG.Texture] <-> [Bitmap] conversion.
 *
 * To simplify texture storage (which usually require uploading to the GPU, and releasing it once not used),
 * the [AgBitmapTextureManager] allows to get temporal textures that are available while referenced in the coming 60 frames.
 * If it has been 60 frames without being referenced, the textures are collected.
 * This greatly simplifies texture management, but might have an impact in performance.
 * If you want to keep a Bitmap or an atlas in the GPU so there is no impact on uploading when required,
 * you can just call any of the getTexture* methods here each frame, even if not using it in the current frame.
 * You can also manage [Texture] manually, but you should release the textures manually by calling [Texture.close] so the resources are freed.
 */
@OptIn(KorgeInternal::class, KorgeExperimental::class)
class AgBitmapTextureManager(
    val ag: AG
) {
    // @TODO: Use HashSet if items.size increases
    internal class SmallSet<T> {
        val items = FastArrayList<T>()

        fun add(item: T) {
            if (item in items) return
            items.add(item)
        }

        fun remove(item: T) {
            items.remove(item)
        }

        inline operator fun contains(item: T): Boolean = item in items

        fun clear() {
            items.clear()
        }
    }

	private val referencedBitmapsSinceGC = SmallSet<Bitmap>()
	private var referencedBitmaps = FastArrayList<Bitmap>()

    /** Number of frames between each Texture Garbage Collection step */
    var framesBetweenGC = 60
    //var framesBetweenGC = 30 * 60 // 30 seconds
    //var framesBetweenGC = 360

	//var Bitmap._textureBase: Texture.Base? by Extra.Property { null }
	//var Bitmap._slices by Extra.Property { LinkedHashSet<BmpSlice>() }
	//var BmpSlice._texture: Texture? by Extra.Property { null }

    /** Wrapper of [Texture.Base] that contains all the [Texture] slices referenced as [BmpSlice] in our current cache */
	private class BitmapTextureInfo {
        var textureBase: Texture.Base = Texture.Base(null, 0, 0)
		val slices = FastIdentityMap<BmpSlice, Texture>()
        fun reset() {
            textureBase.base = null
            textureBase.version = -1
            textureBase.width = 0
            textureBase.height = 0
            slices.clear()
        }
	}

    private val textureInfoPool = Pool(reset = { it.reset() }) { BitmapTextureInfo() }
	private val bitmapsToTextureBase = FastIdentityMap<Bitmap, BitmapTextureInfo>()

	private var cachedBitmap: Bitmap? = null
	private var cachedBitmapTextureInfo: BitmapTextureInfo? = null

    private var cachedBitmap2: Bitmap? = null
    private var cachedBitmapTextureInfo2: BitmapTextureInfo? = null

    private var cachedBmpSlice: BmpSlice? = null
	private var cachedBmpSliceTexture: Texture? = null

    private var cachedBmpSlice2: BmpSlice? = null
    private var cachedBmpSliceTexture2: Texture? = null

    /**
     * Obtains a temporal [BitmapTextureInfo] from a [Bitmap].
     *
     * The [BitmapTextureInfo] is a wrapper of [Bitmap] including a [Texture.Base] and information about slices of that [Bitmap]
     * that is just kept temporarily until released.
     *
     * You shouldn't call this method directly. Use [getTexture] or [getTextureBase] instead.
     */
	private fun getTextureInfo(bitmap: Bitmap): BitmapTextureInfo {
		if (cachedBitmap === bitmap) return cachedBitmapTextureInfo!!
        if (cachedBitmap2 === bitmap) return cachedBitmapTextureInfo2!!
        referencedBitmapsSinceGC.add(bitmap)

		if (cachedBitmap == bitmap) return cachedBitmapTextureInfo!!

		val textureInfo = bitmapsToTextureBase.getOrPut(bitmap) {
            textureInfoPool.alloc().also {
                val base = it.textureBase
                base.version = -1
                base.base = ag.createTexture(bitmap.premultiplied)
                base.width = bitmap.width
                base.height = bitmap.height
            }
        }

        cachedBitmap2 = cachedBitmap
        cachedBitmapTextureInfo2 = cachedBitmapTextureInfo

        val base = textureInfo.textureBase
		cachedBitmap = bitmap
		cachedBitmapTextureInfo = textureInfo
        if (bitmap.contentVersion != base.version) {
            base.version = bitmap.contentVersion
            // @TODO: Use dirtyRegion to upload only a fragment of the image
            base.update(bitmap, bitmap.mipmaps)
            bitmap.clearDirtyRegion()
        }

		return textureInfo
	}

    /** Obtains a temporal [Texture.Base] from [bitmap] [Bitmap]. The texture shouldn't be stored, but used for drawing since it will be destroyed once not used anymore. */
	fun getTextureBase(bitmap: Bitmap): Texture.Base = getTextureInfo(bitmap).textureBase!!

    /** Obtains a temporal [Texture] from [slice] [BmpSlice]. The texture shouldn't be stored, but used for drawing since it will be destroyed once not used anymore. */
	fun getTexture(slice: BmpSlice): Texture {
		if (cachedBmpSlice === slice) return cachedBmpSliceTexture!!
        if (cachedBmpSlice2 === slice) return cachedBmpSliceTexture2!!

        val info = getTextureInfo(slice.bmpBase)

		val texture = info.slices.getOrPut(slice) {
            Texture(info.textureBase!!).slice(Rectangle(slice.left, slice.top, slice.width, slice.height))
        }

        cachedBmpSlice2 = cachedBmpSlice
        cachedBmpSliceTexture2 = cachedBmpSliceTexture

		cachedBmpSlice = slice
		cachedBmpSliceTexture = texture

		return texture
	}

	private var fcount = 0

    /**
     * Called automatically by the engine after the render has been executed (each frame). It executes a texture GC every [framesBetweenGC] frames.
     */
    internal fun afterRender() {
        // Prevent leaks when not referenced anymore
        removeCache()

		fcount++
		if (fcount >= framesBetweenGC) {
			fcount = 0
			gc()
		}
	}

    /** Performs a kind of Garbage Collection of textures references since the last GC. This method is automatically executed every [framesBetweenGC] frames. */
	internal fun gc() {
        //println("AgBitmapTextureManager.gc[${referencedBitmaps.size}] - [${referencedBitmapsSinceGC.size}]")
        referencedBitmaps.fastForEach { bmp ->
            if (bmp !in referencedBitmapsSinceGC) {
                removeBitmap(bmp)
            }
        }
        referencedBitmaps.clear()
        referencedBitmaps.addAll(referencedBitmapsSinceGC.items)
        referencedBitmapsSinceGC.clear()
	}

    @KorgeExperimental
    fun removeBitmap(bmp: Bitmap) {
        //println("removeBitmap:${bmp.size}")
        val info = bitmapsToTextureBase.getAndRemove(bmp) ?: return
        referencedBitmapsSinceGC.remove(bmp)
        if (cachedBitmapTextureInfo == info || cachedBitmapTextureInfo2 == info) removeCache()
        info.textureBase.close()
        textureInfoPool.free(info)
    }

    private fun removeCache() {
        cachedBitmap = null
        cachedBitmapTextureInfo = null
        cachedBitmap2 = null
        cachedBitmapTextureInfo2 = null

        cachedBmpSlice = null
        cachedBmpSliceTexture = null
        cachedBmpSlice2 = null
        cachedBmpSliceTexture2 = null
    }
}

