package org.koitharu.kotatsu.parsers.site.booru.gelbooru.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.gelbooru.GelbooruParser

@MangaSourceParser("HYPNOHUB", "HypnoHub", type = ContentType.HENTAI)
internal class HypnoHub(context: MangaLoaderContext) :
	GelbooruParser(context, MangaParserSource.HYPNOHUB, "hypnohub.net")
