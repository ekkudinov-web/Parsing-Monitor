package com.minenergo.monitor.data

import kotlinx.serialization.Serializable

/**
 * Тип парсера, используемого для источника.
 *
 * - [UNIVERSAL_HTML] — универсальный парсер HTML-страниц (Jsoup + эвристики
 *   и/или CSS-селекторы из настроек сайта). Подходит для гос-сайтов с
 *   обычной серверной вёрсткой, где список документов сразу присутствует
 *   в HTML.
 * - [MINENERGO_API] — прямые запросы к JSON-API minenergo.gov.ru через
 *   action=organizations.getItemDetail. Возвращает все разделы и файлы
 *   указанной организации одним запросом. Работает в десятки раз быстрее
 *   и надёжнее, чем парсинг SPA на Angular.
 */
@Serializable
enum class SourceType {
    UNIVERSAL_HTML,
    MINENERGO_API,
}
