package com.mushaf.reader.data.content

import org.junit.Assert.assertEquals
import org.junit.Test

class MukhtasarTextTest {

    @Test
    fun `marks stay out of what is copied or shared`() {
        assertEquals("لا يأخذه نعاس ولا نوم", MukhtasarText.plain("لا يأخذه _نعاس_ ولا نوم"))
        assertEquals("قوِّ به ظهري.", MukhtasarText.plain("قوِّ به _ظهري_."))
    }

    @Test
    fun `a phrase can be marked, and a row can hold several marks`() {
        val raw = "يدار عليهم _بكؤوس خمر_ من _أنهار_ جارية"
        assertEquals(listOf("بكؤوس خمر", "أنهار"), MukhtasarText.Gloss.findAll(raw).map { it.groupValues[1] }.toList())
    }

    @Test
    fun `text with no marks is left alone`() {
        assertEquals("(حم) تقدم الكلام.", MukhtasarText.plain("(حم) تقدم الكلام."))
    }
}
