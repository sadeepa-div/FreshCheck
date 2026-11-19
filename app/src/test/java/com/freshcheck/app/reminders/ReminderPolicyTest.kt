package com.freshcheck.app.reminders

import com.freshcheck.app.data.local.entity.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import java.time.*

class ReminderPolicyTest {
    private val now = Instant.parse("2026-11-18T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var store: FakeReminderStore
    private lateinit var settings: FakeReminderSettings
    private lateinit var scheduler: FakeReminderScheduler
    private lateinit var notifications: FakeReminderNotifications
    private lateinit var coordinator: ReminderCoordinator
    private fun draft(id: String? = null) = ReminderDraft("food", id, "2026-11-19", "14:30", "Asia/Colombo")
    private fun row(state: ReminderState = ReminderState.Enqueued, enabled: Boolean = true) =
        ReminderEntity("reminder", "food", now, enabled, state, now.minusSeconds(60), now.minusSeconds(60))
    private fun delivery(at: Instant = now) = ReminderDelivery(store, settings, notifications, Clock.fixed(at, ZoneOffset.UTC))
    @Before fun setup() {
        store = FakeReminderStore().also { it.foods["food"] = fixtureFood() }
        settings = FakeReminderSettings()
        scheduler = FakeReminderScheduler()
        notifications = FakeReminderNotifications()
        coordinator = ReminderCoordinator(store, settings, scheduler, notifications, clock)
    }

    @Test fun explicitLocalTimeUsesTheSelectedZoneAndRejectsMissingPastOrInvalidInput() {
        assertEquals(Instant.parse("2026-11-19T09:00:00Z"), ReminderRules.resolve(draft(), now).second)
        listOf(draft().copy(date = ""), draft().copy(time = ""), draft().copy(time = "24:61"),
            draft().copy(zoneId = "Invalid/Zone"), draft().copy(date = "2026-11-17"),
            draft().copy(foodId = "demo-food"), draft().copy(reminderId = "demo-reminder")).forEach {
            assertThrows(IllegalArgumentException::class.java) { ReminderRules.resolve(it, now) }
        }
    }
    @Test fun daylightSavingGapIsRejectedAndOverlapRequiresExplicitOffset() {
        val base = ReminderDraft("food", date = "2027-03-14", time = "02:30", zoneId = "America/New_York")
        assertThrows(IllegalArgumentException::class.java) { ReminderRules.resolve(base, now) }
        val overlap = base.copy(date = "2027-11-07", time = "01:30")
        assertThrows(IllegalArgumentException::class.java) { ReminderRules.resolve(overlap, now) }
        val early = ReminderRules.resolve(overlap.copy(overlapOffsetSeconds = -4 * 3600), now).second
        val late = ReminderRules.resolve(overlap.copy(overlapOffsetSeconds = -5 * 3600), now).second
        assertEquals(3600L, Duration.between(early, late).seconds)
    }
    @Test fun creationAndUpdateUseOneStableIdWithNewRevisionAndUniqueWork() = runTest {
        val first = coordinator.save(draft())
        assertTrue(first.saved && first.scheduled)
        val before = store.rows.value.single()
        val second = coordinator.save(draft(before.id).copy(time = "15:30"))
        assertTrue(second.scheduled)
        assertEquals(before.id, store.rows.value.single().id)
        assertTrue(store.rows.value.single().updatedAt > before.updatedAt)
        assertEquals(1, scheduler.work.size)
        assertEquals(2, scheduler.enqueues)
        coordinator.reconcile()
        coordinator.reconcile()
        assertEquals(2, scheduler.enqueues)
    }
    @Test fun disableAndDeleteCancelWorkAndDisplayedNotification() = runTest {
        coordinator.save(draft())
        val id = store.rows.value.single().id
        notifications.visible += id
        assertTrue(coordinator.disable(id).saved)
        assertFalse(store.rows.value.single().enabled)
        assertTrue(scheduler.work.isEmpty() && notifications.visible.isEmpty())
        assertTrue(coordinator.delete(id).saved)
        assertTrue(store.rows.value.isEmpty())
    }
    @Test fun deniedPermissionPreservesDefinitionWithoutFalseSchedulingThenRecovers() = runTest {
        notifications.capability = NotificationAvailability(false, false, true, "Permission denied")
        val result = coordinator.save(draft())
        assertTrue(result.saved)
        assertTrue(result.permissionNeeded)
        assertFalse(result.scheduled)
        assertEquals(ReminderState.Blocked, store.rows.value.single().state)
        assertTrue(scheduler.work.isEmpty())
        notifications.capability = NotificationAvailability(true, true, true, "Available")
        coordinator.reconcile()
        assertEquals(1, scheduler.work.size)
    }
    @Test fun globalRecheckAppAndChannelSwitchesEachBlockScheduling() = runTest {
        coordinator.save(draft())
        listOf(ReminderPreferences(false, true), ReminderPreferences(true, false)).forEach {
            coordinator.setPreferences(it)
            assertTrue(scheduler.work.isEmpty())
        }
        settings.set(ReminderPreferences(true, true))
        listOf(NotificationAvailability(true, false, true), NotificationAvailability(true, true, false)).forEach {
            notifications.capability = it
            coordinator.reconcile()
            assertTrue(scheduler.work.isEmpty())
        }
    }
    @Test fun enqueueFailureIsReportedAndDefinitionSurvivesForStartupRecovery() = runTest {
        scheduler.fail = true
        val result = coordinator.save(draft())
        assertTrue(result.saved)
        assertFalse(result.scheduled)
        assertFalse(result.message.contains("work enqueued", true))
        assertEquals(1, store.rows.value.size)
        scheduler.fail = false
        val restarted = ReminderCoordinator(store, settings, scheduler, notifications, clock)
        restarted.reconcile()
        assertEquals(1, scheduler.work.size)
    }
    @Test fun postedAndClaimedDefinitionsAreNeverReenqueuedAfterProcessRestart() = runTest {
        for (state in listOf(ReminderState.Posted, ReminderState.Claimed)) {
            store.put(row(state, enabled = false))
            coordinator.reconcile()
            assertTrue(scheduler.work.isEmpty())
            assertEquals(0, scheduler.enqueues)
        }
    }
    @Test fun reconciliationDoesNotCancelTheCurrentWorkerWhileItPostsAClaim() = runTest {
        val claimed = row(ReminderState.Claimed, enabled = false)
        store.put(claimed)
        scheduler.work[claimed.id] = ReminderVersion.of(claimed)
        coordinator.reconcile()
        assertTrue(scheduler.enqueued(ReminderVersion.of(claimed)))
        assertEquals(0, scheduler.enqueues)
    }
    @Test fun deletedUsedDiscardedAndRemovedRecheckFoodCannotNotify() = runTest {
        for (status in listOf<FoodStatus?>(null, FoodStatus.Used, FoodStatus.Discarded, FoodStatus.Active)) {
            store.put(row())
            if (status == null) store.foods.clear()
            else store.foods["food"] = fixtureFood().copy(status = status,
                recheck = if (status == FoodStatus.Active) null else fixtureFood().recheck)
            delivery().run(ReminderVersion.of(row()))
            coordinator.reconcile()
            assertEquals(0, notifications.attempts)
            assertTrue(scheduler.work.isEmpty())
        }
    }
    @Test fun changedRecheckDefinitionAndDisabledReminderRejectOldWorker() = runTest {
        val old = row()
        store.put(old.copy(state = ReminderState.Disabled, enabled = false, updatedAt = now))
        delivery().run(ReminderVersion.of(old))
        store.put(old.copy(dueAt = now.plusSeconds(3600)))
        delivery().run(ReminderVersion.of(old))
        store.put(old.copy(updatedAt = now))
        delivery().run(ReminderVersion.of(old))
        assertEquals(0, notifications.attempts)
    }
    @Test fun invalidMissingAndDemoWorkerIdentitiesAreHarmless() = runTest {
        delivery().run(ReminderVersion("missing", 0, 0))
        store.put(row().copy(id = "demo-reminder"))
        delivery().run(ReminderVersion.of(store.rows.value.single()))
        store.put(row().copy(foodId = "demo-food"))
        delivery().run(ReminderVersion.of(row()))
        assertEquals(0, notifications.attempts)
    }
    @Test fun dueWorkerPostsOnceAndSecondAttemptIsConsumed() = runTest {
        store.put(row())
        val version = ReminderVersion.of(row())
        assertEquals(DeliveryResult.Finished, delivery().run(version))
        assertEquals(ReminderState.Posted, store.get("reminder")!!.state)
        delivery().run(version)
        assertEquals(1, notifications.attempts)
    }
    @Test fun backwardClockMovementRetriesWithoutEarlyPosting() = runTest {
        store.put(row())
        assertEquals(DeliveryResult.Retry, delivery(now.minusSeconds(60)).run(ReminderVersion.of(row())))
        assertEquals(0, notifications.attempts)
        assertEquals(ReminderState.Enqueued, store.get("reminder")!!.state)
    }
    @Test fun unavailableDeliveryIsBlockedNotFalselyPosted() = runTest {
        store.put(row())
        notifications.capability = NotificationAvailability(true, true, false, "Channel blocked")
        delivery().run(ReminderVersion.of(row()))
        assertEquals(ReminderState.Blocked, store.get("reminder")!!.state)
        assertEquals(0, notifications.attempts)
    }
    @Test fun uncertainNotificationOutcomeIsNotRetriedOnRestart() = runTest {
        store.put(row())
        notifications.outcome = PostResult.Uncertain
        delivery().run(ReminderVersion.of(row()))
        assertEquals(ReminderState.Claimed, store.get("reminder")!!.state)
        delivery().run(ReminderVersion.of(row()))
        assertEquals(1, notifications.attempts)
        coordinator.reconcile()
        assertTrue(scheduler.work.isEmpty())
    }
    @Test fun databaseReadFailuresPropagateForWorkerRetryBeforeClaim() = runTest {
        store.failRead = true
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { delivery().run(ReminderVersion.of(row())) }
        }
        assertEquals(0, notifications.attempts)
    }
    @Test fun forcedReconciliationRebuildsDelayAfterClockChangeWithoutDuplicates() = runTest {
        coordinator.save(draft())
        coordinator.reconcile(force = true)
        assertEquals(2, scheduler.enqueues)
        assertEquals(1, scheduler.work.size)
    }
    @Test fun notificationRouteAllowlistRejectsUntrustedOrMalformedTargets() {
        val valid = "freshcheck-internal://recheck/food/reminder/1234"
        assertEquals("food", ReminderNotificationIntent.parseData(valid)!!.foodId)
        listOf("https://recheck/food/reminder/1", "freshcheck-internal://other/food/reminder/1",
            "freshcheck-internal://recheck/demo-food/reminder/1", valid + "?redirect=elsewhere",
            valid + "#fragment", "freshcheck-internal://recheck/food/../../1",
            "freshcheck-internal://recheck/%66ood/reminder/1").forEach {
            assertNull(it, ReminderNotificationIntent.parseData(it))
        }
    }
}
