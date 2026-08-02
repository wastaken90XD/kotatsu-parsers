package org.koitharu.kotatsu.parsers.site.booru.gelbooru

import okhttp3.HttpUrl
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
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
import org.koitharu.kotatsu.parsers.util.json.toJSONArrayOrNull
import org.koitharu.kotatsu.parsers.util.json.toJSONObjectOrNull
import java.text.SimpleDateFormat
import java.util.*

/**
 * Base parser for Gelbooru and its forks (rule34, safebooru, ...).
 *
 * API: XML.
 *  - search: `/index.php?page=dapi&s=post&q=index&tags=&pid=&limit=`
 *  - post:   `/index.php?page=dapi&s=post&q=index&id=`
 *  - tags:   `/index.php?page=dapi&s=tag&q=index&orderby=count&limit=`
 *
 * Ratings use the long form (`rating:safe`).
 *
 * Deviations from the brief, both dictated by the actual API:
 *
 *  1. `pid` is a zero based *page index* for `page=dapi`, not an item offset; the offset meaning
 *     only applies to the HTML pages. The paginator is therefore started at 0 and `pid` receives
 *     the page number unchanged.
 *  2. Two incompatible response shapes exist in the wild. Gelbooru 0.2.5 (gelbooru.com) puts every
 *     field in a child element, while Gelbooru 0.2 forks put them in attributes of `<post>`.
 *     [value] reads either, so one base class covers both without per-site code.
 */
