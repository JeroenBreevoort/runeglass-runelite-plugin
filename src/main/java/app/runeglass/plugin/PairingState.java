/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import javax.annotation.Nullable;

/**
 * Immutable snapshot of where a pairing attempt has got to. Published across threads, so every
 * field is final and instances are never mutated after construction.
 */
public final class PairingState
{
	public enum Phase
	{
		/** No attempt in progress. */
		IDLE,
		/** Asking the backend for a code. */
		STARTING,
		/** Showing a code, waiting for the app to claim it. */
		WAITING,
		/** Claimed; a device token has been stored. */
		LINKED,
		/** Gave up. {@link #getMessage()} says why. */
		FAILED,
	}

	private static final PairingState IDLE_STATE = new PairingState(Phase.IDLE, null, 0L, null);

	private final Phase phase;
	private final String code;
	private final long expiresAt;
	private final String message;

	private PairingState(Phase phase, @Nullable String code, long expiresAt, @Nullable String message)
	{
		this.phase = phase;
		this.code = code;
		this.expiresAt = expiresAt;
		this.message = message;
	}

	public static PairingState idle()
	{
		return IDLE_STATE;
	}

	public static PairingState starting()
	{
		return new PairingState(Phase.STARTING, null, 0L, null);
	}

	public static PairingState waiting(String code, long expiresAt)
	{
		return new PairingState(Phase.WAITING, code, expiresAt, null);
	}

	public static PairingState linked(String accountName)
	{
		return new PairingState(Phase.LINKED, null, 0L, accountName);
	}

	public static PairingState failed(String message)
	{
		return new PairingState(Phase.FAILED, null, 0L, message);
	}

	public Phase getPhase()
	{
		return phase;
	}

	@Nullable
	public String getCode()
	{
		return code;
	}

	public long getExpiresAt()
	{
		return expiresAt;
	}

	@Nullable
	public String getMessage()
	{
		return message;
	}

	public boolean isInProgress()
	{
		return phase == Phase.STARTING || phase == Phase.WAITING;
	}
}
