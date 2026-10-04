package org.koitharu.kotatsu.parsers.site.booru.wordpress

import okhttp3.HttpUrl
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

/**
 * HTML parser for WordPress video listings which place a direct MP4 or WebM URL in every card.
 */
internal abstract class WordpressVideoParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
) : PagedMangaParser(context, source, PAGE_SIZE) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.NEWEST)

	override val filterCapabilities = MangaListFilterCapabilities(
	isMultipleTagsSupported = false,
	isTagsExclusionSupported = false,
	isSearchSupported = true,
	isSearchWithFiltersSupported = true,
)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
	availableContentRating = EnumSet.of(ContentRating.ADULT),
)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val query = filter.query?.trim()?.nullIfEmpty()
		val url = listingUrl(page, query)
		val document = webClient.httpGet(url).parseHtml()
		val result = LinkedHashMap<String, Manga>()
		document.select("video[src], video source[src], a[href]").forEach { element ->
			val videoUrl = element.directVideoUrl() ?: return@forEach
			if (!videoUrl.startsWith("https://$domain/")) return@forEach
			result.putIfAbsent(videoUrl, element.toManga(videoUrl))
		}
		return result.values.toList()
	}

	override suspend fun getDetails(manga: Manga): Manga = manga.copy(
		chapters = listOf(
			MangaChapter(
				id = generateUid(manga.url),
				title = null,
				number = 1f,
				volume = 0,
				url = manga.url,
				scanlator = null,
				uploadDate = 0L,
				branch = null,
				source = source,
			),
		),
	)

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> = listOf(
		MangaPage(
			id = generateUid(chapter.url),
			url = chapter.url,
			preview = null,
			source = source,
		),
	)

	private fun listingUrl(page: Int, query: String?): String {
		if (query == null) {
			return if (page <= 1) "https://$domain/" else "https://$domain/page/$page/"
		}
		return HttpUrl.Builder()
			.scheme("https")
			.host(domain)
			.addQueryParameter("s", query)
			.apply { if (page > 1) addQueryParameter("paged", page.toString()) }
			.build()
			.toString()
	}

	private fun Element.directVideoUrl(): String? {
		val raw = attr("src").nullIfEmpty() ?: attr("href").nullIfEmpty() ?: return null
		val url = raw.toAbsoluteUrl(domain)
		return url.takeIf { VIDEO_EXTENSION_REGEX.containsMatchIn(it.substringBefore('?')) }
	}

	private fun Element.toManga(videoUrl: String): Manga {
		val card = closest("article, li, .item, .post") ?: parent()
		val title = card?.selectFirst("h1, h2, h3, h4, .entry-title")?.text()?.nullIfEmpty()
			?: videoUrl.substringAfterLast('/').substringBeforeLast('.').replace('-', ' ').replace('_', ' ')
		val cover = card?.selectFirst("img[src], img[data-src]")?.let { image ->
			(image.attr("data-src").nullIfEmpty() ?: image.attr("src").nullIfEmpty())?.toAbsoluteUrl(domain)
		}
		return Manga(
			id = generateUid(videoUrl),
			title = title,
			altTitles = emptySet(),
			url = videoUrl,
			publicUrl = videoUrl,
			rating = RATING_UNKNOWN,
			contentRating = ContentRating.ADULT,
			coverUrl = cover,
			tags = emptySet(),
			state = null,
			authors = emptySet(),
			largeCoverUrl = cover,
			source = source,
		)
	}

	private companion object {
		const val PAGE_SIZE = 8
		val VIDEO_EXTENSION_REGEX = Regex("\\.(mp4|webm)$", RegexOption.IGNORE_CASE)
	}
}
