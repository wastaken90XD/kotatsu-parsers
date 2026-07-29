package org.koitharu.kotatsu.parsers.site.booru.gelbooru.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.gelbooru.GelbooruParser

@MangaSourceParser("GELBOORU", "Gelbooru", type = ContentType.BOORU)
internal class Gelbooru(context: MangaLoaderContext) :
	GelbooruParser(context, MangaParserSource.GELBOORU, "gelbooru.com")
