/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs the account pairing handshake.
 * <p>
 * The plugin asks the backend for a short code, shows it in the side panel, and polls until the
 * phone app claims it. The poll carries a {@code clientNonce} that never leaves this process, so
 * seeing the code is not enough to collect the device token — an onlooker can at worst claim the
 * pairing to their own account, which fails visibly rather than leaking a credential.
 */
@Slf4j
@Singleton
public class PairingService
{
	private static final long POLL_INTERVAL_MS = 2000L;
	/** Stop polling a little after the server's own TTL, in case the clocks disagree slightly. */
	private static final long EXPIRY_GRACE_MS = 5000L;

	private final RuneGlassClient client;
	private final RuneGlassConfig config;
	private final ScheduledExecutorService executor;

	private final AtomicReference<PairingState> state = new AtomicReference<>(PairingState.idle());

	/** Guarded by {@code this}: only touched from start/cancel/shutdown. */
	private ScheduledFuture<?> pollFuture;
	private volatile String clientNonce;
	private volatile String pairingId;

	private volatile Consumer<PairingState> listener;

	@Inject
	PairingService(RuneGlassClient client, RuneGlassConfig config, ScheduledExecutorService executor)
	{
		this.client = client;
		this.config = config;
		this.executor = executor;
	}

	public void setListener(@Nullable Consumer<PairingState> listener)
	{
		this.listener = listener;
	}

	public PairingState getState()
	{
		return state.get();
	}

	/**
	 * Begins a pairing attempt. Safe to call from the EDT — the request itself is asynchronous.
	 */
	public synchronized void start()
	{
		if (state.get().isInProgress())
		{
			return;
		}

		cancelPoll();

		final String nonce = newNonce();
		clientNonce = nonce;
		pairingId = null;
		publish(PairingState.starting());

		client.pairStart(nonce, new ApiCallback<RuneGlassApi.PairStartResponse>()
		{
			@Override
			public void onSuccess(RuneGlassApi.PairStartResponse result)
			{
				if (result.pairingId == null || result.code == null)
				{
					publish(PairingState.failed("Server did not return a pairing code"));
					return;
				}

				// A slow response could land after the user cancelled and started again.
				if (!nonce.equals(clientNonce))
				{
					log.debug("Discarding stale pairing response");
					return;
				}

				pairingId = result.pairingId;
				publish(PairingState.waiting(result.code, result.expiresAt));
				schedulePoll(nonce, result.expiresAt);
			}

			@Override
			public void onFailure(ApiError error)
			{
				publish(PairingState.failed(describe(error)));
			}
		});
	}

	/**
	 * Abandons the current attempt and returns to idle.
	 */
	public synchronized void cancel()
	{
		cancelPoll();
		clientNonce = null;
		pairingId = null;
		publish(PairingState.idle());
	}

	/**
	 * Drops the stored credential. The backend keeps its side until the user revokes it in the app.
	 */
	public synchronized void unlink()
	{
		cancelPoll();
		clientNonce = null;
		pairingId = null;
		config.deviceToken("");
		config.linkedAccountName("");
		publish(PairingState.idle());
	}

	/**
	 * Cancels in-flight work. Never blocks; the executor belongs to RuneLite, so it is not shut down.
	 */
	public synchronized void shutdown()
	{
		cancelPoll();
		listener = null;
		clientNonce = null;
		pairingId = null;
	}

	private synchronized void schedulePoll(String nonce, long expiresAt)
	{
		cancelPoll();
		pollFuture = executor.scheduleWithFixedDelay(
			() -> poll(nonce, expiresAt), POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
	}

	private void poll(String nonce, long expiresAt)
	{
		if (!nonce.equals(clientNonce))
		{
			return; // superseded by a newer attempt
		}

		if (expiresAt > 0 && System.currentTimeMillis() > expiresAt + EXPIRY_GRACE_MS)
		{
			expire("Pairing code expired. Try again.");
			return;
		}

		final String id = pairingId;
		if (id == null)
		{
			return;
		}

		client.pairPoll(id, nonce, new ApiCallback<RuneGlassApi.PairPollResponse>()
		{
			@Override
			public void onSuccess(RuneGlassApi.PairPollResponse result)
			{
				if (!nonce.equals(clientNonce))
				{
					return;
				}

				if (RuneGlassApi.STATUS_CLAIMED.equals(result.status))
				{
					if (result.deviceToken == null || result.deviceToken.isEmpty())
					{
						expire("Server claimed the pairing but returned no token");
						return;
					}
					complete(result.deviceToken, result.accountName);
				}
				else if (RuneGlassApi.STATUS_EXPIRED.equals(result.status))
				{
					expire("Pairing code expired. Try again.");
				}
				// STATUS_PENDING: keep waiting.
			}

			@Override
			public void onFailure(ApiError error)
			{
				if (!nonce.equals(clientNonce))
				{
					return;
				}

				// A dropped packet shouldn't kill a five-minute window; keep polling until expiry.
				if (error.isRetryable())
				{
					log.debug("Transient pairing poll failure: {}", error);
					return;
				}

				expire(describe(error));
			}
		});
	}

	private synchronized void complete(String token, @Nullable String accountName)
	{
		cancelPoll();
		clientNonce = null;
		pairingId = null;

		final String name = accountName != null ? accountName : "";
		config.deviceToken(token);
		config.linkedAccountName(name);

		log.info("RuneGlass linked to account {}", name.isEmpty() ? "(unnamed)" : name);
		publish(PairingState.linked(name));
	}

	private synchronized void expire(String message)
	{
		cancelPoll();
		clientNonce = null;
		pairingId = null;
		publish(PairingState.failed(message));
	}

	private synchronized void cancelPoll()
	{
		if (pollFuture != null)
		{
			pollFuture.cancel(false);
			pollFuture = null;
		}
	}

	private void publish(PairingState next)
	{
		state.set(next);

		final Consumer<PairingState> current = listener;
		if (current != null)
		{
			current.accept(next);
		}
	}

	private static String describe(ApiError error)
	{
		if (error.getStatus() == 0)
		{
			return "Could not reach RuneGlass. Check your connection.";
		}
		return error.getMessage();
	}

	private static String newNonce()
	{
		final byte[] bytes = new byte[24];
		new SecureRandom().nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
