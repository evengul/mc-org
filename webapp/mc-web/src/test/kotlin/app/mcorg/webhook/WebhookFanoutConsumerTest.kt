package app.mcorg.webhook

import app.mcorg.domain.model.project.ProjectType
import app.mcorg.event.ProjectCreated
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

/**
 * Unit tests for the `onEnqueued` wake signal added for MCO-251: [WebhookFanoutConsumer] must fire
 * it exactly when it actually writes at least one outbox row, so [WebhookDeliveryPoller] wakes
 * promptly instead of waiting for its next scheduled tick. Persisted enqueue/delivery semantics are
 * covered by the `database`-tagged [WebhookDeliveryIT].
 */
class WebhookFanoutConsumerTest {

    private val subscription = WebhookSubscription(
        id = 1,
        worldId = 42,
        callbackUrl = "https://example.test/hook",
        secret = "s",
        eventFilter = listOf("*"),
        active = true,
        consecutiveFailures = 0,
    )

    @BeforeEach
    fun setup() {
        mockkObject(WebhookStore)
        coEvery { WebhookStore.enqueueDelivery(any(), any(), any()) } returns Unit
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(WebhookStore)
    }

    private fun event() = ProjectCreated(42, 1, Instant.now(), 1, "Iron Farm", ProjectType.REDSTONE)

    @Test
    fun `signals a wake after enqueueing at least one matching subscription`() = runBlocking {
        coEvery { WebhookStore.findActiveSubscriptions(42) } returns listOf(subscription)
        var wakeCount = 0

        WebhookFanoutConsumer(onEnqueued = { wakeCount++ }).handle(event())

        coVerify(exactly = 1) { WebhookStore.enqueueDelivery(1, "project_created", any()) }
        assertEquals(1, wakeCount, "expected exactly one wake signal after enqueueing")
    }

    @Test
    fun `does not signal when no subscription matches`() = runBlocking {
        coEvery { WebhookStore.findActiveSubscriptions(42) } returns emptyList()
        var wakeCount = 0

        WebhookFanoutConsumer(onEnqueued = { wakeCount++ }).handle(event())

        coVerify(exactly = 0) { WebhookStore.enqueueDelivery(any(), any(), any()) }
        assertEquals(0, wakeCount, "no subscriptions matched, so nothing was enqueued to wake for")
    }

    @Test
    fun `defaults to a no-op wake so it stays constructible without a poller`() = runBlocking {
        coEvery { WebhookStore.findActiveSubscriptions(42) } returns listOf(subscription)

        // Should not throw despite no onEnqueued being supplied.
        WebhookFanoutConsumer().handle(event())

        coVerify(exactly = 1) { WebhookStore.enqueueDelivery(1, "project_created", any()) }
    }
}
