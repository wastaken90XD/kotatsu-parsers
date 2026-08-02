package org.koitharu.kotatsu.parsers.site.booru.philomena

import okhttp3.HttpUrl
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
import org.koitharu.kotatsu.parsers.site.booru.danbooru.toLegacyTimezoneOffset
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getIntOrDefault
import org.koitharu.kotatsu.parsers.util.json.getLongOrDefault
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSONNotNull
import org.koitharu.kotatsu.parsers.util.json.mapJSONNotNullToSet
import org.koitharu.kotatsu.parsers.util.json.toJSONArrayOrNull
import java.text.SimpleDateFormat
import java.util.*

/**
 * Base parser for Philomena-based boorus (Derpibooru, Ponybooru, Furbooru, Twibooru).
 *
 * API v1: `/api/v1/json/search/images?q=...&page=N&per_page=N` returning `{total, images:[...]}`.
 * Single post: `/api/v1/json/images/{id}` returning `{"image":{...}}`.
 * Tags list: tags are returned inline on each image; we fetch them from the top-tags listing.
 *
 * Twibooru uses v3: `/api/v3/search/posts` returning `{posts:[...]}` and `/api/v3/posts/{id}`
 * returning `{"post":{...}` — it is supported via the override hooks below.
 */
internal abstract class PhilomenaParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 30,
) : BooruParser(context, source, pageSize), MangaParserAuthProvider {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override val authCookieNames: Array<String> = arrayOf("_session_id", "remember_web")

	override val authUrl: String
		get() = "https://$domain/users/sign_in"

	/** API version: 1 for Derpibooru/Ponybooru/Furbooru, 3 for Twibooru. */
	protected open val apiVersion: Int = 1

	/** Name of the search resource, e.g. "images" or "posts". */
	protected open val searchResource: String = "images"

	/** Name of the array key holding results in a search response. */
	protected open val postsArrayKey: String = "images"

	/** Name of the object key holding a single post. */
	protected open val postObjectKey: String = "image"

	/**
	 * Path segment used to fetch a single post by id. Defaults to [searchResource] with the
	 * trailing 's' stripped (images -> image), which is correct for Philomena v1. Twibooru v3
	 * uses the plural form ("posts") so it overrides this.
	 */
	protected open val singlePostResource: String
		get() = searchResource.removeSuffix("s")

	/** URL path that displays a single post to a user, e.g. "/images/" or "/". */
	protected open val postViewPath: String = "/images/"

	/** Filter id parameter (Derpibooru family uses this to hide explicit posts unless configured). */
	protected open val defaultFilterId: Int? = null

	/** CDN domain if images are hosted off a different host (e.g. "cdn.ponybooru.org", "furrycdn.org"). */
	protected open val cdnDomain: String? = null

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

	override val supportedRatings: Set<ContentRating> =
		EnumSet.of(ContentRating.SAFE, ContentRating.SUGGESTIVE, ContentRating.ADULT)

	override suspend fun isAuthorized(): Boolean = hasAuthCookies()

	override suspend fun getUsername(): String {
		val url = baseApiUrl().addPathSegment("profiles").addPathSegment("user").build().toString()
		val me = webClient.httpGet(url).parseJson()
		return me.optJSONObject("user")?.getStringOrNull("name")
			?: me.optJSONObject("users")?.getStringOrNull("name")
			?: runCatching { me.getStringOrNull("name") }.getOrNull()
			?: throw AuthRequiredException(source)
	}

	override fun postUrl(id: Long): String = "$postViewPath$id"

	/**
	 * Philomena ratings are expressed as tags (`safe`, `suggestive`, `questionable`, `explicit`).
	 * We combine them with the rest of the query string using comma (AND) semantics.
	 */
	override fun ratingToken(rating: ContentRating): String? = when (rating) {
		ContentRating.SAFE -> "safe"
		ContentRating.SUGGESTIVE -> "suggestive"
		ContentRating.ADULT -> "explicit"
	}

	override suspend fun fetchPosts(page: Int, tags: String): List<BooruPost> {
		val q = tags.ifEmpty { "*" }
		val url = baseApiUrl()
			.addPathSegment("search")
			.addPathSegment(searchResource)
			.addQueryParameter("q", q)
			.addQueryParameter("page", page.toString())
			.addQueryParameter("per_page", pageSize.toString())
			.apply {
				defaultFilterId?.let { addQueryParameter("filter_id", it.toString()) }
			}.build()
		val raw = webClient.httpGet(url).parseRaw()
		throwOnApiError(raw, url.toString())
		val jo = raw.toJSONObjectOrNull() ?: throw ParseException("Cannot parse posts response", url.toString())
		val array = jo.optJSONArray(postsArrayKey)
			?: throw ParseException("Missing '$postsArrayKey' array in response", url.toString())
		return array.mapJSONNotNull { postJo ->
			val id = postJo.getLongOrDefault("id", 0L).takeIf { it > 0L } ?: return@mapJSONNotNull null
			postJo.toBooruPost(id)
		}
	}

	override suspend fun fetchPost(id: Long): BooruPost {
		val url = baseApiUrl()
			.addPathSegment(singlePostResource)
			.addPathSegment(id.toString())
			.build()
		val raw = webClient.httpGet(url).parseRaw()
		throwOnApiError(raw, url.toString())
		val jo = raw.toJSONObjectOrNull() ?: throw ParseException("Cannot parse post response", url.toString())
		val postJo = jo.optJSONObject(postObjectKey)
			?: throw ParseException("Missing '$postObjectKey' object in response", url.toString())
		return postJo.toBooruPost(id)
	}

	override suspend fun fetchTags(): Set<MangaTag> {
		// No dedicated "top tags" endpoint is stable across the Philomena family; instead we ask
		// for one page of wildcard results and harvest their tags.  The list is only used to
		// populate the autocomplete / filter sheet, so a best-effort best-of set is enough.
		val posts = runCatchingCancellable { fetchPosts(1, "*") }.getOrDefault(emptyList())
		val names = HashSet<String>()
		for (p in posts) {
			p.tags?.split(' ')?.forEach { raw ->
				val cleaned = raw.trim().nullIfEmpty() ?: return@forEach
				names += cleaned
			}
		}
		// Additionally hit the /tags endpoint if available.
		val tagUrl = baseApiUrl()
			.addPathSegment("tags")
			.addQueryParameter("q", "*")
			.addQueryParameter("per_page", tagsLimit.toString())
			.build()
		runCatchingCancellable {
			val raw = webClient.httpGet(tagUrl).parseRaw()
			val arr = raw.toJSONArrayOrNull()
				?: raw.toJSONObjectOrNull()?.optJSONArray("tags")
				?: return@runCatchingCancellable
			arr.mapJSONNotNullToSet { it.getStringOrNull("name") }
				.forEach { names += it.replace(' ', '_') }
		}
		return names.mapNotNullToSet { key ->
			if (key.lowercase(Locale.ROOT) in RATING_TAGS) null else tagOf(key)
		}
	}

	protected open fun baseApiUrl(): HttpUrl.Builder {
		val builder = HttpUrl.Builder()
			.scheme("https")
			.host(domain)
			.addPathSegment("api")
			.addPathSegment("v$apiVersion")
		if (apiVersion == 1) {
			builder.addPathSegment("json")
		}
		return builder
	}

	protected open fun JSONObject.toBooruPost(id: Long): BooruPost {
		val fileUrl = resolveUrl(getStringOrNull("view_url") ?: getStringOrNull("image") ?: getStringOrNull("file_url"))
		val reps = optJSONObject("representations")
		val previewUrl = resolveUrl(
			reps?.getStringOrNull("thumb")
				?: reps?.getStringOrNull("thumb_small")
				?: reps?.getStringOrNull("thumb_tiny"),
		)
		val sampleUrl = resolveUrl(
			reps?.getStringOrNull("large")
				?: reps?.getStringOrNull("medium")
				?: reps?.getStringOrNull("full")
				?: fileUrl,
		)
		val tagsArray = opt("tags")
		val tagNames: List<String> = when (tagsArray) {
			is JSONArray -> (0 until tagsArray.length()).mapNotNull { i ->
				val item = tagsArray.opt(i)
				when (item) {
					is String -> item.trim()
					is JSONObject -> item.getStringOrNull("name")?.trim()
					else -> null
				}
			}.filter { it.isNotEmpty() }
			is String -> tagsArray.split(',').map { it.trim() }.filter { it.isNotEmpty() }
			else -> {
				val tl = getStringOrNull("tag_list") ?: getStringOrNull("tags")
				tl?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
			}
		}
		// BooruParser expects a space-separated list of underscore-joined tokens, so
		// normalise tags with spaces into underscore form before storing them.
		val tagString = tagNames.joinToString(" ") { it.replace(' ', '_') }
		val rating = ratingFromNames(tagNames)
		val createdAt = dateFormat.parseSafe(getStringOrNull("created_at")?.toLegacyTimezoneOffset())
		val uploader = optJSONObject("uploader")?.getStringOrNull("name")
			?: getStringOrNull("uploader")
			?: getStringOrNull("uploader_name")
		return BooruPost(
			id = id,
			fileUrl = fileUrl,
			previewUrl = previewUrl,
			sampleUrl = sampleUrl,
			tags = tagString,
			rating = rating,
			sourceUrl = getStringOrNull("source_url") ?: getStringOrNull("source"),
			author = uploader,
			createdAt = createdAt,
			score = getIntOrDefault("score", 0),
			width = getIntOrDefault("width", 0),
			height = getIntOrDefault("height", 0),
		)
	}

	private fun resolveUrl(url: String?): String? {
		if (url == null) return null
		if (url.startsWith("http")) return url
		val cdn = cdnDomain ?: domain
		return "https://$cdn${if (url.startsWith('/')) url else "/$url"}"
	}

	private fun ratingFromNames(tagNames: List<String>): String? {
		val tags = tagNames.asSequence().map { it.trim().lowercase(Locale.ROOT) }.toSet()
		return when {
			"explicit" in tags -> "explicit"
			"questionable" in tags -> "questionable"
			"suggestive" in tags -> "suggestive"
			"safe" in tags -> "safe"
			else -> null
		}
	}

	protected fun throwOnApiError(raw: String, url: String) {
		val trimmed = raw.trim()
		if (trimmed.isEmpty()) throw ParseException("Empty response", url)
		if (!trimmed.startsWith('[') && !trimmed.startsWith('{')) {
			val message = trimmed.removeSurrounding("\"").trim()
			if (message.isNotEmpty() && message.length < 1000) {
				throw when {
					message.contains("unauthorized", ignoreCase = true) ||
						message.contains("authentication", ignoreCase = true) ||
						message.contains("must be logged in", ignoreCase = true) ||
						message.contains("signed in", ignoreCase = true) -> AuthRequiredException(source)
					else -> ParseException(message, url)
				}
			}
		}
		if (trimmed.startsWith('{')) {
			val jo = runCatching { JSONObject(trimmed) }.getOrNull() ?: return
			val msg = jo.optString("error").nullIfEmpty()
				?: jo.optString("message").nullIfEmpty()
				?: jo.optString("reason").nullIfEmpty()
			if (msg != null) {
				throw when {
					msg.contains("unauthorized", ignoreCase = true) ||
						msg.contains("authentication", ignoreCase = true) ||
						msg.contains("must be logged in", ignoreCase = true) ||
						msg.contains("signed in", ignoreCase = true) -> AuthRequiredException(source)
					else -> ParseException(msg, url)
				}
			}
		}
	}

	companion object {
		/**
		 * Tags that represent content ratings, not descriptive tags; they must not appear in the
		 * filter tag list so users pick rating via the standard [ContentRating] control instead.
		 */
		private val RATING_TAGS = setOf("safe", "suggestive", "questionable", "explicit", "grimdark", "semi-grimdark")

		/** ISO-8601 date format used by Philomena, e.g. "2026-08-02T00:03:03Z". */
		private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ROOT)
	}
}
