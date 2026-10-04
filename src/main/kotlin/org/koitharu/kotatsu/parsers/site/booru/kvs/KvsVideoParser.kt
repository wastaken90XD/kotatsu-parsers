package org.koitharu.kotatsu.parsers.site.booru.kvs

import okhttp3.HttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Parser for Kernel Video Sharing-style sites with direct, time-signed MP4 download links.
 *
 * Download URLs are intentionally read from the post immediately before use rather than retained
 * in [Manga.url], because the site signs each URL. The page HTML exposes a factual quality label
 * for every downloadable rendition, so each rendition is represented as a chapter.
 */
internal abstract class KvsVideoParser(
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

	private val mediaCache = ConcurrentHashMap<Long, PostMedia>()

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

	override suspend fun getFilterOptions() = MangaListFilterOptions(
	availableContentRating = EnumSet.of(ContentRating.ADULT),
	// The site has no safe/questionable selector: it is an adult-video index.
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val query = filter.query?.trim()?.nullIfEmpty()
		// The server renders only search result page one and uses a JavaScript-only async endpoint
		// for later pages. Returning an empty list avoids repeating page one indefinitely.
		if (query != null && page > 1) return emptyList()
		val document = webClient.httpGet(listUrl(page, query)).parseHtml()
		if (document.title().contains("404", ignoreCase = true)) return emptyList()
		return document.select("a[href*=/video/]").mapNotNull { anchor ->
			anchor.toMangaOrNull()
		}.distinctBy { it.url }
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val id = postId(manga.url)
		val document = webClient.httpGet(manga.url).parseHtml()
		val media = parseMedia(document)
		mediaCache[id] = media
		val title = document.selectFirst("h1")?.text()?.trim()?.nullIfEmpty() ?: manga.title
		val tags = document.select("a[href*=/tags/]").mapNotNull { element ->
			element.text().trim().nullIfEmpty()?.let { name ->
				MangaTag(name, name.lowercase(Locale.ROOT), source)
			}
		}.toSet()
		val cover = document.selectFirst("img[src*=/contents/videos_screenshots/]")?.directUrl()
		val chapters = media.variants.mapIndexed { index, variant ->
			MangaChapter(
				id = generateUid("${manga.url}?quality=${variant.key}"),
				title = variant.label,
				number = index.toFloat(),
				volume = 0,
				url = "${manga.url.substringBefore('?')}?quality=${variant.key}",
				scanlator = null,
				uploadDate = 0L,
				branch = null,
				source = source,
			)
		}
		return manga.copy(
			title = title,
			coverUrl = cover ?: manga.coverUrl,
			largeCoverUrl = cover ?: manga.largeCoverUrl,
			tags = tags.ifEmpty { manga.tags },
			chapters = chapters,
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val id = postId(chapter.url)
		val key = chapter.url.substringAfter("quality=", "").substringBefore('&')
		val media = mediaCache[id] ?: webClient.httpGet(chapter.url.substringBefore('?')).parseHtml()
			.let(::parseMedia)
			.also { mediaCache[id] = it }
		val directUrl = media.variants.firstOrNull { it.key == key }?.url
			?: throw ParseException("Video rendition '$key' is unavailable", chapter.url)
		return listOf(MangaPage(generateUid(directUrl), directUrl, media.previewUrl, source))
	}

	private fun listUrl(page: Int, query: String?): String = if (query == null) {
		if (page <= 1) "https://$domain/latest-updates/" else "https://$domain/latest-updates/$page/"
	} else {
		val pathQuery = query.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString("+")
		HttpUrl.Builder()
			.scheme("https")
			.host(domain)
			.addPathSegment("search")
			.addPathSegment(pathQuery)
			.build()
			.toString()
	}

	private fun Element.toMangaOrNull(): Manga? {
		val relativeUrl = attr("href").substringBefore('#').nullIfEmpty() ?: return null
		if (!relativeUrl.contains(VIDEO_PATH_REGEX)) return null
		val url = relativeUrl.toAbsoluteUrl(domain)
		val title = attr("title").trim().nullIfEmpty() ?: return null
		val duration = durationDescription(text())
		val cover = previewUrl(url)
		return Manga(
			id = generateUid(url),
			title = title,
			altTitles = emptySet(),
			url = url,
			publicUrl = url,
			rating = RATING_UNKNOWN,
			contentRating = ContentRating.ADULT,
			coverUrl = cover,
			tags = emptySet(),
			state = null,
			authors = emptySet(),
			largeCoverUrl = cover,
			description = duration,
			source = source,
		)
	}

	private fun durationDescription(text: String): String? {
		val match = DURATION_REGEX.find(text) ?: return null
		val hours = match.groupValues[1].toLongOrNull() ?: 0L
		val minutes = match.groupValues[2].toLongOrNull() ?: return null
		val seconds = match.groupValues[3].toLongOrNull() ?: return null
		return formatDuration(hours * 3_600L + minutes * 60L + seconds)
	}

	private fun formatDuration(totalSeconds: Long): String {
		val hours = totalSeconds / 3_600L
		val minutes = totalSeconds % 3_600L / 60L
		val seconds = totalSeconds % 60L
		val value = if (hours > 0L) {
			String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
		} else {
			String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
		}
		return "Duration: $value"
	}

	private fun previewUrl(postUrl: String): String? {
		val id = POST_ID_REGEX.find(postUrl)?.groupValues?.get(1)?.toLongOrNull() ?: return null
		val group = id / SCREENSHOT_GROUP_SIZE * SCREENSHOT_GROUP_SIZE
		return "https://$domain/contents/videos_screenshots/$group/$id/preview_preview.mp4.jpg"
	}

	private fun parseMedia(document: Document): PostMedia {
		val variants = document.select("a[href*=/get_file/]").mapNotNull { anchor ->
			val url = anchor.directUrl() ?: return@mapNotNull null
			val label = anchor.text().trim().nullIfEmpty() ?: return@mapNotNull null
			val key = QUALITY_REGEX.find(label)?.groupValues?.get(1)?.lowercase(Locale.ROOT) ?: return@mapNotNull null
			VideoVariant(key, label, url)
		}.distinctBy { it.key }.sortedByDescending { variant ->
			variant.key.substringBefore('p').toIntOrNull() ?: 0
		}
		if (variants.isEmpty()) throw ParseException("No direct MP4 download URL", document.location())
		return PostMedia(
			variants = variants,
			previewUrl = document.selectFirst("img[src*=/contents/videos_screenshots/]")?.directUrl(),
		)
	}

	private fun Element.directUrl(): String? {
		val raw = attr("src").nullIfEmpty() ?: attr("href").nullIfEmpty() ?: return null
		return raw.toAbsoluteUrl(domain).takeUnless { it.startsWith("data:") }
	}

	private fun postId(url: String): Long = requireNotNull(POST_ID_REGEX.find(url)?.groupValues?.get(1)?.toLongOrNull()) {
		"Cannot find video id in $url"
	}

	private data class PostMedia(val variants: List<VideoVariant>, val previewUrl: String?)

	private data class VideoVariant(val key: String, val label: String, val url: String)

	private companion object {
		const val PAGE_SIZE = 24
		const val SCREENSHOT_GROUP_SIZE = 1_000L
		val VIDEO_PATH_REGEX = Regex("/video/\\d+/")
		val POST_ID_REGEX = Regex("/video/(\\d+)/")
		val QUALITY_REGEX = Regex("(\\d+p)", RegexOption.IGNORE_CASE)
		val DURATION_REGEX = Regex("(?<!\\d)(?:(\\d+):)?(\\d{1,2}):(\\d{2})(?!\\d)")
	}
}
