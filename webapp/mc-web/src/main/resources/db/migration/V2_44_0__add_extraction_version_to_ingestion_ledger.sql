-- The ingestion freshness check re-ingests a version when Mojang's server-jar SHA changes,
-- but misses the case where the *extraction code* changed (new synthetic sources, parser
-- fixes, item mapping) while the jar did not. Record the extraction-version each ingest ran
-- under; the pipeline re-ingests a version whose stored extraction_version is older than the
-- code's current one (app.mcorg.data.minecraft.ExtractionVersion.CURRENT). This makes a code
-- bump trigger exactly one automatic, self-resetting re-ingest per version.
--
-- Existing rows default to 0 so the next run re-ingests them once under the new (>= 1)
-- extraction version — picking up the synthetic sources and mapping added in this batch.
ALTER TABLE minecraft_version_ingestion
    ADD COLUMN extraction_version INTEGER NOT NULL DEFAULT 0;
