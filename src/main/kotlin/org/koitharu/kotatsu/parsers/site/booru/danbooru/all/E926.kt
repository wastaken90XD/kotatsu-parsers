package org.koitharu.kotatsu.parsers.site.booru.danbooru.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.danbooru.E621ngParser
import java.util.EnumSet

// The safe-for-work mirror of e621; it only ever serves posts rated "safe".
@MangaSourceParser("E926", "e926", type = ContentType.BOORU)
internal class E926(context: MangaLoaderContext) :
	E621ngParser(context, MangaParserSource.E926, "e926.net") {

	override val supportedRatings: Set<ContentRating> = EnumSet.of(ContentRating.SAFE)
}
