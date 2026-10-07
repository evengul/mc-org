# Renewability review page

`index.html` is the page every renewability verdict was reviewed on (MCO-565). It shows each
item's verdict with every way to make it, and saves *Correct*/*Wrong* marks and accepted
hand-written sources.

The marks are stored by the claude.ai artifact runtime, so the page only saves when published
with Claude's Artifact tool. Publishing to the **same URL** keeps the marks:
<https://claude.ai/artifact/97yto4gLaNz5fqsCrc4TF6>.

## Reviewing a new version, or a rule change

From `webapp/`, with `local.env` sourced and the engine installed (`exec:java` reads `~/.m2`):

```bash
mvn -q install -DskipTests -pl mc-domain,mc-pipeline,mc-engine
mvn -q -pl mc-web exec:java@renewability-diagnostics -Dexec.args="version=26.4.0"
mvn -q -pl mc-web exec:java@renewability-diagnostics -Dexec.args="review versions=26.4.0,26.3.0,1.21.11"
```

The first prints what moved against the committed snapshot. The second writes
`target/renewability-review/index.html` and `renewability-data.js`; ask Claude to publish that
`index.html`, with `renewability-data.js` as a supporting file, to the URL above.

Every item whose verdict matches its version's committed snapshot opens settled, so the page shows
only what is new or has flipped. A version with no snapshot shows everything.

## Accepting the result

When the page has nothing marked wrong, the verdicts become the new snapshot:

```bash
mvn -q -pl mc-web exec:java@renewability-diagnostics -Dexec.args="version=26.4.0 snapshot"
```

That writes `mc-engine/src/test/resources/renewability/<version>/` — `verdicts.tsv` and the
`sources.tsv.gz` fixture — which `RenewabilitySnapshotTest` pins. A wrong verdict is fixed in
`Renewability` or `RenewabilityMechanics` first, never by editing a snapshot.
