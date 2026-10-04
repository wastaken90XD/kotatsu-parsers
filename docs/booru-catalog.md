# Booru catalog

This catalog records only evidence collected on this branch. A failed fetch is inconclusive unless
its response itself says that the site or booru does not exist.

| Classification | FETCHED | DOCUMENTED | ASSUMED |
|---|---|---|---|
| Meaning | Live response or local command output collected on this branch | Source code, task brief, or user-supplied contract | Neither fetched nor documented |

## Added sources

| Site | Tier | Engine | Base | Quirks | Verdict | Evidence basis | Response excerpt (under 2 KB) |
|---|---|---|---|---|---|---|---|
| Manebooru | A | Philomena v1 | `PhilomenaParser` | Q7, Q8, Q22 | ADDED; device media check pending | FETCHED: homepage, API pages 1 and 2, two-tag search, empty search, detail, rating search, last and past-last pages. DOCUMENTED: user stated the existing single-direct-URL video contract applies to comparable boorus. | `{"images":[{"id":4049776,"mime_type":"video/webm","representations":{"full":"…/4049776.webm","medium":"…/4049776/medium.webm"}}],"total":1002688}` |
| Ponerpics | A | Philomena v1 | `PhilomenaParser` | Q7, Q8, Q22 | ADDED; device media check pending | FETCHED: homepage, API pages 1 and 2, two-tag search, empty search, detail, rating search, last and past-last pages. DOCUMENTED: user stated the existing single-direct-URL video contract applies to comparable boorus. | `{"images":[{"id":21,"mime_type":"image/gif","representations":{"mp4":"…/21/full.mp4","webm":"…/21/full.webm"}}],"total":2965342}` |

The sandbox fetcher can only issue GET requests. Its GET attempts for a Manebooru WebM and a
Ponerpics thumbnail returned HTTP 500, so they are not treated as media availability evidence.
The parser uses each API record's direct medium rendition for `video/*` records, as requested;
the current `MangaPage` contract carries one direct URL and no alternate-rendition collection.

## Repaired or held sources

| Site | Tier | Engine | Base | Quirks | Verdict | Evidence basis | Response excerpt (under 2 KB) |
|---|---|---|---|---|---|---|---|
| MoeBooru | — | Gelbooru 0.1 network endpoint | `MoebooruParser` registration | Q11 | BROKEN | FETCHED: the endpoint rendered a nonexistence page. | `# Booru moe does not exist` |
| 8booru | — | Gelbooru 0.1 network endpoint | `GelbooruParser` registration | Q11 | BROKEN | FETCHED: the endpoint rendered a nonexistence page. | `# Booru 8booru does not exist` |
| Behoimi | — | Moebooru (task brief) | `MoebooruParser` | Q19 | BLOCKED; no code change | FETCHED: homepage fetch returned HTTP 500. DOCUMENTED: the task brief described a parking-range lead. | `Failed to fetch page (HTTP 500)` |
| Lolibooru | — | Moebooru (task brief) | `MoebooruParser` | Q19 | BLOCKED; no code change | FETCHED: homepage fetch returned HTTP 500. | `Failed to fetch page (HTTP 500)` |
| Zerochan | B pending device check | Independent JSON | `Zerochan` | Q5, Q6, Q7, Q9 | CONFIGURED; device check pending | FETCHED: `/api` returned the crawler block and says to use a custom User-Agent. DOCUMENTED: a public Kotlin client specifies `application name - account username`. | `Crawlers are not permitted … If you want to use the API, be sure to set a custom user agent` |
| Tantabus | — | Chevereto | — | — | REJECTED as a Philomena candidate | FETCHED: homepage identifies itself as Chevereto image hosting, not the expected engine. | `Chevereto image hosting — Upload and share your media` |
| Memebooru | — | Unknown | — | — | BLOCKED | FETCHED: Cloudflare returned a 522 origin timeout. | `522: Connection timed out … Host Error` |
| Rainbooru | — | Unknown | — | — | BLOCKED | FETCHED: `rainbooru.org` returned a Cloudflare 522; `rainbooru.art` fetch failed without a response. | `522: Connection timed out … Host Error` |

## Existing sources not reassessed on this branch

The following registrations remain in the tree. Their engine and base are documented by the
source hierarchy; no live response was collected for them on this branch, so no current status is
claimed here.

