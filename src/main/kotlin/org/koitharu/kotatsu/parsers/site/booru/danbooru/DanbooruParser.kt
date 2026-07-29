package org.koitharu.kotatsu.parsers.site.booru.danbooru

import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.site.booru.BooruParser
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getIntOrDefault
import org.koitharu.kotatsu.parsers.util.json.getLongOrDefault
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSONNotNull
import org.koitharu.kotatsu.parsers.util.json.mapJSONNotNullToSet
import java.text.SimpleDateFormat
import java.util.*

/**
 * Base parser for Danbooru and its forks.
 *
 * API: JSON.
 *  - search: `/posts.json?tags=&page=&limit=`
 *  - post:   `/posts/{id}.json`
 *  - tags:   `/tags.json`
 *
 * Pagination is page number based and starts at 1.
 * Ratings use the long form (`rating:safe`), which upstream still accepts as an alias of the
 * newer `g`/`s`/`q`/`e` letters.
 */
internal abstract class DanbooruParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 20,
) : BooruParser(context, source, pageSize), MangaParserAuthProvider {

	override val configKeyDomain = ConfigKey.Domain(domain)

	/**
	 * Danbooru keeps the signed in state in a Rails session cookie.
	 *
	 * NOTE: cookie names could not be verified against a live site while this was written, so
	 * confirm them on device; an incorrect name only makes [isAuthorized] report false, it does
	 * not break anonymous browsing.
	 */
	override val authCookieNames: Array<String> = arrayOf("_danbooru_session")

	override val authUrl: String
		get() = "https://$domain/login"

	/**
	 * Danbooru timestamps are ISO-8601 with a numeric offset, for example
	 * `2025-08-26T12:00:00.000-05:00`.
	 */
	private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT)

	override suspend fun isAuthorized(): Boolean = hasAuthCookies()

	override suspend fun getUsername(): String {
		val response = webClient.httpGet("https://$domain/profile.json").parseJson()
		return response.getStringOrNull("name") ?: throw AuthRequiredException(source)
	}

	override fun postUrl(id: Long): String = "/posts/$id"

	override fun ratingToken(rating: ContentRating): String = when (rating) {
		ContentRating.SAFE -> "rating:safe"
		ContentRating.SUGGESTIVE -> "rating:questionable"
		ContentRating.ADULT -> "rating:explicit"
	}

	override suspend fun fetchPosts(page: Int, tags: String): List<BooruPost> {
		val url = urlBuilder()
			.addPathSegment("posts.json")
			.addQueryParameter("page", page.toString())
			.addQueryParameter("limit", pageSize.toString())
			.apply {
				if (tags.isNotEmpty()) {
					addQueryParameter("tags", tags)
				}
			}.build()
		return webClient.httpGet(url).parseJsonArray().mapJSONNotNull { jo ->
			// Posts whose file is withheld (gold-only, deleted) carry no id-bearing file url.
			val id = jo.getLongOrDefault("id", 0L).takeIf { it > 0L } ?: return@mapJSONNotNull null
			jo.toBooruPost(id)
		}
	}

	override suspend fun fetchPost(id: Long): BooruPost {
		val jo = webClient.httpGet("https://$domain/posts/$id.json").parseJson()
		return jo.toBooruPost(id)
	}

	override suspend fun fetchTags(): Set<MangaTag> {
		val url = urlBuilder()
			.addPathSegment("tags.json")
			.addQueryParameter("search[order]", "count")
			.addQueryParameter("search[hide_empty]", "yes")
			.addQueryParameter("limit", tagsLimit.toString())
			.build()
		return webClient.httpGet(url).parseJsonArray().mapJSONNotNullToSet { jo ->
			jo.getStringOrNull("name")?.let { tagOf(it) }
		}
	}

	private fun JSONObject.toBooruPost(id: Long) = BooruPost(
		id = id,
		// `file_url` is absent for restricted posts; the caller reports that as an auth error.
		fileUrl = getStringOrNull("file_url"),
		previewUrl = getStringOrNull("preview_file_url"),
		sampleUrl = getStringOrNull("large_file_url"),
		tags = getStringOrNull("tag_string"),
		rating = getStringOrNull("rating"),
		sourceUrl = getStringOrNull("source"),
		author = getStringOrNull("tag_string_artist")?.substringBefore(' '),
		createdAt = dateFormat.parseSafe(getStringOrNull("created_at")),
		score = getIntOrDefault("score", 0),
		width = getIntOrDefault("image_width", 0),
		height = getIntOrDefault("image_height", 0),
	)
}
