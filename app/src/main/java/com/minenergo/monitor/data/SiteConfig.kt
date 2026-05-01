package com.minenergo.monitor.data

import kotlinx.serialization.Serializable

/**
 * Описание одного сайта-источника. Хранится в SharedPreferences в
 * виде JSON-массива через [PreferencesStore].
 *
 * Поля [linkSelector], [containerSelector] и [dateRegex] позволяют
 * "донастроить" парсер под нестандартный сайт — для большинства
 * гос-сайтов их можно оставить пустыми и положиться на универсальную
 * эвристику.
 */
@Serializable
data class SiteConfig(
    val id: String,
    val name: String,
    val url: String,
    val dateFromIso: String,
    val enabled: Boolean = true,
    /** CSS-селектор ссылок на документы. Если null — используется `a[href]`. */
    val linkSelector: String? = null,
    /** CSS-селектор контейнера, в тексте которого ищется дата. Если null — поднимаемся по DOM. */
    val containerSelector: String? = null,
    /** Regex для извлечения даты. Должен иметь группу 1 = дата. Если null — стандартные шаблоны. */
    val dateRegex: String? = null,
    /** Формат даты для парсинга, например "dd.MM.yyyy". */
    val dateFormat: String = "dd.MM.yyyy",
    /** Локальный фильтр поверх глобального. Если null — используется только глобальный. */
    val siteFilter: FilterConfig? = null,
)
