# TOGETL browser

Read-only PHP browser over the SQLite database produced by `togetl load`.

## Run

```sh
./togetl load --db out/togetl.sqlite3 \
    --input out/3dparts-data.togetltsv \
    --report out/3dparts-data-report.tsv

TOGETL_DB=out/togetl.sqlite3 php -S localhost:8080 web/router.php
```

Then open <http://localhost:8080/>.

`TOGETL_DB` defaults to `out/togetl.sqlite3` relative to the repository root.
The PDO SQLite extension is required. The browser opens the database with
`PRAGMA query_only = ON` and has no write paths.

## Pages

- `/` — counts, recent batches, sources, issue summary
- `/subject?iri=...` — statements about a subject, grouped by predicate
- `/predicate?iri=...` — subjects that use a predicate
- `/document?iri=...` — document metadata, loadings, and parse issues
- `/vocab` — defined, used, unused, proposed, and undefined terms
- `/issues` — parse issues and import conflicts
- `/part/<id>` or `/part?iri=...` — parts view over the generic statement tables

Query-string URLs (`index.php?page=subject&iri=...`) work under any PHP server.
The included `router.php` and `.htaccess` enable the shorter paths.
