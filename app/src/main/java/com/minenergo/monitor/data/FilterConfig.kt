package com.minenergo.monitor.data

import kotlinx.serialization.Serializable

/**
 * Фильтр найденных документов. Используется в двух местах:
 * - глобальный фильтр в настройках,
 * - локальный фильтр внутри [SiteConfig.siteFilter].
 *
 * Документ считается прошедшим фильтр, если выполнены ВСЕ
 * заданные критерии (AND). Критерий считается заданным, если
 * соответствующее поле непустое (для строк/наборов) или непусто
 * (для размеров — задан min или max).
 *
 * Глобальный и per-site фильтры применяются последовательно (AND).
 */
@Serializable
data class FilterConfig(
    val enabled: Boolean = false,
    /** Подстроки, которые должны встречаться в названии. ИЛИ — достаточно одного совпадения. */
    val titleContains: List<String> = emptyList(),
    /** Если true — учитывать регистр; если false — без учёта регистра. */
    val titleCaseSensitive: Boolean = false,
    /** Список расширений (с точкой, в нижнем регистре): ".pdf", ".docx" и т.д. ИЛИ. */
    val extensions: List<String> = emptyList(),
    /** Минимальный размер в МБ (включительно). */
    val sizeMinMb: Double? = null,
    /** Максимальный размер в МБ (включительно). */
    val sizeMaxMb: Double? = null,
    /** Если true — документы без указанного на странице размера всё равно проходят фильтр размера. */
    val passUnknownSize: Boolean = true,
) {
    fun isEffectivelyEmpty(): Boolean =
        !enabled || (
            titleContains.isEmpty() &&
                extensions.isEmpty() &&
                sizeMinMb == null &&
                sizeMaxMb == null
            )

    companion object {
        val EMPTY: FilterConfig = FilterConfig()
    }
}
