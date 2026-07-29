package org.koitharu.kotatsu.parsers.site.booru.moebooru.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.moebooru.MoebooruParser

@MangaSourceParser("LOLIBOORU", "Lolibooru", type = ContentType.BOORU)
internal class Lolibooru(context: MangaLoaderContext) :
	MoebooruParser(context, MangaParserSource.LOLIBOORU, "lolibooru.moe")
