package app.mmmap.util

internal fun jaroWinklerDistance(s1: String, s2: String): Double {
    val a = s1.lowercase()
    val b = s2.lowercase()
    if (a == b) return 0.0
    val matchWindow = maxOf(a.length, b.length) / 2 - 1
    if (matchWindow < 0) return 1.0
    val aMatches = BooleanArray(a.length)
    val bMatches = BooleanArray(b.length)
    var matches = 0
    var transpositions = 0
    for (i in a.indices) {
        val start = maxOf(0, i - matchWindow)
        val end = minOf(i + matchWindow + 1, b.length)
        for (j in start until end) {
            if (bMatches[j] || a[i] != b[j]) continue
            aMatches[i] = true; bMatches[j] = true; matches++; break
        }
    }
    if (matches == 0) return 1.0
    var k = 0
    for (i in a.indices) {
        if (!aMatches[i]) continue
        while (!bMatches[k]) k++
        if (a[i] != b[k]) transpositions++
        k++
    }
    val jaro = (matches.toDouble() / a.length + matches.toDouble() / b.length +
            (matches - transpositions / 2.0) / matches) / 3.0
    val prefix = (0 until minOf(4, minOf(a.length, b.length))).takeWhile { a[it] == b[it] }.size
    return 1.0 - (jaro + prefix * 0.1 * (1 - jaro))
}
