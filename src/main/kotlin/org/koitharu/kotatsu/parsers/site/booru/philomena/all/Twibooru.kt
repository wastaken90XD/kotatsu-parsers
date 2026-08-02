package org.koitharu.kotatsu.parsers.site.booru.philomena.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.booru.philomena.PhilomenaParser

@MangaSourceParser("TWIBOORU", "Twibooru", "en", ContentType.BOORU)
internal class Twibooru(context: MangaLoaderContext) :
	PhilomenaParser(context, MangaParserSource.TWIBOORU, "twibooru.org") {
	override val apiVersion = 3
	override val searchResource = "posts"
	override val singlePostResource = "posts"
	override val postsArrayKey = "posts"
	override val postObjectKey = "post"
	override val postViewPath = "/"
	override val cdnDomain = "cdn.twibooru.org"
}
