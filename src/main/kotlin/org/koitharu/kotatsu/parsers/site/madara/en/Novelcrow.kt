package org.koitharu.kotatsu.parsers.site.madara.en

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("NOVELCROW", "NovelCrow", "en", ContentType.HENTAI)
internal class Novelcrow(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.NOVELCROW, "novelcrow.com", pageSize = 24) {

	override val tagPrefix = "comic-genre/"
	override val listUrl = "comic/"

	override val withoutAjax = true

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
		SortOrder.ALPHABETICAL,
		SortOrder.RATING,
		SortOrder.RELEVANCE,
	)

	override val selectDesc = "div.summary__content, div.manga-summary, div.desc, div.post-content div.summary__content"
	override val selectGenre = "div.genres-content a, .summary-content a[href*=/comic-genre/]"
	override val selectChapter = "li.wp-manga-chapter, .version-chap li"
	override val selectBodyPage = "div.reading-content, div.page-internal, div.entry-content"
	override val selectPage = "div.page-break, .page-break, img.wp-manga-chapter-img"

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val sortParam = when (order) {
			SortOrder.POPULARITY -> "views"
			SortOrder.UPDATED -> "latest"
			SortOrder.NEWEST -> "new-manga"
			SortOrder.ALPHABETICAL -> "alphabet"
			SortOrder.RATING -> "rating"
			else -> ""
		}
		val url = buildString {
			append("https://")
			append(domain)
			when {
				!filter.query.isNullOrEmpty() -> {
					append("/?s=")
					append(filter.query.urlEncoded())
					append("&post_type=wp-manga")
					if (page > 1) append("&page=").append(page)
					if (sortParam.isNotEmpty()) append("&m_orderby=").append(sortParam)
				}
				else -> {
					if (filter.tags.isNotEmpty()) {
						val tag = filter.tags.first()
						append('/').append(tagPrefix).append(tag.key).append('/')
					} else {
						append('/').append(listUrl)
					}
					if (page > 1) append("page/").append(page).append('/')
					if (sortParam.isNotEmpty()) append("?m_orderby=").append(sortParam)
				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return parseMangaList(doc)
	}

	override fun parseMangaList(doc: Document): List<Manga> {
		val manga = ArrayList<Manga>()
		val seen = HashSet<String>()

		for (h in doc.select("h3 a[href*=/comic/], h2 a[href*=/comic/], h4 a[href*=/comic/]")) {
			val titleLink = h.closest("a") ?: h
			val href = titleLink.attrAsRelativeUrl("href")
			val parts = href.removePrefix("/").removeSuffix("/").split('/')
			if (parts.size < 2 || parts[0] != "comic") continue
			if (parts.size > 2 && !parts[2].startsWith("?")) continue
			if (!seen.add(href)) continue

			val title = titleLink.text().cleanupTitle()

			val container = titleLink.parents().firstOrNull {
				it.tagName() == "article" ||
					it.hasClass("page-item-detail") ||
					it.hasClass("c-tabs-item__content") ||
					it.hasClass("manga-card") ||
					it.hasClass("post")
			} ?: titleLink.parent()?.parent() ?: titleLink.parent() ?: continue

			val coverEl = container.selectFirst("a[href] img") ?: container.selectFirst("img")
			val coverUrl = coverEl?.attrAsAbsoluteUrlOrNull("data-src")
				?: coverEl?.attrAsAbsoluteUrlOrNull("data-lazy-src")
				?: coverEl?.attrAsAbsoluteUrlOrNull("src")

			val ratingText = container.selectFirst("span.total_votes, .rating, .post-total-rating span, .numscore")?.text()
			var rating = ratingText?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN
			if (rating == RATING_UNKNOWN) {
				val numeric = container.ownText().trim()
				val m = Regex("""(\d\.\d)""").find(numeric)
				if (m != null) rating = m.groupValues[1].toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN
			}

			val tags = container.select("a[href*=/comic-genre/]").mapNotNullToSet { a ->
				val key = a.attr("href").removeSuffix('/').substringAfterLast('/')
				val text = a.text().cleanupTitle()
				if (text.isEmpty() || key.isEmpty()) null else MangaTag(
					key = key,
					title = text.toTitleCase(sourceLocale),
					source = source,
				)
			}

			manga += Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				title = title,
				altTitles = emptySet(),
				coverUrl = coverUrl,
				tags = tags,
				rating = rating,
				authors = emptySet(),
				state = null,
				source = source,
				contentRating = ContentRating.ADULT,
			)
		}
		return manga
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()

		val chaptersDeferred = async { loadChapters(manga.url, doc) }

		val desc = doc.selectFirst(selectDesc)?.html().orEmpty()
			.ifEmpty { doc.selectFirst("div.summary__content, div.manga-excerpt, div#summary")?.html().orEmpty() }

		fun linksAfterLabel(label: String): List<Element> {
			val headings = doc.select("h3, h4, h5")
			for (h in headings) {
				if (!h.ownText().trim().startsWith(label, ignoreCase = true)) continue
				val result = ArrayList<Element>()
				var sib = h.nextElementSibling()
				while (sib != null) {
					if (sib.tagName().startsWith("h") && sib.tagName().length == 2 && sib.tagName()[1].isDigit()) break
					val links = sib.select("a[href]")
					if (links.isNotEmpty()) {
						result.addAll(links)
						if (result.isNotEmpty()) break
					}
					sib = sib.nextElementSibling()
				}
				return result
			}
			return emptyList()
		}

		fun textAfterLabel(label: String): String? {
			val headings = doc.select("h3, h4, h5")
			for (h in headings) {
				if (!h.ownText().trim().startsWith(label, ignoreCase = true)) continue
				var sib = h.nextElementSibling()
				while (sib != null) {
					if (sib.tagName().startsWith("h") && sib.tagName().length == 2 && sib.tagName()[1].isDigit()) break
					val txt = sib.ownText().trim().nullIfEmpty() ?: sib.text().trim().nullIfEmpty()
					if (txt != null) return txt
					sib = sib.nextElementSibling()
				}
			}
			return null
		}

		val stateText = textAfterLabel("Status")?.lowercase(Locale.ROOT).orEmpty()
		val state = when {
			stateText in ongoing -> MangaState.ONGOING
			stateText in finished -> MangaState.FINISHED
			stateText in abandoned -> MangaState.ABANDONED
			stateText in paused -> MangaState.PAUSED
			stateText in upcoming -> MangaState.UPCOMING
			else -> null
		}

		val tagSet = LinkedHashSet<MangaTag>()
		for (a in linksAfterLabel("Genre") + linksAfterLabel("Tags") + doc.select("a[href*=/comic-genre/]")) {
			val href = a.attr("href")
			if (!href.contains("/comic-genre/")) continue
			val key = href.removeSuffix('/').substringAfterLast('/')
			val text = a.text().cleanupTitle()
			if (text.isEmpty() || key.isEmpty()) continue
			tagSet += MangaTag(
				key = key,
				title = text.toTitleCase(sourceLocale),
				source = source,
			)
		}

		val authors = (linksAfterLabel("Artist") + linksAfterLabel("Author"))
			.mapNotNullTo(LinkedHashSet()) { a ->
				a.text().cleanupTitle().ifEmpty { null }
			}

		val title = doc.selectFirst("h1")?.text()?.cleanupTitle() ?: manga.title

		manga.copy(
			title = title,
			tags = tagSet,
			authors = authors,
			description = desc,
			altTitles = emptySet(),
			state = state,
			chapters = chaptersDeferred.await(),
			contentRating = ContentRating.ADULT,
		)
	}

	override suspend fun getChapters(manga: Manga, doc: Document): List<MangaChapter> {
		return loadChapters(manga.url, doc)
	}

	override suspend fun loadChapters(mangaUrl: String, document: Document): List<MangaChapter> {
		val doc = runCatching {
			val url = mangaUrl.toAbsoluteUrl(domain).removeSuffix('/') + "/ajax/chapters/"
			webClient.httpPost(url, emptyMap<String, String>()).parseHtml()
		}.getOrElse {
			val mangaId = document.selectFirst("div#manga-chapters-holder")?.attr("data-id")
				?: document.selectFirst("[data-id]")?.attr("data-id")
				.orEmpty()
			if (mangaId.isNotEmpty()) {
				runCatching {
					webClient.httpPost(
						"https://$domain/wp-admin/admin-ajax.php",
						"action=manga_get_chapters&manga=$mangaId",
					).parseHtml()
				}.getOrNull()
			} else {
				null
			} ?: document
		}

		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return doc.body().select(selectChapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirst("a[href]") ?: return@mapChapters null
			val href = a.attrAsRelativeUrl("href")
			val link = if (href.contains('?')) "$href&style=list" else "$href?style=list"
			val name = a.selectFirst("p")?.text()?.cleanupTitle() ?: a.ownText().cleanupTitle()
			val dateText = li.selectFirst("a.c-new-tag")?.attr("title")
				?: li.selectFirst(selectDate)?.text()
				?: li.selectFirst("span.chapter-release-date, span i")?.text()
			MangaChapter(
				id = generateUid(href),
				title = name,
				number = i + 1f,
				volume = 0,
				url = link,
				uploadDate = parseChapterDate(dateFormat, dateText),
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val root = doc.body().selectFirst(selectBodyPage)
			?: doc.body().selectFirst("div.read-container, div#wraper, div.text-center")
			?: throw ParseException("No image found, try to log in", fullUrl)
		fun isMangaImage(url: String): Boolean {
			val u = url.lowercase()
			if (u.startsWith("data:")) return false
			if (u.contains("wp-content/uploads", ignoreCase = true)) return true
			if (u.contains("wp-content", ignoreCase = true)) return true
			val ext = u.substringAfterLast('.').substringBefore('?').substringBefore('#')
			return ext in imageExts && u.startsWith("http")
		}
		return root.select("$selectPage, img").flatMap { el ->
			if (el.tagName() == "img") {
				val url = (el.attrAsAbsoluteUrlOrNull("data-src")
					?: el.attrAsAbsoluteUrlOrNull("data-lazy-src")
					?: el.attrAsAbsoluteUrlOrNull("src"))
					?.takeIf(::isMangaImage)
					?: return@flatMap emptyList()
				listOf(
					MangaPage(
						id = generateUid(url),
						url = url.toRelativeUrl(domain),
						preview = null,
						source = source,
					),
				)
			} else {
				el.selectOrThrow("img").mapNotNull { img ->
					val url = img.attrAsAbsoluteUrlOrNull("data-src")
						?: img.attrAsAbsoluteUrlOrNull("data-lazy-src")
						?: img.requireSrc().toRelativeUrl(domain)
					MangaPage(
						id = generateUid(url),
						url = url,
						preview = null,
						source = source,
					)
				}
			}
		}.distinctBy { it.url }
	}

	override suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/$listUrl").parseHtml()
		return doc.select("a[href*=/$tagPrefix]").mapNotNullToSet { a ->
			val href = a.attr("href")
			if (!href.contains(tagPrefix)) return@mapNotNullToSet null
			val key = href.removeSuffix('/').substringAfterLast('/')
			val text = a.ownText().cleanupTitle()
			val name = text.substringBefore('\n').substringBefore('(').trim()
			if (name.isEmpty() || key.isEmpty() || key == tagPrefix.removeSuffix("/")) {
				null
			} else {
				MangaTag(
					key = key,
					title = name.toTitleCase(sourceLocale),
					source = source,
				)
			}
		}
	}

	private fun String.cleanupTitle(): String =
		replace(Regex("""\[\d+\]"""), "").trim()

	private companion object {
		private val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif")
	}
}
