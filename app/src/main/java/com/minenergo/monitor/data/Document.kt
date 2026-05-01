package com.minenergo.monitor.data

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Документ, найденный на сайте-источнике.
 */
@Serializable
data class Document(
    val title: String,
    val publicationDateIso: String,
    val url: String,
    val extension: String? = null,
    val sizeText: String? = null,
    /** Размер в байтах, если удалось распарсить из [sizeText]. */
    val sizeBytes: Long? = null,
    /** Идентификатор сайта-источника (для группировки и фильтрации). */
    val siteId: String,
    /** Имя сайта на момент находки — кэшируется, чтобы оно отображалось,
     *  даже если пользователь позже переименует сайт. */
    val siteName: String,
) {
    val publicationDate: LocalDate
        get() = LocalDate.parse(publicationDateIso)
}

data class DownloadResult(
    val document: Document,
    val downloaded: Boolean,
    val filePath: String? = null,
    val error: String? = null,
    val archiveUnpacked: Boolean = false,
    val unpackDir: String? = null,
    val unpackedFilesCount: Int = 0,
    val skippedFilesCount: Int = 0,
    val archiveError: String? = null,
)
