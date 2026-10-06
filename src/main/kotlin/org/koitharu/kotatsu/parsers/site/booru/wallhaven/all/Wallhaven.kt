package org.koitharu.kotatsu.parsers.site.booru.wallhaven.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.wallhaven.WallhavenParser

@MangaSourceParser("WALLHAVEN", "Wallhaven", type = ContentType.BOORU)
internal class Wallhaven(context: MangaLoaderContext) :
	WallhavenParser(context, MangaParserSource.WALLHAVEN, "wallhaven.cc")
