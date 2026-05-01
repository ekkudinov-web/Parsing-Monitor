package com.minenergo.monitor.network

import com.minenergo.monitor.data.Document
import com.minenergo.monitor.data.SiteConfig
import com.minenergo.monitor.data.SourceType
import java.io.IOException

/**
 * Точка входа для всех типов источников. Выбирает правильный парсер
 * по [SiteConfig.sourceType] и возвращает уже отфильтрованный по
 * [SiteConfig.dateFromIso] список документов.
 */
object SiteParser {
    @Throws(IOException::class)
    fun findDocuments(site: SiteConfig): List<Document> = when (site.sourceType) {
        SourceType.MINENERGO_API -> MinenergoApiParser.findDocuments(site)
        SourceType.UNIVERSAL_HTML -> GenericSiteParser.findDocuments(site)
    }
}
