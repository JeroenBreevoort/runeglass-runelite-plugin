package com.runeglass.runelite;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.runelite.api.GameState;
import net.runelite.api.WorldType;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class DropLootHandlerTest
{
	@Rule public TemporaryFolder temporary = new TemporaryFolder();
	private DropHandlerFixture fixture;

	@Before public void setUp() throws Exception
	{
		fixture = new DropHandlerFixture(temporary.newFolder().toPath());
	}

	@After public void tearDown() throws Exception
	{
		fixture.close();
	}

	@Test public void everyGuardReportsItsReasonWithoutReadingTheLoot() throws Exception
	{
		assertBlocked(DropCaptureResult.Reason.SYNC_OFF, () -> fixture.syncEnabled = false, () -> fixture.syncEnabled = true);
		assertBlocked(DropCaptureResult.Reason.DROP_SYNC_OFF, () -> fixture.dropSyncEnabled = false, () -> fixture.dropSyncEnabled = true);
		assertBlocked(DropCaptureResult.Reason.LOOT_TRACKER_OFF, () -> set("lootTrackerRunning", false), () -> set("lootTrackerRunning", true));
		assertBlocked(DropCaptureResult.Reason.LOGGED_OUT, () -> fixture.gameState = GameState.LOGIN_SCREEN, () -> fixture.gameState = GameState.LOGGED_IN);
		assertBlocked(DropCaptureResult.Reason.SESSION_INACTIVE, () -> set("syncSessionId", null), () -> set("syncSessionId", DropHandlerFixture.SESSION));
		LoginBaselineGate gate = (LoginBaselineGate) DropHandlerFixture.getField(fixture.plugin, "baselineGate");
		assertBlocked(DropCaptureResult.Reason.BASELINE_PENDING, gate::arm, gate::cancel);
		assertBlocked(DropCaptureResult.Reason.PROFILE_CHANGED, () -> set("activeProfileKey", "other-profile"), () -> set("activeProfileKey", "fixture-profile"));
		assertBlocked(DropCaptureResult.Reason.NOT_PAIRED, () -> set("activeCredentials", null), () -> set("activeCredentials", DropHandlerFixture.CREDENTIALS));
		assertBlocked(DropCaptureResult.Reason.UNSUPPORTED_PROFILE, () -> fixture.worldTypes = EnumSet.of(WorldType.SEASONAL),
			() -> fixture.worldTypes = EnumSet.noneOf(WorldType.class));
		assertBlocked(DropCaptureResult.Reason.TRANSPORT_NOT_READY, fixture.dropClient::pause,
			() -> { fixture.dropClient.connect(DropHandlerFixture.CREDENTIALS, DropCaptureTest.CONTEXT); fixture.drainTransport(); });
		assertBlocked(DropCaptureResult.Reason.PLUGIN_STOPPED, () -> set("dropClient", null), () -> set("dropClient", fixture.dropClient));
		assertEquals(0, fixture.definitionReads.get());
		assertNull(fixture.server.takeRequest(250, TimeUnit.MILLISECONDS));
		assertEquals(DropCaptureResult.Reason.SUBMITTED_FOR_LOCAL_SAVE, fixture.plugin.handleLoot(coins(50_000)).reason());
	}

	@Test public void exactCoinBoundaryIsFilteredOrSubmittedThroughTheRealHandler() throws Exception
	{
		DropCaptureResult low = fixture.plugin.handleLoot(coins(49_999));
		assertEquals(DropCaptureResult.Reason.BELOW_THRESHOLD, low.reason());
		assertEquals(Long.valueOf(49_999), low.highestStackGp().get());
		assertFalse(low.event().isPresent());

		DropCaptureResult kept = fixture.plugin.handleLoot(coins(50_000));
		assertEquals(DropCaptureResult.Stage.SUBMITTED, kept.stage());
		JsonObject uploaded = fixture.takeUpload();
		assertEquals(kept.event().get().eventId(), uploaded.get("eventId").getAsString());
		assertEquals(50_000, uploaded.getAsJsonArray("items").get(0).getAsJsonObject().get("quantity").getAsInt());
		assertNull(fixture.uploads.poll(250, TimeUnit.MILLISECONDS));
	}

	@Test public void notableUntradeableRewardStillRequiresTheExactSource() throws Exception
	{
		DropCaptureResult exact = fixture.plugin.handleLoot(loot("Vorkath", new ItemStack(21907, 1)));
		assertEquals(DropCaptureResult.Reason.SUBMITTED_FOR_LOCAL_SAVE, exact.reason());
		assertTrue(exact.notable());
		assertEquals("untradeable", fixture.takeUpload().getAsJsonArray("items").get(0).getAsJsonObject()
			.getAsJsonObject("value").get("kind").getAsString());

		DropCaptureResult renamed = fixture.plugin.handleLoot(loot("RuneGlass TEST not Vorkath", new ItemStack(21907, 1)));
		assertEquals(DropCaptureResult.Reason.BELOW_THRESHOLD, renamed.reason());
		assertFalse(renamed.notable());
	}

	@Test public void observerSeesOnlyEventBusDeliveriesAndBlockedResultsCarryNoLoot() throws Exception
	{
		List<DropCaptureResult> observed = new ArrayList<>();
		fixture.plugin.setDropCaptureObserver(new RuneGlassPlugin.DropCaptureObserver()
		{
			@Override public void onLootHandled(DropCaptureResult result) { observed.add(result); }
			@Override public void onDropStatus(String status) { }
		});
		fixture.plugin.handleLoot(coins(50_000));
		assertTrue(observed.isEmpty());

		fixture.plugin.onLootReceived(coins(1));
		fixture.dropSyncEnabled = false;
		fixture.plugin.onLootReceived(coins(50_000));
		assertEquals(2, observed.size());
		assertEquals(DropCaptureResult.Reason.BELOW_THRESHOLD, observed.get(0).reason());
		DropCaptureResult blocked = observed.get(1);
		assertEquals(DropCaptureResult.Reason.DROP_SYNC_OFF, blocked.reason());
		assertFalse(blocked.sourceType().isPresent());
		assertEquals(0, blocked.itemStacks());
		assertFalse(blocked.highestStackGp().isPresent());
		assertFalse(blocked.event().isPresent());
		fixture.plugin.setDropCaptureObserver(null);
		fixture.plugin.onLootReceived(coins(1));
		assertEquals(2, observed.size());
	}

	@Test public void unsafeRewardsReportInvalidAndMarkTheGap() throws Exception
	{
		fixture.definitions.remove(4151);
		assertEquals(DropCaptureResult.Reason.INVALID_REWARD, fixture.plugin.handleLoot(loot("RuneGlass TEST", new ItemStack(4151, 1))).reason());
		fixture.drainTransport();
		assertTrue(fixture.statuses.stream().anyMatch(status -> status.contains("could not be captured safely")));
	}

	@Test public void workerRefusalIsNotReportedAsSubmitted() throws Exception
	{
		fixture.executor.shutdown();
		assertTrue(fixture.executor.awaitTermination(3, TimeUnit.SECONDS));
		DropCaptureResult refused = fixture.plugin.handleLoot(coins(50_000));
		assertEquals(DropCaptureResult.Reason.LOCAL_SAVE_UNAVAILABLE, refused.reason());
		assertEquals(DropCaptureResult.Stage.NOT_SUBMITTED, refused.stage());
		assertTrue(refused.event().isPresent());
	}

	private void assertBlocked(DropCaptureResult.Reason reason, Step block, Step restore) throws Exception
	{
		block.run();
		DropCaptureResult result = fixture.plugin.handleLoot(coins(50_000));
		assertEquals(reason, result.reason());
		assertEquals(DropCaptureResult.Stage.BLOCKED, result.stage());
		assertFalse(result.sourceType().isPresent());
		assertFalse(result.event().isPresent());
		restore.run();
	}

	private void set(String field, Object value) throws Exception
	{
		DropHandlerFixture.setField(fixture.plugin, field, value);
	}

	static LootReceived coins(int quantity)
	{
		return loot("RuneGlass TEST coins boundary", new ItemStack(995, quantity));
	}

	static LootReceived loot(String source, ItemStack... items)
	{
		return new LootReceived(source, 0, LootRecordType.NPC, List.of(items), 1, null);
	}

	private interface Step
	{
		void run() throws Exception;
	}
}
