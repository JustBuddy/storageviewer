package wtf.buddyworks.storageviewer.ui.components

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SquarifyTest {
    private val bounds = Rect(0f, 0f, 400f, 300f)

    @Test
    fun areasAreProportionalToValues() {
        val values = listOf(500.0, 250.0, 120.0, 80.0, 30.0, 15.0, 5.0)
        val rects = squarify(values, bounds)
        assertEquals(values.size, rects.size)
        val total = values.sum()
        values.forEachIndexed { i, v ->
            val expected = bounds.width * bounds.height * v / total
            val actual = rects[i].width.toDouble() * rects[i].height
            assertTrue("tile $i area $actual vs $expected", abs(actual - expected) < expected * 0.001 + 0.5)
        }
    }

    @Test
    fun tilesStayInsideAndCoverTheBounds() {
        val rects = squarify(List(25) { (25 - it).toDouble() }, bounds)
        rects.forEach { r ->
            assertTrue(r.left >= -0.01f && r.top >= -0.01f)
            assertTrue(r.right <= bounds.right + 0.01f && r.bottom <= bounds.bottom + 0.01f)
        }
        val covered = rects.sumOf { it.width.toDouble() * it.height }
        assertEquals(bounds.width.toDouble() * bounds.height, covered, 1.0)
    }

    @Test
    fun tilesAreReasonablySquare() {
        val rects = squarify(List(12) { 10.0 }, bounds)
        rects.forEach { r ->
            val ratio = max(r.width, r.height) / min(r.width, r.height)
            assertTrue("aspect ratio $ratio", ratio < 3f)
        }
    }

    @Test
    fun emptyInputGivesEmptyTiles() {
        assertEquals(listOf(Rect.Zero, Rect.Zero), squarify(listOf(0.0, 0.0), bounds))
        assertTrue(squarify(emptyList(), bounds).isEmpty())
    }

    @Test
    fun plannedTilesAreAllBigEnoughForALabel() {
        val values = List(179) { 10.0 + it % 7 } // a camera folder: many similar files
        val total = values.sum()
        val plan = planTreemap(values, total, bounds, 70f, 54f)
        if (plan != null) {
            val layout = values.take(plan.tiles) + if (plan.hasRest) listOf(total - values.take(plan.tiles).sum()) else emptyList()
            layoutBySize(layout, bounds).forEach { assertTrue(it.width >= 70f && it.height >= 54f) }
            assertTrue(plan.tiles < values.size)
        }
    }

    @Test
    fun dominantItemsKeepTheirOwnTiles() {
        val values = listOf(800.0, 120.0, 50.0, 20.0, 5.0, 3.0, 2.0)
        val total = values.sum()
        val plan = planTreemap(values, total, bounds, 70f, 54f)!!
        assertTrue("got ${plan.tiles}", plan.tiles >= 2)
        val layout = values.take(plan.tiles) + if (plan.hasRest) listOf(total - values.take(plan.tiles).sum()) else emptyList()
        layoutBySize(layout, bounds).forEach { assertTrue(it.width >= 70f && it.height >= 54f) }
    }

    @Test
    fun nothingFitsInATinyArea() {
        assertEquals(null, planTreemap(listOf(1.0, 1.0), 2.0, Rect(0f, 0f, 40f, 40f), 70f, 54f))
    }

    @Test
    fun layoutBySizeKeepsInputOrderAndAreas() {
        val values = listOf(10.0, 500.0, 40.0)
        val rects = layoutBySize(values, bounds)
        val total = values.sum()
        values.forEachIndexed { i, v ->
            val expected = bounds.width * bounds.height * v / total
            assertEquals(expected, rects[i].width.toDouble() * rects[i].height, expected * 0.001 + 0.5)
        }
    }
}
