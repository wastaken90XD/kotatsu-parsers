package org.koitharu.kotatsu.parsers.site.booru.gelbooru.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.gelbooru.GelbooruParser
import java.util.EnumSet

@MangaSourceParser("SAFEBOORU", "Safebooru", type = ContentType.BOORU)
internal class Safebooru(context: MangaLoaderContext) :
	GelbooruParser(context, MangaParserSource.SAFEBOORU, "safebooru.org") {

	// The site only ever hosts safe posts, so offering the other ratings would return nothing.
	override val supportedRatings: Set<ContentRating> = EnumSet.of(ContentRating.SAFE)
}
