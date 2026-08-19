/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.Socket;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * Drives the real HTTP client against the mock backend, so the Java DTOs and the mock's JSON
 * field names are checked against each other rather than assumed to agree.
 * <p>
 * Skips itself when the mock is not running. Start it with:
 * <pre>node tools/mock-server/server.js</pre>
 */
public class PairingIntegrationTest
{
	private static final String BASE_URL = "http://localhost:8787";
	private static final MediaType JSON = MediaType.get("application/json");

	private OkHttpClient http;
	private Gson gson;
	private RuneGlassClient client;

	@Before
	public void setUp()
	{
		Assume.assumeTrue("mock server not running on " + BASE_URL, mockIsUp());

		http = new OkHttpClient();
		gson = new Gson();
		client = new RuneGlassClient(http, gson, new MockConfig());
	}

	@Test
	public void completesTheHandshakeAgainstTheMock() throws Exception
	{
		final RuneGlassApi.PairStartResponse started = await(cb -> client.pairStart("integration-nonce", cb));

		assertNotNull("pairingId did not deserialize — field name mismatch?", started.pairingId);
		assertNotNull("code did not deserialize — field name mismatch?", started.code);
		assertTrue("expiresAt should be in the future", started.expiresAt > System.currentTimeMillis());
		assertTrue("code should look like XXX-XXX", started.code.matches("[A-Z0-9]{3}-[A-Z0-9]{3}"));

		// Before the app claims it, the backend must not hand out anything.
		final RuneGlassApi.PairPollResponse pending =
			await(cb -> client.pairPoll(started.pairingId, "integration-nonce", cb));
		assertEquals(RuneGlassApi.STATUS_PENDING, pending.status);

		claimAsApp(started.code);

		final RuneGlassApi.PairPollResponse claimed =
			await(cb -> client.pairPoll(started.pairingId, "integration-nonce", cb));
		assertEquals(RuneGlassApi.STATUS_CLAIMED, claimed.status);
		assertNotNull("deviceToken did not deserialize", claimed.deviceToken);
		assertTrue(claimed.deviceToken.startsWith("rg_"));
		assertEquals("Integration", claimed.accountName);
	}

	@Test
	public void theWrongNonceIsRejected() throws Exception
	{
		final RuneGlassApi.PairStartResponse started = await(cb -> client.pairStart("real-nonce", cb));
		claimAsApp(started.code);

		// Someone who only saw the code, polling with a nonce they guessed.
		final ApiError error = this.<RuneGlassApi.PairPollResponse>awaitFailure(
			cb -> client.pairPoll(started.pairingId, "guessed-nonce", cb));

		assertEquals(403, error.getStatus());
		assertTrue(error.isUnauthorized());
	}

	@Test
	public void ingestRejectsAnAccountHashSentAsANumber() throws Exception
	{
		// Guards the precision trap: the backend must refuse anything but a decimal string.
		final Request request = new Request.Builder()
			.url(BASE_URL + RuneGlassApi.INGEST)
			.header("Authorization", "Bearer " + linkedToken())
			.post(RequestBody.create(JSON, "{\"accountHash\":6041938472910385761,\"events\":[]}"))
			.build();

		try (Response response = http.newCall(request).execute())
		{
			assertEquals(400, response.code());
			assertTrue(response.body().string().contains("bad_account_hash"));
		}
	}

