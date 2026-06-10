-- Average amount produced per attempt, extracted from loot-table rolls, entry
-- weights, and count functions. NULL = unknown (recipes, conditional drops,
-- unrecognized number providers). Lets the planner compute attempts needed and
-- rank low-yield sources honestly.

ALTER TABLE resource_source_produced_item ADD COLUMN expected_yield DOUBLE PRECISION;
ALTER TABLE resource_source_produced_tag ADD COLUMN expected_yield DOUBLE PRECISION;

COMMENT ON COLUMN resource_source_produced_item.expected_yield IS 'Average items per attempt from loot-table data; NULL when unknown or not applicable';
COMMENT ON COLUMN resource_source_produced_tag.expected_yield IS 'Average items per attempt from loot-table data; NULL when unknown or not applicable';
