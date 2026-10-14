package com.example.pdfscan

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.min

class Corrector(private val dictionary: Set<String>) {
    private val trLocale = Locale("tr", "TR")
    private val byLength: Map<Int, List<String>> = dictionary.groupBy { it.length }
    private val skipRegex = Regex("^(.*[0-9@#€$₺%&/\\\\+\\-*=].*|^[IVXLCDM]+$)")
    companion object {
        fun fromAssets(context: Context, assetName: String = "lexicon_tr.txt"): Corrector {
            val words = mutableSetOf<String>()
            try {
                context.assets.open(assetName).use { stream ->
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).forEachLine { line ->
                        val w = line.trim().lowercase(Locale("tr", "TR"))
                        if (w.isNotEmpty()) words.add(w)
                    }
                }
            } catch (e: Exception) {
                words.addAll(listOf("fatura", "toplam", "tarih", "miktar", "birim", "fiyat", "tutar", "kdv", "açıklama", "ürün", "genel"))
            }
            return Corrector(words)
        }
    }
    fun correctLine(text: String): String = text.split(" ").joinToString(" ") { correctWord(it) }
    fun correctWord(rawToken: String): String {
        if (rawToken.length <= 2 || skipRegex.matches(rawToken)) return rawToken
        val prefix = rawToken.takeWhile { !it.isLetterOrDigit() }
        val suffix = rawToken.takeLastWhile { !it.isLetterOrDigit() }
        val core = rawToken.substring(prefix.length, rawToken.length - suffix.length)
        if (core.length <= 2 || skipRegex.matches(core)) return rawToken
        val lowerCore = core.lowercase(trLocale)
        if (dictionary.contains(lowerCore)) return rawToken
        val candidates = mutableListOf<String>()
        for (len in (lowerCore.length - 1)..(lowerCore.length + 1)) byLength[len]?.let { candidates.addAll(it) }
        var bestMatch: String? = null
        var bestDist = Int.MAX_VALUE
        val maxAllowedDist = if (lowerCore.length <= 5) 1 else 2
        for (candidate in candidates) {
            if (candidate[0] != lowerCore[0] && levenshtein(candidate.take(2), lowerCore.take(2)) > 1) continue
            val dist = levenshtein(lowerCore, candidate)
            if (dist < bestDist && dist <= maxAllowedDist) {
                bestDist = dist; bestMatch = candidate
                if (dist == 1) break
            }
        }
        val correctedCore = if (bestMatch != null) preserveCase(core, bestMatch) else core
        return "$prefix$correctedCore$suffix"
    }
    private fun preserveCase(original: String, replacement: String): String = when {
        original.all { it.isUpperCase() } -> replacement.uppercase(trLocale)
        original.first().isUpperCase() -> replacement.replaceFirstChar { it.titlecase(trLocale) }
        else -> replacement
    }
    private fun levenshtein(s1: String, s2: String): Int {
        val m = s1.length; val n = s2.length
        val dp = IntArray(n + 1) { it }
        for (i in 1..m) {
            var prev = dp[0]; dp[0] = i
            for (j in 1..n) {
                val temp = dp[j]
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[j] = min(min(dp[j] + 1, dp[j - 1] + 1), prev + cost)
                prev = temp
            }
        }
        return dp[n]
    }
}
