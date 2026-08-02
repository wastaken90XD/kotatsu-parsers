package org.koitharu.kotatsu.parsers.site.booru.danbooru.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.danbooru.DanbooruParser

@MangaSourceParser("TESTBOORU", "Testbooru", type = ContentType.BOORU)
internal class Testbooru(context: MangaLoaderContext) :
	DanbooruParser(context, MangaParserSource.TESTBOORU, "testbooru.donmai.us")
