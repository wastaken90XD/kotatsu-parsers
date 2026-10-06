package org.koitharu.kotatsu.parsers.site.booru.wordpress.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.wordpress.WordpressVideoParser

@MangaSourceParser("SFMCOMPILE", "SFM Compile", type = ContentType.BOORU)
internal class SfmCompile(context: MangaLoaderContext) :
	WordpressVideoParser(context, MangaParserSource.SFMCOMPILE, "sfmcompile.club")
