# Booru catalog

This catalog records only evidence collected on this branch. A failed fetch is inconclusive unless
its response itself says that the site or booru does not exist. A later 20-path no-authentication
public-gateway/proxy sweep produced target content only through a read-only gateway for the Pbooru
and Yukkuri conclusions below; the other gateway errors and fifteen open-proxy connection resets
are not treated as target-site evidence.

| Classification | FETCHED | DOCUMENTED | ASSUMED |
|---|---|---|---|
| Meaning | Live response or local command output collected on this branch | Source code, task brief, or user-supplied contract | Neither fetched nor documented |

## Added sources

| Site | Tier | Engine | Base | Quirks | Verdict | Evidence basis | Response excerpt (under 2 KB) |
|---|---|---|---|---|---|---|---|
| Manebooru | A | Philomena v1 | `PhilomenaParser` | Q7, Q8, Q22 | ADDED; device media check pending | FETCHED: homepage, API pages 1 and 2, two-tag search, empty search, detail, rating search, last and past-last pages. DOCUMENTED: user stated the existing single-direct-URL video contract applies to comparable boorus. | `{"images":[{"id":4049776,"mime_type":"video/webm","representations":{"full":"…/4049776.webm","medium":"…/4049776/medium.webm"}}],"total":1002688}` |
| Ponerpics | A | Philomena v1 | `PhilomenaParser` | Q7, Q8, Q22 | ADDED; device media check pending | FETCHED: homepage, API pages 1 and 2, two-tag search, empty search, detail, rating search, last and past-last pages. DOCUMENTED: user stated the existing single-direct-URL video contract applies to comparable boorus. | `{"images":[{"id":21,"mime_type":"image/gif","representations":{"mp4":"…/21/full.mp4","webm":"…/21/full.webm"}}],"total":2965342}` |
| Genboard | A | Moebooru 6.0.0 | `MoebooruParser` | Q7 | ADDED | FETCHED: homepage, API pages 1 and 2, two-tag search, empty search, detail, safe-rating search, last and past-last pages. | `[{"id":385,"tags":"big_hero_6_help_brain_surgery","file_url":"/data/image/84/56/…jpg","rating":"s"}]` |
| SFM Compile | A | WordPress/Bimber | `WordpressVideoParser` | Q20, Q28 | ADDED; device media check pending | FETCHED: listing pages 1 and 2, a search, and an empty search. Listing HTML exposes same-host direct MP4 URLs; the WordPress REST content did not carry a direct media URL. | `https://sfmcompile.club/wp-content/uploads/2026/10/Peter-mating-with-Emma-Frost.mp4` |
| Rule34Hentai | A | Shimmie2 | `ShimmieParser` | Q10, Q13 | ADDED; device media check pending | FETCHED: listing pages 1 and 2, `Animated+3D` two-tag search, empty and far-terminal pages, static and MP4 detail pages, plus GET responses whose file signatures identify the constructed JPG and MP4 original URLs. Detail markup exposes the static original; the video player source is not present in the fetcher’s rendered extraction. | `https://rule34hentai.net/_images/b2df9d20a07f5efd2f2dd9ab7b83b54f/711051%20-%20Animated%20King_of_Fighters%20Shermie%20chairfucker69.mp4` |

The sandbox fetcher can only issue GET requests. Its GET attempts for a Manebooru WebM and a
Ponerpics thumbnail returned HTTP 500, so they are not treated as media availability evidence.

Philomena `animated` records and `webm`/`mp4` records now expose `full`, `large`, `medium`,
`small`, and `tall` direct representations as rank-ordered chapters. Each chapter carries the
canonical post path with an opaque `rep` query value; resolving it emits one direct `MangaPage`.
The original/full rendition is chapter zero, which preserves the existing resolver default. Poster
representations (`thumb`, `thumb_small`, `thumb_tiny`) are excluded.

## Round 2 thumbnail audit

The rows below are source-side evidence gathered on 2026-10-05. `FETCHED` means a listing, API
object, detail image, or both were fetched through the available read-only fetcher; it does **not**
mean the Android image request or decoder was observed. Every `UNPROVEN` row still needs the
corresponding device evidence described after the table.

