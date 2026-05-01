package com.minenergo.monitor.network

import com.minenergo.monitor.Config
import com.minenergo.monitor.data.Document
import com.minenergo.monitor.data.SiteConfig
import com.minenergo.monitor.log.AppLogger
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Универсальный парсер сайтов-источников.
 *
 * Алгоритм:
 * 1. Скачивает HTML страницы (с повторами).
 * 2. Выбирает ссылки по [SiteConfig.linkSelector] (по умолчанию `a[href]`)
 *    и фильтрует "похожие на документы" (по расширению или ключевым словам).
 * 3. Для каждой ссылки определяет контейнер с датой:
 *    - либо ближайший предок, попадающий под [SiteConfig.containerSelector],
 *    - либо ближайший предок (до 7 уровней), в тексте которого есть дата.
 * 4. Извлекает дату:
 *    - по [SiteConfig.dateRegex], если задан, с группой 1,
 *    - либо по стандартным шаблонам "дата публикации:", "от ДД.ММ.ГГГГ",
 *      или просто первой ДД.ММ.ГГГГ.
 * 5. Возвращает только документы с датой `>=` [SiteConfig.dateFromIso].
 */
object GenericSiteParser {

    private const val TAG = "Parser"

    private val DOCUMENT_EXTENSIONS = setOf(
        ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
        ".zip", ".rar", ".7z", ".rtf", ".odt", ".ods", ".csv", ".xml"
    )

    private val DEFAULT_DATE_PUBLICATION_RE =
        Regex("""дата\s+публикации\s*[:：]?\s*(\d{2}\.\d{2}\.\d{4})""", RegexOption.IGNORE_CASE)
    private val DEFAULT_DATE_AFTER_OT_RE =
        Regex("""\bот\s+(\d{2}\.\d{2}\.\d{4})\b""", RegexOption.IGNORE_CASE)
    private val DEFAULT_ANY_DATE_RE = Regex("""\b(\d{2}\.\d{2}\.\d{4})\b""")

    private val SIZE_RE =
        Regex("""\b(\d+(?:[.,]\d+)?)\s*(КБ|МБ|ГБ|KB|MB|GB)\b""", RegexOption.IGNORE_CASE)

    private val CONTAINER_TAGS = setOf("li", "tr", "article", "section")

