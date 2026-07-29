package org.koitharu.kotatsu.parsers.site.booru.danbooru.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.danbooru.DanbooruParser
import java.util.EnumSet

// Danbooru's own safe-for-work mirror; unrelated to safebooru.org, which is a Gelbooru fork.
@MangaSourceParser("SAFEBOORU_DONMAI", "Safebooru (Danbooru)", type = ContentType.IMAGE_SET)
internal class SafebooruDonmai(context: MangaLoaderContext) :
	DanbooruParser(context, MangaParserSource.SAFEBOORU_DONMAI, "safebooru.donmai.us") {

	override val supportedRatings: Set<ContentRating> = EnumSet.of(ContentRating.SAFE)
}
