package org.koitharu.kotatsu.parsers.site.vi

import okhttp3.Headers
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.suspendlazy.getOrNull
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("DAMCONUONG", "Dâm Cô Nương", "vi", type = ContentType.HENTAI)
internal class DamCoNuong(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.DAMCONUONG, 30) {

	override val configKeyDomain = ConfigKey.Domain("damconuong.skin")

	private val availableTags = suspendLazy(initializer = ::fetchTags)

	private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

	private val relativeDateRegex = Regex("""(\d+)\s*(giây|phút|giờ|ngày|tuần|tháng|năm)\s+trước""")

	private val fallbackUrlsRegex = Regex(""""fallbackUrls"\s*:\s*(\[.*?\])""", RegexOption.DOT_MATCHES_ALL)

	private val imageUrlRegex = Regex("""(https?:\\?/\\?[^"]+\.(?:jpg|jpeg|png|webp|gif))""")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override fun getRequestHeaders(): Headers = Headers.Builder()
		.add("User-Agent", config[userAgentKey])
		.add("referer", "https://$domain")
		.build()

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.ALPHABETICAL_DESC,
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = availableTags.get(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/tim-kiem")

			append("?sort=")
			append(
				when (order) {
					SortOrder.UPDATED -> "-updated_at"
					SortOrder.NEWEST -> "-created_at"
					SortOrder.POPULARITY -> "-views"
					SortOrder.ALPHABETICAL -> "name"
					SortOrder.ALPHABETICAL_DESC -> "-name"
					else -> "-updated_at"
				},
			)

			if (filter.states.isNotEmpty()) {
				append("&filter[status]=")
				filter.states.joinTo(this, ",") {
					when (it) {
						MangaState.ONGOING -> "2"
						MangaState.FINISHED -> "1"
						else -> ""
					}
				}
			}

			if (filter.tags.isNotEmpty()) {
				append("&filter[accept_genres]=")
				filter.tags.joinTo(this, ",") { it.key }
			}

			if (!filter.query.isNullOrEmpty()) {
				append("&filter[name]=")
				append(filter.query.urlEncoded())
			}

			if (filter.tagsExclude.isNotEmpty()) {
				append("&filter[reject_genres]=")
				filter.tagsExclude.joinTo(this, ",") { it.key }
			}

			append("&page=$page")
		}

		val doc = webClient.httpGet(url).parseHtml()
		return parseMangaList(doc)
	}

	private fun parseMangaList(doc: Document): List<Manga> {
		return doc.select(
			"div.border.rounded-xl.border-gray-300.dark\\:border-dark-blue.bg-white.dark\\:bg-fire-blue"
		).map { element ->
			val mainA = element.selectFirstOrThrow("div.relative a")
			val href = mainA.attrAsRelativeUrl("href")
			val title = mainA.selectFirst("div.cover-frame img")?.attr("alt")
				?.takeIf { it.isNotBlank() }
				?: element.selectFirst("div.p-3 h3 a")?.text()?.takeIf { it.isNotBlank() }
				?: "Không có tiêu đề"
			val coverUrl = mainA.select("div.cover-frame img").attr("data-src").takeIf { it.isNotBlank() }
				?: mainA.select("div.cover-frame img").attr("src")

			Manga(
				id = generateUid(href),
				title = title,
				altTitles = emptySet(),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				contentRating = ContentRating.ADULT,
				coverUrl = coverUrl,
				tags = emptySet(),
				state = null,
				authors = emptySet(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val url = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(url).parseHtml()

		val altTitles = doc.select("div.mt-2:contains(Tên khác:) span").mapNotNullToSet { it.textOrNull() }
		val allTags = availableTags.getOrNull().orEmpty()
		val tags = doc.select("div.mt-2:contains(Thể loại:) a").mapNotNullToSet { a ->
			val title = a.text().toTitleCase()
			allTags.find { x -> x.title == title }
		}

		val stateText = doc.selectFirst("div.mt-2:contains(Tình trạng:) span")?.text()
		val state = when (stateText) {
			"Đang tiến hành" -> MangaState.ONGOING
			else -> MangaState.FINISHED
		}

		val chapterListDiv = doc.selectFirst("div#chapterList")
			?: throw ParseException("Chapters list not found!", url)

		val chapterLinks = chapterListDiv.select("a.block")
		val chapters = chapterLinks.mapChapters(reversed = true) { index, a ->
			val title = a.selectFirst("span.text-ellipsis")?.textOrNull()
			val href = a.attrAsRelativeUrl("href")
			val uploadDate = a.selectFirst("span.ml-2.whitespace-nowrap")?.text()

			MangaChapter(
				id = generateUid(href),
				title = title,
				number = index + 1f,
				volume = 0,
				url = href,
				scanlator = null,
				uploadDate = parseChapterDate(uploadDate),
				branch = null,
				source = source,
			)
		}

		return manga.copy(
			altTitles = altTitles,
			tags = tags,
			state = state,
			chapters = chapters,
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()

		doc.selectFirst("script:containsData(window.encryptionConfig)")?.data()?.let { scriptContent ->
			val arrayString = scriptContent.findGroupValue(fallbackUrlsRegex) ?: return@let
			val scriptImages = imageUrlRegex.findAll(arrayString).map {
				it.groupValues[1].replace("\\/", "/")
			}.toList()

			if (scriptImages.isNotEmpty()) {
				return scriptImages.map { url ->
					MangaPage(id = generateUid(url), url = url, preview = null, source = source)
				}
			}
		}

		val tagImagePages = doc.select("div#chapter-content img").mapNotNull { img ->
			val imageUrl = img.src() ?: return@mapNotNull null
			MangaPage(id = generateUid(imageUrl), url = imageUrl, preview = null, source = source)
		}

		if (tagImagePages.isNotEmpty()) {
			return tagImagePages
		}

		throw ParseException("Cannot find any image source", chapter.url)
	}

	private fun parseChapterDate(date: String?): Long {
		if (date.isNullOrEmpty()) {
			return 0L
		}
		val amount = date.findGroupValue(relativeDateRegex)?.toLongOrNull()
		if (amount != null) {
			val unitMillis = when {
				date.contains("giây trước") -> 1000L
				date.contains("phút trước") -> 60L * 1000L
				date.contains("giờ trước") -> 60L * 60L * 1000L
				date.contains("ngày trước") -> 24L * 60L * 60L * 1000L
				date.contains("tuần trước") -> 7L * 24L * 60L * 60L * 1000L
				date.contains("tháng trước") -> 30L * 24L * 60L * 60L * 1000L
				date.contains("năm trước") -> 365L * 24L * 60L * 60L * 1000L
				else -> 0L
			}
			if (unitMillis != 0L) {
				return System.currentTimeMillis() - amount * unitMillis
			}
		}
		return dateFormat.parseSafe(date)
	}

	private suspend fun fetchTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/tim-kiem").parseHtml()
		val regex = Regex("toggleGenre\\('([0-9]+)'\\)")
		return doc.body().getElementsByAttribute("@click")
			.mapNotNullToSet { label ->
				// @click="toggleGenre('1')"
				val attr = label.attr("@click")
				val number = attr.findGroupValue(regex) ?: return@mapNotNullToSet null
				MangaTag(
					key = number,
					title = label.textOrNull()?.toTitleCase(sourceLocale) ?: return@mapNotNullToSet null,
					source = source,
				)
			}
	}
}