| Source / item | Exact emitted cover URL | Host | Source field or attribute | Format | Source-side status | Android status |
|---|---|---|---|---|---|---|
| Manebooru 4049780 | `https://static.manebooru.art/img/2026/10/4/4049780/thumb.jpg` | `static.manebooru.art` | API `representations.thumb` | JPEG | FETCHED: API object and image URL | UNPROVEN |
| Manebooru 4049779 | `https://static.manebooru.art/img/2026/10/4/4049779/thumb.jpg` | `static.manebooru.art` | API `representations.thumb` | JPEG | FETCHED: API object | UNPROVEN |
| Manebooru 4049778 | `https://static.manebooru.art/img/2026/10/4/4049778/thumb.jpg` | `static.manebooru.art` | API `representations.thumb` | JPEG | FETCHED: API object | UNPROVEN |
| Ponerpics 7598623 | `https://ponerpics.org/img/2026/10/4/7598623/thumb.jpg` | `ponerpics.org` | API relative `representations.thumb`, resolved by `PhilomenaParser` | JPEG | FETCHED: API object; read-only image gateway returned site verification instead of the asset | UNPROVEN |
| Ponerpics 7598622 | `https://ponerpics.org/img/2026/10/4/7598622/thumb.jpg` | `ponerpics.org` | API relative `representations.thumb`, resolved by `PhilomenaParser` | JPEG | FETCHED: API object | UNPROVEN |
| Ponerpics 7598620 | `https://ponerpics.org/img/2026/10/4/7598620/thumb.jpg` | `ponerpics.org` | API relative `representations.thumb`, resolved by `PhilomenaParser` | JPEG | FETCHED: API object | UNPROVEN |
| Genboard 385 | `https://img.genshiken-itb.org/data/preview/84/56/8456ec6c172e61828d5091e94e384ad4.jpg` | `img.genshiken-itb.org` | API relative `preview_url`, resolved by `BooruParser` | JPEG | FETCHED: API object and preview URL | UNPROVEN |
| Genboard 384 | `https://img.genshiken-itb.org/data/preview/04/eb/04eb2f8d8803e89f263170e6be86b92c.jpg` | `img.genshiken-itb.org` | API relative `preview_url`, resolved by `BooruParser` | JPEG | FETCHED: API object | UNPROVEN |
| Genboard 383 | `https://img.genshiken-itb.org/data/preview/87/ad/87ad0adf3e2bc71d50c6ed0dbc30ef85.jpg` | `img.genshiken-itb.org` | API relative `preview_url`, resolved by `BooruParser` | JPEG | FETCHED: API object | UNPROVEN |
| SFM Compile Jack-o Valentine bulge ride | `https://sfmcompile.club/wp-content/uploads/2026/10/Jack-o-Valentine-bulge-ride.jpg` | `sfmcompile.club` | WordPress REST `yoast_head_json.og_image[0].url`; same-stem listing MP4 fallback | JPEG | FETCHED: post API, attachment API, and image URL | UNPROVEN |
| SFM Compile Aunt Cass giving a paizuri pov | `https://sfmcompile.club/wp-content/uploads/2026/10/Aunt-Cass-giving-a-paizuri-pov.jpg` | `sfmcompile.club` | WordPress REST `yoast_head_json.og_image[0].url`; same-stem listing MP4 fallback | JPEG | FETCHED: post API and image URL | UNPROVEN |
| SFM Compile Grace Crowne top down bottom up and creampie | `https://sfmcompile.club/wp-content/uploads/2026/10/Grace-Crowne-top-down-bottom-up-and-creampie.jpg` | `sfmcompile.club` | WordPress REST `yoast_head_json.og_image[0].url`; same-stem listing MP4 fallback | JPEG | FETCHED: post API and image URL | UNPROVEN |
| Rule34Video 4644590 | `https://rule34video.com/contents/videos_screenshots/4644000/4644590/preview_preview.mp4.jpg` | `rule34video.com` | Detail `img[src*=/contents/videos_screenshots/]`; list URL uses the fetched KVS screenshot layout | JPEG | FETCHED: detail and image URL; listing `img[src]` was a `data:` placeholder in extraction | UNPROVEN |
| Rule34Video 4644566 | `https://rule34video.com/contents/videos_screenshots/4644000/4644566/preview_preview.mp4.jpg` | `rule34video.com` | Detail `img[src*=/contents/videos_screenshots/]`; list URL uses the fetched KVS screenshot layout | JPEG | FETCHED: detail and image URL | UNPROVEN |
| Rule34Video 4644466 | `https://rule34video.com/contents/videos_screenshots/4644000/4644466/preview_preview.mp4.jpg` | `rule34video.com` | Detail `img[src*=/contents/videos_screenshots/]`; list URL uses the fetched KVS screenshot layout | JPEG | FETCHED: detail and image URL | UNPROVEN |
| Rule34Hentai 711143 | `https://rule34hentai.net/_thumbs/af53707bd5888c09546b76bab57e0299/thumb.jpg` | `rule34hentai.net` | Listing post-anchor `img[src]` | JPEG | FETCHED: listing and image URL | UNPROVEN |
| Rule34Hentai 711141 | `https://rule34hentai.net/_thumbs/9bcd2e29cd9886824b10cc4848a87a01/thumb.jpg` | `rule34hentai.net` | Listing post-anchor `img[src]` | JPEG | FETCHED: listing and image URL | UNPROVEN |
| Rule34Hentai 711139 | `https://rule34hentai.net/_thumbs/63021e5944ef2b429158cb40f46bcfcf/thumb.jpg` | `rule34hentai.net` | Listing post-anchor `img[src]` | JPEG | FETCHED: listing and image URL | UNPROVEN |
| Wallhaven rqewvj | `https://th.wallhaven.cc/lg/rq/rqewvj.jpg` | `th.wallhaven.cc` | API `thumbs.large` | JPEG | FETCHED: listing API and image URL | UNPROVEN |
| Wallhaven qrp5wr | `https://th.wallhaven.cc/lg/qr/qrp5wr.jpg` | `th.wallhaven.cc` | API `thumbs.large` | JPEG | FETCHED: listing API and image URL | UNPROVEN |
| Wallhaven k8jrm1 | `https://th.wallhaven.cc/lg/k8/k8jrm1.jpg` | `th.wallhaven.cc` | API `thumbs.large` | JPEG | FETCHED: listing API and image URL | UNPROVEN |
| Zerochan 4729591 | `https://s3.zerochan.net/240/41/41/4729591.jpg` | `s3.zerochan.net` | API `thumbnail` is documented by the parser; current public listing gave matching `.avif`, and the matching JPEG was fetched | JPEG | FETCHED: public listing AVIF and matching JPEG URL; API call itself requires configured source username | UNPROVEN |
| Zerochan 4729590 | `https://s3.zerochan.net/240/40/41/4729590.jpg` | `s3.zerochan.net` | API `thumbnail` with AVIF-to-JPEG replacement | JPEG | FETCHED: public listing AVIF and matching JPEG URL | UNPROVEN |
| Zerochan 4729589 | `https://s3.zerochan.net/240/39/41/4729589.jpg` | `s3.zerochan.net` | API `thumbnail` with AVIF-to-JPEG replacement | JPEG | FETCHED: public listing AVIF and matching JPEG URL | UNPROVEN |

