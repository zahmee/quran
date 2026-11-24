package com.mushaf.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AyahNeighboursTest {

    @Test
    fun `within a surah it moves one ayah`() {
        assertEquals(2 to 256, AyahNeighbours.step(2, 255, +1))
        assertEquals(2 to 254, AyahNeighbours.step(2, 255, -1))
    }

    @Test
    fun `past a surah's last ayah comes the next surah's first`() {
        assertEquals(3 to 1, AyahNeighbours.step(2, 286, +1))
        assertEquals(5 to 1, AyahNeighbours.step(4, 176, +1))
    }

    @Test
    fun `before a surah's first ayah comes the previous surah's last`() {
        assertEquals(2 to 286, AyahNeighbours.step(3, 1, -1))
        assertEquals(1 to 7, AyahNeighbours.step(2, 1, -1))
    }

    @Test
    fun `the mushaf's two ends have nothing beyond them`() {
        assertNull(AyahNeighbours.step(1, 1, -1))
        assertNull(AyahNeighbours.step(114, 6, +1))
    }

    @Test
    fun `a place that does not exist has no neighbour`() {
        assertNull(AyahNeighbours.step(1, 8, +1))
        assertNull(AyahNeighbours.step(115, 1, -1))
    }
}
