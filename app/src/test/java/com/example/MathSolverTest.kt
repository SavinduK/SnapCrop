package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MathSolverTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("snapcrop_feature_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun testMathSolverFeatureToggle() {
        // Default should be enabled
        assertTrue(CropFeaturePreferenceManager.isMathSolverEnabled(context))

        // Set to false
        CropFeaturePreferenceManager.setMathSolverEnabled(context, false)
        assertFalse(CropFeaturePreferenceManager.isMathSolverEnabled(context))

        // Set back to true
        CropFeaturePreferenceManager.setMathSolverEnabled(context, true)
        assertTrue(CropFeaturePreferenceManager.isMathSolverEnabled(context))
    }

    @Test
    fun testArithmeticExpressionEvaluation() {
        val solution = MathSolverEngine.solve("15 + 25")
        assertTrue(solution.isSuccess)
        assertEquals("40", solution.answer)
        assertTrue(solution.steps.isNotEmpty())
    }

    @Test
    fun testComplexArithmeticWithParenthesesAndPower() {
        val solution = MathSolverEngine.solve("(10 + 20) * 3 / 2")
        assertTrue(solution.isSuccess)
        assertEquals("45", solution.answer)
    }

    @Test
    fun testSquareRootEvaluation() {
        val solution = MathSolverEngine.solve("sqrt(144) + 10")
        assertTrue(solution.isSuccess)
        assertEquals("22", solution.answer)
        assertTrue(solution.latexFormula.contains("\\sqrt{144}"))
    }

    @Test
    fun testLinearEquationSolving() {
        val solution = MathSolverEngine.solve("2x + 5 = 15")
        assertTrue(solution.isSuccess)
        assertEquals("x = 5", solution.answer)
        assertTrue(solution.steps.any { it.contains("2x") })
    }

    @Test
    fun testLinearEquationWithVariablesOnBothSides() {
        val solution = MathSolverEngine.solve("3x - 4 = x + 8")
        assertTrue(solution.isSuccess)
        assertEquals("x = 6", solution.answer)
    }

    @Test
    fun testQuadraticEquationSolving() {
        val solution = MathSolverEngine.solve("x^2 - 5x + 6 = 0")
        assertTrue(solution.isSuccess)
        // Roots are 3 and 2
        assertTrue(solution.answer.contains("x = 3") || solution.answer.contains("x = 2"))
        assertTrue(solution.steps.any { it.contains("discriminant", ignoreCase = true) })
    }

    @Test
    fun testUnicodeSymbolNormalization() {
        // Test unicode symbols like ×, ÷, ², √
        val cleaned = MathSolverEngine.cleanOcrMathText("12 × 5 ÷ 2 + 3²")
        assertEquals("12 * 5 / 2 + 3^2", cleaned)
        val solution = MathSolverEngine.solve("12 × 5 ÷ 2 + 3²")
        assertTrue(solution.isSuccess)
        assertEquals("39", solution.answer)
    }

    @Test
    fun testLatexConversion() {
        val latex = MathSolverEngine.convertToLatex("sqrt(x) + x^2 = 10")
        assertTrue(latex.contains("\\sqrt{x}"))
        assertTrue(latex.contains("^{2}"))
        assertTrue(latex.contains("="))
    }
}
