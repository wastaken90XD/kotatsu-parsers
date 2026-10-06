package org.koitharu.kotatsu.parsers.site.en

import kotlinx.coroutines.delay
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.closeQuietly
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.exception.TooManyRequestExceptions
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getLongOrDefault
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.toJSONArrayOrNull
import org.koitharu.kotatsu.parsers.util.json.toJSONObjectOrNull
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Parser for [Zerochan](https://www.zerochan.net), an anime image board.
 *
 * Zerochan exposes an informal JSON API: append `?json` to any tag page (or `/` for the front page),
 * add `p=<n>` for page and `l=<n>` for items per page.  The response is a bare JSON array of image
 * items; each item carries `id`, `primary` (full URL), `thumbnail` (thumb), `width`, `height`,
 * `tags` (comma separated).
 *
 * The API requires a custom User-Agent in the `application name - account username` form and a
 * Referer, so [getRequestHeaders] derives the header from the configured account username.
 */
@MangaSourceParser("ZEROCHAN", "Zerochan", "en", ContentType.BOORU)
internal class Zerochan(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.ZEROCHAN, PAGE_SIZE), MangaParserAuthProvider {

	override val configKeyDomain = ConfigKey.Domain("www.zerochan.net")

	private val usernameConfig = ConfigKey.StringConfig("username")

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(usernameConfig)
	}

	override fun getRequestHeaders(): Headers {
		val username = config[usernameConfig].trim()
		if (username.isEmpty()) {
			throw ParseException("Set your Zerochan username in source settings", "https://$domain/api")
		}
		return Headers.Builder()
			// Q5: Zerochan identifies API clients by application name and account username.
			.add("User-Agent", "Kotatsu - $username")
			.add("Referer", "https://$domain/")
			.add("Accept", "application/json, text/javascript, */*; q=0.01")
			.add("Accept-Language", "en-US,en;q=0.9")
			.add("X-Requested-With", "XMLHttpRequest")
			.build()
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.NEWEST)

	override val filterCapabilities: MangaListFilterCapabilities = MangaListFilterCapabilities(
		isMultipleTagsSupported = true,
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

	override suspend fun getUsername(): String = config[usernameConfig].trim().ifEmpty { "Zerochan" }

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		// Q7: Zerochan accepts no more than one hundred result pages per tag query.
		if (page !in 1..MAX_PAGE) {
			return emptyList()
		}
		val bld = urlBuilder()
		val query = filter.query?.trim()?.nullIfEmpty()
		val ratingToken = ratingToken(filter.contentRating.oneOrThrowIfMany())
		val terms = ArrayList<String>()
		if (query != null) terms.addAll(query.splitByWhitespace())
		terms.addAll(filter.tags.map { it.key.replace('_', ' ') })
		if (ratingToken != null) terms.add(ratingToken)
		// Q9: Zerochan joins several tags with commas in the path, rather than in a query field.
		if (terms.isNotEmpty()) {
			bld.addPathSegment(terms.joinToString(",").replace(' ', '+'))
		}
		bld.addQueryParameter("json", "")
		bld.addQueryParameter("p", page.toString())
		bld.addQueryParameter("l", pageSize.toString())
		val url = bld.build().toString()
		awaitRequestSlot()
		val raw = webClient.httpGet(url).parseRaw()
		val crawlerBlock = raw.contains("Crawlers are not permitted", ignoreCase = true)
		if (crawlerBlock) {
			throw ParseException("Blocked by Zerochan after sending the configured username", url)
		}
		val array = raw.toJSONArrayOrNull()
			?: throw ParseException("Cannot parse response (missing JSON array)", url)
		return array.mapJSON { jo ->
			val id = jo.getLongOrDefault("id", 0L)
			val fileUrl = jo.getStringOrNull("primary")?.toAbsolute()
			val thumb = jo.getStringOrNull("thumbnail")?.toAbsolute()?.preferredThumbnailUrl()
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
		awaitRequestSlot()
		val raw = webClient.httpGet(url).parseRaw()
		val jo = raw.toJSONObjectOrNull() ?: throw ParseException("Cannot parse post response", url)
		val full = jo.getStringOrNull("full") ?: jo.getStringOrNull("primary") ?: jo.getStringOrNull("image")
		val thumb = jo.getStringOrNull("thumbnail")?.preferredThumbnailUrl()
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
		awaitRequestSlot()
		val raw = webClient.httpGet(url).parseRaw()
		val jo = raw.toJSONObjectOrNull() ?: throw ParseException("Cannot parse post response", url)
		val full = jo.getStringOrNull("full") ?: jo.getStringOrNull("primary") ?: jo.getStringOrNull("image")
			?: throw ParseException("Full image URL missing", url)
		return listOf(
			MangaPage(
				id = generateUid(full),
				url = full.toAbsolute(),
				preview = jo.getStringOrNull("thumbnail")?.toAbsolute()?.preferredThumbnailUrl(),
				source = source,
			),
		)
	}

	/** The site offers matching JPEGs for its AVIF 240px thumbnails; JPEG remains Android API 21 compatible. */
	private fun String.preferredThumbnailUrl(): String {
		val suffixStart = indexOfFirst { it == '?' || it == '#' }
		val path = if (suffixStart < 0) this else substring(0, suffixStart)
		if (!path.endsWith(".avif", ignoreCase = true)) return this
		val suffix = if (suffixStart < 0) "" else substring(suffixStart)
		return path.dropLast(4) + ".jpg" + suffix
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

	override fun intercept(chain: Interceptor.Chain): Response {
		val response = chain.proceed(chain.request())
		if (response.request.url.host != domain || (response.code != 429 && response.code != 503)) {
			return response
		}
		// Q6: propagate server backoff information instead of retrying a throttled request.
		val retryAfter = retryAfterMillis(response.header("Retry-After"))
		response.closeQuietly()
		throw TooManyRequestExceptions(response.request.url.toString(), retryAfter)
	}

	private suspend fun awaitRequestSlot() {
		// Q6: reserve one shared slot per second, matching Zerochan's 60 requests/minute limit.
		val waitMillis = synchronized(requestLock) {
			val now = System.currentTimeMillis()
			val requestAt = maxOf(now, nextRequestAt)
			nextRequestAt = requestAt + REQUEST_INTERVAL_MILLIS
			requestAt - now
		}
		if (waitMillis > 0L) {
			delay(waitMillis)
		}
	}

	private fun retryAfterMillis(value: String?): Long {
		val header = value?.trim()?.takeIf { it.isNotEmpty() } ?: return 0L
		header.toLongOrNull()?.let { return TimeUnit.SECONDS.toMillis(it.coerceAtLeast(0L)) }
		val retryAt = synchronized(retryAfterDateFormat) {
			runCatching { retryAfterDateFormat.parse(header)?.time }.getOrNull()
		}
		return (retryAt ?: 0L).minus(System.currentTimeMillis()).coerceAtLeast(0L)
	}

	private fun String.toAbsolute(): String = when {
		startsWith("//") -> "https:$this"
		startsWith("http") -> this
		else -> "https://$domain/${this.removePrefix("/")}"
	}

	private fun urlBuilder(): HttpUrl.Builder = HttpUrl.Builder().scheme("https").host(domain)

	private companion object {

		const val PAGE_SIZE = 200
		const val MAX_PAGE = 100
		const val REQUEST_INTERVAL_MILLIS = 1_000L

		val requestLock = Any()
		var nextRequestAt = 0L
		val retryAfterDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
	}
}
