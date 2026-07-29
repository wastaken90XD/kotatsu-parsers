package org.koitharu.kotatsu.parsers.site.booru.moebooru.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.moebooru.MoebooruParser

@MangaSourceParser("YANDERE", "Yande.re", type = ContentType.BOORU)
internal class YandeRe(context: MangaLoaderContext) :
	MoebooruParser(context, MangaParserSource.YANDERE, "yande.re")
