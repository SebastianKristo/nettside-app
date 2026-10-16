# Utregninger (felles for Android og iOS)

- **4xx/5xx-andel** = sum(status 400–499 / 500–599) / alle forespørsler × 100
- **Fra hurtiglager** = forespørsler med `cacheStatus` ∈ {hit, stale, revalidated, updating}
- **Treffrate** = `hit` / alle × 100
- **Båndbredde spart** = bytes fra hurtiglager / alle bytes × 100
- **NXDOMAIN-andel** = `NXDOMAIN` / alle DNS-oppslag × 100
- **Sidevisninger** ≈ forespørsler med `edgeResponseContentTypeName: "html"`
- **Unike besøkende** ≈ `sum.visits` (adaptive groups har ikke ekte unike)
- **Land** = antall land med besøk > 0 (unntatt `XX` og `T1`)
- **Historikk**: ett punkt per dag. `httpRequests1dGroups` gir requests, bytes, cached, pageViews, threats, uniques; resten lagres fra «I dag»-tallene. Besøk faller tilbake på unike der det mangler.
- **Søyler**: 7 d = 7, 30 d = 30, 90 d = 45 (2 dager), 1 år = 52 (uker). Tellinger summeres, rater/svartid bruker maks.

`countries.json`: world-atlas 110m (Natural Earth), Antarktis utelatt. Felter: `code` (ISO alpha-2), `name` (nb), `lat`/`lon` (sentroid av største polygon), `rings` (flate `[lon, lat, …]`-lister, 0,1° presisjon).