	@Test
	public void ingestAcceptsAWellFormedBatch() throws Exception
	{
		final RuneGlassApi.IngestRequest batch = new RuneGlassApi.IngestRequest();
		batch.accountHash = "6041938472910385761";
		batch.displayName = "Zezima";
		batch.accountType = "IRONMAN";
		batch.worldTypes = Collections.singletonList("MEMBERS");
		batch.sessionId = "integration-session";
		batch.sentAt = System.currentTimeMillis();
		batch.events = Collections.singletonList(new RuneGlassApi.Event(
			0L, System.currentTimeMillis(), RuneGlassApi.Kind.LEVEL_UP,
			Collections.singletonMap("skill", "SLAYER")));

		final MockConfig config = new MockConfig();
		config.token = linkedToken();
		final RuneGlassClient linked = new RuneGlassClient(http, gson, config);

		final RuneGlassApi.IngestResponse response = await(cb -> linked.ingest(batch, cb));

		assertTrue("backend did not accept the batch", response.ok);
		assertEquals(0L, response.ackSeq);
	}

	@Test
	public void ingestWithoutATokenFailsFastWithoutHittingTheNetwork() throws Exception
	{
		final RuneGlassApi.IngestRequest batch = new RuneGlassApi.IngestRequest();
		batch.accountHash = "1";

		final ApiError error = this.<RuneGlassApi.IngestResponse>awaitFailure(cb -> client.ingest(batch, cb));

		assertEquals("not_linked", error.getCode());
		assertTrue(error.isUnauthorized());
	}

	// ------------------------------------------------------------------

	/** Runs a full pairing and returns a usable device token. */
	private String linkedToken() throws Exception
	{
		final RuneGlassApi.PairStartResponse started = await(cb -> client.pairStart("token-nonce", cb));
		claimAsApp(started.code);
		final RuneGlassApi.PairPollResponse claimed =
			await(cb -> client.pairPoll(started.pairingId, "token-nonce", cb));
		return claimed.deviceToken;
	}

	/** Stands in for the phone app claiming the code. */
	private void claimAsApp(String code) throws IOException
	{
		final Request request = new Request.Builder()
			.url(BASE_URL + "/dev/claim")
			.post(RequestBody.create(JSON, "{\"code\":\"" + code + "\",\"accountName\":\"Integration\"}"))
			.build();

		try (Response response = http.newCall(request).execute())
		{
			assertEquals("mock could not claim the code", 200, response.code());
		}
	}

	private interface Call<T>
	{
		void invoke(ApiCallback<T> callback);
	}

	private <T> T await(Call<T> call) throws InterruptedException
	{
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicReference<T> success = new AtomicReference<>();
		final AtomicReference<ApiError> failure = new AtomicReference<>();

		call.invoke(new ApiCallback<T>()
		{
			@Override
			public void onSuccess(T result)
			{
				success.set(result);
				latch.countDown();
			}

			@Override
			public void onFailure(ApiError error)
			{
				failure.set(error);
				latch.countDown();
			}
		});

		assertTrue("call did not complete within 10s", latch.await(10, TimeUnit.SECONDS));
		if (failure.get() != null)
		{
			fail("expected success but got " + failure.get());
		}
		return success.get();
	}

	private <T> ApiError awaitFailure(Call<T> call) throws InterruptedException
	{
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicReference<ApiError> failure = new AtomicReference<>();

		call.invoke(new ApiCallback<T>()
		{
			@Override
			public void onSuccess(T result)
			{
				latch.countDown();
			}

			@Override
			public void onFailure(ApiError error)
			{
				failure.set(error);
				latch.countDown();
			}
		});

		assertTrue("call did not complete within 10s", latch.await(10, TimeUnit.SECONDS));
		assertNotNull("expected the call to fail", failure.get());
		return failure.get();
	}

	private static boolean mockIsUp()
	{
		try (Socket socket = new Socket("localhost", 8787))
		{
			return socket.isConnected();
		}
		catch (IOException e)
		{
			return false;
		}
	}

	private static final class MockConfig implements RuneGlassConfig
	{
		private String token = "";

		@Override
		public String apiBaseUrl()
		{
			return BASE_URL;
		}

		@Override
		public String deviceToken()
		{
			return token;
		}

		@Override
		public void deviceToken(String value)
		{
			token = value;
		}

		@Override
		public void linkedAccountName(String value)
		{
		}
	}
}
