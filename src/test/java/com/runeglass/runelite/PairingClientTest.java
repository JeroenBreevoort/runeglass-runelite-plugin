package com.runeglass.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PairingClientTest
{
	private static final String DEVICE_CODE = "ddddddddddddddddddddddddddddddddddddddddddd";
	private static final String CONNECTION_ID = "pcn_ccccccccccccccccccccccccccccccccccccccccccc";

	private MockWebServer server;
	private ScheduledExecutorService executor;
	private PairingClient client;

	@Before
	public void setUp() throws IOException
	{
		server = new MockWebServer();
		server.start();
		executor = Executors.newSingleThreadScheduledExecutor(runnable ->
		{
			Thread thread = new Thread(runnable, "runeglass-pairing-test");
			thread.setDaemon(true);
			return thread;
		});
		client = client(server.url("/"));
	}

	@After
	public void tearDown() throws IOException
	{
		if (client != null)
		{
			client.cancel();
		}
		executor.shutdownNow();
		server.shutdown();
	}

	@Test
	public void pairsThroughPendingStateAndKeepsCredentialInMemory() throws Exception
	{
		server.enqueue(json(201,
			"{\"protocolVersion\":1,\"deviceCode\":\"" + DEVICE_CODE
				+ "\",\"userCode\":\"ABCDE-F2345\","
				+ "\"verificationUri\":\"" + PairingClient.VERIFICATION_URI + "\","
				+ "\"expiresInSeconds\":60,\"pollIntervalSeconds\":1}"));
		server.enqueue(json(202, "{\"protocolVersion\":1,\"error\":\"authorization_pending\"}"));
		server.enqueue(json(200,
			"{\"protocolVersion\":1,\"status\":\"issued\",\"connectionId\":\""
				+ CONNECTION_ID
				+ "\",\"credentialType\":\"Bearer\",\"scope\":\"skills:write\"}"));

		CountDownLatch codeReceived = new CountDownLatch(1);
		CountDownLatch connected = new CountDownLatch(1);
		AtomicReference<PairingClient.Failure> failure = new AtomicReference<>();
		client.start(listener(codeReceived, connected, failure));

		assertTrue(codeReceived.await(2, TimeUnit.SECONDS));
		assertTrue(connected.await(4, TimeUnit.SECONDS));
		assertNull(failure.get());

		RecordedRequest start = assertRequest("/runelite/v1/pairing/start");
		JsonObject startBody = parseBody(start);
		assertEquals(1, startBody.size());
		String credentialHash = startBody.get("connectionCredentialHash").getAsString();

		RecordedRequest pending = assertRequest("/runelite/v1/pairing/token");
		assertEquals(DEVICE_CODE, parseBody(pending).get("deviceCode").getAsString());
		RecordedRequest issued = assertRequest("/runelite/v1/pairing/token");
		assertEquals(DEVICE_CODE, parseBody(issued).get("deviceCode").getAsString());

		Optional<PairingClient.Credentials> credentials = client.getCredentials();
		assertTrue(credentials.isPresent());
		assertEquals(CONNECTION_ID, credentials.get().getConnectionId());
		String rawCredential = credentials.get().getRawCredential();
		assertTrue(rawCredential.matches("^[A-Za-z0-9_-]{43}$"));
		assertEquals(
			credentialHash,
			PairingClient.hashConnectionCredential(rawCredential));
		assertFalse(start.getBody().readUtf8().contains(rawCredential));
		assertNotEquals(rawCredential, credentialHash);

		client.cancelPending();
		assertTrue(client.getCredentials().isPresent());

		client.cancel();
		assertFalse(client.getCredentials().isPresent());
	}

	@Test
	public void rejectsUnexpectedResponseFieldsAndDoesNotPoll() throws Exception
	{
		server.enqueue(json(201,
			"{\"protocolVersion\":1,\"deviceCode\":\"" + DEVICE_CODE
				+ "\",\"userCode\":\"ABCDE-F2345\","
				+ "\"verificationUri\":\"" + PairingClient.VERIFICATION_URI + "\","
				+ "\"expiresInSeconds\":60,\"pollIntervalSeconds\":1,\"unexpected\":true}"));

		CountDownLatch failureReceived = new CountDownLatch(1);
		AtomicReference<PairingClient.Failure> failure = new AtomicReference<>();
		client.start(new PairingClient.Listener()
		{
			@Override
			public void onCode(String userCode, java.time.Instant expiresAt)
			{
				throw new AssertionError("Malformed response must not expose a code");
			}

			@Override
			public void onConnected()
			{
				throw new AssertionError("Malformed response must not connect");
			}

			@Override
			public void onFailure(PairingClient.Failure nextFailure)
			{
				failure.set(nextFailure);
				failureReceived.countDown();
			}
		});

		assertTrue(failureReceived.await(2, TimeUnit.SECONDS));
		assertEquals(PairingClient.Failure.PROTOCOL_ERROR, failure.get());
		assertEquals(1, server.getRequestCount());
		assertFalse(client.getCredentials().isPresent());
	}

	@Test
	public void cancellationStopsPollingAndDiscardsPendingCredential() throws Exception
	{
		server.enqueue(json(201,
			"{\"protocolVersion\":1,\"deviceCode\":\"" + DEVICE_CODE
				+ "\",\"userCode\":\"ABCDE-F2345\","
				+ "\"verificationUri\":\"" + PairingClient.VERIFICATION_URI + "\","
				+ "\"expiresInSeconds\":60,\"pollIntervalSeconds\":1}"));

		CountDownLatch codeReceived = new CountDownLatch(1);
		CountDownLatch terminalCallback = new CountDownLatch(1);
		client.start(new PairingClient.Listener()
		{
			@Override
			public void onCode(String userCode, java.time.Instant expiresAt)
			{
				codeReceived.countDown();
			}

			@Override
			public void onConnected()
			{
				terminalCallback.countDown();
			}

			@Override
			public void onFailure(PairingClient.Failure failure)
			{
				terminalCallback.countDown();
			}
		});

		assertTrue(codeReceived.await(2, TimeUnit.SECONDS));
		client.cancel();
		assertFalse(terminalCallback.await(1_500, TimeUnit.MILLISECONDS));
		assertEquals(1, server.getRequestCount());
		assertFalse(client.getCredentials().isPresent());
	}

	@Test
	public void pollingHonorsRateLimitRetryAfter() throws Exception
	{
		server.enqueue(json(201,
			"{\"protocolVersion\":1,\"deviceCode\":\"" + DEVICE_CODE
				+ "\",\"userCode\":\"ABCDE-F2345\","
				+ "\"verificationUri\":\"" + PairingClient.VERIFICATION_URI + "\","
				+ "\"expiresInSeconds\":60,\"pollIntervalSeconds\":1}"));
		server.enqueue(json(429, "{\"protocolVersion\":1,\"error\":\"rate_limited\"}")
			.setHeader("Retry-After", "1"));
		server.enqueue(json(200,
			"{\"protocolVersion\":1,\"status\":\"issued\",\"connectionId\":\""
				+ CONNECTION_ID
				+ "\",\"credentialType\":\"Bearer\",\"scope\":\"skills:write\"}"));

		CountDownLatch connected = new CountDownLatch(1);
		AtomicReference<PairingClient.Failure> failure = new AtomicReference<>();
		client.start(listener(new CountDownLatch(1), connected, failure));

		assertTrue(connected.await(4, TimeUnit.SECONDS));
		assertNull(failure.get());
		assertEquals(3, server.getRequestCount());
		assertTrue(client.getCredentials().isPresent());
	}

	@Test
	public void reportsDeniedAndExpiredPairingAsTerminalStates() throws Exception
	{
		assertTerminalPairingFailure(
			403,
			"authorization_denied",
			PairingClient.Failure.AUTHORIZATION_DENIED);
		assertTerminalPairingFailure(
			400,
			"expired_device_code",
			PairingClient.Failure.EXPIRED);
	}

	private PairingClient client(HttpUrl baseUrl)
	{
		return new PairingClient(
			new OkHttpClient(),
			new Gson(),
			executor,
			baseUrl,
			Clock.systemUTC());
	}

	private void assertTerminalPairingFailure(
		int status,
		String error,
		PairingClient.Failure expected) throws Exception
	{
		server.enqueue(json(201,
			"{\"protocolVersion\":1,\"deviceCode\":\"" + DEVICE_CODE
				+ "\",\"userCode\":\"ABCDE-F2345\","
				+ "\"verificationUri\":\"" + PairingClient.VERIFICATION_URI + "\","
				+ "\"expiresInSeconds\":60,\"pollIntervalSeconds\":1}"));
		server.enqueue(json(status,
			"{\"protocolVersion\":1,\"error\":\"" + error + "\"}"));

		CountDownLatch codeReceived = new CountDownLatch(1);
		CountDownLatch failed = new CountDownLatch(1);
		AtomicReference<PairingClient.Failure> failure = new AtomicReference<>();
		client.start(new PairingClient.Listener()
		{
			@Override
			public void onCode(String userCode, java.time.Instant expiresAt)
			{
				codeReceived.countDown();
			}

			@Override
			public void onConnected()
			{
				throw new AssertionError("Terminal pairing response must not connect");
			}

			@Override
			public void onFailure(PairingClient.Failure nextFailure)
			{
				failure.set(nextFailure);
				failed.countDown();
			}
		});

		assertTrue(codeReceived.await(2, TimeUnit.SECONDS));
		assertTrue(failed.await(3, TimeUnit.SECONDS));
		assertEquals(expected, failure.get());
		assertFalse(client.getCredentials().isPresent());
	}

	private RecordedRequest assertRequest(String path) throws InterruptedException
	{
		RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
		assertNotNull(request);
		assertEquals("POST", request.getMethod());
		assertEquals(path, request.getPath());
		assertEquals("application/json", request.getHeader("Accept"));
		assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"));
		return request;
	}

	private static JsonObject parseBody(RecordedRequest request)
	{
		return new JsonParser().parse(request.getBody().clone().readUtf8()).getAsJsonObject();
	}

	private static MockResponse json(int status, String body)
	{
		return new MockResponse()
			.setResponseCode(status)
			.setHeader("Content-Type", "application/json")
			.setBody(body);
	}

	private static PairingClient.Listener listener(
		CountDownLatch codeReceived,
		CountDownLatch connected,
		AtomicReference<PairingClient.Failure> failure)
	{
		return new PairingClient.Listener()
		{
			@Override
			public void onCode(String userCode, java.time.Instant expiresAt)
			{
				codeReceived.countDown();
			}

			@Override
			public void onConnected()
			{
				connected.countDown();
			}

			@Override
			public void onFailure(PairingClient.Failure nextFailure)
			{
				failure.set(nextFailure);
			}
		};
	}
}
