package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken("Domain hijacked/replaced with an OnlyFans directory (sarcasmscans.com compromised)")
@MangaSourceParser("SARCASMSCANS", "SarcasmScans", "tr", ContentType.HENTAI)
internal class SarcasmScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.SARCASMSCANS, "sarcasmscans.com", 16)
