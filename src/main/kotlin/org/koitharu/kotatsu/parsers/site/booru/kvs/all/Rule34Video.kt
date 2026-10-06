package org.koitharu.kotatsu.parsers.site.booru.kvs.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.kvs.KvsVideoParser

@MangaSourceParser("RULE34VIDEO", "Rule34Video", type = ContentType.BOORU)
internal class Rule34Video(context: MangaLoaderContext) :
	KvsVideoParser(context, MangaParserSource.RULE34VIDEO, "rule34video.com")
