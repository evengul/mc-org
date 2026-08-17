package app.mcorg.presentation.templated.idea.createwizard

/**
 * The groups a draft's fields fall into.
 *
 * These were the steps of a six-screen wizard until MCO-310 put every field on one page. The
 * screens are gone; the grouping survives because parsing and validation are still organised by it
 * — `buildStageJson` and `ValidateStageStep` both dispatch on these, and a draft row still records
 * one in `current_stage`.
 *
 * There is no ordering here any more (no `next()`/`previous()`): nothing steps through them, the
 * form submits all of them at once.
 */
enum class DraftWizardStage(val displayName: String) {
    BASIC_INFO("Basic Info"),
    AUTHOR_INFO("Author"),
    VERSION_COMPATIBILITY("Version"),
    ITEM_REQUIREMENTS("Items"),
    PRODUCTIONS("Produces"),
    CATEGORY_FIELDS("Category"),
    REVIEW("Review"),
}
