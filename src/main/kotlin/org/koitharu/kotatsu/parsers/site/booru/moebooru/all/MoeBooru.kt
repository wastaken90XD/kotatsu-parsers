package org.koitharu.kotatsu.parsers.site.booru.moebooru.all

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.moebooru.MoebooruParser

@Broken("The site states that this booru does not exist")
@MangaSourceParser("MOEBOORU", "MoeBooru", type = ContentType.BOORU)
internal class MoeBooru(context: MangaLoaderContext) :
	MoebooruParser(context, MangaParserSource.MOEBOORU, "moe.booru.org")
