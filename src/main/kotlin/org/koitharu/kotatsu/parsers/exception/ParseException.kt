package org.koitharu.kotatsu.parsers.exception

import org.koitharu.kotatsu.parsers.InternalParsersApi

public class ParseException @InternalParsersApi @JvmOverloads constructor(
	public val shortMessage: String?,
	public val url: String,
	cause: Throwable? = null,
) : RuntimeException(
	buildString {
		append(shortMessage)
		append(" at ")
		append(url)
		if (cause != null) {
			append(" (")
			append(cause::class.java.simpleName)
			if (cause.message != null) {
				append(": ")
				append(cause.message)
			}
			append(")")
		}
	},
	cause,
)
