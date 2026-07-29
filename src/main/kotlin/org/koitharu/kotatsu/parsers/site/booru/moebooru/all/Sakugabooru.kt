package org.koitharu.kotatsu.parsers.site.booru.moebooru.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.moebooru.MoebooruParser

@MangaSourceParser("SAKUGABOORU", "Sakugabooru", type = ContentType.IMAGE_SET)
internal class Sakugabooru(context: MangaLoaderContext) :
	MoebooruParser(context, MangaParserSource.SAKUGABOORU, "sakugabooru.com")
