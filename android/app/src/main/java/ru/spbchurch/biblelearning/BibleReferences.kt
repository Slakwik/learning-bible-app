package ru.spbchurch.biblelearning

data class BibleBook(val code: String, val name: String, val aliases: List<String>)
data class BibleReference(val start: Int, val end: Int, val book: String, val location: String) {
    // Bible.com does not reliably support cross-chapter ranges in one URL.
    val endpoints: List<String> get() {
        val parts = location.split('-')
        return if (parts.size == 2 && ('.' !in parts[0] || '.' in parts[1])) parts else listOf(location)
    }
    val url: String get() = "https://www.bible.com/bible/167/$book.${endpoints.first()}"
}

/** Only public HTTPS links; no API key, downloaded Bible text or account tokens. */
object BibleReferences {
    val books = listOf(
        BibleBook("GEN", "Бытие", listOf("Бытие", "Быт")),
        BibleBook("EXO", "Исход", listOf("Исход", "Исх")),
        BibleBook("LEV", "Левит", listOf("Левит", "Лев")),
        BibleBook("NUM", "Числа", listOf("Числа", "Чис")),
        BibleBook("DEU", "Второзаконие", listOf("Второзаконие", "Втор")),
        BibleBook("JOS", "Иисус Навин", listOf("Иисус Навин", "Нав", "Иис. Нав", "Иисуса Навина")),
        BibleBook("JDG", "Судьи", listOf("Судьи", "Суд")),
        BibleBook("RUT", "Руфь", listOf("Руфь", "Руф")),
        BibleBook("1SA", "1 Царств", listOf("1 Царств", "1 Цар")),
        BibleBook("2SA", "2 Царств", listOf("2 Царств", "2 Цар")),
        BibleBook("1KI", "3 Царств", listOf("3 Царств", "3 Цар")),
        BibleBook("2KI", "4 Царств", listOf("4 Царств", "4 Цар")),
        BibleBook("1CH", "1 Паралипоменон", listOf("1 Паралипоменон", "1 Пар")),
        BibleBook("2CH", "2 Паралипоменон", listOf("2 Паралипоменон", "2 Пар")),
        BibleBook("EZR", "Ездра", listOf("Ездра", "Ездр")),
        BibleBook("NEH", "Неемия", listOf("Неемия", "Неем")),
        BibleBook("EST", "Есфирь", listOf("Есфирь", "Есф")),
        BibleBook("JOB", "Иов", listOf("Иов", "Иова")),
        BibleBook("PSA", "Псалтирь", listOf("Псалтирь", "Пс", "Псалом", "Псалмы")),
        BibleBook("PRO", "Притчи", listOf("Притчи", "Пр", "Притч")),
        BibleBook("ECC", "Екклесиаст", listOf("Екклесиаст", "Еккл", "Екклезиаст")),
        BibleBook("SNG", "Песнь песней", listOf("Песнь песней", "Песн", "Песни песней")),
        BibleBook("ISA", "Исаия", listOf("Исаия", "Ис", "Исаии")),
        BibleBook("JER", "Иеремия", listOf("Иеремия", "Иер", "Иеремии")),
        BibleBook("LAM", "Плач Иеремии", listOf("Плач Иеремии", "Плач")),
        BibleBook("EZK", "Иезекииль", listOf("Иезекииль", "Иез", "Иезекииля")),
        BibleBook("DAN", "Даниил", listOf("Даниил", "Дан", "Даниила")),
        BibleBook("HOS", "Осия", listOf("Осия", "Ос", "Осии")),
        BibleBook("JOL", "Иоиль", listOf("Иоиль", "Иоил", "Иоиля")),
        BibleBook("AMO", "Амос", listOf("Амос", "Ам", "Амоса")),
        BibleBook("OBA", "Авдий", listOf("Авдий", "Авд", "Авдия")),
        BibleBook("JON", "Иона", listOf("Иона", "Ион", "Ионы")),
        BibleBook("MIC", "Михей", listOf("Михей", "Мих", "Михея")),
        BibleBook("NAM", "Наум", listOf("Наум", "Наума")),
        BibleBook("HAB", "Аввакум", listOf("Аввакум", "Авв", "Аввакума")),
        BibleBook("ZEP", "Софония", listOf("Софония", "Соф", "Софонии")),
        BibleBook("HAG", "Аггей", listOf("Аггей", "Агг", "Аггея")),
        BibleBook("ZEC", "Захария", listOf("Захария", "Зах", "Захарии")),
        BibleBook("MAL", "Малахия", listOf("Малахия", "Мал", "Малахии")),
        BibleBook("MAT", "Матфея", listOf("Матфея", "Мф", "Матфей", "Матф")),
        BibleBook("MRK", "Марка", listOf("Марка", "Мк", "Марк", "Мр")),
        BibleBook("LUK", "Луки", listOf("Луки", "Лк", "Лука")),
        BibleBook("JHN", "Иоанна", listOf("Иоанна", "Ин", "Иоанн", "Иоан")),
        BibleBook("ACT", "Деяния", listOf("Деяния", "Деян", "Деяний")),
        BibleBook("ROM", "Римлянам", listOf("Римлянам", "Рим")),
        BibleBook("1CO", "1 Коринфянам", listOf("1 Коринфянам", "1 Кор")),
        BibleBook("2CO", "2 Коринфянам", listOf("2 Коринфянам", "2 Кор")),
        BibleBook("GAL", "Галатам", listOf("Галатам", "Гал")),
        BibleBook("EPH", "Ефесянам", listOf("Ефесянам", "Еф")),
        BibleBook("PHP", "Филиппийцам", listOf("Филиппийцам", "Флп", "Фил")),
        BibleBook("COL", "Колоссянам", listOf("Колоссянам", "Кол")),
        BibleBook("1TH", "1 Фессалоникийцам", listOf("1 Фессалоникийцам", "1 Фес", "1 Фесс")),
        BibleBook("2TH", "2 Фессалоникийцам", listOf("2 Фессалоникийцам", "2 Фес", "2 Фесс")),
        BibleBook("1TI", "1 Тимофею", listOf("1 Тимофею", "1 Тим")),
        BibleBook("2TI", "2 Тимофею", listOf("2 Тимофею", "2 Тим")),
        BibleBook("TIT", "Титу", listOf("Титу", "Тит")),
        BibleBook("PHM", "Филимону", listOf("Филимону", "Флм", "Филим")),
        BibleBook("HEB", "Евреям", listOf("Евреям", "Евр")),
        BibleBook("JAS", "Иакова", listOf("Иакова", "Иак", "Иаков")),
        BibleBook("1PE", "1 Петра", listOf("1 Петра", "1 Пет")),
        BibleBook("2PE", "2 Петра", listOf("2 Петра", "2 Пет")),
        BibleBook("1JN", "1 Иоанна", listOf("1 Иоанна", "1 Ин", "1 Иоан")),
        BibleBook("2JN", "2 Иоанна", listOf("2 Иоанна", "2 Ин", "2 Иоан")),
        BibleBook("3JN", "3 Иоанна", listOf("3 Иоанна", "3 Ин", "3 Иоан")),
        BibleBook("JUD", "Иуды", listOf("Иуды", "Иуд")),
        BibleBook("REV", "Откровение", listOf("Откровение", "Откр", "Откровения", "Апокалипсис")))
    // No platform regex engine: Android 10 ICU and desktop JVM disagree on long patterns.
    private val aliases = books.flatMap { book -> book.aliases.map { it to book.code } }
        .sortedByDescending { it.first.length }
    private fun space(text: String, start: Int): Int {
        var i = start
        while (i < text.length && text[i].isWhitespace()) i++
        return i
    }
    private fun matchName(text: String, start: Int, alias: String): Int? {
        var i = start
        for (part in alias.split(' ')) {
            val word = part.trimEnd('.')
            if (!text.regionMatches(i, word, 0, word.length, ignoreCase = true)) return null
            i += word.length
            if (text.getOrNull(i) == '.') i++
            i = space(text, i)
        }
        return i
    }
    private data class Point(val end: Int, val chapter: Int, val verse: Int?) {
        val value get() = chapter.toString() + (verse?.let { ".$it" } ?: "")
    }
    private fun number(text: String, start: Int): Pair<Int, Int>? {
        var end = start
        while (end < text.length && text[end] in '0'..'9') end++
        if (end == start || end - start > 3) return null
        val value = text.substring(start, end).toInt()
        return if (value in 1..176) end to value else null
    }
    private fun point(text: String, start: Int): Point? {
        val first = number(text, start) ?: return null
        val separator = space(text, first.first)
        if (text.getOrNull(separator) in listOf(':', '.')) {
            val second = number(text, space(text, separator + 1))
            if (second != null) return Point(second.first, first.second, second.second)
            if (text.getOrNull(separator) == ':') return null
        }
        return Point(first.first, first.second, null)
    }
    private fun reference(text: String, start: Int, numbers: Int, book: String, requireVerse: Boolean = false): BibleReference? {
        val first = point(text, numbers) ?: return null
        if (requireVerse && first.verse == null) return null
        var end = first.end
        var location = first.value
        val dash = space(text, end)
        if (text.getOrNull(dash) in listOf('-', '–', '—')) {
            val last = point(text, space(text, dash + 1)) ?: return null
            end = last.end
            location += "-" + last.value
        }
        if (text.getOrNull(end) == ':' || text.getOrNull(end)?.isDigit() == true) return null
        return BibleReference(start, end, book, location)
    }
    fun find(text: String, defaultBook: String? = null): List<BibleReference> {
        val result = mutableListOf<BibleReference>()
        var i = 0
        while (i < text.length) {
            if (i > 0 && text[i - 1].isLetterOrDigit()) { i++; continue }
            val named = aliases.firstNotNullOfOrNull { (alias, book) ->
                val numbers = matchName(text, i, alias) ?: return@firstNotNullOfOrNull null
                reference(text, i, numbers, book)
            }
            val found = named ?: if (defaultBook != null && books.any { it.code == defaultBook } &&
                (i == 0 || text[i - 1] !in "/:.")) reference(text, i, i, defaultBook, requireVerse = true) else null
            if (found == null) i++ else { result += found; i = found.end }
        }
        return result
    }
    fun defaultBook(reference: String): String? = find(reference).map { it.book }.distinct().singleOrNull()
}
