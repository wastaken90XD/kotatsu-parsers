package org.koitharu.kotatsu.parsers.site.booru.danbooru.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.danbooru.E621ngParser

@MangaSourceParser("E621", "e621", type = ContentType.BOORU)
internal class E621(context: MangaLoaderContext) :
	E621ngParser(context, MangaParserSource.E621, "e621.net")
