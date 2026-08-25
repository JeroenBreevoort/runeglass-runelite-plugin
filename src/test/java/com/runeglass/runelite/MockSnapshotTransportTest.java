package com.runeglass.runelite;

import java.time.Instant;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class MockSnapshotTransportTest
{
	@Test
	public void keepsOnlyABoundedInMemoryPreviewAndCanBeCleared()
	{
		MockSnapshotTransport transport = new MockSnapshotTransport(
			snapshot -> snapshot.getType() + ":" + snapshot.getReason());
		SkillsSnapshotReducer reducer = new SkillsSnapshotReducer();
		for (int index = 0; index < SkillCatalog.trainableSkills().size(); index++)
		{
			reducer.accept(
				SkillCatalog.trainableSkills().get(index),
				(index + 1) * 100,
				index + 1,
				index + 1);
		}

		SkillsSnapshot snapshot = reducer.takeSnapshot(
			Instant.parse("2026-08-24T10:00:00Z"),
			SnapshotReason.LOGIN_BASELINE).orElseThrow(AssertionError::new);

		for (int index = 0; index < 20; index++)
		{
			transport.publish(snapshot);
		}

		assertEquals(16, transport.snapshotCount());
		assertEquals(
			"skills.snapshot.v1:login_baseline",
			transport.latestPayload().orElseThrow(AssertionError::new));

		transport.clear();
		assertEquals(0, transport.snapshotCount());
		assertFalse(transport.latestPayload().isPresent());
	}
}
