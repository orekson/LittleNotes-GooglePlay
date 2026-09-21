package tw.local.memonote

import org.junit.Assert.assertEquals
import org.junit.Test
import tw.local.memonote.data.StickerAssets

class StickerAssetsTest {
    @Test
    fun sorts_sticker_files_by_numeric_suffix() {
        val files = listOf("sticker_10.png", "sticker_02.png", "sticker_1.png", "not-a-sticker.png")

        assertEquals(
            listOf("sticker_1.png", "sticker_02.png", "sticker_10.png", "not-a-sticker.png"),
            StickerAssets.sortStickerFiles(files),
        )
    }

    @Test
    fun maps_legacy_asset_reference_to_stable_sticker_name() {
        assertEquals(
            listOf("stickers/legacy_3.png", "stickers/sticker_03.png"),
            StickerAssets.assetCandidates("stickers/legacy_3.png"),
        )
    }
}
