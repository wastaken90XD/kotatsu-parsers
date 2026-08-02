package org.koitharu.kotatsu.parsers.site.booru.philomena.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.philomena.PhilomenaParser

@MangaSourceParser("DERPIBOORU", "Derpibooru", "en", ContentType.BOORU)
internal class Derpibooru(context: MangaLoaderContext) :
	PhilomenaParser(context, MangaParserSource.DERPIBOORU, "derpibooru.org") {
	// Default "Everything" filter id is 56027; leave null to respect the user's account default.
}