    @Throws(IOException::class)
    fun fetchHtml(url: String): String {
        val client = HttpClientFactory.get()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", Config.USER_AGENT)
            .header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            .build()
        var lastError: IOException? = null
        repeat(Config.RETRY_COUNT) { attempt ->
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}")
                    }
                    val body = response.body
                        ?: throw IOException("Пустой ответ")
                    return body.string()
                }
            } catch (e: IOException) {
                lastError = e
                AppLogger.w(TAG, "Попытка ${attempt + 1} загрузки $url не удалась: ${e.message}")
                Thread.sleep((300L * (attempt + 1)).coerceAtMost(2000L))
            }
        }
        throw lastError ?: IOException("Не удалось загрузить страницу")
    }

    /** Полный pipeline для одного сайта: скачать + распарсить + отфильтровать по дате. */
    @Throws(IOException::class)
    fun findDocuments(site: SiteConfig): List<Document> {
        val html = fetchHtml(site.url)
        val all = parseDocuments(html, site)
        val cutoff = LocalDate.parse(site.dateFromIso)
        val filtered = all.filter { !it.publicationDate.isBefore(cutoff) }
        AppLogger.i(TAG, "[${site.name}] всего: ${all.size}, с датой >= $cutoff: ${filtered.size}")
        return filtered
    }

    fun parseDocuments(html: String, site: SiteConfig): List<Document> {
        val doc = Jsoup.parse(html, site.url)
        val seen = mutableSetOf<Triple<String, String, String>>()
        val result = mutableListOf<Document>()

        val linkSelector = site.linkSelector?.takeIf { it.isNotBlank() } ?: "a[href]"
        val links = runCatching { doc.select(linkSelector) }
            .onFailure { AppLogger.w(TAG, "Некорректный selector '$linkSelector': ${it.message}") }
            .getOrNull() ?: return emptyList()

        val customRegex = site.dateRegex
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrNull() }

        val dateFormatter = runCatching { DateTimeFormatter.ofPattern(site.dateFormat) }
            .getOrDefault(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

        for (anchor in links) {
            val href = anchor.attr("href")
            if (!isDocumentLink(href)) continue
            val absolute = anchor.absUrl("href").ifEmpty { href }

            val container = containerForLink(anchor, site.containerSelector)
            val contextText = container.text()

            val publicationDate = extractDate(contextText, customRegex, dateFormatter)
            if (publicationDate == null) {
                AppLogger.i(TAG, "Пропущена ссылка без даты: $absolute")
                continue
            }

            val title = extractTitle(anchor, contextText, absolute)
            val extension = extensionOf(absolute)
            val (sizeText, sizeBytes) = extractSize(contextText)

            val key = Triple(absolute, publicationDate.toString(), title)
            if (!seen.add(key)) continue

            result.add(
                Document(
                    title = title,
                    publicationDateIso = publicationDate.toString(),
                    url = absolute,
                    extension = extension,
                    sizeText = sizeText,
                    sizeBytes = sizeBytes,
                    siteId = site.id,
                    siteName = site.name,
                )
            )
        }
        result.sortWith(compareByDescending<Document> { it.publicationDate }.thenBy { it.title })
        return result
    }

    // === helpers ===

    private fun isDocumentLink(href: String): Boolean {
        if (href.isBlank()) return false
        if (href.startsWith("#") || href.startsWith("mailto:") || href.startsWith("tel:")) return false
        val ext = extensionOf(href)
        if (ext != null && ext in DOCUMENT_EXTENSIONS) return true
        val lowered = href.lowercase()
        return listOf("/upload/", "/uploads/", "/download", "download=").any { it in lowered }
    }

    private fun containerForLink(anchor: Element, customContainerSelector: String?): Element {
        // Если задан кастомный селектор — ищем ближайшего предка, ему соответствующего.
        if (!customContainerSelector.isNullOrBlank()) {
            var current: Element? = anchor
            while (current != null) {
                if (matches(current, customContainerSelector)) return current
                current = current.parent()
            }
            // Не нашли — fallback на родителя.
            return anchor.parent() ?: anchor
        }
        // Стандартная эвристика: поднимаемся до 7 уровней, ищем элемент с датой в тексте.
        var current: Element = anchor
        repeat(7) {
            val parent = current.parent() ?: return@repeat
            val text = parent.text()
            if (DEFAULT_DATE_PUBLICATION_RE.containsMatchIn(text) ||
                DEFAULT_DATE_AFTER_OT_RE.containsMatchIn(text)
            ) return parent
            if (parent.tagName() in CONTAINER_TAGS && DEFAULT_ANY_DATE_RE.containsMatchIn(text)) return parent
            current = parent
        }
        return anchor.parent() ?: anchor
    }

    private fun matches(element: Element, selector: String): Boolean =
        runCatching { element.`is`(selector) }.getOrDefault(false)

    private fun extractDate(
        text: String,
        customRegex: Regex?,
        formatter: DateTimeFormatter,
    ): LocalDate? {
        // Если задан кастомный regex с группой 1 — пробуем его.
        if (customRegex != null) {
            val match = customRegex.find(text)
            val raw = match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
                ?: match?.value
            if (raw != null) {
                runCatching { return LocalDate.parse(raw, formatter) }
            }
        }
        // Стандартные шаблоны.
        val match =
            DEFAULT_DATE_PUBLICATION_RE.find(text)?.groupValues?.get(1)
                ?: DEFAULT_DATE_AFTER_OT_RE.find(text)?.groupValues?.get(1)
                ?: DEFAULT_ANY_DATE_RE.find(text)?.groupValues?.get(1)
                ?: return null
        return runCatching {
            LocalDate.parse(match, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        }.getOrNull()
    }

    private fun extractTitle(anchor: Element, contextText: String, url: String): String {
        val anchorText = cleanTitle(anchor.text())
        if (anchorText.length >= 5) return anchorText.take(300)

        var ctx = contextText
        DEFAULT_DATE_PUBLICATION_RE.find(ctx)?.let { ctx = ctx.substring(0, it.range.first) }
            ?: DEFAULT_ANY_DATE_RE.find(ctx)?.let { ctx = ctx.substring(0, it.range.first) }
        val cleaned = cleanTitle(ctx)
        if (cleaned.length >= 5) return cleaned.take(300)

        return runCatching {
            val path = URI(url).path ?: return@runCatching null
            val name = URLDecoder.decode(path.substringAfterLast('/'), Charsets.UTF_8)
            if (name.isBlank()) null else name
        }.getOrNull() ?: "document"
    }

    private fun cleanTitle(text: String): String {
        var result = text.replace(Regex("\\s+"), " ").trim()
        result = DEFAULT_DATE_PUBLICATION_RE.replace(result, "")
        result = result.replace(Regex("""\(\s*\)"""), "")
        result = result.replace(Regex("""\bдата\s+публикации\b\s*[:：]?""", RegexOption.IGNORE_CASE), "")
        return result.trim(' ', '-', '—', '–', ':', ';', ',', '.')
    }

    /** @return пара (текстовое представление, размер в байтах). */
    private fun extractSize(text: String): Pair<String?, Long?> {
        val match = SIZE_RE.find(text) ?: return Pair(null, null)
        val numStr = match.groupValues[1].replace(',', '.')
        val number = numStr.toDoubleOrNull() ?: return Pair(match.value, null)
        val unitMul: Long = when (match.groupValues[2].uppercase()) {
            "КБ", "KB" -> 1024L
            "МБ", "MB" -> 1024L * 1024L
            "ГБ", "GB" -> 1024L * 1024L * 1024L
            else -> return Pair(match.value, null)
        }
        return Pair(match.value, (number * unitMul).toLong())
    }

    private fun extensionOf(url: String): String? {
        val path = runCatching { URI(url).path }.getOrNull() ?: url
        val name = path.substringAfterLast('/')
        val decoded = runCatching { URLDecoder.decode(name, Charsets.UTF_8) }.getOrDefault(name)
        val dotIndex = decoded.lastIndexOf('.')
        if (dotIndex < 0) return null
        val ext = decoded.substring(dotIndex).lowercase()
        return if (ext.length in 2..6) ext else null
    }
}
