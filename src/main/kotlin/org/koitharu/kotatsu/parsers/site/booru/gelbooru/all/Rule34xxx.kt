package org.koitharu.kotatsu.parsers.site.booru.gelbooru.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.gelbooru.GelbooruParser

@MangaSourceParser("RULE34XXX", "Rule34.xxx", type = ContentType.BOORU)
internal class Rule34xxx(context: MangaLoaderContext) :
	GelbooruParser(context, MangaParserSource.RULE34XXX, "rule34.xxx")
