package com.minenergo.monitor.data

import kotlinx.serialization.Serializable

/**
 * Описание одного сайта-источника.
 *
 * Источники бывают двух типов (см. [SourceType]):
 *
 * 1. **UNIVERSAL_HTML** — парсер берёт URL [url], скачивает HTML и
 *    ищет ссылки + даты по эвристикам или по [linkSelector] /
 *    [containerSelector] / [dateRegex]. Подходит для сайтов с
 *    серверной вёрсткой.
 *
 * 2. **MINENERGO_API** — парсер делает запрос к API minenergo.gov.ru
 *    `?action=organizations.getItemDetail&code={apiCode}`. Из ответа
 *    извлекаются все файлы во всех разделах организации. Если задан
 *    [apiSectionIds] — оставляются только разделы с этими id.
 *    Параметр [url] для этого режима не используется парсером, но
 *    хост из него попадает в allowlist для скачивания.
 */
@Serializable
data class SiteConfig(
    val id: String,
    val name: String,
    val url: String,
    val dateFromIso: String,
    val enabled: Boolean = true,
    /** Тип источника. По умолчанию — универсальный HTML-парсер. */
    val sourceType: SourceType = SourceType.UNIVERSAL_HTML,
    // === Поля только для UNIVERSAL_HTML ===
    /** CSS-селектор ссылок. Если null — используется `a[href]`. */
    val linkSelector: String? = null,
    /** CSS-селектор контейнера, в тексте которого ищется дата. */
    val containerSelector: String? = null,
    /** Regex для даты. Должен иметь группу 1 = дата. */
    val dateRegex: String? = null,
    /** Формат даты, например "dd.MM.yyyy". */
    val dateFormat: String = "dd.MM.yyyy",
    // === Поля только для MINENERGO_API ===
    /**
     * Код организации в API minenergo. Например для Россети ЦиП:
     * `pao_rosseti_tsentr_i_privolzhe`. Это ровно тот же сегмент,
     * который виден в URL страницы на сайте Минэнерго.
     */
    val apiCode: String? = null,
    /**
     * Идентификаторы разделов, которые нужно отслеживать. Пустой
     * список = брать все разделы. Например, для "Информация о
     * проектах ИПР" нужен раздел id=647.
     */
    val apiSectionIds: List<Long> = emptyList(),
    /** Локальный фильтр поверх глобального. */
    val siteFilter: FilterConfig? = null,
)
