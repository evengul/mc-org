-- Convert world_role column from VARCHAR to INTEGER
-- Role mappings from Role.kt:
-- OWNER: 0, ADMIN: 10, MEMBER: 100, BANNED: 1000

-- Step 1: Add new integer column
ALTER TABLE world_members ADD COLUMN world_role_int INTEGER;

-- Step 2: Update the new column with integer values based on existing string values
UPDATE world_members
SET world_role_int = CASE
    WHEN UPPER(world_role) = 'OWNER' THEN 0
    WHEN UPPER(world_role) = 'ADMIN' THEN 10
    WHEN UPPER(world_role) = 'MEMBER' THEN 100
    WHEN UPPER(world_role) = 'BANNED' THEN 1000
    ELSE 100 -- Default to MEMBER if unknown role
END;

-- Step 3: Make the new column NOT NULL after setting values
ALTER TABLE world_members ALTER COLUMN world_role_int SET NOT NULL;

-- Step 4: Drop the old VARCHAR column
ALTER TABLE world_members DROP COLUMN world_role;

-- Step 5: Rename the new column to the original name
ALTER TABLE world_members RENAME COLUMN world_role_int TO world_role;
