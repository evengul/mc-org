-- Migration V2_13_0: Consolidate Ratings into Comments
-- Merges the idea_ratings table into idea_comments, allowing comments to optionally include ratings

-- ============================================================================
-- Step 1: Drop existing rating-related triggers and functions
-- ============================================================================

DROP TRIGGER IF EXISTS trigger_update_idea_rating_aggregates ON idea_ratings;
DROP FUNCTION IF EXISTS update_idea_rating_aggregates();

-- ============================================================================
-- Step 2: Drop the idea_ratings table and its indexes
-- ============================================================================

DROP TABLE IF EXISTS idea_ratings CASCADE;

-- ============================================================================
-- Step 3: Modify idea_comments table to support ratings
-- ============================================================================

-- Add rating column (nullable, between 1.0 and 5.0)
ALTER TABLE idea_comments
ADD COLUMN rating DECIMAL(2,1) CHECK (rating IS NULL OR (rating >= 1.0 AND rating <= 5.0));

-- Make content nullable (since a comment can be just a rating without text)
ALTER TABLE idea_comments
ALTER COLUMN content DROP NOT NULL;

-- Add constraint: content and/or rating must be present
ALTER TABLE idea_comments
ADD CONSTRAINT chk_comment_has_content_or_rating
CHECK (content IS NOT NULL OR rating IS NOT NULL);

-- Add unique constraint: one comment per user per idea
ALTER TABLE idea_comments
ADD CONSTRAINT uq_idea_comments_user_idea
UNIQUE (idea_id, commenter_id);

-- ============================================================================
-- Step 4: Add index for rating column
-- ============================================================================

CREATE INDEX idx_idea_comments_rating ON idea_comments(rating DESC) WHERE rating IS NOT NULL;

-- ============================================================================
-- Step 5: Create new trigger to update rating aggregates from comments
-- ============================================================================

-- Function to update rating_average and rating_count from comments with ratings
CREATE OR REPLACE FUNCTION update_idea_rating_aggregates_from_comments()
RETURNS TRIGGER AS $$
DECLARE
    target_idea_id INTEGER;
BEGIN
    -- Determine which idea_id to update
    IF TG_OP = 'DELETE' THEN
        target_idea_id := OLD.idea_id;
    ELSE
        target_idea_id := NEW.idea_id;
    END IF;

    -- Update the ideas table with new aggregates
    UPDATE ideas
    SET rating_average = (
            SELECT COALESCE(AVG(rating), 0.0)
            FROM idea_comments
            WHERE idea_id = target_idea_id
              AND rating IS NOT NULL
        ),
        rating_count = (
            SELECT COUNT(*)
            FROM idea_comments
            WHERE idea_id = target_idea_id
              AND rating IS NOT NULL
        ),
        updated_at = CURRENT_TIMESTAMP
    WHERE id = target_idea_id;

    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

-- Trigger fires on INSERT, UPDATE, or DELETE of comments
CREATE TRIGGER trigger_update_idea_rating_aggregates_from_comments
AFTER INSERT OR UPDATE OR DELETE ON idea_comments
FOR EACH ROW
EXECUTE FUNCTION update_idea_rating_aggregates_from_comments();

-- ============================================================================
-- Comments
-- ============================================================================

-- The idea_comments table now supports three types of entries:
-- 1. Text-only comment: content IS NOT NULL, rating IS NULL
-- 2. Rating-only comment: content IS NULL, rating IS NOT NULL
-- 3. Rated text comment: content IS NOT NULL, rating IS NOT NULL
--
-- This maps to the domain model Comment sealed class:
-- - TextComment (content, no rating)
-- - RatingComment (rating, no content)
-- - RatedTextComment (content and rating)
--
-- Each user can only have ONE comment per idea (enforced by unique constraint).
-- If a user wants to change their rating or comment, they must UPDATE the existing row.

