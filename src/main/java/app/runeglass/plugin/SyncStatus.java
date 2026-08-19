/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import javax.annotation.Nullable;

/**
 * Immutable view of the sync loop, for display in the side panel.
 */
public final class SyncStatus
{
	public enum Phase
	{
		/** The user has not enabled sync. */
		OFF,
		/** Sync is on but this installation is not linked to an account. */
		NOT_LINKED,
		/** Linked and enabled, waiting for a character to log in. */
		WAITING_FOR_LOGIN,
		/** Everything sent so far has been acknowledged. */
		IDLE,
		/** Events are buffered and waiting to go out. */
		PENDING,
		/** The last attempt failed. {@link #getMessage()} says why. */
		ERROR,
	}

	private final Phase phase;
	private final String message;
	private final long lastSuccessAt;
	private final int pendingCount;

	public SyncStatus(Phase phase, @Nullable String message, long lastSuccessAt, int pendingCount)
	{
		this.phase = phase;
		this.message = message;
		this.lastSuccessAt = lastSuccessAt;
		this.pendingCount = pendingCount;
	}

	public Phase getPhase()
	{
		return phase;
	}

	@Nullable
	public String getMessage()
	{
		return message;
	}

	/** Epoch millis of the last acknowledged batch, or 0 if nothing has been sent yet. */
	public long getLastSuccessAt()
	{
		return lastSuccessAt;
	}

	public int getPendingCount()
	{
		return pendingCount;
	}
}
