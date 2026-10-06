package org.koitharu.kotatsu.parsers.site.booru.shimmie

import okhttp3.HttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.site.booru.BooruParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.EnumSet
import java.util.Locale

/**
 * HTML parser for Shimmie2 installations that use `/post/list/{tags}/{page}` listings.
 *
 * Shimmie exposes the original file URL for images in its detail markup.  Some installations
 * render the video source only at runtime, so a direct listing-derived original URL is retained
 * as a fallback for those posts.  This matches the filename layout observed on Rule34Hentai and
 * keeps the regular detail-markup source as the first choice.
 */
internal abstract class ShimmieParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = PAGE_SIZE,
) : BooruParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	// This installation labels posts "Unrated" but is an adult-only source, so it exposes a
	// single truthful app-side content category instead of fabricating safe/questionable filters.
	override val supportedRatings = EnumSet.of(ContentRating.ADULT)

	private val listMediaCache = LruCache<Long, BooruPost>(MEDIA_CACHE_SIZE)
	private val durationCache = LruCache<Long, String>(MEDIA_CACHE_SIZE)

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

	override suspend fun fetchPosts(page: Int, tags: String): List<BooruPost> {
		val document = webClient.httpGet(listUrl(page, tags)).parseHtml()
		return document.select("a[href*=/post/view/]").mapNotNull { anchor ->
			anchor.selectFirst("img[src]")?.let { image ->
				parseListPost(anchor, image)?.also { post ->
					durationDescription(image.attr("alt"))?.let { durationCache[post.id] = it }
				}
			}
		}.also { posts ->
			posts.forEach { post -> listMediaCache[post.id] = post }
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val details = super.getDetails(manga)
		val duration = durationCache[postId(manga.url)] ?: return details
		return details.copy(description = "$duration<br>${details.description.orEmpty()}")
	}

	override suspend fun fetchPost(id: Long): BooruPost {
		val document = webClient.httpGet(postUrl(id).toAbsoluteUrl(domain)).parseHtml()
		val cached = listMediaCache[id]
		val image = document.selectFirst("#main_image[src], img#main_image[src]")
		val video = document.selectFirst("video source[src], video[src], #main_image source[src]")
		val direct = (video ?: image)?.directUrl()
		val tags = document.select("table a[href*=/post/list/], #tag_list a[href*=/post/list/]")
			.mapNotNull { it.text().trim().nullIfEmpty() }
			.joinToString(" ")
			.nullIfEmpty()
		return BooruPost(
			id = id,
			fileUrl = direct ?: cached?.fileUrl,
			previewUrl = cached?.previewUrl ?: image?.directUrl(),
			sampleUrl = direct ?: cached?.sampleUrl,
			tags = tags ?: cached?.tags,
			// The site labels posts "Unrated" and has no server-side age-rating selector.
			rating = "explicit",
			width = cached?.width ?: 0,
			height = cached?.height ?: 0,
		)
	}

	override suspend fun fetchTags(): Set<MangaTag> = emptySet()

	override fun postUrl(id: Long): String = "/post/view/$id"

	override fun ratingToken(rating: ContentRating): String? = null

	private fun listUrl(page: Int, tags: String): String {
		val search = tags.trim().ifEmpty { "order=id_desc" }.replace(' ', '+')
		return HttpUrl.Builder()
			.scheme("https")
			.host(domain)
			.addPathSegment("post")
			.addPathSegment("list")
			.addPathSegment(search)
			.addPathSegment(page.toString())
			.build()
			.toString()
	}

	private fun parseListPost(anchor: Element, image: Element): BooruPost? {
		val id = POST_ID_REGEX.find(anchor.attr("href"))?.groupValues?.get(1)?.toLongOrNull() ?: return null
		val chunks = image.attr("alt").split(" // ").map { it.trim() }
		val tags = chunks.firstOrNull()?.nullIfEmpty() ?: return null
		val format = chunks.lastOrNull()?.lowercase()?.takeIf { FILE_EXTENSION_REGEX.matches(it) }
		val thumbnail = image.directUrl() ?: return null
		val hash = THUMB_HASH_REGEX.find(thumbnail)?.groupValues?.get(1)
		val original = if (hash != null && format != null) originalUrl(hash, id, tags, format) else null
		val dimensions = DIMENSIONS_REGEX.find(chunks.getOrNull(1).orEmpty())
		return BooruPost(
			id = id,
			fileUrl = original,
			previewUrl = thumbnail,
			sampleUrl = original,
			tags = tags,
			rating = "explicit",
			width = dimensions?.groupValues?.get(1)?.toIntOrNull() ?: 0,
			height = dimensions?.groupValues?.get(2)?.toIntOrNull() ?: 0,
		)
	}

	private fun durationDescription(alt: String): String? {
		val seconds = DURATION_SECONDS_REGEX.find(alt)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
		val rounded = Math.round(seconds).coerceAtLeast(0L)
		val hours = rounded / 3_600L
		val minutes = rounded % 3_600L / 60L
		val remainingSeconds = rounded % 60L
		val value = if (hours > 0L) {
			String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainingSeconds)
		} else {
			String.format(Locale.ROOT, "%d:%02d", minutes, remainingSeconds)
		}
		return "Duration: $value"
	}

	private fun originalUrl(hash: String, id: Long, tags: String, format: String): String = HttpUrl.Builder()
		.scheme("https")
		.host(domain)
		.addPathSegment("_images")
		.addPathSegment(hash)
		.addPathSegment("$id - $tags.$format")
		.build()
		.toString()

	private fun Element.directUrl(): String? {
		val raw = attr("src").nullIfEmpty() ?: attr("href").nullIfEmpty() ?: return null
		return raw.toAbsoluteUrl(domain)
	}

	private companion object {
		const val PAGE_SIZE = 48
		const val MEDIA_CACHE_SIZE = 32
		val POST_ID_REGEX = Regex("/post/view/(\\d+)")
		val THUMB_HASH_REGEX = Regex("/_thumbs/([^/]+)/")
		val FILE_EXTENSION_REGEX = Regex("[a-z0-9]{2,5}")
		val DIMENSIONS_REGEX = Regex("(\\d+)x(\\d+)")
		val DURATION_SECONDS_REGEX = Regex("(\\d+(?:\\.\\d+)?)s(?:\\s|$)")
	}
}