internal abstract class GelbooruParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 20,
	protected val apiDomain: String = domain,
	protected val useJson: Boolean = false,
	protected val apiKeyRequired: Boolean = false,
) : BooruParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	private val apiKeyConfig = ConfigKey.StringConfig("api_key")
	private val userIdConfig = ConfigKey.StringConfig("user_id")

	/**
	 * Gelbooru dates look like `Tue Aug 26 12:00:00 -0500 2025`, so the day and month names must
	 * be read with an English locale regardless of the device language.
	 */
	private val dateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH)
	private val fallbackDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)

	init {
		// The API numbers its pages from zero.
		setFirstPage(0)
	}

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		if (apiKeyRequired) {
			keys.add(apiKeyConfig)
			keys.add(userIdConfig)
		}
	}

	override suspend fun fetchPosts(page: Int, tags: String): List<BooruPost> {
		val url = dapiUrlBuilder("post")
			.addQueryParameter("pid", page.toString())
			.addQueryParameter("limit", pageSize.toString())
			.apply {
				if (tags.isNotEmpty()) {
					addQueryParameter("tags", tags)
				}
				appendApiCredentials(this)
			}.build()
		val raw = webClient.httpGet(url).parseRaw()
		throwOnApiError(raw, url.toString())
		val trimmed = raw.trimStart()
		if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
			return parseJsonPosts(raw)
		}
		return Jsoup.parse(raw, url.toString(), Parser.xmlParser()).select("post").mapNotNull { el ->
			val id = el.value("id")?.toLongOrNull()?.takeIf { it > 0L } ?: return@mapNotNull null
			val post = el.toBooruPost(id)
			if (post.previewUrl == null && post.sampleUrl == null && post.fileUrl == null) {
				return@mapNotNull null
			}
			post
		}
	}

	override suspend fun fetchPost(id: Long): BooruPost {
		val url = dapiUrlBuilder("post")
			.addQueryParameter("id", id.toString())
			.apply { appendApiCredentials(this) }
			.build()
		val raw = webClient.httpGet(url).parseRaw()
		throwOnApiError(raw, url.toString())
		val trimmed = raw.trimStart()
		if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
			val array = raw.toJSONObjectOrNull()?.optJSONArray("post")
				?: raw.toJSONObjectOrNull()?.optJSONArray("posts")
				?: raw.toJSONArrayOrNull()
			val jo = array?.optJSONObject(0)
				?: raw.toJSONObjectOrNull()
				?: throw NotFoundException("Post $id not found", url.toString())
			val postId = jo.getLongOrDefault("id", id).takeIf { it > 0L } ?: id
			return jo.toBooruPost(postId)
		}
		val el = Jsoup.parse(raw, url.toString(), Parser.xmlParser()).selectFirst("post")
			?: throw NotFoundException("Post $id not found", url.toString())
		return el.toBooruPost(id)
	}

	override suspend fun fetchTags(): Set<MangaTag> {
		val url = dapiUrlBuilder("tag")
			.addQueryParameter("orderby", "count")
			.addQueryParameter("limit", tagsLimit.toString())
			.apply { appendApiCredentials(this) }
			.build()
		val raw = webClient.httpGet(url).parseRaw()
		throwOnApiError(raw, url.toString())
		return Jsoup.parse(raw, url.toString(), Parser.xmlParser()).select("tag").mapNotNullToSet { el ->
			el.value("name")?.let { tagOf(it) }
		}
	}

	override fun postUrl(id: Long): String = "/index.php?page=post&s=view&id=$id"

	override fun ratingToken(rating: ContentRating): String = when (rating) {
		ContentRating.SAFE -> "rating:safe"
		ContentRating.SUGGESTIVE -> "rating:questionable"
		ContentRating.ADULT -> "rating:explicit"
	}

	private fun dapiUrlBuilder(subject: String): HttpUrl.Builder = urlBuilder()
		.host(apiDomain)
		.addPathSegment("index.php")
		.addQueryParameter("page", "dapi")
		.addQueryParameter("s", subject)
		.addQueryParameter("q", "index")
		.apply {
			if (useJson) {
				addQueryParameter("json", "1")
			}
		}

	private fun parseJsonPosts(body: String): List<BooruPost> {
		val array = body.toJSONObjectOrNull()?.optJSONArray("post")
			?: body.toJSONObjectOrNull()?.optJSONArray("posts")
			?: body.toJSONArrayOrNull()
			?: JSONArray()
		return array.mapJSONNotNull { jo ->
			val id = jo.getLongOrDefault("id", 0L).takeIf { it > 0L } ?: return@mapJSONNotNull null
			val post = jo.toBooruPost(id)
			if (post.previewUrl == null && post.sampleUrl == null && post.fileUrl == null) {
				return@mapJSONNotNull null
			}
			post
		}
	}

	private fun Element.toBooruPost(id: Long): BooruPost {
		val fileUrl = value("file_url")
		val previewUrl = value("preview_url")
		val sampleUrl = value("sample_url")
		return BooruPost(
			id = id,
			fileUrl = fileUrl,
			previewUrl = previewUrl,
			sampleUrl = sampleUrl,
			tags = value("tags"),
			rating = value("rating"),
			sourceUrl = value("source"),
			author = value("owner"),
			createdAt = dateFormat.parseSafe(value("created_at"))
				.takeIf { it > 0L }
				?: fallbackDateFormat.parseSafe(value("created_at")),
			score = value("score")?.toIntOrNull() ?: 0,
			width = value("width")?.toIntOrNull() ?: 0,
			height = value("height")?.toIntOrNull() ?: 0,
		)
	}

	private fun JSONObject.toBooruPost(id: Long): BooruPost {
		val fileUrl = getStringOrNull("file_url") ?: getStringOrNull("url")
		val previewUrl = getStringOrNull("preview_url") ?: getStringOrNull("preview_file_url")
		val sampleUrl = getStringOrNull("sample_url") ?: getStringOrNull("large_file_url") ?: fileUrl
		val createdTimestamp = getLongOrDefault("created_at", 0L).takeIf { it > 0L }
			?.let { if (it < 100000000000L) it * 1000L else it }
			?: dateFormat.parseSafe(getStringOrNull("created_at"))
				.takeIf { it > 0L }
			?: fallbackDateFormat.parseSafe(getStringOrNull("created_at"))
		return BooruPost(
			id = id,
			fileUrl = fileUrl,
			previewUrl = previewUrl,
			sampleUrl = sampleUrl,
			tags = getStringOrNull("tags") ?: getStringOrNull("tag_string"),
			rating = getStringOrNull("rating"),
			sourceUrl = getStringOrNull("source"),
			author = getStringOrNull("owner") ?: getStringOrNull("creator_id") ?: getStringOrNull("author"),
			createdAt = createdTimestamp,
			score = getIntOrDefault("score", 0),
			width = getIntOrDefault("width", 0).takeIf { it > 0 } ?: getIntOrDefault("image_width", 0),
			height = getIntOrDefault("height", 0).takeIf { it > 0 } ?: getIntOrDefault("image_height", 0),
		)
	}

	/**
	 * Reads a field that a fork may expose either as an attribute or as a child element.
	 */
	private fun Element.value(name: String): String? =
		attrOrNull(name) ?: selectFirst(name)?.text()?.trim()?.nullIfEmpty()

	/**
	 * Appends the configured API key / user id to a request when this source requires them.
	 *
	 * Several Gelbooru forks (notably rule34.xxx since August 2025) have locked the public dapi
	 * endpoint behind per-account credentials, and return a plain text "Missing authentication"
	 * message (HTTP 200) when they are absent. We never fabricate credentials, so only attach
	 * them when the user has supplied both in settings.
	 */
	private fun appendApiCredentials(builder: HttpUrl.Builder) {
		if (!apiKeyRequired) return
		val key = config[apiKeyConfig].trim()
		val uid = config[userIdConfig].trim()
		if (key.isNotEmpty()) {
			builder.addQueryParameter("api_key", key)
		}
		if (uid.isNotEmpty()) {
			builder.addQueryParameter("user_id", uid)
		}
	}

	/**
	 * Inspects a raw dapi response and throws a descriptive exception when it carries an API
	 * level error rather than a post list. Gelbooru-family APIs return errors in three shapes
	 * that the list parser would otherwise silently accept as "zero posts":
	 *
	 *  * Plain text, e.g. `Missing authentication...` (HTTP 200, no XML/JSON marker).
	 *  * A JSON quoted string, e.g. `"Missing authentication..."` (produced when `json=1`).
	 *  * XML with a root `<error>` element or a `<response success="false">` element.
	 *
	 * When the message matches the known authentication-required phrasing we throw
	 * [AuthRequiredException] so the app can surface a login prompt; any other unexpected
	 * payload becomes a [ParseException] with the server's message attached, instead of an
	 * empty list that the user has no way to diagnose.
	 */
	private fun throwOnApiError(raw: String, url: String) {
		val trimmed = raw.trim()
		if (trimmed.isEmpty()) {
			throw ParseException("Empty response", url)
		}
		// Plain-text / quoted-string error body.
		if (!trimmed.startsWith('<') && !trimmed.startsWith('[') && !trimmed.startsWith('{')) {
			val message = trimmed.removeSurrounding("\"").trim()
			if (message.isNotEmpty()) {
				throw when {
					message.contains("authentication", ignoreCase = true) ||
						message.contains("api-key", ignoreCase = true) ||
						message.contains("user-id", ignoreCase = true) ||
						message.contains("api key", ignoreCase = true) ->
						AuthRequiredException(source)
					else -> ParseException(message, url)
				}
			}
		}
		// XML <error> body.
		if (trimmed.startsWith('<')) {
			val doc = runCatching { Jsoup.parse(trimmed, url, Parser.xmlParser()) }.getOrNull() ?: return
			val errEl = doc.selectFirst("error")
				?: doc.selectFirst("response[success=false]")
				?: doc.selectFirst("response[success=\"false\"]")
			if (errEl != null) {
				val message = errEl.text().nullIfEmpty()
					?: errEl.attr("reason").nullIfEmpty()
					?: errEl.attr("message").nullIfEmpty()
					?: "API error"
				throw when {
					message.contains("authentication", ignoreCase = true) ||
						message.contains("api-key", ignoreCase = true) ||
						message.contains("logged in", ignoreCase = true) ->
						AuthRequiredException(source)
					else -> ParseException(message, url)
				}
			}
		}
		// JSON error object ({"success":false,"message":"..."}, {"error":"..."}, or a string literal).
		if (trimmed.startsWith('{')) {
			val jo = runCatching { JSONObject(trimmed) }.getOrNull() ?: return
			val msg = jo.optString("message").nullIfEmpty()
				?: jo.optString("error").nullIfEmpty()
				?: jo.optString("reason").nullIfEmpty()
				?: return
			throw when {
				msg.contains("authentication", ignoreCase = true) ||
					msg.contains("api-key", ignoreCase = true) ||
					msg.contains("logged in", ignoreCase = true) ->
					AuthRequiredException(source)
				else -> ParseException(msg, url)
			}
		}
	}
}
