package com.runeglass.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BirdHouseClientTest
{
	private static final String RAW_CREDENTIAL = "rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr";
	private static final String CONNECTION_ID = "pcn_ccccccccccccccccccccccccccccccccccccccccccc";
	private static final Instant OBSERVED_AT = Instant.parse("2026-08-28T10:00:00Z");
	private static final String SESSION_ID = "0b54873e-c169-4de7-9bd0-abc879f84d2f";

	private MockWebServer server;
	private ScheduledExecutorService executor;
	private SemanticSnapshotClient<BirdHouseSnapshot> client;

	@Before
	public void setUp() throws IOException
	{
		server = new MockWebServer();
		server.start();
		executor = Executors.newSingleThreadScheduledExecutor();
		client = new SemanticSnapshotClient<>(
			new OkHttpClient(),
			new Gson(),
			executor,
			server.url("/"),
			Clock.fixed(OBSERVED_AT, ZoneOffset.UTC),
			1,
			() -> 0L,
			"runelite/v1/bird-houses",
			(context, connectionId, snapshot) -> SemanticSnapshotPayload.create(
				context,
				connectionId,
				snapshot.getSnapshotId(),
				snapshot.getObservedAt(),
				"houses",
				snapshot.getHouses()));
	}

	@After
	public void tearDown() throws IOException
	{
		client.discard();
		executor.shutdownNow();
		server.shutdown();
	}

	@Test
	public void sendsAnExactAuthenticatedSemanticSnapshot() throws Exception
	{
		server.enqueue(accepted());
		CountDownLatch accepted = new CountDownLatch(1);
		AtomicReference<SemanticSnapshotClient.Failure> failure = new AtomicReference<>();
		client.connect(credentials(), context(), listener(accepted, failure));
		assertTrue(client.publish(snapshot()));

		RecordedRequest request = server.takeRequest(3, TimeUnit.SECONDS);
		assertEquals("/runelite/v1/bird-houses", request.getPath());
		assertEquals("Bearer " + RAW_CREDENTIAL, request.getHeader("Authorization"));
		String bodyText = request.getBody().readUtf8();
		assertFalse(bodyText.contains(RAW_CREDENTIAL));
		JsonObject body = new JsonParser().parse(bodyText).getAsJsonObject();
		assertExactKeys(
			body,
			"protocolVersion",
			"snapshotId",
			"connectionId",
			"sessionId",
			"observedAt",
			"client",
			"character",
			"houses");
		assertEquals(CONNECTION_ID, body.get("connectionId").getAsString());
		assertEquals(4, body.getAsJsonArray("houses").size());
		JsonObject empty = body.getAsJsonArray("houses").get(0).getAsJsonObject();
		assertExactKeys(empty, "space", "state");
		JsonObject seeded = body.getAsJsonArray("houses").get(2).getAsJsonObject();
		assertExactKeys(seeded, "space", "state", "tier", "startedAt", "readyAt");

		assertTrue(accepted.await(3, TimeUnit.SECONDS));
		assertNull(failure.get());
	}

	@Test
	public void discardDropsAnUnsentObservation()
	{
		client.connect(credentials(), context(), listener(new CountDownLatch(0), new AtomicReference<>()));
		client.discard();
		assertFalse(client.publish(snapshot()));
	}

	private static PairingClient.Credentials credentials()
	{
		return new PairingClient.Credentials(CONNECTION_ID, RAW_CREDENTIAL);
	}

	private static SyncContext context()
	{
		return new SyncContext(
			SESSION_ID,
			"Iron Jeromey",
			"ironman",
			"standard",
			"1.1.0",
			"1.12.36",
			231);
	}

	private static BirdHouseSnapshot snapshot()
	{
		return new BirdHouseSnapshot(
			"018f35d8-29e4-7cc2-8a58-f9a76b1f3d91",
			OBSERVED_AT,
			Arrays.asList(
				new BirdHouseObservation(BirdHouseSpace.MEADOW_NORTH, BirdHouseState.EMPTY, null, null, null),
				new BirdHouseObservation(BirdHouseSpace.MEADOW_SOUTH, BirdHouseState.BUILT, BirdHouseTier.YEW, null, null),
				new BirdHouseObservation(
					BirdHouseSpace.VALLEY_NORTH,
					BirdHouseState.SEEDED,
					BirdHouseTier.REDWOOD,
					OBSERVED_AT,
					OBSERVED_AT.plusSeconds(50 * 60)),
				new BirdHouseObservation(BirdHouseSpace.VALLEY_SOUTH, BirdHouseState.SEEDED, BirdHouseTier.MAGIC, null, null)));
	}

	private static SemanticSnapshotClient.Listener listener(
		CountDownLatch accepted,
		AtomicReference<SemanticSnapshotClient.Failure> failure)
	{
		return new SemanticSnapshotClient.Listener()
		{
			@Override
			public void onAccepted(Instant serverTime)
			{
				accepted.countDown();
			}

			@Override
			public void onRetryScheduled()
			{
			}

			@Override
			public void onFailure(SemanticSnapshotClient.Failure value)
			{
				failure.set(value);
			}
		};
	}

	private static MockResponse accepted()
	{
		return new MockResponse()
			.setResponseCode(200)
			.setHeader("Content-Type", "application/json")
			.setBody("{\"protocolVersion\":1,\"status\":\"accepted\","
				+ "\"serverTime\":\"2026-08-28T10:00:01Z\","
				+ "\"nextUploadAfterSeconds\":5}");
	}

	private static void assertExactKeys(JsonObject body, String... keys)
	{
		assertEquals(keys.length, body.size());
		for (String key : keys)
		{
			assertTrue(body.has(key));
		}
	}
}
