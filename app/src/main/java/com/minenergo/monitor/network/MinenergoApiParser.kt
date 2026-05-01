package com.minenergo.monitor.network

import com.minenergo.monitor.Config
import com.minenergo.monitor.data.Document
import com.minenergo.monitor.data.SiteConfig
import com.minenergo.monitor.log.AppLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Парсер для прямого обращения к API minenergo.gov.ru.
 *
 * Используется один endpoint: `/api/v1/?action=organizations.getItemDetail`
 * с параметром `code = {apiCode}`. Ответ содержит всю иерархию документов
 * организации в готовом JSON, что в десятки раз быстрее парсинга SPA.
 *
 * Структура ответа:
 * ```json
 * {
 *   "id": 22931,
 *   "name": "ПАО «Россети Центр и Приволжье»",
 *   "code": "pao_rosseti_tsentr_i_privolzhe",
 *   "docs": [
 *     {
 *       "id": 647,
 *       "name": "Информация о проектах ИПР...",
 *       "files": [
 *         { "ext": "ZIP", "id": 359261, "name": "...", "size": ...,
 *           "src": "/upload/...", "description": "...(Дата публикации: 24.12.2025)" }
 *       ]
 *     }
 *   ]
 * }
 * ```
 *
 * Дата публикации вытаскивается из текстового поля `description`
 * регулярным выражением, потому что отдельного поля даты в файле нет.
 */
object MinenergoApiParser {

    private const val TAG = "ApiParser"
    const val BASE_HOST: String = "minenergo.gov.ru"
    private const val BASE_URL: String = "https://minenergo.gov.ru/api/v1/"

    /** "(Дата публикации: ДД.ММ.ГГГГ)" с допуском к регистру и пробелам. */
    private val DATE_PUBLICATION_RE = Regex(
        """\(\s*Дата\s+публикации\s*:?\s*(\d{1,2}\.\d{1,2}\.\d{4})\s*\)?""",
        RegexOption.IGNORE_CASE,
    )

    /** Запасной шаблон — просто "ДД.ММ.ГГГГ" в конце строки. */
    private val ANY_DATE_RE = Regex("""(\d{2}\.\d{2}\.\d{4})""")

    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d.M.yyyy")

    private val json = Json { ignoreUnknownKeys = true }

    @Throws(IOException::class)
    fun findDocuments(site: SiteConfig): List<Document> {
        val code = site.apiCode?.takeIf { it.isNotBlank() }
            ?: throw IOException("Для API-источника не задан код организации (apiCode)")
        val raw = fetchJson(code)
        val parsed = parseResponse(raw, site)
        val cutoff = LocalDate.parse(site.dateFromIso)
        val filtered = parsed.filter { !it.publicationDate.isBefore(cutoff) }
        AppLogger.i(TAG, "[${site.name}] всего: ${parsed.size}, с датой >= $cutoff: ${filtered.size}")
        return filtered
    }

