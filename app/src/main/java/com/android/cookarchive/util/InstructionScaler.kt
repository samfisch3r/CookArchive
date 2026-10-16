package com.android.cookarchive.util

import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.math.abs

object InstructionScaler {

    private val QUANTITY_UNIT_REGEX = Pattern.compile(
        """(?i)\b(\d+\s+\d+/\d+|\d+/\d+|\d+(?:[.,]\d+)?)\s*([a-zäöü°]+)\b"""
    )

    private val SCALABLE_UNITS = setOf(
        "ml", "l", "g", "kg", "mg", "oz", "lb", "lbs",
        "tbsp", "tsp", "el", "tl", "cup", "cups",
        "gramm", "gram", "grams", "milliliter", "milliliters", "liter", "liters",
        "teaspoon", "teaspoons", "tablespoon", "tablespoons",
        "clove", "cloves", "zehe", "zehen", "prise", "prisen",
        "stk", "stück", "stueck", "dosis", "dose", "dosen", "becher", "packung", "pck", "tl", "el"
    )

    fun scaleInstruction(instructionText: String, scaleFactor: Double): String {
        if (instructionText.isBlank() || scaleFactor <= 0.0 || abs(scaleFactor - 1.0) < 0.001) {
            return instructionText
        }

        val matcher = QUANTITY_UNIT_REGEX.matcher(instructionText)
        val sb = StringBuffer()

        while (matcher.find()) {
            val numStr = matcher.group(1) ?: continue
            val rawUnit = matcher.group(2) ?: continue
            val cleanUnit = rawUnit.lowercase(Locale.ROOT).trimEnd('.')

            if (SCALABLE_UNITS.contains(cleanUnit)) {
                val parsedValue = parseQuantity(numStr)
                if (parsedValue != null && parsedValue > 0.0) {
                    val scaledValue = parsedValue * scaleFactor
                    val formattedVal = formatQuantityValue(scaledValue)

                    val originalMatch = matcher.group(0) ?: ""
                    val hasSpace = originalMatch.contains(" ")
                    val replacement = if (hasSpace) "$formattedVal $rawUnit" else "$formattedVal$rawUnit"
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement))
                    continue
                }
            }
            val matchText = matcher.group(0) ?: ""
            matcher.appendReplacement(sb, Matcher.quoteReplacement(matchText))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun parseQuantity(text: String): Double? {
        val clean = text.trim().replace(',', '.')

        if (clean.contains(" ") && clean.contains("/")) {
            val parts = clean.split("\\s+".toRegex())
            if (parts.size == 2) {
                val whole = parts[0].toDoubleOrNull() ?: return null
                val frac = parseFraction(parts[1]) ?: return null
                return whole + frac
            }
        }

        if (clean.contains("/")) {
            return parseFraction(clean)
        }

        return clean.toDoubleOrNull()
    }

    private fun parseFraction(fracStr: String): Double? {
        val parts = fracStr.split("/")
        if (parts.size == 2) {
            val num = parts[0].toDoubleOrNull() ?: return null
            val den = parts[1].toDoubleOrNull() ?: return null
            if (den != 0.0) return num / den
        }
        return null
    }

    private fun formatQuantityValue(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else if ((value * 10) % 1.0 == 0.0) {
            String.format(Locale.US, "%.1f", value)
        } else {
            String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        }
    }
}
