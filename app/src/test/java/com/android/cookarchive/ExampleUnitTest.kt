package com.android.cookarchive

import com.android.cookarchive.util.InstructionScaler
import org.junit.Test
import org.junit.Assert.*

class ExampleUnitTest {
    @Test
    fun testInstructionScalerWithScaleFactor1_5() {
        val text = "In a pan, heat 200ml milk and 50g butter at 180°C for 15 minutes."
        val scaled = InstructionScaler.scaleInstruction(text, 1.5)
        assertEquals("In a pan, heat 300ml milk and 75g butter at 180°C for 15 minutes.", scaled)
    }

    @Test
    fun testInstructionScalerWithFractionsAndGermanUnits() {
        val text = "Gießen Sie 250 ml Wasser und 1 1/2 Becher Mehl dazu. Bei 200 Grad 20 Minuten backen."
        val scaled = InstructionScaler.scaleInstruction(text, 2.0)
        assertEquals("Gießen Sie 500 ml Wasser und 3 Becher Mehl dazu. Bei 200 Grad 20 Minuten backen.", scaled)
    }

    @Test
    fun testInstructionScalerWithScaleFactor1_0() {
        val text = "Add 200ml milk and 100g sugar."
        val scaled = InstructionScaler.scaleInstruction(text, 1.0)
        assertEquals("Add 200ml milk and 100g sugar.", scaled)
    }
}
