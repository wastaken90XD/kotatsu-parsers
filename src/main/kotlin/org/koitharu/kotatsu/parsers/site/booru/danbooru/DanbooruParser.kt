package org.koitharu.kotatsu.parsers.site.booru.danbooru

import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.exception.ParseException
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
import org.koitharu.kotatsu.parsers.util.json.toJSONArrayOrNull
import org.koitharu.kotatsu.parsers.util.json.toJSONObjectOrNull
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
	 *
	 * Do not use the `X` pattern here: Android 5's [SimpleDateFormat] does not support it.
	 * `Z` accepts the equivalent offset once its colon is removed.
	 */
	protected val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ROOT)

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
		val raw = webClient.httpGet(url).parseRaw()
		throwOnApiError(raw, url.toString())
		return parsePostsResponse(raw, url.toString()).mapJSONNotNull { jo ->
			// Posts whose file is withheld (gold-only, deleted) carry no usable id.
			val id = jo.getLongOrDefault("id", 0L).takeIf { it > 0L } ?: return@mapJSONNotNull null
			val post = jo.toBooruPost(id)
			if (post.previewUrl == null && post.sampleUrl == null && post.fileUrl == null) {
				return@mapJSONNotNull null
			}
			post
		}
	}

	override suspend fun fetchPost(id: Long): BooruPost {
		val url = "https://$domain/posts/$id.json"
		val raw = webClient.httpGet(url).parseRaw()
		throwOnApiError(raw, url)
		val jo = unwrapPost(raw.toJSONObjectOrNull() ?: throw ParseException("Cannot parse post response", url))
		return jo.toBooruPost(id)
	}

	override suspend fun fetchTags(): Set<MangaTag> {
		val url = urlBuilder()
			.addPathSegment("tags.json")
			.addQueryParameter("search[order]", "count")
			.addQueryParameter("search[hide_empty]", "yes")
			.addQueryParameter("limit", tagsLimit.toString())
			.build()
		val raw = webClient.httpGet(url).parseRaw()
		throwOnApiError(raw, url.toString())
		val array = raw.toJSONArrayOrNull()
			?: raw.toJSONObjectOrNull()?.optJSONArray("data")
			?: throw ParseException("Cannot parse tags response", url.toString())
		return array.mapJSONNotNullToSet { jo ->
			jo.getStringOrNull("name")?.let { tagOf(it) }
		}
	}

	/**
	 * Reads the search response. Vanilla Danbooru replies with a bare array, so the raw body is
	 * parsed here to let forks that wrap it in an object override just this step.
	 */
	protected open fun parsePostsResponse(body: String, url: String): JSONArray {
		val json = body.toJSONObjectOrNull()
		if (json != null) {
			val posts = json.optJSONArray("posts") ?: json.optJSONArray("post") ?: json.optJSONArray("data")
			if (posts != null) {
				return posts
			}
		}
		return body.toJSONArrayOrNull() ?: throw ParseException("Cannot parse posts response", url)
	}

	/**
	 * Unwraps a single post payload. Vanilla Danbooru returns the post object directly.
	 */
	protected open fun unwrapPost(json: JSONObject): JSONObject =
		json.optJSONObject("post") ?: json.optJSONObject("posts") ?: json

	/**
	 * Converts one post object. Split out so that forks with a different field layout can
	 * override the mapping without touching the request code.
	 */
	protected open fun JSONObject.toBooruPost(id: Long): BooruPost {
		val fileUrl = getStringOrNull("file_url") ?: getStringOrNull("url")
		val previewUrl = getStringOrNull("preview_file_url") ?: getStringOrNull("preview_url")
		val sampleUrl = getStringOrNull("large_file_url") ?: getStringOrNull("sample_url") ?: fileUrl
		val createdTimestamp = getLongOrDefault("created_at", 0L).takeIf { it > 0L }
			?.let { if (it < 100000000000L) it * 1000L else it }
			?: dateFormat.parseSafe(getStringOrNull("created_at")?.toLegacyTimezoneOffset())
		return BooruPost(
			id = id,
			fileUrl = fileUrl,
			previewUrl = previewUrl,
			sampleUrl = sampleUrl,
			tags = getStringOrNull("tag_string") ?: getStringOrNull("tags"),
			rating = getStringOrNull("rating"),
			sourceUrl = getStringOrNull("source"),
			author = getStringOrNull("tag_string_artist")?.substringBefore(' ')
				?: getStringOrNull("author")
				?: getStringOrNull("uploader_name"),
			createdAt = createdTimestamp,
			score = optJSONObject("score")?.getIntOrDefault("total", 0) ?: getIntOrDefault("score", 0),
			width = getIntOrDefault("image_width", 0).takeIf { it > 0 } ?: getIntOrDefault("width", 0),
			height = getIntOrDefault("image_height", 0).takeIf { it > 0 } ?: getIntOrDefault("height", 0),
		)
	}

	/**
	 * Detects Danbooru-style API error payloads that HTTP 200 but describe an error rather
	 * than the requested resource. Without this check those bodies get fed straight into the
	 * JSON parser and either throw an unhelpful syntax error or — worse — parse as an array
	 * with zero posts, returning a silent empty list.
	 *
	 * Recognised shapes:
	 *  * JSON object with `"success": false` plus a `message` / `reason` field.
	 *  * JSON string literal (some error endpoints return a quoted message).
	 *  * Plain text (e.g. a Cloudflare interstitial that slips through with HTTP 200).
	 */
	protected fun throwOnApiError(raw: String, url: String) {
		val trimmed = raw.trim()
		if (trimmed.isEmpty()) {
			throw ParseException("Empty response", url)
		}
		if (!trimmed.startsWith('[') && !trimmed.startsWith('{')) {
			val message = trimmed.removeSurrounding("\"").trim()
			if (message.isNotEmpty() && message.length < 1000) {
				throw when {
					message.contains("authentication", ignoreCase = true) ||
						message.contains("logged in", ignoreCase = true) ||
						message.contains("login required", ignoreCase = true) ||
						message.contains("api key", ignoreCase = true) ->
						AuthRequiredException(source)
					else -> ParseException(message, url)
				}
			}
		}
		if (trimmed.startsWith('{')) {
			val jo = runCatching { JSONObject(trimmed) }.getOrNull() ?: return
			val success = jo.opt("success")
			if (success is Boolean && !success) {
				val msg = jo.optString("message").nullIfEmpty()
					?: jo.optString("reason").nullIfEmpty()
					?: jo.optString("error").nullIfEmpty()
					?: "API error"
				throw when {
					msg.contains("authentication", ignoreCase = true) ||
						msg.contains("logged in", ignoreCase = true) ||
						msg.contains("login", ignoreCase = true) ->
						AuthRequiredException(source)
					else -> ParseException(msg, url)
				}
			}
		}
	}
}

/** Converts ISO-8601 timezone forms unsupported by Android 5's SimpleDateFormat `Z` parser. */
internal fun String.toLegacyTimezoneOffset(): String = when {
	endsWith('Z') -> dropLast(1) + "+0000"
	length >= 6 && this[length - 3] == ':' && (this[length - 6] == '+' || this[length - 6] == '-') ->
		removeRange(length - 3, length - 2)
	else -> this
}
