# Add Database Migration

Template for creating a new Flyway database migration in MC-ORG.

## Step 1: Determine Version Number

Current version: **V2_21_0**

Next version should be: **V2_22_0** (or increment appropriately)

Check existing migrations:
```bash
ls src/main/resources/db/migration/ | tail -5
```

## Step 2: Create Migration File

Location: `src/main/resources/db/migration/`

File naming: `V{major}_{minor}_{patch}__{description}.sql`

Examples:
- `V2_22_0__add_user_preferences.sql`
- `V2_22_1__add_index_on_projects.sql`
- `V2_23_0__create_comments_table.sql`

## Step 3: Write Migration SQL

### Creating a New Table

```sql
-- V2_22_0__create_{table_name}.sql

CREATE TABLE {table_name} (
    id SERIAL PRIMARY KEY,
    -- Foreign keys
    world_id INT NOT NULL REFERENCES worlds(id) ON DELETE CASCADE,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- Data columns
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    -- Audit columns
    created_by INT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_{table_name}_world_id ON {table_name}(world_id);
CREATE INDEX idx_{table_name}_user_id ON {table_name}(user_id);
CREATE INDEX idx_{table_name}_status ON {table_name}(status);
```

### Adding Columns

```sql
-- V2_22_0__add_{column}_to_{table}.sql

ALTER TABLE {table_name}
ADD COLUMN {column_name} VARCHAR(255);

-- With default value
ALTER TABLE {table_name}
ADD COLUMN {column_name} BOOLEAN NOT NULL DEFAULT false;

-- Nullable foreign key
ALTER TABLE {table_name}
ADD COLUMN related_id INT REFERENCES related_table(id) ON DELETE SET NULL;
```

### Adding Index

```sql
-- V2_22_0__add_index_on_{table}_{columns}.sql

CREATE INDEX idx_{table}_{column} ON {table}({column});

-- Composite index
CREATE INDEX idx_{table}_{col1}_{col2} ON {table}({col1}, {col2});

-- Unique index
CREATE UNIQUE INDEX idx_{table}_{column}_unique ON {table}({column});
```

### Adding Constraint

```sql
-- V2_22_0__add_{constraint}_constraint.sql

ALTER TABLE {table_name}
ADD CONSTRAINT {constraint_name} CHECK ({condition});

-- Unique constraint
ALTER TABLE {table_name}
ADD CONSTRAINT {table}_{columns}_unique UNIQUE ({column1}, {column2});
```

### Modifying Column

```sql
-- V2_22_0__modify_{column}_in_{table}.sql

-- Change type
ALTER TABLE {table_name}
ALTER COLUMN {column_name} TYPE TEXT;

-- Add NOT NULL
ALTER TABLE {table_name}
ALTER COLUMN {column_name} SET NOT NULL;

-- Remove NOT NULL
ALTER TABLE {table_name}
ALTER COLUMN {column_name} DROP NOT NULL;

-- Change default
ALTER TABLE {table_name}
ALTER COLUMN {column_name} SET DEFAULT 'new_default';
```

### Dropping (use with caution)

```sql
-- V2_22_0__drop_{column}_from_{table}.sql

ALTER TABLE {table_name}
DROP COLUMN {column_name};

-- Drop table
DROP TABLE IF EXISTS {table_name};
```

## Step 4: Test Migration

```bash
# Apply migration
mvn flyway:migrate

# Verify migration applied
mvn flyway:info

# If issues, repair Flyway history
mvn flyway:repair
```

## Step 5: Create Kotlin Model (if new table)

Location: `src/main/kotlin/app/mcorg/domain/model/{feature}/{Entity}.kt`

```kotlin
data class {Entity}(
    val id: Int,
    val worldId: Int,
    val name: String,
    val description: String?,
    val status: {Entity}Status,
    val createdBy: Int,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime
)

enum class {Entity}Status {
    ACTIVE,
    INACTIVE,
    ARCHIVED
}
```

## Step 6: Create ResultSet Mapper

```kotlin
fun ResultSet.mapTo{Entity}(): {Entity} {
    return {Entity}(
        id = getInt("id"),
        worldId = getInt("world_id"),
        name = getString("name"),
        description = getString("description"),
        status = {Entity}Status.valueOf(getString("status")),
        createdBy = getInt("created_by"),
        createdAt = getTimestamp("created_at").toZonedDateTime(),
        updatedAt = getTimestamp("updated_at").toZonedDateTime()
    )
}
```

## Common Patterns

### Foreign Key Options

```sql
-- Delete child when parent deleted
REFERENCES {parent}(id) ON DELETE CASCADE

-- Set to NULL when parent deleted
REFERENCES {parent}(id) ON DELETE SET NULL

-- Prevent parent deletion if children exist
REFERENCES {parent}(id) ON DELETE RESTRICT
```

### JSONB Column

```sql
-- Add JSONB column
ALTER TABLE {table}
ADD COLUMN data JSONB NOT NULL DEFAULT '{}';

-- Add GIN index for JSONB queries
CREATE INDEX idx_{table}_data ON {table} USING GIN (data);
```

### Enum Type

```sql
-- Create enum type
CREATE TYPE {enum_name} AS ENUM ('VALUE1', 'VALUE2', 'VALUE3');

-- Use in column
ALTER TABLE {table}
ADD COLUMN status {enum_name} NOT NULL DEFAULT 'VALUE1';
```

## Checklist

- [ ] Version number determined
- [ ] Migration file created with correct naming
- [ ] SQL syntax is valid
- [ ] Foreign keys have appropriate ON DELETE behavior
- [ ] Indexes added for frequently queried columns
- [ ] Migration tested locally (`mvn flyway:migrate`)
- [ ] Kotlin model created (if new table)
- [ ] ResultSet mapper created (if new table)
- [ ] `mvn test` passes
