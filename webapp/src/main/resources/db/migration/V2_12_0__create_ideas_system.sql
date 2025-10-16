-- Migration V2_12_0: Create Idea System Tables
-- Creates tables for ideas, ratings, comments, favourites, and test data

-- ============================================================================
-- Main Ideas Table
-- ============================================================================
CREATE TABLE ideas (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(50) NOT NULL,

    -- Author information (stored as JSONB to handle SingleAuthor vs Team)
    -- Format: {"type": "single", "name": "username"} or {"type": "team", "members": [...]}
    author JSONB NOT NULL,

    -- Sub-authors (contributors) stored as array of JSONB
    sub_authors JSONB[] DEFAULT '{}',

    -- Labels/tags for the idea
    labels TEXT[] DEFAULT '{}',

    -- Aggregate counts (denormalized for performance)
    favourites_count INTEGER DEFAULT 0,
    rating_average DECIMAL(3,2) DEFAULT 0.0,
    rating_count INTEGER DEFAULT 0,

    -- Difficulty level
    difficulty VARCHAR(100) NOT NULL,

    -- Minecraft version compatibility
    -- Stored as JSONB to handle Bounded/LowerBounded/UpperBounded/Unbounded
    -- Format: {"type": "bounded", "from": "1.19.0", "to": "1.20.4"}
    minecraft_version_range JSONB NOT NULL,

    -- Category-specific data stored as JSONB
    -- This is the flexible field that holds all category-specific attributes
    -- Example for FARM: {"productionRate": 1000, "afkable": true, "biomes": ["Plains", "Forest"]}
    category_data JSONB NOT NULL DEFAULT '{}',

    -- Audit fields
    created_by INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- Performance Test Data Table
-- ============================================================================
CREATE TABLE idea_test_data (
    id SERIAL PRIMARY KEY,
    idea_id INTEGER NOT NULL REFERENCES ideas(id) ON DELETE CASCADE,
    mspt DOUBLE PRECISION NOT NULL CHECK (mspt >= 0),
    hardware TEXT NOT NULL,
    minecraft_version VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- Favourites Table (Many-to-Many)
-- ============================================================================
CREATE TABLE idea_favourites (
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    idea_id INTEGER NOT NULL REFERENCES ideas(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, idea_id)
);

-- ============================================================================
-- Ratings Table
-- ============================================================================
CREATE TABLE idea_ratings (
    id SERIAL PRIMARY KEY,
    idea_id INTEGER NOT NULL REFERENCES ideas(id) ON DELETE CASCADE,
    rater_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rater_name VARCHAR(255) NOT NULL,
    score DECIMAL(2,1) NOT NULL CHECK (score >= 1.0 AND score <= 5.0),
    content TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (idea_id, rater_id)
);

-- ============================================================================
-- Comments Table
-- ============================================================================
CREATE TABLE idea_comments (
    id SERIAL PRIMARY KEY,
    idea_id INTEGER NOT NULL REFERENCES ideas(id) ON DELETE CASCADE,
    commenter_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    commenter_name VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    likes_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- Comment Likes Table (Many-to-Many)
-- ============================================================================
CREATE TABLE idea_comment_likes (
    comment_id INTEGER NOT NULL REFERENCES idea_comments(id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (comment_id, user_id)
);

-- ============================================================================
-- Project-Idea Link Table (for importing ideas as projects)
-- ============================================================================
CREATE TABLE project_ideas (
    project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    idea_id INTEGER NOT NULL REFERENCES ideas(id) ON DELETE SET NULL,
    imported_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id)
);

-- ============================================================================
-- Indexes for Performance
-- ============================================================================

-- Ideas table indexes
CREATE INDEX idx_ideas_category ON ideas(category);
CREATE INDEX idx_ideas_difficulty ON ideas(difficulty);
CREATE INDEX idx_ideas_created_by ON ideas(created_by);
CREATE INDEX idx_ideas_created_at ON ideas(created_at DESC);
CREATE INDEX idx_ideas_rating_average ON ideas(rating_average DESC);
CREATE INDEX idx_ideas_favourites_count ON ideas(favourites_count DESC);

-- Full-text search index on name and description
CREATE INDEX idx_ideas_search ON ideas USING GIN(to_tsvector('english', name || ' ' || description));

-- GIN index for JSONB category_data (enables efficient filtering)
CREATE INDEX idx_ideas_category_data ON ideas USING GIN(category_data);

-- GIN index for labels array
CREATE INDEX idx_ideas_labels ON ideas USING GIN(labels);

-- Test data indexes
CREATE INDEX idx_idea_test_data_idea_id ON idea_test_data(idea_id);

-- Favourites indexes
CREATE INDEX idx_idea_favourites_user_id ON idea_favourites(user_id);
CREATE INDEX idx_idea_favourites_idea_id ON idea_favourites(idea_id);

-- Ratings indexes
CREATE INDEX idx_idea_ratings_idea_id ON idea_ratings(idea_id);
CREATE INDEX idx_idea_ratings_rater_id ON idea_ratings(rater_id);
CREATE INDEX idx_idea_ratings_score ON idea_ratings(score DESC);
CREATE INDEX idx_idea_ratings_created_at ON idea_ratings(created_at DESC);

-- Comments indexes
CREATE INDEX idx_idea_comments_idea_id ON idea_comments(idea_id);
CREATE INDEX idx_idea_comments_commenter_id ON idea_comments(commenter_id);
CREATE INDEX idx_idea_comments_created_at ON idea_comments(created_at DESC);

-- Comment likes indexes
CREATE INDEX idx_idea_comment_likes_comment_id ON idea_comment_likes(comment_id);
CREATE INDEX idx_idea_comment_likes_user_id ON idea_comment_likes(user_id);

-- Project-idea link indexes
CREATE INDEX idx_project_ideas_idea_id ON project_ideas(idea_id);

-- ============================================================================
-- Triggers for Maintaining Aggregate Counts
-- ============================================================================

-- Trigger to update rating_average and rating_count when ratings change
CREATE OR REPLACE FUNCTION update_idea_rating_aggregates()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE ideas
    SET rating_average = (
            SELECT COALESCE(AVG(score), 0.0)
            FROM idea_ratings
            WHERE idea_id = COALESCE(NEW.idea_id, OLD.idea_id)
        ),
        rating_count = (
            SELECT COUNT(*)
            FROM idea_ratings
            WHERE idea_id = COALESCE(NEW.idea_id, OLD.idea_id)
        ),
        updated_at = CURRENT_TIMESTAMP
    WHERE id = COALESCE(NEW.idea_id, OLD.idea_id);

    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_idea_rating_aggregates
AFTER INSERT OR UPDATE OR DELETE ON idea_ratings
FOR EACH ROW
EXECUTE FUNCTION update_idea_rating_aggregates();

-- Trigger to update favourites_count when favourites change
CREATE OR REPLACE FUNCTION update_idea_favourites_count()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE ideas
    SET favourites_count = (
            SELECT COUNT(*)
            FROM idea_favourites
            WHERE idea_id = COALESCE(NEW.idea_id, OLD.idea_id)
        ),
        updated_at = CURRENT_TIMESTAMP
    WHERE id = COALESCE(NEW.idea_id, OLD.idea_id);

    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_idea_favourites_count
AFTER INSERT OR DELETE ON idea_favourites
FOR EACH ROW
EXECUTE FUNCTION update_idea_favourites_count();

-- Trigger to update comment likes_count when likes change
CREATE OR REPLACE FUNCTION update_comment_likes_count()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE idea_comments
    SET likes_count = (
            SELECT COUNT(*)
            FROM idea_comment_likes
            WHERE comment_id = COALESCE(NEW.comment_id, OLD.comment_id)
        ),
        updated_at = CURRENT_TIMESTAMP
    WHERE id = COALESCE(NEW.comment_id, OLD.comment_id);

    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_comment_likes_count
AFTER INSERT OR DELETE ON idea_comment_likes
FOR EACH ROW
EXECUTE FUNCTION update_comment_likes_count();

-- Trigger to update ideas.updated_at on any change
CREATE OR REPLACE FUNCTION update_idea_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_idea_updated_at
BEFORE UPDATE ON ideas
FOR EACH ROW
EXECUTE FUNCTION update_idea_updated_at();

-- ============================================================================
-- Comments
-- ============================================================================

-- JSONB Structure Examples:
--
-- author (SingleAuthor):
-- {"type": "single", "name": "TechnicalMC"}
--
-- author (Team):
-- {
--   "type": "team",
--   "members": [
--     {"name": "ilmango", "order": 0, "role": "Lead Designer", "contributions": ["Design", "Testing"]},
--     {"name": "gnembon", "order": 1, "role": "Redstone Engineer", "contributions": ["Redstone"]}
--   ]
-- }
--
-- sub_authors array:
-- [
--   {"type": "single", "name": "Contributor1"},
--   {"type": "single", "name": "Contributor2"}
-- ]
--
-- minecraft_version_range (Bounded):
-- {"type": "bounded", "from": "1.19.0", "to": "1.20.4"}
--
-- minecraft_version_range (LowerBounded):
-- {"type": "lowerBounded", "from": "1.20.0"}
--
-- minecraft_version_range (UpperBounded):
-- {"type": "upperBounded", "to": "1.19.4"}
--
-- minecraft_version_range (Unbounded):
-- {"type": "unbounded"}
--
-- category_data example (FARM):
-- {
--   "farmVersion": "v3.2",
--   "productionRate": 12000,
--   "consumptionRate": 0,
--   "size": {"x": 32, "y": 48, "z": 16},
--   "stackable": true,
--   "tileable": false,
--   "yLevel": 64,
--   "subChunkAligned": true,
--   "biomes": ["Plains", "Forest"],
--   "afkable": true,
--   "playersRequired": "1",
--   "directional": false,
--   "locational": true
-- }

