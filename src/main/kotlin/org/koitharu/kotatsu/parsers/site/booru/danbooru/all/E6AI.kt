package org.koitharu.kotatsu.parsers.site.booru.danbooru.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.danbooru.E621ngParser

@MangaSourceParser("E6AI", "E6AI", type = ContentType.BOORU)
internal class E6AI(context: MangaLoaderContext) :
	E621ngParser(context, MangaParserSource.E6AI, "e6ai.net")
