package org.koitharu.kotatsu.parsers.site.booru.danbooru.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.danbooru.DanbooruParser

@MangaSourceParser("DANBOORU", "Danbooru", type = ContentType.BOORU)
internal class Danbooru(context: MangaLoaderContext) :
	DanbooruParser(context, MangaParserSource.DANBOORU, "danbooru.donmai.us") {

	// Documented quirk: upstream rejects searches with more than two tags for anonymous users,
	// while signed in accounts are allowed more. Forks generally drop this limit.
	override val maxTagsPerSearch: Int
		get() = if (hasAuthCookies()) Int.MAX_VALUE else 2
}
