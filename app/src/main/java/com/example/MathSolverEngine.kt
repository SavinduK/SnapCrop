package com.example

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.objecthunter.exp4j.ExpressionBuilder
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * MathSolverEngine
 *
 * Provides end-to-end mathematical formula recognition, LaTeX conversion,
 * and step-by-step solving:
 * 1. Recognizes math equations and expressions in cropped bitmaps using Google ML Kit.
 * 2. Cleans OCR artifacts and converts formulas into clean LaTeX.
 * 3. Solves arithmetic expressions, linear equations, and quadratic equations using exp4j and algebraic solvers.
 * 4. Produces clear pedagogical step-by-step solutions and the final answer.
 */
object MathSolverEngine {

    private val decimalFormat = DecimalFormat("#.######")

    data class MathSolution(
        val rawOcrText: String,
        val cleanedExpression: String,
        val latexFormula: String,
        val answer: String,
        val steps: List<String>,
        val isSuccess: Boolean,
        val errorMessage: String? = null
    )

    /**
     * Recognizes math expressions from a cropped bitmap via Google ML Kit Text Recognition
     * and produces a solved MathSolution.
     */
    suspend fun recognizeAndSolve(bitmap: Bitmap): MathSolution = withContext(Dispatchers.Default) {
        val ocrText = extractTextFromBitmap(bitmap)
        if (ocrText.isBlank()) {
            return@withContext MathSolution(
                rawOcrText = "",
                cleanedExpression = "",
                latexFormula = "",
                answer = "",
                steps = emptyList(),
                isSuccess = false,
                errorMessage = "No text or equations found in the selected area."
            )
        }
        solve(ocrText)
    }

    private suspend fun extractTextFromBitmap(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = kotlinx.coroutines.suspendCancellableCoroutine<String> { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        cont.resume(visionText.text, null)
                    }
                    .addOnFailureListener { exception ->
                        cont.resumeWith(Result.failure(exception))
                    }
            }
            result
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Solves raw OCR text: cleans it, detects equation type, builds LaTeX, and computes the answer.
     */
    fun solve(rawText: String): MathSolution {
        val cleaned = cleanOcrMathText(rawText)
        if (cleaned.isBlank()) {
            return MathSolution(
                rawOcrText = rawText,
                cleanedExpression = "",
                latexFormula = "",
                answer = "",
                steps = listOf("Could not detect a valid mathematical expression in the selection."),
                isSuccess = false,
                errorMessage = "No valid equation or formula detected."
            )
        }

        val latex = convertToLatex(cleaned)

        // Check if equation contains '='
        if (cleaned.contains("=")) {
            val parts = cleaned.split("=")
            if (parts.size == 2) {
                val lhs = parts[0].trim()
                val rhs = parts[1].trim()

                // Check if right side is just a placeholder (e.g. '?', 'x', empty)
                if (rhs.isEmpty() || rhs == "?" || rhs == "X") {
                    return solveArithmeticExpression(rawText, lhs, latex)
                }

                // Check for single variable equation (e.g. 2x + 5 = 15 or x^2 - 4 = 0)
                val variable = detectVariable(cleaned)
                if (variable != null) {
                    // Try quadratic equation: ax^2 + bx + c = 0
                    val quadraticSolution = trySolveQuadratic(rawText, lhs, rhs, variable, latex)
                    if (quadraticSolution != null) {
                        return quadraticSolution
                    }

                    // Try linear equation: ax + b = cx + d
                    val linearSolution = trySolveLinear(rawText, lhs, rhs, variable, latex)
                    if (linearSolution != null) {
                        return linearSolution
                    }
                }

                // If no variable, evaluate both sides to verify equality or evaluate LHS - RHS
                return tryEvaluateEquality(rawText, lhs, rhs, latex)
            }
        }

        // Pure arithmetic or algebraic expression (no '=')
        return solveArithmeticExpression(rawText, cleaned, latex)
    }

    /**
     * Normalizes OCR text into standard mathematical format.
     */
    fun cleanOcrMathText(text: String): String {
        // Collect relevant lines
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) return ""

        // Join lines with space
        var raw = lines.joinToString(" ")

        // Replace common unicode math symbols and OCR artifacts
        raw = raw.replace("–", "-")
            .replace("—", "-")
            .replace("−", "-")
            .replace("×", "*")
            .replace("÷", "/")
            .replace("·", "*")
            .replace("•", "*")
            .replace("²", "^2")
            .replace("³", "^3")
            .replace("⁴", "^4")
            .replace("⁵", "^5")
            .replace("√", "sqrt")
            .replace("π", "pi")
            .replace("PI", "pi")
            .replace("{", "(")
            .replace("}", ")")
            .replace("[", "(")
            .replace("]", ")")

