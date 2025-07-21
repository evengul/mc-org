package app.mcorg.domain.model.invite

import java.time.ZonedDateTime

sealed class InviteStatus(
    val reachedAt: ZonedDateTime
) {
    class Pending(
        createdAt: ZonedDateTime
    ) : InviteStatus(createdAt)

    class Accepted(
        acceptedAt: ZonedDateTime
    ) : InviteStatus(acceptedAt)

    class Declined(
        declinedAt: ZonedDateTime
    ) : InviteStatus(declinedAt)

    class Expired(
        expiredAt: ZonedDateTime
    ) : InviteStatus(expiredAt)

    class Cancelled(
        cancelledAt: ZonedDateTime
    ) : InviteStatus(cancelledAt)
}