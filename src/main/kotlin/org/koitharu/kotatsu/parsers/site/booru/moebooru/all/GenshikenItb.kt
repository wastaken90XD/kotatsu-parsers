package org.koitharu.kotatsu.parsers.site.booru.moebooru.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.moebooru.MoebooruParser

@MangaSourceParser("GENSHIKEN_ITB", "Genboard", type = ContentType.BOORU)
internal class GenshikenItb(context: MangaLoaderContext) :
	MoebooruParser(context, MangaParserSource.GENSHIKEN_ITB, "img.genshiken-itb.org")