        // Handle 'X' or 'x' as multiplication between numbers e.g. 5 x 4 -> 5 * 4
        raw = raw.replace(Regex("(\\d+)\\s*[xX]\\s*(\\d+)")) { match ->
            "${match.groupValues[1]} * ${match.groupValues[2]}"
        }

        // Handle implicit multiplication like 2x -> 2*x (while preserving variable name)
        // Clean multiple whitespace
        raw = raw.replace(Regex("\\s+"), " ")

        // Trim unwanted trailing punctuation e.g. full stops from sentence endings
        raw = raw.trimEnd('.', ',', ';')

        return raw.trim()
    }

    /**
     * Converts a mathematical expression or equation into a formatted LaTeX formula.
     */
    fun convertToLatex(expr: String): String {
        var result = expr.trim()

        if (result.contains("=")) {
            val parts = result.split("=")
            if (parts.size == 2) {
                return "${convertExpressionToLatex(parts[0].trim())} = ${convertExpressionToLatex(parts[1].trim())}"
            }
        }

        return convertExpressionToLatex(result)
    }

    private fun convertExpressionToLatex(expr: String): String {
        var latex = expr

        // Square root: sqrt(...) -> \sqrt{...}
        latex = latex.replace(Regex("sqrt\\(([^)]+)\\)")) { match ->
            "\\sqrt{${convertExpressionToLatex(match.groupValues[1])}}"
        }
        latex = latex.replace(Regex("sqrt([0-9a-zA-Z]+)")) { match ->
            "\\sqrt{${match.groupValues[1]}}"
        }

        // Exponents: x^2 -> x^{2}, (a+b)^n -> (a+b)^{n}
        latex = latex.replace(Regex("\\^([0-9a-zA-Z]+)")) { match ->
            "^{${match.groupValues[1]}}"
        }
        latex = latex.replace(Regex("\\^\\(([^)]+)\\)")) { match ->
            "^{${match.groupValues[1]}}"
        }

        // Multiplication: * -> \times
        latex = latex.replace("*", " \\times ")

        // Division: / -> \div
        latex = latex.replace("/", " \\div ")

        // Functions: sin, cos, tan, log, ln
        latex = latex.replace(Regex("\\bsin\\b"), "\\\\sin")
            .replace(Regex("\\bcos\\b"), "\\\\cos")
            .replace(Regex("\\btan\\b"), "\\\\tan")
            .replace(Regex("\\blog\\b"), "\\\\log")
            .replace(Regex("\\bln\\b"), "\\\\ln")
            .replace(Regex("\\bpi\\b"), "\\\\pi")

        // Clean extra spaces
        latex = latex.replace(Regex("\\s+"), " ").trim()

        return latex
    }

    private fun detectVariable(expr: String): Char? {
        val candidates = listOf('x', 'y', 'z', 'n', 'a', 'b', 't')
        for (c in candidates) {
            val lower = expr.lowercase()
            // Make sure it's not part of function names like sin, cos, tan, sqrt, log
            val stripped = lower
                .replace("sqrt", "")
                .replace("sin", "")
                .replace("cos", "")
                .replace("tan", "")
                .replace("log", "")
                .replace("pi", "")
            if (stripped.contains(c)) {
                return c
            }
        }
        return null
    }

    /**
     * Solves linear equation of the form ax + b = cx + d
     */
    private fun trySolveLinear(
        rawText: String,
        lhs: String,
        rhs: String,
        v: Char,
        latex: String
    ): MathSolution? {
        try {
            // Collect terms on both sides
            val (coeffL, constL) = parseLinearSide(lhs, v) ?: return null
            val (coeffR, constR) = parseLinearSide(rhs, v) ?: return null

            val netCoeff = coeffL - coeffR
            val netConst = constR - constL

            if (abs(netCoeff) < 1e-9) {
                return null // Not a standard linear equation with unique root
            }

            val solutionVal = netConst / netCoeff
            val formattedSolution = formatNumber(solutionVal)
            val answerStr = "$v = $formattedSolution"

            val steps = mutableListOf<String>()
            steps.add("Original equation: $lhs = $rhs")
            steps.add("LaTeX representation: $$ $latex $$")

            if (coeffR != 0.0) {
                steps.add("Group variable terms on the left: (${formatNumber(coeffL)}$v - ${formatNumber(coeffR)}$v) = ${formatNumber(netCoeff)}$v")
            }
            if (constL != 0.0) {
                steps.add("Move constant terms to the right: ${formatNumber(constR)} - (${formatNumber(constL)}) = ${formatNumber(netConst)}")
            }
            steps.add("Simplified equation: ${formatNumber(netCoeff)}$v = ${formatNumber(netConst)}")
            steps.add("Divide both sides by ${formatNumber(netCoeff)}: $v = ${formatNumber(netConst)} / ${formatNumber(netCoeff)}")
            steps.add("Final Answer: $v = $formattedSolution")

            return MathSolution(
                rawOcrText = rawText,
                cleanedExpression = "$lhs = $rhs",
                latexFormula = latex,
                answer = answerStr,
                steps = steps,
                isSuccess = true
            )
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Parses linear expression like '2x + 5' into pair of (coefficient, constant)
     */
    private fun parseLinearSide(side: String, v: Char): Pair<Double, Double>? {
        var s = side.replace(" ", "")
        if (s.isEmpty()) return Pair(0.0, 0.0)

        // Insert '+' before '-' for easy splitting, unless at start
        var normalized = ""
        for (i in s.indices) {
            val c = s[i]
            if (c == '-' && i > 0 && s[i - 1] != '+' && s[i - 1] != '(' && s[i - 1] != '*') {
                normalized += "+-"
            } else {
                normalized += c
            }
        }

        val tokens = normalized.split("+").filter { it.isNotBlank() }
        var coeff = 0.0
        var constVal = 0.0

        for (token in tokens) {
            if (token.contains(v, ignoreCase = true)) {
                // Term has variable
                val coeffStr = token.replace(v.toString(), "", ignoreCase = true)
                val c = when (coeffStr) {
                    "", "+" -> 1.0
                    "-" -> -1.0
                    else -> coeffStr.toDoubleOrNull() ?: return null
                }
                coeff += c
            } else {
                // Constant term
                val value = token.toDoubleOrNull() ?: return null
                constVal += value
            }
        }

        return Pair(coeff, constVal)
    }

    /**
     * Solves quadratic equation of the form ax^2 + bx + c = 0
     */
    private fun trySolveQuadratic(
        rawText: String,
        lhs: String,
        rhs: String,
        v: Char,
        latex: String
    ): MathSolution? {
        if (!lhs.contains("$v^2", ignoreCase = true) && !rhs.contains("$v^2", ignoreCase = true)) {
            return null
        }

        try {
            val (aL, bL, cL) = parseQuadraticSide(lhs, v) ?: return null
            val (aR, bR, cR) = parseQuadraticSide(rhs, v) ?: return null

            val a = aL - aR
            val b = bL - bR
            val c = cL - cR

            if (abs(a) < 1e-9) return null // Linear, not quadratic

            val d = b * b - 4 * a * c
            val steps = mutableListOf<String>()
            steps.add("Original quadratic equation: $lhs = $rhs")
            steps.add("Standard form: ${formatNumber(a)}$v² + (${formatNumber(b)})$v + (${formatNumber(c)}) = 0")
            steps.add("Identify coefficients: a = ${formatNumber(a)}, b = ${formatNumber(b)}, c = ${formatNumber(c)}")
            steps.add("Calculate discriminant: D = b² - 4ac = (${formatNumber(b)})² - 4(${formatNumber(a)})(${formatNumber(c)}) = ${formatNumber(d)}")

            val answerStr: String
            if (d > 0) {
                val sqrtD = sqrt(d)
                val x1 = (-b + sqrtD) / (2 * a)
                val x2 = (-b - sqrtD) / (2 * a)
                answerStr = "$v = ${formatNumber(x1)},  $v = ${formatNumber(x2)}"
                steps.add("Since D > 0, there are two distinct real roots:")
                steps.add("$v₁ = (-b + √D) / 2a = (-(${formatNumber(b)}) + ${formatNumber(sqrtD)}) / ${formatNumber(2 * a)} = ${formatNumber(x1)}")
                steps.add("$v₂ = (-b - √D) / 2a = (-(${formatNumber(b)}) - ${formatNumber(sqrtD)}) / ${formatNumber(2 * a)} = ${formatNumber(x2)}")
            } else if (abs(d) < 1e-9) {
                val x = -b / (2 * a)
                answerStr = "$v = ${formatNumber(x)}"
                steps.add("Since D = 0, there is one repeated real root:")
                steps.add("$v = -b / (2a) = -(${formatNumber(b)}) / ${formatNumber(2 * a)} = ${formatNumber(x)}")
            } else {
                val realPart = -b / (2 * a)
                val imagPart = sqrt(-d) / (2 * abs(a))
                answerStr = "$v = ${formatNumber(realPart)} ± ${formatNumber(imagPart)}i"
                steps.add("Since D < 0, roots are complex numbers:")
                steps.add("$v = ${formatNumber(realPart)} ± ${formatNumber(imagPart)}i")
            }

            return MathSolution(
                rawOcrText = rawText,
                cleanedExpression = "$lhs = $rhs",
                latexFormula = latex,
                answer = answerStr,
                steps = steps,
                isSuccess = true
            )
        } catch (_: Exception) {
            return null
        }
    }

    private fun parseQuadraticSide(side: String, v: Char): Triple<Double, Double, Double>? {
        var s = side.replace(" ", "")
        if (s.isEmpty()) return Triple(0.0, 0.0, 0.0)

        var normalized = ""
        for (i in s.indices) {
            val c = s[i]
            if (c == '-' && i > 0 && s[i - 1] != '+' && s[i - 1] != '(' && s[i - 1] != '*' && s[i - 1] != '^') {
                normalized += "+-"
            } else {
                normalized += c
            }
        }

        val tokens = normalized.split("+").filter { it.isNotBlank() }
        var a = 0.0
        var b = 0.0
        var c = 0.0

        for (token in tokens) {
            if (token.contains("$v^2", ignoreCase = true)) {
                val coeffStr = token.replace("$v^2", "", ignoreCase = true)
                val coeff = when (coeffStr) {
                    "", "+" -> 1.0
                    "-" -> -1.0
                    else -> coeffStr.toDoubleOrNull() ?: return null
                }
                a += coeff
            } else if (token.contains(v, ignoreCase = true)) {
                val coeffStr = token.replace(v.toString(), "", ignoreCase = true)
                val coeff = when (coeffStr) {
                    "", "+" -> 1.0
                    "-" -> -1.0
                    else -> coeffStr.toDoubleOrNull() ?: return null
                }
                b += coeff
            } else {
                val value = token.toDoubleOrNull() ?: return null
                c += value
            }
        }

        return Triple(a, b, c)
    }

    private fun tryEvaluateEquality(rawText: String, lhs: String, rhs: String, latex: String): MathSolution {
        return try {
            val evalLhs = ExpressionBuilder(lhs).build().evaluate()
            val evalRhs = ExpressionBuilder(rhs).build().evaluate()
            val isEqual = abs(evalLhs - evalRhs) < 1e-6

            val steps = listOf(
                "Evaluate left-hand side: $lhs = ${formatNumber(evalLhs)}",
                "Evaluate right-hand side: $rhs = ${formatNumber(evalRhs)}",
                if (isEqual) "Both sides are equal (${formatNumber(evalLhs)} = ${formatNumber(evalRhs)}). Statement is TRUE."
                else "Sides are not equal (${formatNumber(evalLhs)} ≠ ${formatNumber(evalRhs)}). Statement is FALSE."
            )

            MathSolution(
                rawOcrText = rawText,
                cleanedExpression = "$lhs = $rhs",
                latexFormula = latex,
                answer = if (isEqual) "True (${formatNumber(evalLhs)})" else "False (${formatNumber(evalLhs)} ≠ ${formatNumber(evalRhs)})",
                steps = steps,
                isSuccess = true
            )
        } catch (_: Exception) {
            solveArithmeticExpression(rawText, lhs, latex)
        }
    }

    /**
     * Evaluates general arithmetic or scientific expressions using exp4j.
     */
    private fun solveArithmeticExpression(rawText: String, expr: String, latex: String): MathSolution {
        return try {
            // Prepare expression for exp4j
            var evalExpr = expr
                .replace(Regex("(\\d+)%")) { match ->
                    "(${match.groupValues[1]}/100)"
                }

            val expression = ExpressionBuilder(evalExpr).build()
            val result = expression.evaluate()
            val formattedResult = formatNumber(result)

            val steps = mutableListOf<String>()
            steps.add("Expression: $expr")
            steps.add("LaTeX representation: $$ $latex $$")

            // Breakdown if compound expression
            if (expr.contains("(") && expr.contains(")")) {
                steps.add("Step 1: Evaluate terms within parentheses")
            }
            if (expr.contains("^") || expr.contains("sqrt")) {
                steps.add("Step 2: Calculate powers and roots")
            }
            if (expr.contains("*") || expr.contains("/")) {
                steps.add("Step 3: Perform multiplication and division")
            }
            if (expr.contains("+") || expr.contains("-")) {
                steps.add("Step 4: Perform addition and subtraction")
            }
            steps.add("Final Answer: $formattedResult")

            MathSolution(
                rawOcrText = rawText,
                cleanedExpression = expr,
                latexFormula = latex,
                answer = formattedResult,
                steps = steps,
                isSuccess = true
            )
        } catch (e: Exception) {
            MathSolution(
                rawOcrText = rawText,
                cleanedExpression = expr,
                latexFormula = latex,
                answer = "",
                steps = listOf("Error evaluating expression: ${e.message ?: "Invalid math syntax"}"),
                isSuccess = false,
                errorMessage = e.message ?: "Evaluation failed"
            )
        }
    }

    private fun formatNumber(num: Double): String {
        return if (num == num.toLong().toDouble()) {
            num.toLong().toString()
        } else {
            decimalFormat.format(num)
        }
    }
}
