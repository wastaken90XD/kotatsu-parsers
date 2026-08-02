package org.koitharu.kotatsu.parsers.site.booru.philomena.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.philomena.PhilomenaParser

@MangaSourceParser("PONYBOORU", "Ponybooru", "en", ContentType.BOORU)
internal class Ponybooru(context: MangaLoaderContext) :
	PhilomenaParser(context, MangaParserSource.PONYBOORU, "ponybooru.org") {
	override val cdnDomain = "cdn.ponybooru.org"
}
