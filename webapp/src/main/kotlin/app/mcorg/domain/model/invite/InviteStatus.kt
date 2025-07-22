package app.mcorg.domain.model.invite

import java.time.ZonedDateTime

sealed class InviteStatus(
    val reachedAt: ZonedDateTime
) {
    class Pending(
        createdAt: ZonedDateTime
    ) : InviteStatus(createdAt) {
        fun accept() = Accepted(ZonedDateTime.now())
        fun decline() = Declined(ZonedDateTime.now())
        fun expire() = Expired(ZonedDateTime.now())
        fun cancel() = Cancelled(ZonedDateTime.now())
    }

    class Accepted(
        acceptedAt: ZonedDateTime
    ) : InviteStatus(acceptedAt)

    class Declined(
        declinedAt: ZonedDateTime
    ) : InviteStatus(declinedAt)

    class Expired(
        expiredAt: ZonedDateTime
    ) : InviteStatus(expiredAt) {
        fun reactivate() = Pending(ZonedDateTime.now())
    }

    class Cancelled(
        cancelledAt: ZonedDateTime
    ) : InviteStatus(cancelledAt) {
        fun reactivate() = Pending(ZonedDateTime.now())
    }
}