| Site | Tier | Engine | Base | Quirks | Verdict | Evidence basis | Response excerpt (under 2 KB) |
|---|---|---|---|---|---|---|---|
| AIBooru | not reassessed | Danbooru | `DanbooruParser` | — | Existing; unchanged | DOCUMENTED: source hierarchy | No response fetched on this branch. |
| ATFBooru | not reassessed | Danbooru | `DanbooruParser` | Q13 | Existing; unchanged | DOCUMENTED: source hierarchy and task brief | No response fetched on this branch. |
| Betabooru | not reassessed | Danbooru | `DanbooruParser` | Q15 | Existing; unchanged | DOCUMENTED: source hierarchy | No response fetched on this branch. |
| Danbooru | not reassessed | Danbooru | `DanbooruParser` | Q17 | Existing; unchanged | DOCUMENTED: source hierarchy | No response fetched on this branch. |
| E621 | not reassessed | e621ng | `E621ngParser` | Q5, Q6, Q7 | Existing; unchanged | DOCUMENTED: source hierarchy | No response fetched on this branch. |
| E6AI | not reassessed | e621ng | `E621ngParser` | Q5, Q6, Q7 | Existing; unchanged | DOCUMENTED: source hierarchy | No response fetched on this branch. |
| E926 | not reassessed | e621ng | `E621ngParser` | Q5, Q6, Q7 | Existing; unchanged | DOCUMENTED: source hierarchy | No response fetched on this branch. |
| Safebooru (Danbooru) | not reassessed | Danbooru | `DanbooruParser` | Q15 | Existing; unchanged | DOCUMENTED: source hierarchy | No response fetched on this branch. |
| Testbooru | not reassessed | Danbooru | `DanbooruParser` | Q15 | Existing; unchanged | DOCUMENTED: source hierarchy | No response fetched on this branch. |
| Gelbooru | not reassessed | Gelbooru | `GelbooruParser` | Q1, Q6, Q18 | Existing; unchanged | DOCUMENTED: source hierarchy | No response fetched on this branch. |
| HypnoHub | not reassessed | Gelbooru | `GelbooruParser` | Q18 | Existing; unchanged | DOCUMENTED: source hierarchy | No response fetched on this branch. |
| Realbooru | not reassessed | Gelbooru | `GelbooruParser` | Q4, Q18 | Existing; unchanged | DOCUMENTED: source hierarchy | No response fetched on this branch. |
| Rule34.xxx | not reassessed | Gelbooru | `GelbooruParser` | Q1, Q2, Q4 | Existing; unchanged | DOCUMENTED: source hierarchy | No response fetched on this branch. |
| Safebooru | not reassessed | Gelbooru | `GelbooruParser` | Q18 | Existing; unchanged | DOCUMENTED: source hierarchy | No response fetched on this branch. |
| TBIB | not reassessed | Gelbooru | `GelbooruParser` | Q18 | Existing; unchanged | DOCUMENTED: source hierarchy | No response fetched on this branch. |
| XBooru | not reassessed | Gelbooru | `GelbooruParser` | Q18 | Existing; unchanged | DOCUMENTED: source hierarchy | No response fetched on this branch. |
| Konachan | not reassessed | Moebooru | `MoebooruParser` | — | Existing; unchanged | DOCUMENTED: source hierarchy | No response fetched on this branch. |
| Konachan.net | not reassessed | Moebooru | `MoebooruParser` | — | Existing; unchanged | DOCUMENTED: source hierarchy | No response fetched on this branch. |
| Sakugabooru | not reassessed | Moebooru | `MoebooruParser` | Q14, Q22 | Existing; unchanged | DOCUMENTED: source hierarchy | No response fetched on this branch. |
| Yande.re | not reassessed | Moebooru | `MoebooruParser` | — | Existing; unchanged | DOCUMENTED: source hierarchy | No response fetched on this branch. |
| Derpibooru | not reassessed | Philomena v1 | `PhilomenaParser` | Q8 | Existing; unchanged | DOCUMENTED: source hierarchy | No response fetched on this branch. |
| Furbooru | not reassessed | Philomena v1 | `PhilomenaParser` | Q8 | Existing; unchanged | DOCUMENTED: source hierarchy | No response fetched on this branch. |
| Ponybooru | not reassessed | Philomena v1 | `PhilomenaParser` | Q8 | Existing; unchanged | DOCUMENTED: source hierarchy | No response fetched on this branch. |
| Twibooru | not reassessed | Philomena v3 | `PhilomenaParser` | Q8 | Existing; unchanged | DOCUMENTED: source hierarchy | No response fetched on this branch. |
| Baraag | not reassessed | Mastodon | independent | Q1 | Existing; unchanged | DOCUMENTED: source hierarchy | No response fetched on this branch. |
