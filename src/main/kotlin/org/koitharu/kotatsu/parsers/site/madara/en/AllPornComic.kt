package org.koitharu.kotatsu.parsers.site.madara.en

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("ALLPORN_COMIC", "AllPornComic", "en", ContentType.HENTAI)
internal class AllPornComic(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.ALLPORN_COMIC, "allporncomic.com", pageSize = 24) {

	override val tagPrefix = "porncomic-cat/"
	override val datePattern = "MMMM dd, yyyy"
	override val listUrl = "porncomic/"

	// The site's markup has diverged from vanilla Madara; parse the server-rendered pages
	// directly instead of relying on the admin-ajax endpoint which now returns a 400/empty.
	override val withoutAjax = true

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
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
	override val selectGenre = "div.genres-content a, .summary-content a[href*=/porncomic-cat/]"
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
					// Path-style pagination applies to tag pages and the archive listing.
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
		// Walk all <h3> headings (which contain the title link) and then inspect siblings for
		// cover image, rating, genres, and status. This avoids depending on wrapper classes.
		val manga = ArrayList<Manga>()
		val seen = HashSet<String>()

		for (h in doc.select("h3 a[href*=/porncomic/], h2 a[href*=/porncomic/], h4 a[href*=/porncomic/]")) {
			val titleLink = h.closest("a") ?: h
			val href = titleLink.attrAsRelativeUrl("href")
			// Must be a manga page (not a chapter: chapters have a third path segment with slug).
			val parts = href.removePrefix("/").removeSuffix("/").split('/')
			if (parts.size < 2 || parts[0] != "porncomic") continue
			// Chapters look like /porncomic/<slug>/<chapter-slug>/
			if (parts.size > 2 && !parts[2].startsWith("?")) continue
			if (!seen.add(href)) continue

			val title = titleLink.text().cleanupTitle()

			// Find the card container by walking up to a reasonable wrapper.
			val container = titleLink.parents().firstOrNull {
				it.tagName() == "article" ||
					it.hasClass("page-item-detail") ||
					it.hasClass("c-tabs-item__content") ||
					it.hasClass("manga-card") ||
					it.hasClass("post")
			} ?: titleLink.parent()?.parent() ?: titleLink.parent() ?: continue

			// Cover: first <img> inside the card; prefer a link pointing to the manga.
			val coverEl = container.selectFirst("a[href] img") ?: container.selectFirst("img")
			val coverUrl = coverEl?.src()?.takeIf { it.startsWith("http") }
				?: coverEl?.attrAsAbsoluteUrlOrNull("data-src")
				?: coverEl?.attrAsAbsoluteUrlOrNull("data-lazy-src")

			// Rating: find a number like "4.3" immediately preceding the heading block.
			val ratingText = container.selectFirst("span.total_votes, .rating, .post-total-rating span, .numscore")?.text()
			var rating = ratingText?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN
			if (rating == RATING_UNKNOWN) {
				// Fallback: scan text nodes for a floating-point rating out of 5.
				val numeric = container.ownText().trim()
				val m = Regex("""(\d\.\d)""").find(numeric)
				if (m != null) rating = m.groupValues[1].toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN
			}

			// Genres: anchors pointing to porncomic-cat/ pages.
			val tags = container.select("a[href*=/porncomic-cat/]").mapNotNullToSet { a ->
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

		// Helper: given a heading label (h5/h3/h4), gather links that follow it until the next heading.
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
						// Stop at the first non-empty block to avoid bleeding into the next section.
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

		// Genres: prefer links under "Genre(s)" label, but also collect all /porncomic-cat/ links.
		val tagSet = LinkedHashSet<MangaTag>()
		for (a in linksAfterLabel("Genre") + linksAfterLabel("Tags") + doc.select("a[href*=/porncomic-cat/]")) {
			val href = a.attr("href")
			if (!href.contains("/porncomic-cat/")) continue
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
		// Chapters on detail page are either in the "LATEST MANGA RELEASES" list (first chapter only)
		// or must be loaded via the AJAX endpoint. Use loadChapters for the full list.
		return loadChapters(manga.url, doc)
	}

	override suspend fun loadChapters(mangaUrl: String, document: Document): List<MangaChapter> {
		// Try the ajax/chapters/ POST endpoint first; fall back to parsing the inline listing.
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
		// Only accept images that look like chapter content: they live under wp-content/ or
		// WP-manga/ on the CDN. Stray icons/logos that share the <img> tag are filtered out.
		fun isMangaImage(url: String): Boolean = url.contains("WP-manga/data", ignoreCase = true) ||
			url.contains("wp-content/uploads", ignoreCase = true) ||
			(url.contains("cdn.allporncomic.com") && url.substringAfterLast('.').lowercase() in imageExts)
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
		// Genre index is rendered as a big list of anchor tags to /porncomic-cat/<slug>/.
		return doc.select("a[href*=/$tagPrefix]").mapNotNullToSet { a ->
			val href = a.attr("href")
			if (!href.contains(tagPrefix)) return@mapNotNullToSet null
			val key = href.removeSuffix('/').substringAfterLast('/')
			val text = a.ownText().cleanupTitle()
			// Skip genre links that are part of the sidebar filter widget (they end with counts
			// like "Anal\n(2023)" and are stripped to just the name).
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

	private fun Element.src(): String? =
		attrAsAbsoluteUrlOrNull("src")
			?: attrAsAbsoluteUrlOrNull("data-src")
			?: attrAsAbsoluteUrlOrNull("data-lazy-src")

	private companion object {
		private val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif")
	}
}
