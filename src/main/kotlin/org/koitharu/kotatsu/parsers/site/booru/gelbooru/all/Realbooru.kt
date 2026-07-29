package org.koitharu.kotatsu.parsers.site.booru.gelbooru.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.gelbooru.GelbooruParser

@MangaSourceParser("REALBOORU", "Realbooru", type = ContentType.BOORU)
internal class Realbooru(context: MangaLoaderContext) :
	GelbooruParser(
		context = context,
		source = MangaParserSource.REALBOORU,
		domain = "realbooru.com",
		useJson = true,
	)
