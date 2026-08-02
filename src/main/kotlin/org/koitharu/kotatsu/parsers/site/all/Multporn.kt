package org.koitharu.kotatsu.parsers.site.all

import okhttp3.Headers
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("MULTPORN", "Multporn", type = ContentType.HENTAI)
internal class Multporn(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.MULTPORN, 42) {

	override val configKeyDomain = ConfigKey.Domain("multporn.net")

	override fun getRequestHeaders(): Headers = Headers.Builder()
		.add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
		.add("Accept-Language", "en-US,en;q=0.9")
		.build()

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.NEWEST,
		SortOrder.NEWEST_ASC,
		SortOrder.UPDATED,
		SortOrder.UPDATED_ASC,
		SortOrder.ALPHABETICAL,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isMultipleTagsSupported = false,
			isTagsExclusionSupported = false,
		)

	init {
		setFirstPage(0)
	}

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableLocales = setOf(
			Locale.ENGLISH,
			Locale.GERMAN,
			Locale("ru"),
			Locale.CHINESE,
			Locale("es"),
		),
		availableContentTypes = EnumSet.of(
			ContentType.COMICS,
			ContentType.HENTAI,
		),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when {
				!filter.query.isNullOrEmpty() -> {
					append("/search?search_api_views_fulltext=")
					append(filter.query.splitByWhitespace().joinToString(separator = "+") { it.urlEncoded() })
					append("&page=")
					append(page)
				}

				filter.tags.isNotEmpty() -> {
					val tag = filter.tags.first()
					append(tag.key)
					append("?page=")
					append(page)
					// Sort on tag pages is a small set of tabs; keep simple (date desc).
				}

				else -> {
					append("/new")
					append("?type=")
					append(
						when (filter.types.oneOrThrowIfMany()) {
							ContentType.COMICS -> "1"
							ContentType.HENTAI -> "2"
							else -> "All"
						},
					)
					filter.locale?.let {
						append("&language=")
						append(
							when (it.language) {
								"en" -> "1"
								"de" -> "2"
								"ru" -> "3"
								"zh" -> "4"
								"es" -> "5"
								else -> "All"
							},
						)
					}
					append("&sort_by=")
					append(
						when (order) {
							SortOrder.NEWEST -> "created&sort_order=DESC"
							SortOrder.NEWEST_ASC -> "created&sort_order=ASC"
							SortOrder.UPDATED -> "changed&sort_order=DESC"
							SortOrder.UPDATED_ASC -> "changed&sort_order=ASC"
							SortOrder.ALPHABETICAL -> "title&sort_order=ASC"
							else -> "created&sort_order=DESC"
						},
					)
					append("&page=")
					append(page)
				}
			}
		}

		val doc = webClient.httpGet(url).parseHtml()
		// New site uses two layouts: a <table> of cells on tag pages, and content with strong > a + a > img
		// everywhere else. Build an (href -> Pair<title, img>) map by walking links and pairing them.
		val links = doc.select("a[href]")

		// Candidate title links: anchors to a content page where either the anchor is wrapped in
		// <strong>/<b>/<h2>/<h3>/<h4>, or the anchor itself contains a <strong>/<b>/<h1-4> child
		// (Multporn mixes both markup patterns on list vs search pages).
		val titleByHref = HashMap<String, String>()
		for (a in links) {
			val href = a.attrAsRelativeUrl("href")
			if (!isContentUrl(href)) continue
			val txt = a.text().trim()
			if (txt.isEmpty() || txt.length < 2) continue
			val parent = a.parent()
			val isHeadingWrapped = parent != null &&
				(parent.tagName() == "strong" || parent.tagName() == "b" ||
					parent.tagName() == "h1" || parent.tagName() == "h2" ||
					parent.tagName() == "h3" || parent.tagName() == "h4")
			val containsHeading = a.selectFirst("strong, b, h1, h2, h3, h4") != null
			if (isHeadingWrapped || containsHeading) {
				titleByHref[href] = txt
			}
		}

		// Candidate image links: anchors that wrap an <img> whose src points to a known preview style.
		val coverByHref = HashMap<String, String>()
		for (a in links) {
			val href = a.attrAsRelativeUrl("href")
			if (!isContentUrl(href)) continue
			val img = a.selectFirst("img") ?: continue
			val src = img.attrAsAbsoluteUrlOrNull("src") ?: img.attrAsAbsoluteUrlOrNull("data-src") ?: continue
			if (!isContentStyleImage(src)) continue
			coverByHref[href] = src
		}

		// Union — produce a Manga for every href that has both a title and a cover.
		val seen = HashSet<String>()
		val result = ArrayList<Manga>()
		for ((href, title) in titleByHref) {
			val cover = coverByHref[href] ?: continue
			if (!seen.add(href)) continue
			result += Manga(
				id = generateUid(href),
				title = title,
				altTitles = emptySet(),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				contentRating = ContentRating.ADULT,
				coverUrl = cover,
				tags = emptySet(),
				state = null,
				authors = emptySet(),
				source = source,
			)
		}
		// If we somehow matched nothing, fall back to legacy masonry selector.
		if (result.isEmpty()) {
			for (div in doc.select(".masonry-item, .views-row, tr:has(a img), .search-result")) {
				val a = div.selectFirst("a[href]") ?: continue
				val href = a.attrAsRelativeUrl("href")
				if (!isContentUrl(href)) continue
				val img = div.selectFirst("img") ?: continue
				val imgSrc = img.attrAsAbsoluteUrlOrNull("src")
					?: img.attrAsAbsoluteUrlOrNull("data-src")
					?: continue
				if (!isContentStyleImage(imgSrc)) continue
				val title = div.selectFirst("strong a, b a, h3 a, a")?.text()?.trim() ?: continue
				if (!seen.add(href)) continue
				result += Manga(
					id = generateUid(href),
					title = title,
					altTitles = emptySet(),
					url = href,
					publicUrl = href.toAbsoluteUrl(domain),
					rating = RATING_UNKNOWN,
					contentRating = ContentRating.ADULT,
					coverUrl = imgSrc,
					tags = emptySet(),
					state = null,
					authors = emptySet(),
					source = source,
				)
			}
		}
		return result
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val title = doc.selectFirst("h1[id=page-title], h1.title, h1")?.text()?.nullIfEmpty() ?: manga.title

		// Generic helper: walk siblings after any <h1>-<h5> whose text starts with [label]
		// and collect link texts until the next heading.  Multiple sections with the same prefix
		// (e.g. "Tags:" and "User tags:") are all collected.
		fun linksAfterHeading(label: String): List<String> {
			val headings = doc.select("h1, h2, h3, h4, h5")
			val found = ArrayList<String>()
			for (h in headings) {
				if (!h.ownText().trim().startsWith(label, ignoreCase = true)) continue
				var sib = h.nextElementSibling()
				while (sib != null) {
					if (sib.tagName().startsWith("h") && sib.tagName().length == 2 && sib.tagName()[1].isDigit()) break
					for (a in sib.select("li a, a[href]")) {
						val txt = a.text().trim()
						if (txt.isNotEmpty() && txt != "...") found += txt
					}
					sib = sib.nextElementSibling()
				}
			}
			return found
		}

		val authors = linksAfterHeading("Author").toCollection(LinkedHashSet())

		val tags = LinkedHashSet<String>()
		tags += linksAfterHeading("Section")
		tags += linksAfterHeading("Tag") // "Tags:" and "User tags:" both start with "Tag"
		tags += linksAfterHeading("Character")
		// Also pick up category tags that appear inline (e.g. "[Big Tits](.../category/big_tits)").
		for (a in doc.select("a[href]")) {
			val href = a.attr("href")
			if (href.contains("/category/") || href.contains("/user_tags/") || href.contains("/category_hentai/")) {
				val txt = a.text().trim()
				if (txt.isNotEmpty() && txt.length > 1 && "..." !in txt) tags += txt
			}
		}

		val isOngoing = doc.select("a[href]").any { it.text().equals("Ongoings", ignoreCase = true) }

		// Pages: any img whose src points at a juicebox / content image style. Juicebox renders a
		// thumbnail strip using juicebox_square_thumbnail_* — those carry the correct public/
		// paths even though their src is tiny, so we accept them and map back to the original.
		val pageUrls = LinkedHashSet<String>()
		for (img in doc.select("img[src]")) {
			val src = img.attrAsAbsoluteUrl("src")
			if (!src.contains("/styles/")) continue
			if (src.contains("styles/menu_") ||
				src.contains("/default_images/") ||
				src.contains("styles/avatars")) continue
			pageUrls += originalFileUrl(src)
		}

		return manga.copy(
			title = title,
			authors = authors,
			tags = tags.mapToSet { tag ->
				MangaTag(
					title = tag.toTitleCase(Locale.ENGLISH),
					key = tag.lowercase(Locale.ROOT).replace(' ', '_'),
					source = source,
				)
			},
			description = "Pages: ${pageUrls.size}",
			state = if (isOngoing) MangaState.ONGOING else MangaState.FINISHED,
			chapters = listOf(
				MangaChapter(
					id = generateUid(manga.url),
					title = null,
					number = 1f,
					volume = 0,
					url = manga.url,
					scanlator = null,
					uploadDate = 0L,
					branch = null,
					source = source,
				),
			),
			largeCoverUrl = pageUrls.firstOrNull(),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		val urls = LinkedHashSet<String>()
		for (img in doc.select("img[src]")) {
			val src = img.attrAsAbsoluteUrl("src")
			if (!src.contains("/styles/")) continue
			if (src.contains("styles/menu_") ||
				src.contains("/default_images/") ||
				src.contains("styles/avatars")) continue
			urls += originalFileUrl(src)
		}
		return urls.mapIndexed { i, url ->
			MangaPage(
				id = generateUid("$url#$i"),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	/**
	 * Content pages live at one of these prefixes; everything else (category, filter,
	 * pagination, user pages, tag hubs) must be ignored when scraping list/link pairs.
	 *
	 * Tag-hub pages also share these prefixes (e.g. `/comics/pokemon`), so the URL shape
	 * alone cannot reliably separate hubs from content.  To avoid false matches the cover
	 * filter in [getListPage] additionally rejects images under `styles/menu_*`, which
	 * is what hubs use; if an href has no valid content-style cover it will not be
	 * paired into a [Manga].  User-uploaded content uses the `/mp<digits>` prefix.
	 */
	private fun isContentUrl(href: String): Boolean {
		val path = href.removePrefix("/").substringBefore("?").substringBefore("#")
		if (path.isEmpty()) return false
		val segments = path.split('/')
		return when (segments[0]) {
			"comics", "hentai_manga", "video", "gif", "flash",
			"hentai_pictures", "cartoon_porn_pictures", "ai_generated_porn",
			"authors_comics_porn_images", "authors_hentai_comics",
			"gay_porn_comics", "user_content",
			-> segments.size >= 2 && segments[1].isNotEmpty()

			else -> segments[0].startsWith("mp") && segments[0].length > 2 &&
				segments[0].removePrefix("mp").all { it.isDigit() }
		}
	}

	private fun isContentStyleImage(src: String): Boolean {
		if (src.contains("/styles/menu_") || src.contains("/default_images/") || src.contains("styles/avatars")) {
			return false
		}
		return src.contains("/styles/search_") ||
			src.contains("/styles/taxonomy_") ||
			src.contains("/styles/random_") ||
			src.contains("/com_preview/") ||
			src.contains("/hentai_com_pre/") ||
			src.contains("/gif_pre/") ||
			src.contains("/fl_pre/") ||
			src.contains("/upload/")
	}

	/**
	 * Multporn serves images through a Drupal image style URL of the form
	 * `https://multporn.net/sites/default/files/styles/<style>/public/<path>`.
	 * The original file lives at `/sites/default/files/<path>`.
	 */
	private fun originalFileUrl(styleUrl: String): String {
		val idx = styleUrl.indexOf("/styles/")
		if (idx < 0) return styleUrl
		// Find the "/public/" segment after the style name.
		val after = styleUrl.substring(idx + "/styles/".length)
		val pub = after.indexOf("/public/")
		if (pub < 0) return styleUrl
		val path = after.substring(pub + "/public".length).substringBefore("?")
		return "https://$domain/sites/default/files$path"
	}
}
