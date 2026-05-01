package com.minenergo.monitor.data

/**
 * Применение фильтров к списку документов.
 * Глобальный и per-site фильтры применяются последовательно (AND).
 */
object DocumentFilter {

    private const val MB: Long = 1024L * 1024L

    /**
     * Возвращает только те документы, которые проходят оба фильтра.
     * Если оба фильтра пустые/выключенные — список не меняется.
     */
    fun apply(
        documents: List<Document>,
        globalFilter: FilterConfig,
        sites: List<SiteConfig>,
    ): List<Document> {
        val sitesById = sites.associateBy { it.id }
        return documents.filter { doc ->
            passes(doc, globalFilter) &&
                passes(doc, sitesById[doc.siteId]?.siteFilter)
        }
    }

    fun passes(document: Document, filter: FilterConfig?): Boolean {
        if (filter == null || filter.isEffectivelyEmpty()) return true

        // Подстроки в названии — ИЛИ.
        if (filter.titleContains.isNotEmpty()) {
            val title = if (filter.titleCaseSensitive) document.title else document.title.lowercase()
            val matchAny = filter.titleContains.any { needle ->
                val n = if (filter.titleCaseSensitive) needle else needle.lowercase()
                n.isNotBlank() && n in title
            }
            if (!matchAny) return false
        }

        // Расширения — ИЛИ. Документы без расширения не проходят, если фильтр задан.
        if (filter.extensions.isNotEmpty()) {
            val docExt = document.extension?.lowercase() ?: return false
            val allowed = filter.extensions.map { it.lowercase() }.toSet()
            if (docExt !in allowed) return false
        }

        // Размер. Если у документа размер не известен — определяется флагом passUnknownSize.
        if (filter.sizeMinMb != null || filter.sizeMaxMb != null) {
            val bytes = document.sizeBytes
            if (bytes == null) {
                if (!filter.passUnknownSize) return false
            } else {
                val mb = bytes.toDouble() / MB
                if (filter.sizeMinMb != null && mb < filter.sizeMinMb) return false
                if (filter.sizeMaxMb != null && mb > filter.sizeMaxMb) return false
            }
        }
        return true
    }
}
