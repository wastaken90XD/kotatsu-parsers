package org.koitharu.kotatsu.parsers.site.booru.gelbooru

import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.NotFoundException
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.site.booru.BooruParser
import org.koitharu.kotatsu.parsers.util.*
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
) : BooruParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	/**
	 * Gelbooru dates look like `Tue Aug 26 12:00:00 -0500 2025`, so the day and month names must
	 * be read with an English locale regardless of the device language.
	 */
	private val dateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH)

	init {
		// The API numbers its pages from zero.
		setFirstPage(0)
	}

	override suspend fun fetchPosts(page: Int, tags: String): List<BooruPost> {
		val url = dapiUrlBuilder("post")
			.addQueryParameter("pid", page.toString())
			.addQueryParameter("limit", pageSize.toString())
			.apply {
				if (tags.isNotEmpty()) {
					addQueryParameter("tags", tags)
				}
			}.build()
		return webClient.httpGet(url).parseXml().select("post").mapNotNull { el ->
			val id = el.value("id")?.toLongOrNull()?.takeIf { it > 0L } ?: return@mapNotNull null
			el.toBooruPost(id)
		}
	}

	override suspend fun fetchPost(id: Long): BooruPost {
		val url = dapiUrlBuilder("post")
			.addQueryParameter("id", id.toString())
			.build()
		val el = webClient.httpGet(url).parseXml().selectFirst("post")
			?: throw NotFoundException("Post $id not found", url.toString())
		return el.toBooruPost(id)
	}

	override suspend fun fetchTags(): Set<MangaTag> {
		val url = dapiUrlBuilder("tag")
			.addQueryParameter("orderby", "count")
			.addQueryParameter("limit", tagsLimit.toString())
			.build()
		return webClient.httpGet(url).parseXml().select("tag").mapNotNullToSet { el ->
			el.value("name")?.let { tagOf(it) }
		}
	}

	override fun postUrl(id: Long): String = "/index.php?page=post&s=view&id=$id"

	override fun ratingToken(rating: ContentRating): String = when (rating) {
		ContentRating.SAFE -> "rating:safe"
		ContentRating.SUGGESTIVE -> "rating:questionable"
		ContentRating.ADULT -> "rating:explicit"
	}

	private fun dapiUrlBuilder(subject: String) = urlBuilder()
		.addPathSegment("index.php")
		.addQueryParameter("page", "dapi")
		.addQueryParameter("s", subject)
		.addQueryParameter("q", "index")

	private fun Element.toBooruPost(id: Long) = BooruPost(
		id = id,
		fileUrl = value("file_url"),
		previewUrl = value("preview_url"),
		sampleUrl = value("sample_url"),
		tags = value("tags"),
		rating = value("rating"),
		sourceUrl = value("source"),
		author = value("owner"),
		createdAt = dateFormat.parseSafe(value("created_at")),
		score = value("score")?.toIntOrNull() ?: 0,
		width = value("width")?.toIntOrNull() ?: 0,
		height = value("height")?.toIntOrNull() ?: 0,
	)

	/**
	 * Reads a field that a fork may expose either as an attribute or as a child element.
	 */
	private fun Element.value(name: String): String? =
		attrOrNull(name) ?: selectFirst(name)?.text()?.trim()?.nullIfEmpty()

	/**
	 * Parses the body as XML. The shared [parseHtml] helper cannot be reused because the HTML
	 * tree builder would rearrange these documents.
	 */
	private fun Response.parseXml(): Document = use { response ->
		val body = response.requireBody()
		val charset = body.contentType()?.charset()?.name()
		Jsoup.parse(body.byteStream(), charset, response.request.url.toString(), Parser.xmlParser())
	}
}
