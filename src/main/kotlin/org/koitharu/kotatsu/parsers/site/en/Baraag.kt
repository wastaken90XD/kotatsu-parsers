package org.koitharu.kotatsu.parsers.site.en

import okhttp3.Headers
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.util.*

/**
 * Parser for [Baraag](https://baraag.net), a Mastodon instance focused on furry art.
 *
 * Mastodon 4.x requires authentication for most public-timeline endpoints, so an access token
 * from a registered application is required. Tokens can be obtained at
 * `https://baraag.net/settings/applications`; a read-only token is enough. Once obtained, enter it
 * in the source settings.
 *
 * We consume the Mastodon REST API directly (`/api/v1/timelines/tag/<tag>`) and map each status
 * containing one or more media attachments into a "manga" with a single chapter and one page per
 * attachment (multi-image posts become multi-page chapters).
 */
@MangaSourceParser("BARAAG", "Baraag", "en", ContentType.BOORU)
internal class Baraag(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.BARAAG, 20), MangaParserAuthProvider {

	override val configKeyDomain = ConfigKey.Domain("baraag.net")

	private val accessTokenKey = ConfigKey.StringConfig("accessToken", "")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
		keys.add(accessTokenKey)
	}

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
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

	override val authUrl: String get() = "https://$domain/settings/applications"

	override suspend fun isAuthorized(): Boolean = config[accessTokenKey].isNotEmpty()

	override suspend fun getUsername(): String = "Baraag user"

	override fun getRequestHeaders(): Headers {
		val builder = Headers.Builder()
			.add("User-Agent", "Mozilla/5.0 (compatible; Kotatsu/1.0)")
			.add("Accept", "application/json")
		val token = config[accessTokenKey]
		if (token.isNotEmpty()) builder.add("Authorization", "Bearer $token")
		return builder.build()
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		if (!isAuthorized()) throw AuthRequiredException(source)
		// Mastodon's tag timeline is cursor based, using max_id = id of the last post on the
		// previous page.  When we are asked to restart from page 1 we reset the cursor so that
		// changing the query or scrolling back to the top works correctly.
		if (page <= 1) {
			lastTag = null
			lastMaxId = null
		}
		val tag = buildTag(filter)
		if (tag != lastTag) {
			// New query -> forget any cursor from a previous search.
			lastTag = tag
			lastMaxId = null
		}
		val bld = apiUrl("timelines/tag/$tag")
			.addQueryParameter("limit", pageSize.toString())
		lastMaxId?.let { bld.addQueryParameter("max_id", it) }
		val arr = webClient.httpGet(bld.build().toString()).parseJsonArray()
		val result = ArrayList<Manga>(arr.length())
		var pageLastId: String? = null
		for (i in 0 until arr.length()) {
			val status = arr.getJSONObject(i)
			val m = parseStatus(status) ?: continue
			result += m
			pageLastId = status.getStringOrNull("id")
		}
		lastMaxId = pageLastId
		return result
	}

	private var lastMaxId: String? = null
	private var lastTag: String? = null

	override suspend fun getDetails(manga: Manga): Manga {
		if (!isAuthorized()) throw AuthRequiredException(source)
		val id = manga.url.substringAfterLast('/')
		val url = apiUrl("statuses/$id").build().toString()
		val jo = webClient.httpGet(url).parseJson()
		val media = jo.optJSONArray("media_attachments")
		val pages = ArrayList<MangaPage>(media?.length() ?: 0)
		if (media != null) {
			for (i in 0 until media.length()) {
				val m = media.getJSONObject(i)
				val fileUrl = m.getStringOrNull("url") ?: continue
				pages.add(
					MangaPage(
						id = generateUid(fileUrl),
						url = fileUrl,
						preview = m.optJSONObject("meta")?.optJSONObject("small")?.getStringOrNull("url")
							?: m.getStringOrNull("preview_url"),
						source = source,
					),
				)
			}
		}
		val tagSet = jo.optJSONArray("tags")?.let { tagsArr ->
			(0 until tagsArr.length()).mapNotNullToSet { idx ->
				val name = tagsArr.getJSONObject(idx).getStringOrNull("name") ?: return@mapNotNullToSet null
				MangaTag(title = name.toTitleCase(sourceLocale), key = name, source = source)
			}
		} ?: manga.tags
		return manga.copy(
			tags = tagSet,
			contentRating = if (jo.optBoolean("sensitive", false)) ContentRating.ADULT else ContentRating.SAFE,
			chapters = listOf(
				MangaChapter(
					id = generateUid(manga.url),
					title = null,
					number = 1f,
					volume = 0,
					url = manga.url,
					scanlator = jo.optJSONObject("account")?.getStringOrNull("acct"),
					uploadDate = parseCreatedAt(jo.getStringOrNull("created_at")),
					branch = null,
					source = source,
				),
			),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		if (!isAuthorized()) throw AuthRequiredException(source)
		val id = chapter.url.substringAfterLast('/')
		val url = apiUrl("statuses/$id").build().toString()
		val jo = webClient.httpGet(url).parseJson()
		val media = jo.optJSONArray("media_attachments")
			?: throw ParseException("No media attachments", url)
		return (0 until media.length()).mapNotNull { i ->
			val m = media.getJSONObject(i)
			val fileUrl = m.getStringOrNull("url") ?: return@mapNotNull null
			MangaPage(
				id = generateUid(fileUrl),
				url = fileUrl,
				preview = m.optJSONObject("meta")?.optJSONObject("small")?.getStringOrNull("url")
					?: m.getStringOrNull("preview_url"),
				source = source,
			)
		}
	}

	private fun parseStatus(status: JSONObject): Manga? {
		val id = status.getStringOrNull("id") ?: return null
		val media = status.optJSONArray("media_attachments") ?: return null
		if (media.length() == 0) return null
		val account = status.optJSONObject("account")
		val first = media.getJSONObject(0)
		val content = status.optString("content", "")
		val tags = status.optJSONArray("tags")
		val tagSet = if (tags != null) {
			(0 until tags.length()).mapNotNullToSet { i ->
				val name = tags.getJSONObject(i).getStringOrNull("name") ?: return@mapNotNullToSet null
				MangaTag(title = name.toTitleCase(sourceLocale), key = name, source = source)
			}
		} else {
			emptySet()
		}
		val url = "/@${account?.optString("username", "user")}/$id"
		val thumb = first.optJSONObject("meta")?.optJSONObject("small")?.getStringOrNull("url")
			?: first.getStringOrNull("preview_url")
			?: first.getStringOrNull("url")
		val full = first.getStringOrNull("url")
		return Manga(
			id = generateUid(url),
			title = buildTitle(content, id, account?.optString("display_name")),
			altTitles = emptySet(),
			url = url,
			publicUrl = "https://$domain$url",
			rating = RATING_UNKNOWN,
			contentRating = if (status.optBoolean("sensitive", false)) ContentRating.ADULT else ContentRating.SAFE,
			coverUrl = thumb,
			largeCoverUrl = full,
			tags = tagSet,
			state = null,
			authors = setOfNotNull(account?.getStringOrNull("display_name") ?: account?.getStringOrNull("username")),
			source = source,
		)
	}

	private fun buildTitle(content: String, id: String, author: String?): String {
		val stripped = content.replace(HTML_TAG_REGEX, "").replace("&nbsp;", " ").trim()
		val excerpt = stripped.lineSequence().firstOrNull { it.isNotBlank() }?.take(60)?.trim()
		return buildString {
			if (!author.isNullOrEmpty()) {
				append(author).append(": ")
			}
			append(if (excerpt.isNullOrEmpty()) "#$id" else excerpt)
			append(" (#").append(id).append(')')
		}
	}

	companion object {
		private val HTML_TAG_REGEX = Regex("""<[^>]+>""")
	}

	private fun parseCreatedAt(raw: String?): Long {
		if (raw == null) return 0L
		return runCatching {
			val normalized = when {
				raw.endsWith('Z') -> raw.dropLast(1) + "+0000"
				raw.length >= 6 && raw[raw.length - 3] == ':' ->
					raw.removeRange(raw.length - 3, raw.length - 2)
				else -> raw
			}
			java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ROOT).parse(normalized)?.time ?: 0L
		}.getOrDefault(0L)
	}

	private fun buildTag(filter: MangaListFilter): String {
		val q = filter.query?.trim()?.nullIfEmpty() ?: filter.tags.firstOrNull()?.key ?: "art"
		return q.substringBefore(',').substringBefore(' ').lowercase(Locale.ROOT)
	}

	private fun apiUrl(path: String): HttpUrl.Builder {
		val builder = HttpUrl.Builder().scheme("https").host(domain)
			.addPathSegment("api").addPathSegment("v1")
		for (segment in path.removePrefix("/").split('/')) {
			if (segment.isNotEmpty()) builder.addPathSegment(segment)
		}
		return builder
	}
}
