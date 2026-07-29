package org.koitharu.kotatsu.parsers.exception

import org.koitharu.kotatsu.parsers.model.MangaParserSource

/**
 * Generates a detailed crash report string for an exception that occurred during parsing.
 * Can be used by the UI to allow users to copy/share the stack trace instead of just seeing a generic message.
 */
public fun Throwable.toCrashReport(
	source: MangaParserSource? = null,
	url: String? = null,
): String = buildString {
	appendLine("=== KOTATSU PARSERS CRASH REPORT ===")
	if (source != null) {
		appendLine("Source: ${source.name} (${source.title})")
	}
	val effectiveUrl = url ?: (this@toCrashReport as? ParseException)?.url
	if (effectiveUrl != null) {
		appendLine("URL: $effectiveUrl")
	}
	appendLine("Exception: ${this@toCrashReport::class.java.name}")
	appendLine("Message: ${message ?: "No message"}")
	val rootCause = generateSequence(this@toCrashReport) { it.cause }.last()
	if (rootCause !== this@toCrashReport) {
		appendLine("Root cause: ${rootCause::class.java.name}: ${rootCause.message}")
	}
	appendLine("--- STACK TRACE ---")
	append(stackTraceToString())
}

public fun Throwable.getCrashReport(
	source: MangaParserSource? = null,
	url: String? = null,
): String = toCrashReport(source, url)
