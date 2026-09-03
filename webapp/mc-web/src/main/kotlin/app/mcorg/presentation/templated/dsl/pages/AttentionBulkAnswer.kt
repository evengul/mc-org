package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.dsl.ALERT_CONTAINER_ID
import app.mcorg.presentation.templated.dsl.AlertType
import app.mcorg.presentation.templated.dsl.createAlert
import kotlinx.html.ButtonType
import kotlinx.html.button
import kotlinx.html.details
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.summary
import kotlinx.html.ul

/**
 * MCO-507 — "answer the remaining N with the recommended pick".
 *
 * After MCO-400 folded the small questions away and MCO-409 stopped asking the wood ones, a real
 * plan still ends in a tail of tiny decisions — on the YAMS build: Red Sand or Sand (4 items),
 * Charcoal or Coal (3), Soul Sand or Soul Soil (2). Each is a click, none is interesting, and
 * together they are the last thing between an import and a finished plan.
 *
 * The alternative — deciding them silently (MCO-410) — was measured and does not work: it hits
 * exact cost ties on two of those three and a known bug on the third, and an auto-pick that lands
 * on an alphabetical tiebreak freezes a default nobody ever sees. A wrong invisible answer never
 * gets corrected.
 *
 * So this is not an auto-resolver, and the difference is one property: **the decisions have an
 * author**. The action writes ordinary `PlanOverrides.tagMember` rows — indistinguishable from
 * having clicked each question by hand. They show as answered in the picker, each can be changed
 * on its own afterwards, and they land in `resource_gathering_plan_override` where the MCO-506
 * dataset can see them rather than hiding from it. It does not need the recommendation to be
 * right, only visible and reversible.
 *
 * Which is why the button lives inside its own disclosure with the picks: you cannot press it
 * without having read what it is about to choose.
 */

/** The element the control is lazy-loaded into, so the picks are computed once, on the server. */
const val BULK_ANSWER_SLOT_ID = "plan-attention-bulk"

/** The undo toast's id — one at a time, so a fixed id is enough for the OOB dismissal. */
const val BULK_ANSWER_TOAST_ID = "plan-attention-bulk-undo"

/** One folded question and the member the picker's ranking recommends for it. */
data class RecommendedAnswer(
    val tagId: String,
    val tagName: String,
    val memberId: String,
    val memberName: String,
)

private fun bulkUrl(worldId: Int, projectId: Int) =
    "/worlds/$worldId/projects/$projectId/plan/attention/bulk"

/**
 * The control itself: a disclosure whose summary makes the offer and whose body shows every pick
 * before the button that applies them.
 *
 * Rendered into `#$BULK_ANSWER_SLOT_ID` (innerHTML). Never rendered for a lead question — those
 * are the ones worth reading — and never for a single question, where it would be more work than
 * the click it replaces.
 */
fun bulkAnswerControl(worldId: Int, projectId: Int, picks: List<RecommendedAnswer>): String =
    createHTML().details("plan-attention__bulk") {
        summary {
            span("btn btn--ghost btn--sm plan-attention__toggle") {
                span("plan-attention__toggle--closed") {
                    +"Answer the remaining ${picks.size} with the recommended pick ▾"
                }
                span("plan-attention__toggle--open") { +"Hide the recommended picks ▴" }
            }
        }
        ul("plan-attention__picks") {
            picks.forEach { pick ->
                li("plan-attention__pick") {
                    span("plan-attention__pick-question") { +pick.tagName }
                    span("plan-attention__pick-arrow") { +"→" }
                    span("plan-attention__pick-answer") { +pick.memberName }
                }
            }
        }
        p("plan-attention__bulk-note") {
            +"These become your answers — each one shows as answered and can be changed on its own."
        }
        button(classes = "btn btn--secondary btn--sm") {
            id = "plan-attention-bulk-submit"
            type = ButtonType.button
            attributes["hx-post"] = bulkUrl(worldId, projectId)
            attributes["hx-target"] = "#project-content"
            attributes["hx-swap"] = "outerHTML"
            +"Answer these ${picks.size}"
        }
    }

/**
 * The undo affordance, swapped out-of-band into the toast container.
 *
 * A toast rather than a row on the page, because the affordance expires with the page and because
 * the section it came from is usually gone by the time it renders — answering the tail is often
 * the last question the plan had. It reverts by **row id**: one action created exactly these N
 * rows, so one action removes exactly them and nothing a user answered themselves.
 */
fun bulkAnswerUndoToast(worldId: Int, projectId: Int, createdIds: List<Int>): String =
    createHTML().ul {
        hxOutOfBands("beforeend:#$ALERT_CONTAINER_ID")
        li {
            createAlert(
                id = BULK_ANSWER_TOAST_ID,
                type = AlertType.SUCCESS,
                title = "Answered ${createdIds.size} ${if (createdIds.size == 1) "question" else "questions"}",
                message = "Recorded as your choices — change any of them from the picker.",
                autoClose = false,
            )
            button(classes = "btn btn--ghost btn--sm") {
                id = "plan-attention-bulk-undo-button"
                type = ButtonType.button
                attributes["hx-delete"] =
                    bulkUrl(worldId, projectId) + "?ids=" + createdIds.joinToString(",")
                attributes["hx-target"] = "#project-content"
                attributes["hx-swap"] = "outerHTML"
                +"Revert those ${createdIds.size}"
            }
        }
    }

/** Removes the undo toast once its undo has been taken — nothing left to revert. */
fun bulkAnswerToastDismissal(): String =
    createHTML().ul {
        hxOutOfBands("delete:li#$BULK_ANSWER_TOAST_ID")
        li { id = BULK_ANSWER_TOAST_ID }
    }