    @Throws(IOException::class)
    private fun fetchJson(code: String): String {
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("action", "organizations.getItemDetail")
            .addQueryParameter("lang", "ru")
            .addQueryParameter("code", code)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", Config.USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", "https://minenergo.gov.ru/")
            .build()
        val client = HttpClientFactory.get()
        var lastError: IOException? = null
        repeat(Config.RETRY_COUNT) { attempt ->
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} при обращении к API")
                    }
                    return response.body?.string()
                        ?: throw IOException("Пустой ответ API")
                }
            } catch (e: IOException) {
                lastError = e
                AppLogger.w(TAG, "Попытка ${attempt + 1} запроса к API не удалась: ${e.message}")
                Thread.sleep((400L * (attempt + 1)).coerceAtMost(2500L))
            }
        }
        throw lastError ?: IOException("Не удалось получить ответ API")
    }

    fun parseResponse(rawJson: String, site: SiteConfig): List<Document> {
        val root = runCatching { json.parseToJsonElement(rawJson).jsonObject }
            .getOrElse {
                AppLogger.w(TAG, "Не удалось распарсить JSON ответа: ${it.message}")
                return emptyList()
            }
        val orgName = root["name"]?.jsonPrimitive?.contentOrNull
            ?.let { stripHtml(it) }
            ?: site.name
        val docs = root["docs"]?.let { it as? JsonArray } ?: return emptyList()

        val sectionFilter: Set<Long> = site.apiSectionIds.toSet()
        val results = mutableListOf<Document>()

        for (sectionElement in docs) {
            val section = sectionElement as? JsonObject ?: continue
            val sectionId = section["id"]?.jsonPrimitive?.longOrNull
            if (sectionFilter.isNotEmpty() && (sectionId == null || sectionId !in sectionFilter)) {
                continue
            }
            val sectionName = section["name"]?.jsonPrimitive?.contentOrNull?.let { stripHtml(it) }
            val files = section["files"]?.let { it as? JsonArray } ?: continue
            for (fileElement in files) {
                val file = fileElement as? JsonObject ?: continue
                val doc = mapFileToDocument(file, site, orgName, sectionName) ?: continue
                results.add(doc)
            }
        }
        results.sortWith(compareByDescending<Document> { it.publicationDate }.thenBy { it.title })
        return results
    }

    private fun mapFileToDocument(
        file: JsonObject,
        site: SiteConfig,
        orgName: String,
        sectionName: String?,
    ): Document? {
        val src = file["src"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: return null
        val absoluteUrl = if (src.startsWith("http://") || src.startsWith("https://")) src
        else "https://$BASE_HOST" + (if (src.startsWith("/")) src else "/$src")

        val rawDescription = file["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val description = stripHtml(rawDescription)
        val nameOnDisk = file["name"]?.jsonPrimitive?.contentOrNull
        val ext = file["ext"]?.jsonPrimitive?.contentOrNull?.lowercase()?.let { ".${it}" }
        val sizeBytes = file["size"]?.jsonPrimitive?.longOrNull
        val sizeText = sizeBytes?.let { humanSize(it) }

        val publicationDate = extractDate(description)
            ?: run {
                AppLogger.i(TAG, "Пропущен файл без даты: $absoluteUrl")
                return null
            }

        // Заголовок: description, очищенный от хвостовой "(Дата публикации: ...)".
        val title = description
            .let { DATE_PUBLICATION_RE.replace(it, "").trim(' ', '.', ',', ';', '—', '-') }
            .ifBlank { nameOnDisk ?: "document" }
            .let { if (sectionName != null) "[${sectionName.take(50)}] $it" else it }

        return Document(
            title = title.take(300),
            publicationDateIso = publicationDate.toString(),
            url = absoluteUrl,
            extension = ext,
            sizeText = sizeText,
            sizeBytes = sizeBytes,
            siteId = site.id,
            siteName = orgName.take(80),
        )
    }

    private fun extractDate(text: String): LocalDate? {
        if (text.isBlank()) return null
        val matched = DATE_PUBLICATION_RE.find(text)?.groupValues?.getOrNull(1)
            ?: ANY_DATE_RE.findAll(text).lastOrNull()?.groupValues?.getOrNull(1)
            ?: return null
        return runCatching { LocalDate.parse(matched, DATE_FORMATTER) }.getOrNull()
    }

    /** Убирает HTML-сущности (&nbsp;, &laquo; и т.п.) и лишние пробелы. */
    private fun stripHtml(text: String): String {
        var t = text
        t = t.replace("&nbsp;", " ")
        t = t.replace("&laquo;", "«")
        t = t.replace("&raquo;", "»")
        t = t.replace("&ndash;", "–")
        t = t.replace("&mdash;", "—")
        t = t.replace("&quot;", "\"")
        t = t.replace("&amp;", "&")
        t = t.replace(Regex("<[^>]+>"), "")
        t = t.replace(Regex("\\s+"), " ").trim()
        return t
    }

    private fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes Б"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f КБ", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f МБ", mb)
        val gb = mb / 1024.0
        return String.format("%.2f ГБ", gb)
    }
}
