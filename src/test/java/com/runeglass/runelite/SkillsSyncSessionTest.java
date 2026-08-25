package com.runeglass.runelite;

import java.time.Instant;
import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SkillsSyncSessionTest
{
	private static final Instant SESSION_STARTED_AT = Instant.parse("2026-08-24T10:00:00Z");

	@Test
	public void emitsOneBaselineThenDebouncesIntoFiveMinuteCheckpoints()
	{
		SkillsSyncSession session = new SkillsSyncSession();
		session.start();

		Optional<SkillsSnapshot> baseline = populateBaseline(session, SESSION_STARTED_AT);
		assertTrue(baseline.isPresent());
		assertEquals("login_baseline", baseline.get().getReason());

		Instant changedAt = SESSION_STARTED_AT.plusSeconds(10);
		session.accept(
			SkillCatalog.trainableSkills().get(0),
			101,
			1,
			1,
			changedAt);

		assertFalse(session.poll(SESSION_STARTED_AT.plusSeconds(299)).isPresent());
		Optional<SkillsSnapshot> checkpoint = session.poll(SESSION_STARTED_AT.plusSeconds(300));
		assertTrue(checkpoint.isPresent());
		assertEquals("change_checkpoint", checkpoint.get().getReason());
		assertEquals(101L, checkpoint.get().getExperience()[1]);
		assertFalse(session.poll(SESSION_STARTED_AT.plusSeconds(600)).isPresent());
	}

	@Test
	public void finalFlushBypassesTheNormalWindow()
	{
		SkillsSyncSession session = new SkillsSyncSession();
		session.start();
		populateBaseline(session, SESSION_STARTED_AT);
		session.accept(
			SkillCatalog.trainableSkills().get(0),
			150,
			1,
			1,
			SESSION_STARTED_AT.plusSeconds(5));

		Optional<SkillsSnapshot> flushed = session.stop(
			SESSION_STARTED_AT.plusSeconds(6),
			SnapshotReason.LOGOUT_FLUSH);

		assertTrue(flushed.isPresent());
		assertEquals("logout_flush", flushed.get().getReason());
		assertFalse(session.isActive());
	}

	@Test
	public void emitsBoundedCoverageHeartbeatWithoutXpChanges()
	{
		SkillsSyncSession session = new SkillsSyncSession();
		session.start();
		populateBaseline(session, SESSION_STARTED_AT);

		assertFalse(session.poll(SESSION_STARTED_AT.plusSeconds(599)).isPresent());
		Optional<SkillsSnapshot> heartbeat = session.poll(
			SESSION_STARTED_AT.plusSeconds(600));

		assertTrue(heartbeat.isPresent());
		assertEquals("coverage_heartbeat", heartbeat.get().getReason());
		assertFalse(session.poll(SESSION_STARTED_AT.plusSeconds(1_199)).isPresent());
	}

	@Test
	public void finalFlushClosesCoverageEvenWithoutXpChanges()
	{
		SkillsSyncSession session = new SkillsSyncSession();
		session.start();
		populateBaseline(session, SESSION_STARTED_AT);

		Optional<SkillsSnapshot> flushed = session.stop(
			SESSION_STARTED_AT.plusSeconds(60),
			SnapshotReason.LOGOUT_FLUSH);

		assertTrue(flushed.isPresent());
		assertEquals("logout_flush", flushed.get().getReason());
		assertFalse(session.isActive());
	}

	@Test
	public void revocationDiscardsLocalChangesWithoutAFlush()
	{
		SkillsSyncSession session = new SkillsSyncSession();
		session.start();
		populateBaseline(session, SESSION_STARTED_AT);
		session.accept(
			SkillCatalog.trainableSkills().get(0),
			150,
			1,
			1,
			SESSION_STARTED_AT.plusSeconds(5));

		session.cancel();

		assertFalse(session.isActive());
		assertFalse(session.poll(SESSION_STARTED_AT.plusSeconds(600)).isPresent());
	}

	@Test
	public void manualSyncEmitsACompleteSnapshotAndResetsPendingChanges()
	{
		SkillsSyncSession session = new SkillsSyncSession();
		session.start();
		populateBaseline(session, SESSION_STARTED_AT);

		Optional<SkillsSnapshot> unchanged = session.manualSync(
			SESSION_STARTED_AT.plusSeconds(1));
		assertTrue(unchanged.isPresent());
		assertEquals("manual_sync", unchanged.get().getReason());

		session.accept(
			SkillCatalog.trainableSkills().get(0),
			150,
			1,
			1,
			SESSION_STARTED_AT.plusSeconds(2));
		Optional<SkillsSnapshot> changed = session.manualSync(
			SESSION_STARTED_AT.plusSeconds(3));
		assertTrue(changed.isPresent());
		assertEquals(150L, changed.get().getExperience()[1]);
		assertFalse(session.poll(SESSION_STARTED_AT.plusSeconds(600)).isPresent());
	}

	private static Optional<SkillsSnapshot> populateBaseline(SkillsSyncSession session, Instant observedAt)
	{
		Optional<SkillsSnapshot> emitted = Optional.empty();
		for (int index = 0; index < SkillCatalog.trainableSkills().size(); index++)
		{
			Optional<SkillsSnapshot> next = session.accept(
				SkillCatalog.trainableSkills().get(index),
				(index + 1) * 100,
				index + 1,
				index + 1,
				observedAt);
			if (next.isPresent())
			{
				emitted = next;
			}
		}
		return emitted;
	}
}
