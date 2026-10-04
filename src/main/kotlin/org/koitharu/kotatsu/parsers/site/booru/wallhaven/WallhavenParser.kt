package org.koitharu.kotatsu.parsers.site.booru.wallhaven

import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/** Anonymous Wallhaven v1 API parser. Anonymous API responses expose SFW results only. */
internal abstract class WallhavenParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
) : PagedMangaParser(context, source, PAGE_SIZE) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.NEWEST)

	override val filterCapabilities = MangaListFilterCapabilities(
	isMultipleTagsSupported = false,
	isTagsExclusionSupported = false,
	isSearchSupported = true,
	isSearchWithFiltersSupported = true,
)

	private val postCache = ConcurrentHashMap<String, Post>()

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

	override suspend fun getFilterOptions() = MangaListFilterOptions(
	availableContentRating = EnumSet.of(ContentRating.SAFE),
	// The unauthenticated v1 API is explicitly queried with SFW purity only.
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = HttpUrl.Builder()
			.scheme("https")
			.host(domain)
			.addPathSegment("api")
			.addPathSegment("v1")
			.addPathSegment("search")
			.addQueryParameter("sorting", "date_added")
			.addQueryParameter("purity", "100")
			.addQueryParameter("page", page.toString())
			.apply { filter.query?.trim()?.nullIfEmpty()?.let { addQueryParameter("q", it) } }
			.build()
		val root = webClient.httpGet(url).parseJson()
		val data = root.optJSONArray("data") ?: return emptyList()
		return data.toPosts().onEach { postCache[it.id] = it }.map { it.toManga() }
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val post = postCache[postId(manga.url)] ?: fetchPost(postId(manga.url))
		return manga.copy(
			title = post.title,
			coverUrl = post.thumbnail ?: manga.coverUrl,
			largeCoverUrl = post.thumbnail ?: manga.largeCoverUrl,
			tags = post.tags,
			description = post.description,
			chapters = listOf(
				MangaChapter(
					id = generateUid(post.url),
					title = null,
					number = 1f,
					volume = 0,
					url = post.url,
					scanlator = null,
					uploadDate = 0L,
					branch = null,
					source = source,
				),
			),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val post = postCache[postId(chapter.url)] ?: fetchPost(postId(chapter.url))
		return listOf(MangaPage(generateUid(post.fileUrl), post.fileUrl, post.thumbnail, source))
	}

	private suspend fun fetchPost(id: String): Post {
		val url = "https://$domain/api/v1/w/$id"
		val root = webClient.httpGet(url).parseJson()
		val post = root.optJSONObject("data")?.toPost() ?: error("Wallhaven post $id is unavailable")
		postCache[id] = post
		return post
	}

	private fun JSONArray.toPosts(): List<Post> = buildList {
		for (index in 0 until length()) {
			optJSONObject(index)?.toPost()?.let(::add)
		}
	}

	private fun JSONObject.toPost(): Post? {
		val id = optString("id").trim().nullIfEmpty() ?: return null
		val publicUrl = optString("url").trim().nullIfEmpty() ?: return null
		val fileUrl = optString("path").trim().nullIfEmpty() ?: return null
		val tags = optJSONArray("tags")?.toTags().orEmpty()
		val category = optString("category").trim().nullIfEmpty()?.replaceFirstChar { it.uppercase() } ?: "Wallpaper"
		val resolution = optString("resolution").trim().nullIfEmpty()
		return Post(
			id = id,
			url = publicUrl,
			fileUrl = fileUrl,
			thumbnail = optJSONObject("thumbs")?.optString("large")?.trim()?.nullIfEmpty(),
			title = "$category wallpaper (#$id)",
			tags = tags,
			description = buildString {
				resolution?.let { append(it) }
				optString("file_type").trim().nullIfEmpty()?.let { type ->
					if (isNotEmpty()) append(" &mdash; ")
					append(type)
				}
			},
		)
	}

	private fun JSONArray.toTags(): Set<MangaTag> = buildSet {
		for (index in 0 until length()) {
			val tag = optJSONObject(index) ?: continue
			val name = tag.optString("name").trim().nullIfEmpty() ?: continue
			val key = tag.optString("id").trim().nullIfEmpty() ?: name
			add(MangaTag(name, key, source))
		}
	}

	private fun Post.toManga() = Manga(
		id = generateUid(url),
		title = title,
		altTitles = emptySet(),
		url = url,
		publicUrl = url,
		rating = RATING_UNKNOWN,
		contentRating = ContentRating.SAFE,
		coverUrl = thumbnail,
		tags = tags,
		state = null,
		authors = emptySet(),
		largeCoverUrl = thumbnail,
		description = description,
		source = source,
	)

	private fun postId(url: String): String = requireNotNull(POST_ID_REGEX.find(url)?.groupValues?.get(1)) {
		"Cannot find wallpaper id in $url"
	}

	private data class Post(
		val id: String,
		val url: String,
		val fileUrl: String,
		val thumbnail: String?,
		val title: String,
		val tags: Set<MangaTag>,
		val description: String,
	)

	private companion object {
		const val PAGE_SIZE = 24
		val POST_ID_REGEX = Regex("/w/([a-z0-9]+)", RegexOption.IGNORE_CASE)
	}
}
