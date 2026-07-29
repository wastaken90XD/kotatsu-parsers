package org.koitharu.kotatsu.parsers.site.booru.danbooru

import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getIntOrDefault
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.toJSONObjectOrNull

/**
 * Base parser for e621ng, the Danbooru fork behind e621 and e926.
 *
 * The routes are the same as Danbooru's, but the payload differs enough to need its own mapping:
 *
 *  1. The search response is an object `{"posts": [...]}` rather than a bare array, and a single
 *     post is wrapped as `{"post": {...}}`.
 *  2. File urls are nested: `file.url`, `preview.url` and `sample.url` instead of flat fields.
 *  3. Tags are an object of categories (`tags.general`, `tags.artist`, ...) holding arrays, not
 *     one space separated string.
 *  4. `score` is an object with `total`, and dimensions live inside `file`.
 *
 * e621ng also requires a descriptive User-Agent and answers with 403 otherwise, so the shared
 * user agent config key stays registered by the base class.
 */
internal abstract class E621ngParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 20,
) : DanbooruParser(context, source, domain, pageSize) {

	/**
	 * e621ng keeps the signed in state in a Rails session cookie.
	 *
	 * NOTE: not verified against the live site; a wrong name only makes the parser report
	 * "signed out", it does not break anonymous browsing.
	 */
	override val authCookieNames: Array<String> = arrayOf("_danbooru2_session")

	override val authUrl: String
		get() = "https://$domain/session/new"

	override suspend fun getUsername(): String {
		// e621ng has no /profile.json equivalent, so the username cannot be read back.
		throw AuthRequiredException(source)
	}

	override fun parsePostsResponse(body: String, url: String): JSONArray {
		val json = body.toJSONObjectOrNull() ?: throw ParseException("Cannot parse posts response", url)
		return json.optJSONArray("posts") ?: JSONArray()
	}

	override fun unwrapPost(json: JSONObject): JSONObject = json.optJSONObject("post") ?: json

	override fun JSONObject.toBooruPost(id: Long): BooruPost {
		val file = optJSONObject("file")
		val preview = optJSONObject("preview")
		val sample = optJSONObject("sample")
		return BooruPost(
			id = id,
			fileUrl = file?.getStringOrNull("url"),
			previewUrl = preview?.getStringOrNull("url"),
			sampleUrl = sample?.getStringOrNull("url") ?: file?.getStringOrNull("url"),
			tags = flattenTags(optJSONObject("tags")),
			rating = getStringOrNull("rating"),
			sourceUrl = optJSONArray("sources")?.optString(0)?.nullIfEmpty(),
			author = optJSONObject("tags")?.optJSONArray("artist")?.optString(0)?.nullIfEmpty(),
			createdAt = dateFormat.parseSafe(getStringOrNull("created_at")),
			score = optJSONObject("score")?.getIntOrDefault("total", 0) ?: 0,
			width = file?.getIntOrDefault("width", 0) ?: 0,
			height = file?.getIntOrDefault("height", 0) ?: 0,
		)
	}

	/**
	 * Collapses the per-category tag arrays into the single space separated string the shared
	 * base expects.
	 */
	private fun flattenTags(tags: JSONObject?): String? {
		if (tags == null) {
			return null
		}
		val result = StringBuilder()
		for (category in tags.keys()) {
			val array = tags.optJSONArray(category) ?: continue
			for (i in 0 until array.length()) {
				val tag = array.optString(i).nullIfEmpty() ?: continue
				if (result.isNotEmpty()) {
					result.append(' ')
				}
				result.append(tag)
			}
		}
		return result.toString().nullIfEmpty()
	}
}
