-- MCO-247: let users ignore/exclude an imported build's material from the
-- "What I need" list and the derived gathering plan, without deleting it.
-- The row is kept (reversible) but is filtered out of plan derivation input.
ALTER TABLE resource_gathering
    ADD COLUMN ignored BOOLEAN NOT NULL DEFAULT FALSE;
