package org.koitharu.kotatsu.parsers.site.booru.philomena.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.philomena.PhilomenaParser

@MangaSourceParser("FURBOORU", "Furbooru", "en", ContentType.BOORU)
internal class Furbooru(context: MangaLoaderContext) :
	PhilomenaParser(context, MangaParserSource.FURBOORU, "furbooru.org") {
	override val cdnDomain = "furrycdn.org"
}
