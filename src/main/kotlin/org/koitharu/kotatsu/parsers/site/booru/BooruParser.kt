package org.koitharu.kotatsu.parsers.site.booru

import okhttp3.Headers
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import java.util.*

/**
 * Common base for imageboard ("booru") software.
 *
 * A booru post is a single image, so it maps to a [Manga] holding exactly one chapter with exactly
 * one page. Subclasses only translate their API into [BooruPost]; assembling the model objects,
 * building the shared `tags` search argument and handling authorization happen here, so that
 * nothing site specific leaks into this class.
 */
internal abstract class BooruParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	pageSize: Int,
) : PagedMangaParser(context, source, pageSize) {

	/**
	 * Number of most-used tags requested for the filter list.
	 */
	protected open val tagsLimit: Int = 200

	/**
	 * Maximum number of tags one search may contain. Several boorus cap this for anonymous users;
	 * override in a subclass when the site documents a limit.
	 */
	protected open val maxTagsPerSearch: Int = Int.MAX_VALUE

	/**
	 * Cookie names that exist once the user is signed in.
	 *
	 * Authentication itself is performed by the app's existing login system: it opens
	 * [MangaParserAuthProvider.authUrl] in a browser and stores the resulting cookies in
	 * [MangaLoaderContext.cookieJar], which is already attached to every request made here.
	 * This parser only *observes* those cookies, exactly like `ExHentaiParser` does.
	 *
	 * A site that requires a login declares it by implementing [MangaParserAuthProvider] and
	 * delegating to [hasAuthCookies]; see `Danbooru` for a worked example. Sources that work
	 * anonymously need no extra code and simply degrade through [imageUnavailable].
	 */
	protected open val authCookieNames: Array<String> = arrayOf("user_id", "pass_hash")

	/**
	 * Content ratings this source can filter by. Deliberately not hardcoded into the request
	 * builder: a subclass may narrow it (a "safe only" mirror exposes just [ContentRating.SAFE]),
	 * and the user chooses a value per search through the standard filter UI.
	 */
	protected open val supportedRatings: Set<ContentRating> =
		EnumSet.of(ContentRating.SAFE, ContentRating.SUGGESTIVE, ContentRating.ADULT)

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.NEWEST)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
		)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override fun getRequestHeaders(): Headers = super.getRequestHeaders().newBuilder()
		.add("Referer", "https://$domain/")
		.add("Accept", "application/json, text/javascript, */*; q=0.01")
		.add("Accept-Language", "en-US,en;q=0.5")
		.add("Connection", "keep-alive")
		.build()

	private val tagsCache = suspendLazy(soft = true) {
		// A failing tag list must not make the whole filter sheet unusable.
		runCatchingCancellable { fetchTags() }.getOrDefault(emptySet())
	}

	override suspend fun getFilterOptions(): MangaListFilterOptions = MangaListFilterOptions(
		availableTags = tagsCache.get(),
		availableContentRating = supportedRatings,
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		return fetchPosts(page, buildTagQuery(filter)).map { it.toManga() }
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val post = fetchPost(postId(manga.url))
		val details = post.toManga()
		// The title is regenerated rather than carried over: it is derived deterministically from
		// the post id and tags, so it matches the list entry, and link resolution passes in a stub
		// title that has to be replaced with the real one.
		return manga.copy(
			title = details.title,
			rating = details.rating,
			contentRating = details.contentRating,
			coverUrl = details.coverUrl ?: manga.coverUrl,
			tags = details.tags.ifEmpty { manga.tags },
			authors = details.authors.ifEmpty { manga.authors },
			largeCoverUrl = details.largeCoverUrl ?: manga.largeCoverUrl,
			description = details.description,
			chapters = listOf(
				MangaChapter(
					id = generateUid(manga.url),
					title = null,
					number = 1f,
					volume = 0,
					url = manga.url,
					scanlator = null,
					uploadDate = post.createdAt,
					branch = null,
					source = source,
				),
			),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val post = fetchPost(postId(chapter.url))
		val fileUrl = post.fileUrl ?: post.sampleUrl ?: imageUnavailable(chapter.url)
		return listOf(
			MangaPage(
				id = generateUid(fileUrl),
				url = fileUrl,
				preview = post.previewUrl,
				source = source,
			),
		)
	}

	/**
	 * Requests one page of search results. [tags] is the already assembled value of the `tags`
	 * argument and may be empty.
	 */
	protected abstract suspend fun fetchPosts(page: Int, tags: String): List<BooruPost>

	/**
	 * Requests a single post by its numeric id.
	 */
	protected abstract suspend fun fetchPost(id: Long): BooruPost

	/**
	 * Requests the tag list used to populate the filter.
	 */
	protected abstract suspend fun fetchTags(): Set<MangaTag>

	/**
	 * Relative url of a post's human readable page, used for [Manga.url] and [Manga.publicUrl].
	 * Must contain the post id so that [postId] can recover it.
	 */
	protected abstract fun postUrl(id: Long): String

	/**
	 * Translates a [ContentRating] into the site specific `rating:` search token, or null when the
	 * site cannot express that rating.
	 */
	protected abstract fun ratingToken(rating: ContentRating): String?

	/**
	 * Builds the value of the `tags` argument shared by every booru API: free text query, included
	 * tags, excluded tags (`-tag`) and an optional `rating:` token.
	 */
	protected fun buildTagQuery(filter: MangaListFilter): String {
		val parts = ArrayList<String>()
		filter.query?.nullIfEmpty()?.let { query ->
			// Boorus have no separate full text search: whitespace separates independent tags.
			query.splitToSequence(' ').forEach { word ->
				word.trim().nullIfEmpty()?.let { parts.add(it) }
			}
		}
		filter.tags.forEach { parts.add(it.key) }
		filter.tagsExclude.forEach { parts.add("-" + it.key) }
		filter.contentRating.oneOrThrowIfMany()?.let { rating ->
			ratingToken(rating)?.let { parts.add(it) }
		}
		require(parts.size <= maxTagsPerSearch) {
			"This source allows at most $maxTagsPerSearch tags per search, got ${parts.size}"
		}
		return parts.joinToString(" ")
	}

	/**
	 * Maps a raw booru rating to [ContentRating]. Accepts both the single letter form used by
	 * Danbooru and Moebooru and the whole word used by Gelbooru forks, and tolerates unknown values.
	 */
	protected fun parseRating(raw: String?): ContentRating? = when (raw?.trim()?.lowercase(Locale.ROOT)) {
		"g", "general", "s", "safe" -> ContentRating.SAFE
		"q", "questionable", "sensitive" -> ContentRating.SUGGESTIVE
		"e", "explicit" -> ContentRating.ADULT
		else -> null
	}

	/**
	 * Turns a whitespace separated booru tag string into [MangaTag]s. A missing or blank string
	 * simply yields no tags.
	 */
	protected fun parseTags(tagString: String?): Set<MangaTag> {
		if (tagString.isNullOrBlank()) {
			return emptySet()
		}
		return tagString.split(' ').mapNotNullToSet { raw ->
			val key = raw.trim().nullIfEmpty() ?: return@mapNotNullToSet null
			tagOf(key)
		}
	}

	protected fun tagOf(key: String): MangaTag = MangaTag(
		title = key.replace('_', ' ').toTitleCase(sourceLocale),
		key = key,
		source = source,
	)

	/**
	 * True when every cookie from [authCookieNames] is present for the current domain.
	 */
	protected fun hasAuthCookies(): Boolean {
		if (authCookieNames.isEmpty()) {
			return false
		}
		val cookies = context.cookieJar.getCookies(domain).mapToSet { it.name }
		return authCookieNames.all { it in cookies }
	}

	/**
	 * Recovers the numeric post id previously embedded into [Manga.url] / [MangaChapter.url].
	 * Uses the last group of digits so it works for both path and query string layouts.
	 */
	protected fun postId(url: String): Long = requireNotNull(
		POST_ID_REGEX.findAll(url).lastOrNull()?.value?.toLongOrNull(),
	) {
		"Cannot find a post id in \"$url\""
	}

	/**
	 * Called when the API answered but withheld the image. On most boorus that means the post is
	 * restricted to signed in users, so report the regular authorization error rather than
	 * crashing with a parse failure.
	 */
	private fun imageUnavailable(url: String): Nothing = throw if (hasAuthCookies()) {
		ParseException("Post has no image file", url)
	} else {
		AuthRequiredException(source)
	}

	private fun BooruPost.toManga(): Manga {
		val relativeUrl = postUrl(id)
		val tagSet = parseTags(tags)
		return Manga(
			id = generateUid(relativeUrl),
			title = buildTitle(tags, id),
			altTitles = emptySet(),
			url = relativeUrl,
			publicUrl = relativeUrl.toAbsoluteUrl(domain),
			rating = score?.let { normalizeScore(it) } ?: RATING_UNKNOWN,
			contentRating = parseRating(rating) ?: sourceContentRating,
			coverUrl = previewUrl ?: sampleUrl ?: fileUrl,
			tags = tagSet,
			state = null,
			authors = setOfNotNull(author?.nullIfEmpty()),
			largeCoverUrl = sampleUrl ?: fileUrl,
			description = buildDescription(this, tagSet),
			source = source,
		)
	}

	/**
	 * A booru post has no name, so a readable title is derived from its first tags with the post id
	 * as a stable suffix. Must stay identical between the list and the detail request, otherwise
	 * the two representations of one post would not match.
	 */
	private fun buildTitle(tagString: String?, id: Long): String {
		val readable = tagString?.splitToSequence(' ')
			?.mapNotNull { it.trim().nullIfEmpty() }
			?.take(TITLE_TAGS_COUNT)
			?.joinToString(", ") { it.replace('_', ' ') }
			?.nullIfEmpty()
		return if (readable == null) "#$id" else "$readable (#$id)"
	}

	private fun buildDescription(post: BooruPost, tags: Set<MangaTag>): String = buildString {
		append("Post #").append(post.id)
		if (post.width > 0 && post.height > 0) {
			append(" &mdash; ").append(post.width).append(" &times; ").append(post.height)
		}
		post.author?.nullIfEmpty()?.let {
			append("<br>Uploader: ").append(it)
		}
		post.sourceUrl?.nullIfEmpty()?.let { src ->
			append("<br>Source: ")
			if (src.startsWith("http", ignoreCase = true)) {
				append("<a href=\"").append(src).append("\">").append(src).append("</a>")
			} else {
				append(src)
			}
		}
		if (tags.isNotEmpty()) {
			append("<br>Tags: ").append(tags.joinToString(", ") { it.title })
		}
	}

	/**
	 * Booru scores are unbounded integers while [Manga.rating] must be within `0..1`,
	 * so the value is squashed instead of scaled.
	 */
	private fun normalizeScore(score: Int): Float = when {
		score <= 0 -> RATING_UNKNOWN
		else -> (score.toFloat() / (score.toFloat() + SCORE_HALF_POINT)).coerceIn(0f, 1f)
	}

	/**
	 * Software independent representation of a single booru post. Every field except [id] is
	 * optional because the three API families disagree on which of them are always present.
	 */
	protected class BooruPost(
		@JvmField val id: Long,
		@JvmField val fileUrl: String?,
		@JvmField val previewUrl: String?,
		@JvmField val sampleUrl: String? = null,
		@JvmField val tags: String? = null,
		@JvmField val rating: String? = null,
		/**
		 * Url of the artwork the post was taken from, if the uploader supplied one.
		 * Named `sourceUrl` rather than `source` to avoid clashing with [MangaParser.source].
		 */
		@JvmField val sourceUrl: String? = null,
		@JvmField val author: String? = null,
		@JvmField val createdAt: Long = 0L,
		@JvmField val score: Int? = null,
		@JvmField val width: Int = 0,
		@JvmField val height: Int = 0,
	)

	protected companion object {

		const val TITLE_TAGS_COUNT: Int = 5

		/**
		 * Score that maps to a rating of 0.5; keeps small positive scores from collapsing to zero.
		 */
		const val SCORE_HALF_POINT: Float = 25f

		private val POST_ID_REGEX = Regex("""\d+""")
	}
}
