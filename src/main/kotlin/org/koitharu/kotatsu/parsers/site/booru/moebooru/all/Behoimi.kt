package org.koitharu.kotatsu.parsers.site.booru.moebooru.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.moebooru.MoebooruParser

@MangaSourceParser("BEHOIMI", "3dbooru", type = ContentType.HENTAI)
internal class Behoimi(context: MangaLoaderContext) :
	MoebooruParser(context, MangaParserSource.BEHOIMI, "behoimi.org")
