package org.koitharu.kotatsu.parsers.exception

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koitharu.kotatsu.parsers.model.MangaParserSource

internal class CrashReportTest {

	@Test
	fun crashReportFormattingTest() {
		val cause = IllegalArgumentException("Invalid input")
		val exception = ParseException("Failed to parse", "https://example.com/test", cause)
		val report = exception.toCrashReport(MangaParserSource.DANBOORU, "https://example.com/test")

		assertTrue(report.contains("=== KOTATSU PARSERS CRASH REPORT ==="))
		assertTrue(report.contains("DANBOORU"))
		assertTrue(report.contains("https://example.com/test"))
		assertTrue(report.contains("ParseException"))
		assertTrue(report.contains("Failed to parse"))
		assertTrue(report.contains("IllegalArgumentException: Invalid input"))
		assertTrue(report.contains("--- STACK TRACE ---"))
	}
}
