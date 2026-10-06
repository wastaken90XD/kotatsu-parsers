package org.koitharu.kotatsu.parsers.site.booru.shimmie.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.shimmie.ShimmieParser

@MangaSourceParser("RULE34HENTAI", "Rule34Hentai", type = ContentType.BOORU)
internal class Rule34Hentai(context: MangaLoaderContext) :
	ShimmieParser(context, MangaParserSource.RULE34HENTAI, "rule34hentai.net")
