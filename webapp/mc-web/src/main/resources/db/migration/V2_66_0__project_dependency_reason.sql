-- MCO-302: give `project_dependencies` a writer, and give every row a reason.
--
-- The table has existed since V2_7_0 with no editor behind it. Its only writer in the
-- application is the idea import, which means an import could assert an ordering that
-- nothing in the app could then remove. This migration is the schema half of the fix;
-- the editor is the world roadmap's "Manual ordering" section.
--
-- ## Why a reason, and why required
--
-- 86 of the dev world's 87 project edges are *derived* — a farm produces what a project
-- needs, and the edge falls out of the data. A row here is the opposite: somebody asserted
-- an ordering that no material justifies ("dig the hole before you build in it"). Nothing
-- in the data can explain such a row six months later, and without an explanation there is
-- no way to tell a deliberate ordering from a misclick. So the editor requires one.
--
-- Rows the idea import wrote have no reason and cannot be given one retroactively, which is
-- why the column is nullable and the CHECK is conditioned on who declared the row.
--
-- ## Why `declared_by` and not `origin`
--
-- The roadmap already calls an edge's provenance its origin (GENERATED vs MANUAL), and the
-- drill's source picker has a third `origin`. Three meanings for one word in one codebase is
-- how the wrong one gets read. Every row in *this* table is manual by definition; what this
-- column records is which surface declared it.
ALTER TABLE project_dependencies
    ADD COLUMN reason TEXT,
    ADD COLUMN declared_by TEXT NOT NULL DEFAULT 'IDEA_IMPORT';

-- The default was for the backfill only. Both writers name it explicitly, and a future one
-- that forgets should fail loudly rather than quietly claim to be an idea import.
ALTER TABLE project_dependencies
    ALTER COLUMN declared_by DROP DEFAULT;

ALTER TABLE project_dependencies
    ADD CONSTRAINT project_dependencies_declared_by
        CHECK (declared_by IN ('EDITOR', 'IDEA_IMPORT'));

ALTER TABLE project_dependencies
    ADD CONSTRAINT project_dependencies_editor_reason
        CHECK (declared_by <> 'EDITOR' OR (reason IS NOT NULL AND btrim(reason) <> ''));

-- A project cannot come before itself. Nothing ever wrote such a row deliberately, but the
-- table has been writable by an import for a year with no constraint, so clear any first —
-- adding the CHECK validates existing rows and would otherwise fail the migration.
DELETE FROM project_dependencies WHERE project_id = depends_on_project_id;

ALTER TABLE project_dependencies
    ADD CONSTRAINT project_dependencies_distinct
        CHECK (project_id <> depends_on_project_id);
