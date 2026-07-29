package org.koitharu.kotatsu.parsers.site.booru.danbooru.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.danbooru.DanbooruParser

@MangaSourceParser("ATFBOORU", "ATFBooru", type = ContentType.BOORU)
internal class AtfBooru(context: MangaLoaderContext) :
	DanbooruParser(context, MangaParserSource.ATFBOORU, "booru.allthefallen.moe")
