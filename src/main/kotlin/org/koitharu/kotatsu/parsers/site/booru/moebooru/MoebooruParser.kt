package org.koitharu.kotatsu.parsers.site.booru.moebooru

import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.exception.NotFoundException
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

/**
 * Base parser for Moebooru (yande.re / konachan style sites).
 *
 * API: JSON.
 *  - search: `/post/index.json?tags=&page=&limit=`
 *  - post:   `/post/index.json?tags=id:{id}`
 *  - tags:   `/tag/index.json?order=count&limit=`
 *
 * Pagination is page number based and starts at 1.
 * Ratings use the single letter form (`rating:s`).
 *
 * Deviation from the brief: the documented `/post/show/{id}.json` route is not implemented by
 * Moebooru; `/post/show/{id}` only serves HTML. A single post is therefore fetched through the
 * index route with an `id:` tag, which is the approach Moebooru's own API docs recommend.
 */
internal abstract class MoebooruParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 20,
) : BooruParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override suspend fun fetchPosts(page: Int, tags: String): List<BooruPost> {
		val url = urlBuilder()
			.addPathSegment("post")
			.addPathSegment("index.json")
			.addQueryParameter("page", page.toString())
			.addQueryParameter("limit", pageSize.toString())
			.apply {
				if (tags.isNotEmpty()) {
					addQueryParameter("tags", tags)
				}
			}.build()
		val raw = webClient.httpGet(url).parseRaw()
		throwOnApiError(raw, url.toString())
		val array = raw.toJSONArrayOrNull()
			?: raw.toJSONObjectOrNull()?.optJSONArray("posts")
			?: throw ParseException("Cannot parse posts response", url.toString())
		return array.mapJSONNotNull { jo ->
			val id = jo.getLongOrDefault("id", 0L).takeIf { it > 0L } ?: return@mapJSONNotNull null
			jo.toBooruPost(id)
		}
	}

	override suspend fun fetchPost(id: Long): BooruPost {
		val url = urlBuilder()
			.addPathSegment("post")
			.addPathSegment("index.json")
			.addQueryParameter("tags", "id:$id")
			.addQueryParameter("limit", "1")
			.build()
		val raw = webClient.httpGet(url).parseRaw()
		throwOnApiError(raw, url.toString())
		val array = raw.toJSONArrayOrNull()
			?: raw.toJSONObjectOrNull()?.optJSONArray("posts")
			?: throw ParseException("Cannot parse post response", url.toString())
		val jo = array.optJSONObject(0)
			?: throw NotFoundException("Post $id not found", url.toString())
		return jo.toBooruPost(id)
	}

	override suspend fun fetchTags(): Set<MangaTag> {
		val url = urlBuilder()
			.addPathSegment("tag")
			.addPathSegment("index.json")
			.addQueryParameter("order", "count")
			.addQueryParameter("limit", tagsLimit.toString())
			.build()
		val raw = webClient.httpGet(url).parseRaw()
		throwOnApiError(raw, url.toString())
		val array = raw.toJSONArrayOrNull()
			?: raw.toJSONObjectOrNull()?.optJSONArray("tags")
			?: raw.toJSONObjectOrNull()?.optJSONArray("data")
			?: throw ParseException("Cannot parse tags response", url.toString())
		return array.mapJSONNotNullToSet { jo ->
			jo.getStringOrNull("name")?.let { tagOf(it) }
		}
	}

	override fun postUrl(id: Long): String = "/post/show/$id"

	override fun ratingToken(rating: ContentRating): String = when (rating) {
		ContentRating.SAFE -> "rating:s"
		ContentRating.SUGGESTIVE -> "rating:q"
		ContentRating.ADULT -> "rating:e"
	}

	private fun JSONObject.toBooruPost(id: Long) = BooruPost(
		id = id,
		fileUrl = getStringOrNull("file_url"),
		previewUrl = getStringOrNull("preview_url"),
		sampleUrl = getStringOrNull("sample_url"),
		tags = getStringOrNull("tags"),
		rating = getStringOrNull("rating"),
		sourceUrl = getStringOrNull("source"),
		author = getStringOrNull("author"),
		// Moebooru reports the creation time as unix seconds, unlike the other two families.
		createdAt = getLongOrDefault("created_at", 0L) * 1000L,
		score = getIntOrDefault("score", 0),
		width = getIntOrDefault("width", 0),
		height = getIntOrDefault("height", 0),
	)

	/**
	 * Catches API error payloads that arrive with HTTP 200 but describe a failure instead of
	 * a post list. Mirrors the behaviour of [org.koitharu.kotatsu.parsers.site.booru.danbooru.DanbooruParser.throwOnApiError];
	 * see that method for the reasoning.
	 */
	private fun throwOnApiError(raw: String, url: String) {
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
						message.contains("login required", ignoreCase = true) ->
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
