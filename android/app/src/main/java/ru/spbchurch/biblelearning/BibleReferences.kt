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
    private fun normalize(value: String) = value.lowercase().replace(".", "").replace(Regex("\\s+"), "")
    private val aliases = books.flatMap { book -> book.aliases.map { normalize(it) to book.code } }.toMap()
    private val names = books.flatMap { it.aliases }.sortedByDescending { it.length }.joinToString("|") {
        it.split(Regex("\\s+")).joinToString("\\s*") { part -> Regex.escape(part.removeSuffix(".")) + "\\.?" }
    }
    private const val point = "[0-9]{1,3}(?:\\s*[:.]\\s*[0-9]{1,3})?"
    private const val location = "$point(?:\\s*[-–—]\\s*$point)?"
    private val explicit = Regex("($names)\\s*($location)(?![0-9:])", RegexOption.IGNORE_CASE)
    private val contextual = Regex("([0-9]{1,3}\\s*:\\s*[0-9]{1,3}(?:\\s*[-–—]\\s*$point)?)(?![0-9:])")
    private fun valid(raw: String): String? {
        val value = raw.replace(Regex("\\s+"), "").replace(':', '.').replace('–', '-').replace('—', '-')
        val numbers = value.split('.', '-').mapNotNull { it.toIntOrNull() }
        return value.takeIf { numbers.isNotEmpty() && numbers.all { n -> n in 1..176 } }
    }
    fun find(text: String, defaultBook: String? = null): List<BibleReference> {
        val named = explicit.findAll(text).mapNotNull { match ->
            if (match.range.first > 0 && text[match.range.first - 1].isLetterOrDigit()) return@mapNotNull null
            val book = aliases[normalize(match.groupValues[1])] ?: return@mapNotNull null
            val loc = valid(match.groupValues[2]) ?: return@mapNotNull null
            BibleReference(match.range.first, match.range.last + 1, book, loc)
        }.toList()
        val implied = if (defaultBook in books.map { it.code }) contextual.findAll(text).mapNotNull { match ->
            if (match.range.first > 0 && text[match.range.first - 1].let { it.isLetterOrDigit() || it in "/:." }) return@mapNotNull null
            if (named.any { match.range.first < it.end && match.range.last >= it.start }) return@mapNotNull null
            val loc = valid(match.value) ?: return@mapNotNull null
            BibleReference(match.range.first, match.range.last + 1, defaultBook!!, loc)
        }.toList() else emptyList()
        return (named + implied).sortedBy { it.start }
    }
    fun defaultBook(reference: String): String? = find(reference).map { it.book }.distinct().singleOrNull()
}
