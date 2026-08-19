/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Drives the pairing state machine with a stub backend that answers synchronously, so the
 * transitions are deterministic and no scheduler timing is involved.
 */
public class PairingServiceTest
{
	private StubConfig config;
	private StubClient client;
	private ScheduledExecutorService executor;
	private PairingService service;

	@Before
	public void setUp()
	{
		config = new StubConfig();
		client = new StubClient(config);
		executor = Executors.newSingleThreadScheduledExecutor();
		service = new PairingService(client, config, executor);
	}

	@After
	public void tearDown()
	{
		service.shutdown();
		executor.shutdownNow();
	}

	@Test
	public void startShowsTheCodeReturnedByTheBackend()
	{
		client.startResponse = pairStart("pair-1", "K7P-M2Q", System.currentTimeMillis() + 300_000);

		service.start();

		final PairingState state = service.getState();
		assertEquals(PairingState.Phase.WAITING, state.getPhase());
		assertEquals("K7P-M2Q", state.getCode());
	}

	@Test
	public void aFailedStartSurfacesTheServerMessage()
	{
		client.startError = new ApiError(400, "bad_request", "clientNonce is required");

		service.start();

		assertEquals(PairingState.Phase.FAILED, service.getState().getPhase());
		assertEquals("clientNonce is required", service.getState().getMessage());
	}

	@Test
	public void networkFailureDuringStartGivesAReadableMessage()
	{
		client.startError = ApiError.network("connection refused");

		service.start();

		assertEquals(PairingState.Phase.FAILED, service.getState().getPhase());
		assertTrue(service.getState().getMessage().contains("Could not reach RuneGlass"));
	}

	@Test
	public void aStartWithoutACodeIsTreatedAsFailure()
	{
		client.startResponse = pairStart("pair-1", null, 0L);

		service.start();

		assertEquals(PairingState.Phase.FAILED, service.getState().getPhase());
	}

	@Test
	public void theNoncePassedToTheBackendIsNotTheCode()
	{
		client.startResponse = pairStart("pair-1", "K7P-M2Q", System.currentTimeMillis() + 300_000);

		service.start();

		assertNotNull(client.lastNonce);
		assertTrue("nonce should be long enough to be unguessable", client.lastNonce.length() >= 32);
		assertTrue("nonce must never equal the displayed code", !client.lastNonce.equals("K7P-M2Q"));
	}

	@Test
	public void startIsIgnoredWhileAnAttemptIsAlreadyRunning()
	{
		client.startResponse = pairStart("pair-1", "AAA-BBB", System.currentTimeMillis() + 300_000);
		service.start();

		client.startCalls = 0;
		client.startResponse = pairStart("pair-2", "CCC-DDD", System.currentTimeMillis() + 300_000);
		service.start();

		assertEquals("second start should be a no-op", 0, client.startCalls);
		assertEquals("AAA-BBB", service.getState().getCode());
	}

	@Test
	public void cancelReturnsToIdle()
	{
		client.startResponse = pairStart("pair-1", "AAA-BBB", System.currentTimeMillis() + 300_000);
		service.start();

		service.cancel();

		assertEquals(PairingState.Phase.IDLE, service.getState().getPhase());
		assertNull(service.getState().getCode());
	}

	@Test
	public void unlinkClearsTheStoredCredential()
	{
		config.deviceToken("rg_secret");
		config.linkedAccountName("Jeroen");

		service.unlink();

		assertEquals("", config.deviceToken());
		assertEquals("", config.linkedAccountName());
		assertEquals(PairingState.Phase.IDLE, service.getState().getPhase());
	}

	@Test
	public void listenerSeesEveryTransition()
	{
		final StringBuilder seen = new StringBuilder();
		service.setListener(state -> seen.append(state.getPhase()).append(' '));

		client.startResponse = pairStart("pair-1", "AAA-BBB", System.currentTimeMillis() + 300_000);
		service.start();
		service.cancel();

		assertEquals("STARTING WAITING IDLE ", seen.toString());
	}

	// ------------------------------------------------------------------

	private static RuneGlassApi.PairStartResponse pairStart(String id, String code, long expiresAt)
	{
		final RuneGlassApi.PairStartResponse response = new RuneGlassApi.PairStartResponse();
		response.pairingId = id;
		response.code = code;
		response.expiresAt = expiresAt;
		return response;
	}

	/** Answers on the calling thread so state transitions are observable immediately. */
	private static final class StubClient extends RuneGlassClient
	{
		RuneGlassApi.PairStartResponse startResponse;
		ApiError startError;
		String lastNonce;
		int startCalls;

		StubClient(RuneGlassConfig config)
		{
			super(null, null, config);
		}

		@Override
		public void pairStart(String clientNonce, ApiCallback<RuneGlassApi.PairStartResponse> callback)
		{
			startCalls++;
			lastNonce = clientNonce;

			if (startError != null)
			{
				callback.onFailure(startError);
			}
			else
			{
				callback.onSuccess(startResponse);
			}
		}

		@Override
		public void pairPoll(String pairingId, String nonce, ApiCallback<RuneGlassApi.PairPollResponse> callback)
		{
			// Polling is scheduled two seconds out; these tests finish long before it fires.
		}
	}

	/** Only the mutable link fields need real behaviour; everything else uses interface defaults. */
	private static final class StubConfig implements RuneGlassConfig
	{
		private String token = "";
		private String accountName = "";

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
		public String linkedAccountName()
		{
			return accountName;
		}

		@Override
		public void linkedAccountName(String value)
		{
			accountName = value;
		}
	}
}