`WordpressVideoParser` now ignores `data:` card placeholders and, where a card does not expose an
image, uses the verified same-stem JPEG layout of the source's WordPress featured media.
`KvsVideoParser` now derives the exact KVS screenshot path from the listing post id instead of
returning `null` after the listing's `data:` image placeholder. `Zerochan` now replaces only an
AVIF thumbnail suffix with the matching JPEG suffix; fetched matching JPEGs exist for all three
audited public-listing items. No image downloader, image-loader, TLS, certificate, dependency, or
app-side header code was changed.

The fetched Philomena video records are a separate source limitation: Manebooru `4049776` and
Ponerpics `7598612` expose `representations.thumb` only as WebM, not as a still-image poster. No
JPEG, PNG, or WebP replacement was present in either fetched API object, so no guessed alternate
URL was emitted. That video-cover decoding outcome remains UNPROVEN on Android and is not treated
as a source-verified thumbnail success.

### Header boundary and required device evidence

The parsers repository proves request headers only for its parser HTTP calls, not for the app's
separate cover-image loader. `BooruParser` attaches `Referer: https://<source-domain>/`, JSON
`Accept`, `Accept-Language`, and `Connection`; this applies to Manebooru, Ponerpics, Genboard,
and Rule34Hentai API/listing requests. Zerochan's API calls add its configured `User-Agent`,
`Referer`, JSON `Accept`, `Accept-Language`, and `X-Requested-With`. No image-specific required
header was exposed by the fetched image responses, and this repository contains no application
image-loader implementation. Header propagation to the CDN/image request is therefore UNPROVEN.

