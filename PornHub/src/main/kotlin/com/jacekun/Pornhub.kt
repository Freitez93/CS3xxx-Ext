package com.jacekun

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import org.jsoup.nodes.Element
import android.util.Log

class Pornhub : MainAPI() {
    private val globalTvType = TvType.NSFW

    override var mainUrl = "https://www.pornhub.com"
    override var name = "PornHub"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val vpnStatus = VPNStatus.MightBeNeeded // Cause it's a big site
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage = mainPageOf(
        "${mainUrl}/video?page=" to "Recently Featured",
        "${mainUrl}/video?o=tr&t=w&hd=1&page=" to "Top Rated",
        "${mainUrl}/video?c=29&page=" to "Milf",
        "${mainUrl}/video?c=89&page=" to "Babysitters",
        "${mainUrl}/video=c=141&page=" to "Behind the scenes",
        "${mainUrl}/video?c=181&page=" to "Young and Old",
        "${mainUrl}/video?c=444&page=" to "Daughter",
        "${mainUrl}/language/spanish?page=" to "Spanish"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("div.sectionWrapper div.wrap").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(name = request.name, list = home, isHorizontalImages = true),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("a")?.attr("title") ?: return null
        val link      = this.selectFirst("a")?.attr("href") ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img.thumb")?.attr("src"))

        return newMovieSearchResponse(title, link, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/video/search?search=${query}").document

        return document.select("div.sectionWrapper div.wrap").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.title span[class='inlineFree']")?.text()?.trim() ?: return null
        val description = title
        val poster = fixUrlNull(document.selectFirst("div.mainPlayerDiv img")?.attr("src"))
        val year = Regex("""uploadDate\": \"(\d+)""").find(document.html())?.groupValues?.get(1)?.toIntOrNull()
        val tags = document.select("div.categoriesWrapper a[data-label='Category']").map {
            it?.text()?.trim().toString().replace(", ", "")
        }
        val rating = document.selectFirst("span.percent")?.text()?.first()?.toString()?.toRatingInt()
        val duration = Regex("duration' : '(.*)',").find(document.html())?.groupValues?.get(1)?.toIntOrNull()
        val actors = document.select("div.pornstarsWrapper a[data-label='pornstar']").mapNotNull {
            Actor(it.text().trim(), it.select("img").attr("src"))
        }

        val recommendations = document.selectXpath("//a[contains(@class, 'img')]").mapNotNull {
            val recName = it?.attr("title")?.trim() ?: return@mapNotNull null
            val recHref = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
            val recPosterUrl = fixUrlNull(it.selectFirst("img")?.attr("src"))
            newMovieSearchResponse(recName, recHref, TvType.NSFW) {
                this.posterUrl = recPosterUrl
            }
        }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.rating = rating
            this.duration = duration
            this.recommendations = recommendations
            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("PHub", "url » ${data}")
        val source = app.get(data).text
        val extracted_value = Regex("""([^\"]*master.m3u8?.[^\"]*)""").find(source)?.groups?.last()?.value
            ?: return false
        val m3u_link = extracted_value.replace("\\", "")

        Log.d("PHub", "extracted_value » ${extracted_value}")
        Log.d("PHub", "m3u_link » ${m3u_link}")

        callback.invoke(
            ExtractorLink(
                source = this.name,
                name = this.name,
                url = m3u_link,
                referer = "${mainUrl}/",
                quality = Qualities.Unknown.value,
                isM3u8 = true
            )
        )

        return true
    }
}
