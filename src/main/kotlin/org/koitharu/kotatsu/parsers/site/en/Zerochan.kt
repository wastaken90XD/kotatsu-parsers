package org.koitharu.kotatsu.parsers.site.en

import okhttp3.Headers
import okhttp3.HttpUrl
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getLongOrDefault
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.toJSONArrayOrNull
import org.koitharu.kotatsu.parsers.util.json.toJSONObjectOrNull
import java.util.*

/**
 * Parser for [Zerochan](https://www.zerochan.net), an anime image board.
 *
 * Zerochan exposes an informal JSON API: append `?json` to any tag page (or `/` for the front page),
 * add `p=<n>` for page and `l=<n>` for items per page.  The response is a bare JSON array of image
 * items; each item carries `id`, `primary` (full URL), `thumbnail` (thumb), `width`, `height`,
 * `tags` (comma separated).
 *
 * The site blocks requests that do not send a browser User-Agent and a Referer, so we set both in
 * [getRequestHeaders].
 */
@MangaSourceParser("ZEROCHAN", "Zerochan", "en", ContentType.BOORU)
internal class Zerochan(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.ZEROCHAN, 24), MangaParserAuthProvider {

	override val configKeyDomain = ConfigKey.Domain("www.zerochan.net")

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override fun getRequestHeaders(): Headers = Headers.Builder()
		.add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
		.add("Referer", "https://$domain/")
		.add("Accept", "application/json, text/javascript, */*; q=0.01")
		.add("Accept-Language", "en-US,en;q=0.9")
		.add("X-Requested-With", "XMLHttpRequest")
		.build()

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.NEWEST)

	override val filterCapabilities: MangaListFilterCapabilities = MangaListFilterCapabilities(
		isMultipleTagsSupported = false,
		isSearchSupported = true,
		isSearchWithFiltersSupported = true,
		isTagsExclusionSupported = false,
	)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableContentRating = EnumSet.of(ContentRating.SAFE, ContentRating.SUGGESTIVE, ContentRating.ADULT),
	)

	override val authUrl: String get() = "https://$domain/"
	override suspend fun isAuthorized(): Boolean =
		context.cookieJar.getCookies(domain).any { it.name == "z_remember" || it.name == "session_hash" }

	override suspend fun getUsername(): String = "Zerochan"

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val bld = urlBuilder()
		// Zerochan exposes two modes: a tag page at /<SingleTag>?json and search via /?q=...&json.
		// Comma/space-separated multi-term queries are not supported (see isMultipleTagsSupported),
		// but we still send free-text queries and tag values consistently through ?q= when more
		// than one term is requested, to avoid producing broken /Tag1,Tag2 URLs.
		val tag = filter.tags.firstOrNull()?.key
		val query = filter.query?.trim()?.nullIfEmpty()
		val ratingToken = ratingToken(filter.contentRating.oneOrThrowIfMany())
		val terms = ArrayList<String>()
		if (query != null) query.splitByWhitespace().forEach { terms += it }
		if (tag != null) terms += tag.replace('_', ' ')
		if (ratingToken != null) terms += ratingToken
		val singleTag = terms.singleOrNull()
		if (singleTag != null) {
			bld.addPathSegment(singleTag.replace(' ', '+'))
		} else if (terms.isNotEmpty()) {
			bld.addQueryParameter("q", terms.joinToString("+"))
		}
		bld.addQueryParameter("json", "")
		bld.addQueryParameter("p", page.toString())
		bld.addQueryParameter("l", pageSize.toString())
		val url = bld.build().toString()
		val raw = webClient.httpGet(url).parseRaw()
		val crawlerBlock = raw.contains("Crawlers are not permitted", ignoreCase = true)
		if (crawlerBlock) {
			throw ParseException("Blocked by Zerochan: set a browser User-Agent", url)
		}
		val array = raw.toJSONArrayOrNull()
			?: throw ParseException("Cannot parse response (missing JSON array)", url)
		return array.mapJSON { jo ->
			val id = jo.getLongOrDefault("id", 0L)
			val fileUrl = jo.getStringOrNull("primary")?.toAbsolute()
			val thumb = jo.getStringOrNull("thumbnail")?.toAbsolute()
			val tagString = jo.getStringOrNull("tags").orEmpty()
			val relUrl = "/$id"
			val tagSet = tagString.split(',').mapNotNullToSet { rawTag ->
				val key = rawTag.trim().replace(' ', '_').nullIfEmpty() ?: return@mapNotNullToSet null
				MangaTag(title = rawTag.trim().toTitleCase(sourceLocale), key = key, source = source)
			}
			Manga(
				id = generateUid(relUrl),
				title = tagString.substringBefore(',').ifEmpty { "#$id" } + " (#$id)",
				altTitles = emptySet(),
				url = relUrl,
				publicUrl = relUrl.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				contentRating = guessRating(tagString),
				coverUrl = thumb ?: fileUrl,
				largeCoverUrl = fileUrl,
				tags = tagSet,
				state = null,
				authors = emptySet(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val id = manga.url.substringAfterLast('/').toLongOrNull()
			?: throw ParseException("Cannot parse post id from url", manga.url)
		val url = urlBuilder().addPathSegment(id.toString()).addQueryParameter("json", "").build().toString()
		val raw = webClient.httpGet(url).parseRaw()
		val jo = raw.toJSONObjectOrNull() ?: throw ParseException("Cannot parse post response", url)
		val full = jo.getStringOrNull("full") ?: jo.getStringOrNull("primary") ?: jo.getStringOrNull("image")
		val thumb = jo.getStringOrNull("thumbnail")
		val tagString = jo.getStringOrNull("tags").orEmpty()
		val tagSet = tagString.split(',').mapNotNullToSet { rawTag ->
			val key = rawTag.trim().replace(' ', '_').nullIfEmpty() ?: return@mapNotNullToSet null
			MangaTag(title = rawTag.trim().toTitleCase(sourceLocale), key = key, source = source)
		}
		return manga.copy(
			title = tagString.substringBefore(',').ifEmpty { "#$id" } + " (#$id)",
			coverUrl = thumb?.toAbsolute() ?: full?.toAbsolute() ?: manga.coverUrl,
			largeCoverUrl = full?.toAbsolute() ?: manga.largeCoverUrl,
			tags = tagSet,
			contentRating = guessRating(tagString),
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
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val id = chapter.url.substringAfterLast('/').toLongOrNull()
			?: throw ParseException("Cannot parse post id", chapter.url)
		val url = urlBuilder().addPathSegment(id.toString()).addQueryParameter("json", "").build().toString()
		val raw = webClient.httpGet(url).parseRaw()
		val jo = raw.toJSONObjectOrNull() ?: throw ParseException("Cannot parse post response", url)
		val full = jo.getStringOrNull("full") ?: jo.getStringOrNull("primary") ?: jo.getStringOrNull("image")
		?: throw ParseException("Full image URL missing", url)
		return listOf(
			MangaPage(
				id = generateUid(full),
				url = full.toAbsolute(),
				preview = jo.getStringOrNull("thumbnail")?.toAbsolute(),
				source = source,
			),
		)
	}

	private fun ratingToken(rating: ContentRating?): String? = when (rating) {
		ContentRating.SAFE -> "safe"
		ContentRating.SUGGESTIVE -> "suggestive"
		ContentRating.ADULT -> "nsfw"
		else -> null
	}

	private fun guessRating(tags: String): ContentRating {
		val lower = tags.lowercase(Locale.ROOT)
		return when {
			"nsfw" in lower || "explicit" in lower || "ecchi" in lower && !("safe" in lower) -> ContentRating.ADULT
			"suggestive" in lower || "ecchi" in lower -> ContentRating.SUGGESTIVE
			else -> ContentRating.SAFE
		}
	}

	private fun String.toAbsolute(): String = when {
		startsWith("//") -> "https:$this"
		startsWith("http") -> this
		else -> "https://$domain/${this.removePrefix("/")}"
	}

	private fun urlBuilder(): HttpUrl.Builder = HttpUrl.Builder().scheme("https").host(domain)
}
