package org.koitharu.kotatsu.parsers.site.booru.philomena.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.philomena.PhilomenaParser

@MangaSourceParser("MANEBOORU", "Manebooru", "en", ContentType.BOORU)
internal class Manebooru(context: MangaLoaderContext) :
	PhilomenaParser(context, MangaParserSource.MANEBOORU, "manebooru.art") {
	override val includeVideoDuration: Boolean = true
}
