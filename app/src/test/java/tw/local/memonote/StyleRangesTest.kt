package tw.local.memonote

import org.junit.Assert.*
import org.junit.Test
import tw.local.memonote.model.*

class StyleRangesTest {
    @Test fun overlappingSelectionPreservesOutsideColors() {
        val blue = TextStyle(color = 1)
        val actual = StyleRanges.apply(listOf(StyleRange(0,6,blue)),2,4) { it.copy(color=2) }
        assertEquals(listOf(StyleRange(0,2,blue),StyleRange(2,4,TextStyle(color=2)),StyleRange(4,6,blue)),actual)
    }
    @Test fun glowKeepsExistingRainbowAndColor() {
        val style = TextStyle(7,true,false)
        assertEquals(listOf(StyleRange(0,3,style.copy(glow=true))), StyleRanges.apply(listOf(StyleRange(0,3,style)),0,3){it.copy(glow=true)})
    }
    @Test fun clearingMiddleDoesNotClearRest() {
        val red = TextStyle(color=4)
        assertEquals(listOf(StyleRange(0,2,red),StyleRange(4,6,red)),StyleRanges.apply(listOf(StyleRange(0,6,red)),2,4){TextStyle()})
    }
    @Test fun adjacentEqualStylesMerge() {
        val s=TextStyle(color=9)
        assertEquals(listOf(StyleRange(0,6,s)),StyleRanges.apply(listOf(StyleRange(0,3,s)),3,6){s})
    }
    @Test fun emptySelectionDoesNotChangeDocument() {
        val existing=listOf(StyleRange(0,3,TextStyle(glow=true)))
        assertEquals(existing, StyleRanges.apply(existing,1,1){TextStyle()})
    }
}
