package org.koitharu.kotatsu.parsers.site.booru.philomena.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.philomena.PhilomenaParser

@MangaSourceParser("PONERPICS", "Ponerpics", "en", ContentType.BOORU)
internal class Ponerpics(context: MangaLoaderContext) :
	PhilomenaParser(context, MangaParserSource.PONERPICS, "ponerpics.org") {
	override val includeVideoDuration: Boolean = true
}
