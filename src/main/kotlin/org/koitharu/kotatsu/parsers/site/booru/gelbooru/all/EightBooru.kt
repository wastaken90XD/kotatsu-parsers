package org.koitharu.kotatsu.parsers.site.booru.gelbooru.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.gelbooru.GelbooruParser

@MangaSourceParser("EIGHTBOORU", "8booru", type = ContentType.BOORU)
internal class EightBooru(context: MangaLoaderContext) :
	GelbooruParser(
		context = context,
		source = MangaParserSource.EIGHTBOORU,
		domain = "8booru.booru.org",
		useJson = false,
	)
