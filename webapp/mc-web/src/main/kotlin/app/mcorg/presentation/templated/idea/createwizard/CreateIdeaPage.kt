package app.mcorg.presentation.templated.idea.createwizard

import kotlinx.serialization.Serializable

/**
 * Legacy stage enum kept for backward compatibility.
 * New code uses [DraftWizardStage].
 */
@Serializable
enum class CreateIdeaStage(val displayName: String, private val previousStep: String?, private val nextStep: String?) {
    BASIC_INFO("Basic Information", null, "AUTHOR_INFO"),
    AUTHOR_INFO("Author Information", "BASIC_INFO", "VERSION_COMPATIBILITY"),
    VERSION_COMPATIBILITY("Version Compatibility", "AUTHOR_INFO", "ITEM_REQUIREMENTS"),
    ITEM_REQUIREMENTS("Item Requirements", "VERSION_COMPATIBILITY", "CATEGORY_SPECIFIC_FIELDS"),
    CATEGORY_SPECIFIC_FIELDS("Category Specific Fields", "ITEM_REQUIREMENTS", "REVIEW_SUBMIT"),
    REVIEW_SUBMIT("Review & Submit", "CATEGORY_SPECIFIC_FIELDS", null);

    fun getPreviousStep(): CreateIdeaStage? = previousStep?.let { valueOf(it) }
    fun getNextStep(): CreateIdeaStage? = nextStep?.let { valueOf(it) }
}
