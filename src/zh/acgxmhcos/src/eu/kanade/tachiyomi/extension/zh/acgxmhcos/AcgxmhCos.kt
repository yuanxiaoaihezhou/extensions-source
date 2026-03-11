package eu.kanade.tachiyomi.extension.zh.acgxmhcos

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class AcgxmhCos : ParsedHttpSource() {

    override val name = "ACGXmh Cos"

    override val baseUrl = "https://www.acgxmh.com"

    override val lang = "zh"

    override val supportsLatest = false

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val url = if (page == 1) "$baseUrl/cos/" else "$baseUrl/cos/index-$page.html"
        return GET(url, headers)
    }

    override fun popularMangaSelector() = "ul#list > li"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val linkElement = element.selectFirst("a.thumb")!!
        url = linkElement.attr("href")
        title = linkElement.attr("title")
        thumbnail_url = element.selectFirst("img")?.attr("src")
    }

    override fun popularMangaNextPageSelector() = "div.page.bigpage span + a"

    // ============================== Latest ==============================

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()

    // ============================== Search ==============================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.isBlank()) {
            return fetchPopularManga(page)
        }
        return fetchPopularManga(page).map { mangasPage ->
            val filtered = mangasPage.mangas.filter { manga ->
                manga.title.contains(query, ignoreCase = true)
            }
            MangasPage(filtered, mangasPage.hasNextPage)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()
    override fun searchMangaSelector() = throw UnsupportedOperationException()
    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException()
    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException()

    // ============================== Details ==============================

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.title")?.text()
            ?.replace(Regex("\\(\\d+\\)$"), "")?.trim() ?: ""
        artist = document.select("div.summary a").eachText().joinToString(", ").ifEmpty { null }
        author = artist
        description = buildString {
            document.selectFirst("div.info")?.let { info ->
                info.select("span").forEach { span ->
                    val text = span.text().trim()
                    if (text.isNotEmpty()) {
                        if (isNotEmpty()) append("\n")
                        append(text)
                    }
                }
            }
        }.ifEmpty { null }
        genre = "Cosplay"
        status = SManga.COMPLETED
        thumbnail_url = document.selectFirst("p.manga-picture img")?.attr("src")
    }

    // ============================== Chapters ==============================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Photos"
        }
        return Observable.just(listOf(chapter))
    }

    override fun chapterListSelector() = throw UnsupportedOperationException()
    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    // ============================== Pages ==============================

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        // Get the first image from the current page
        val firstImg = document.selectFirst("p.manga-picture img")?.attr("src")
            ?: return emptyList()
        pages.add(Page(0, imageUrl = firstImg))

        // Determine total page count from pagination
        val totalPages = getTotalPages(document)

        if (totalPages <= 1) return pages

        // The image URL pattern: base/01.webp, base/02.webp, etc.
        // Extract the base URL and file extension from the first image
        val lastSlashIndex = firstImg.lastIndexOf('/')
        if (lastSlashIndex == -1) return pages

        val baseImgUrl = firstImg.substring(0, lastSlashIndex + 1)
        val extension = firstImg.substringAfterLast('.')

        // Generate all remaining page image URLs
        for (i in 2..totalPages) {
            val imgNum = String.format("%02d", i)
            val imageUrl = "$baseImgUrl$imgNum.$extension"
            pages.add(Page(i - 1, imageUrl = imageUrl))
        }

        return pages
    }

    private fun getTotalPages(document: Document): Int {
        // Try to extract from pagination: last page link like /cos/764811-30.html
        val pageLinks = document.select("div.page#pages a")
        if (pageLinks.isNotEmpty()) {
            val lastLink = pageLinks.last()?.attr("href") ?: return 1
            // Pattern: /cos/{id}-{pageNum}.html
            val match = Regex("-(\\d+)\\.html$").find(lastLink)
            if (match != null) {
                return match.groupValues[1].toIntOrNull() ?: 1
            }
        }

        // Try to extract from title like [30P]
        val title = document.selectFirst("h1.title")?.text() ?: return 1
        val match = Regex("\\[(\\d+)P\\]").find(title)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 1
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()
}