For each of the eight hosts above, clear Logcat, open the source's first listing page, and tap the
first three entries. Collect the complete request/response lines for every exact URL in the table:

```text
adb logcat -c
adb logcat -v threadtime | grep -Ei 'OkHttp|Cronet|Coil|Glide|ImageLoader|SSL|Handshake|certificate|manebooru|ponerpics|genshiken-itb|sfmcompile|rule34video|rule34hentai|wallhaven|zerochan'
```

The needed evidence is a line pair showing `GET <exact URL>` and its HTTP status plus content type,
and any emitted `Referer`, `User-Agent`, or error line. In particular, retain any TLS handshake,
certificate-chain, redirect, 401/403/429, CAPTCHA, decode, or unsupported-format error. If the
installed app has no request logger, that absence cannot establish loading or headers; an app-side
instrumentation change would need prior approval. No `BLOCKED-APP` host is established by this
parser-side audit.

## Round 2 duration audit

`Manga` has a nullable `description`; `MangaChapter` has no duration field. No model was changed.
Where a scoped video source supplies duration, the parser renders exactly one leading
`Duration: m:ss` or `Duration: h:mm:ss` description line using rounded whole seconds and
`Locale.ROOT` decimal formatting. It is attached to the manga, not to each quality chapter. No
media was downloaded or measured; a HEAD request would not have been treated as duration evidence.

| Source | Fetched source evidence | Parser result | Status |
|---|---|---|---|
| Manebooru / Philomena | Image `4049776` API: `"mime_type":"video/webm"`, `"duration":5.109` | `Duration: 0:05` | Implemented for Manebooru video records with a finite API duration |
| Ponerpics / Philomena | `q=webm` API image `7598612`: `"mime_type":"video/webm"`, `"duration":240.261` | `Duration: 4:00` | Implemented for Ponerpics video records with a finite API duration; image `21` explicitly returned `"duration":null` and stays without a duration |
| Rule34Video / KVS | Listing/detail for `4644590`, `4644566`, `4644466` display `0:57`, `0:17`, `1:00` | `Duration: 0:57`, `Duration: 0:17`, `Duration: 1:00` | Implemented from the listing-card time token; one manga description is retained when quality chapters are built |
| Rule34Hentai / Shimmie | Listing image alt text supplies `1200x1200, 60.3s`, `1000x1280, 33.6s`, and `960x540, 24s` for `711143`, `711141`, and `711139` | `Duration: 1:00`, `Duration: 0:34`, `Duration: 0:24` | Implemented from the source alt text, rounded to the nearest whole second |
| SFM Compile | Current listing and WordPress post/featured-media responses expose MP4 and JPEG media but no factual video-duration field. `twitter:data2` is an estimated reading time, not media duration. | No duration | Intentionally omitted |
| Wallhaven | Fetched v1 listing/detail objects have image dimensions, file size, and `file_type`, but no duration field; this is an image-only source. | No duration | Intentionally omitted |

A local source-tree search found no parser-side `duration` read in the frozen Danbooru, Gelbooru,
Moebooru, or e621ng families. No frozen source file was changed and no frozen-source live request
was made for this duration work. This is a local-code result, not a claim that every upstream API
never has a duration property.

## Parser follow-up: signed media and cache bounds

