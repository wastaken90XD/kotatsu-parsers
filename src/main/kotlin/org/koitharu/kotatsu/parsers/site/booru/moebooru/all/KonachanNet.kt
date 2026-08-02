package org.koitharu.kotatsu.parsers.site.booru.moebooru.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.moebooru.MoebooruParser
import java.util.EnumSet

@MangaSourceParser("KONACHAN_NET", "Konachan.net", type = ContentType.BOORU)
internal class KonachanNet(context: MangaLoaderContext) :
	MoebooruParser(context, MangaParserSource.KONACHAN_NET, "konachan.net") {

	override val supportedRatings: Set<ContentRating> = EnumSet.of(ContentRating.SAFE)
}
