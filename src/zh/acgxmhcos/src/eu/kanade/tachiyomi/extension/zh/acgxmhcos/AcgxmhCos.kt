package eu.kanade.tachiyomi.extension.zh.acgxmhcos

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
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

    private val json = Json { ignoreUnknownKeys = true }

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

    private val searchIndex: SearchIndex? by lazy { loadSearchIndex() }

    private fun loadSearchIndex(): SearchIndex? = try {
        javaClass.getResourceAsStream("/assets/index.json")?.use { stream ->
            json.decodeFromStream<SearchIndex>(stream)
        }
    } catch (_: Exception) {
        null
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.isBlank()) {
            return fetchPopularManga(page)
        }

        val index = searchIndex
        if (index != null) {
            return Observable.just(searchFromIndex(query, page, index))
        }

        return fetchPopularManga(page).map { mangasPage ->
            val filtered = mangasPage.mangas.filter { manga ->
                manga.title.contains(query, ignoreCase = true)
            }
            MangasPage(filtered, mangasPage.hasNextPage)
        }
    }

    private fun searchFromIndex(query: String, page: Int, index: SearchIndex): MangasPage {
        val matched = index.entries.filter { entry ->
            entry.title.contains(query, ignoreCase = true)
        }
        val pageSize = 36
        val start = (page - 1) * pageSize
        val end = minOf(start + pageSize, matched.size)
        val hasNext = end < matched.size

        val mangas = if (start < matched.size) {
            matched.subList(start, end).map { entry ->
                SManga.create().apply {
                    url = "/cos/${entry.id}.html"
                    title = entry.title
                    thumbnail_url = entry.thumb
                }
            }
        } else {
            emptyList()
        }

        return MangasPage(mangas, hasNext)
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

        val firstImg = document.selectFirst("p.manga-picture img")?.attr("src")
            ?: return emptyList()
        pages.add(Page(0, imageUrl = firstImg))

        val totalPages = getTotalPages(document)

        if (totalPages <= 1) return pages

        val docUrl = document.location()
        val basePageUrl = docUrl.replace(Regex("-\\d+\\.html$"), "").removeSuffix(".html")

        for (i in 2..totalPages) {
            val pageUrl = "$basePageUrl-$i.html"
            pages.add(Page(i - 1, url = pageUrl))
        }

        return pages
    }

    private fun getTotalPages(document: Document): Int {
        val pageLinks = document.select("div.page#pages a")
        if (pageLinks.isNotEmpty()) {
            var maxPage = 1
            for (link in pageLinks) {
                val href = link.attr("href")
                val match = Regex("-(\\d+)\\.html").find(href)
                if (match != null) {
                    val pageNum = match.groupValues[1].toIntOrNull() ?: continue
                    if (pageNum > maxPage) maxPage = pageNum
                }
            }
            if (maxPage > 1) return maxPage
        }

        val title = document.selectFirst("h1.title")?.text() ?: return 1
        val match = Regex("\\[(\\d+)P\\]").find(title)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 1
    }

    override fun imageUrlParse(document: Document): String = document.selectFirst("p.manga-picture img")?.attr("src")
        ?: throw Exception("Image not found on page")

    // ============================== Data classes ==============================

    @Serializable
    data class SearchIndex(
        val entries: List<SearchEntry> = emptyList(),
    )

    @Serializable
    data class SearchEntry(
        val id: Int,
        val title: String,
        val thumb: String = "",
    )
}