| Area | FETCHED | DOCUMENTED | ASSUMED |
|---|---|---|---|
| Rule34Video signed rendition refresh | Earlier fetched Rule34Video detail pages exposed time-signed `/get_file/` MP4 links. | `KvsVideoParser.getPages` now always fetches and parses the canonical post immediately before returning the selected `MangaPage`; no direct `/get_file/` URL is retained in its cache. | Android playback remains UNPROVEN. |
| Rule34Video retained KVS metadata | No new response was fetched for metadata. | The 32-entry LRU stores only the last post title and quality-label/key pairs; a signed URL, screenshot URL, or `MangaPage` is never stored in it. | Cached labels are an implementation detail, not media availability evidence. |
| Philomena cache | No new response was fetched for cache behavior. | The `PostMedia` cache is a 32-entry, access-ordered LRU. It retains API representation URLs, which are not Rule34Video-style KVS signed download URLs. | Device media access remains UNPROVEN. |
| Rule34Hentai cache | No new response was fetched for cache behavior. | Both persistent Shimmie listing-media and duration caches are separate 32-entry, access-ordered LRUs. | Device media access remains UNPROVEN. |
| Wallhaven cache | No new response was fetched for cache behavior. | The persistent Wallhaven post cache is a 32-entry, access-ordered LRU. | Device media access remains UNPROVEN. |
| SFM Compile / WordPress | Source code inspection found no persistent media cache. | Its `LinkedHashMap` is request-local listing de-duplication and is discarded when `getListPage` returns; no cache entry survives between pages. | No device-memory conclusion is made. |

The cache implementation is a synchronized, access-ordered `LinkedHashMap` with `removeEldestEntry` at 32 entries. No dependency, image-loader, TLS, certificate, or app-side change was made.

## Parser follow-up: Philomena animated rendition order

| Area | FETCHED | DOCUMENTED | ASSUMED |
|---|---|---|---|
| Manebooru GIF representations | `GET /api/v1/json/images/4049797` returned `animated:true`, `format:"gif"`, root `width:682`, `height:682`, and string URL keys `full`, `large`, `medium`, `mp4`, `small`, `tall`, `thumb`, `thumb_small`, `thumb_tiny`, and `webm`. The per-representation values are URL strings only; they provide no individual tier width or height. | Animated records with these keys now expose `MP4`, then `WebM`, then `Original`, `Large`, `Tall`, `Medium`, and `Small`. | Android decode and playback are UNPROVEN. |
| Ponerpics GIF representations | `GET /api/v1/json/images/7599204` returned `animated:true`, `format:"gif"`, root `width:1024`, `height:745`, and the same string URL keys, including `mp4` and `webm`; record `21` independently confirms that key set. No individual tier dimensions were in either response. | The same MP4-first/WebM-second ordering applies. Thumbnail tiers remain covers only and are never chapters. | Android decode and playback are UNPROVEN. |
| WebM records | Manebooru `4049776` reported `mime_type:"video/webm"`, root `1280x720`, with `full`, `large`, `medium`, `small`, `tall`, and thumbnail tiers but no separate `mp4`/`webm` representation keys. Ponerpics `7598612` reported the same standard tier set at root `640x480`. | When individual representation dimensions are absent, the required fallback is `full`, `large`, `tall`, `medium`, `small`; because neither fetched WebM object has explicit codec keys, those are its chapters. | The source’s actual transformed-tier pixel dimensions are not available in the fetched JSON. |
| Static records | No static-image contract was changed. | Direct MP4/WebM keys are considered only for `animated:true` records; static image behavior stays on the existing image path. | No claim is made about unobserved API variants. |

No thumb, `thumb_small`, or `thumb_tiny` representation is exposed as a media chapter. The code uses the documented fallback because the fetched API represents tiers as URL strings rather than dimension-bearing objects.

## Repaired or held sources

| Site | Tier | Engine | Base | Quirks | Verdict | Evidence basis | Response excerpt (under 2 KB) |
|---|---|---|---|---|---|---|---|
| Anime-Pictures | C | API v3 plus HTML | — | Android API 21 preview format | HELD; no parser added | FETCHED: API page `0`, HTML page `2`, two-tag and empty HTML searches, detail `931283`, terminal page `8356`, and empty page `8357`. The live listing/detail preview is `opreviews.anime-pictures.net/..._cp.avif`; the source detail exposes a direct original JPEG link. Replacing `_cp.avif` with `.jpg` or `.webp` was not supported by the available read-only fetcher. An Android API 21-compatible thumbnail would require app-side AVIF decode support or a verified source still-image alternative, so Task C stops here. | `{"posts_per_page":80,"page_number":0,"posts":[{"id":931283,"ext":".jpg"}]}`; listing: `https://opreviews.anime-pictures.net/096/096de6229ae2b67d2c21603b877efe60_cp.avif` |
| MoeBooru | — | Gelbooru 0.1 network endpoint | `MoebooruParser` registration | Q11 | BROKEN | FETCHED: the endpoint rendered a nonexistence page. | `# Booru moe does not exist` |
| 8booru | — | Gelbooru 0.1 network endpoint | `GelbooruParser` registration | Q11 | BROKEN | FETCHED: the endpoint rendered a nonexistence page. | `# Booru 8booru does not exist` |
| Behoimi | — | Moebooru (task brief) | `MoebooruParser` | Q19 | BLOCKED; no code change | FETCHED: homepage fetch returned HTTP 500. DOCUMENTED: the task brief described a parking-range lead. | `Failed to fetch page (HTTP 500)` |
| Lolibooru | — | Moebooru (task brief) | `MoebooruParser` | Q19 | BLOCKED; no code change | FETCHED: homepage fetch returned HTTP 500. | `Failed to fetch page (HTTP 500)` |
| Zerochan | B pending device check | Independent JSON | `Zerochan` | Q5, Q6, Q7, Q9 | CONFIGURED; device check pending | FETCHED: `/api` returned the crawler block and says to use a custom User-Agent. DOCUMENTED: a public Kotlin client specifies `application name - account username`. | `Crawlers are not permitted … If you want to use the API, be sure to set a custom user agent` |
| Tantabus | — | Chevereto | — | — | REJECTED as a Philomena candidate | FETCHED: homepage identifies itself as Chevereto image hosting, not the expected engine. | `Chevereto image hosting — Upload and share your media` |
| Memebooru | — | Unknown | — | — | BLOCKED | FETCHED: Cloudflare returned a 522 origin timeout. | `522: Connection timed out … Host Error` |
| Rainbooru | — | Unknown | — | — | BLOCKED | FETCHED: `rainbooru.org` returned a Cloudflare 522; `rainbooru.art` fetch failed without a response. | `522: Connection timed out … Host Error` |
| Evbooru | — | Unknown | — | Q19 | REJECTED | FETCHED: the homepage rendered a parked-domain advertising page rather than a booru. | `resultsfindershub.com … blocked by an extension` |
| Bronibooru | — | phpBB forum after redirect | — | — | REJECTED as a Danbooru candidate | FETCHED: `bronibooru.com` redirected to the Round Stable phpBB forum, which has no booru listing. | `The Round Stable - Index page … Powered by phpBB` |
| Yukkuri | — | Expired domain | — | — | REJECTED as a Danbooru candidate | FETCHED: direct homepage request returned HTTP 500; a read-only public fetch gateway returned the domain’s Namecheap expiration page. | `Domain registration has expired … renew it through your Namecheap account` |
| Dorkbooru | — | Unknown | — | — | BLOCKED; no code change | FETCHED: homepage request returned HTTP 500. | `Failed to fetch page (HTTP 500)` |
| MSPAbooru | — | Gelbooru 0.2 | — | Q18 | BLOCKED; no code change | FETCHED: API list/search/detail/rating/end responses have image path components but omit direct URLs. DOCUMENTED: the shared Gelbooru base is frozen. | `{"directory":"15","hash":"…","image":"…png"}` |
| Pbooru | — | Parked news portal | — | — | REJECTED as a Gelbooru candidate | FETCHED: direct homepage and listing requests returned HTTP 500; a read-only public fetch gateway returned HeadlineLogic news content instead of booru posts. | `Title: HeadlineLogic News Portal` |
| Rule34Video | A | Kernel Video Sharing-style HTML | `KvsVideoParser` | Q20, Q22 | ADDED; device media check pending | FETCHED: newest pages 1 and 2, two-word search, empty search, terminal and past-terminal pages, detail, and time-signed direct MP4 download links. The site’s JavaScript search-pagination endpoints returned HTTP 500; search deliberately ends after page one rather than repeat results. | `…/get_file/…/4643286_1080p.mp4/?v-acctoken=…` |
| Wallhaven | A | Wallhaven API v1 | `WallhavenParser` | Q20 | ADDED; anonymous SFW API only | FETCHED: API listing pages 1 and 2, two-word search, empty search, detail, and an invalid far page returning `Bad Request`. The anonymous request is explicitly constrained to `purity=100` (SFW). | `{"id":"d8vvlo","purity":"sfw","path":"https://w.wallhaven.cc/full/d8/wallhaven-d8vvlo.png"}` |

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
