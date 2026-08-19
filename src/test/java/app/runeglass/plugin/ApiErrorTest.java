/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * These predicates decide whether a pairing attempt survives a hiccup or dies, so the
 * classification is worth pinning down.
 */
public class ApiErrorTest
{
	@Test
	public void transportFailuresAreRetryable()
	{
		assertTrue(ApiError.network("connection reset").isRetryable());
	}

	@Test
	public void serverFaultsAndThrottlingAreRetryable()
	{
		assertTrue(new ApiError(500, "boom", "").isRetryable());
		assertTrue(new ApiError(503, "unavailable", "").isRetryable());
		assertTrue(new ApiError(429, "slow_down", "").isRetryable());
	}

	@Test
	public void clientMistakesAreNotRetryable()
	{
		assertFalse(new ApiError(400, "bad_request", "").isRetryable());
		assertFalse(new ApiError(404, "not_found", "").isRetryable());
	}

	@Test
	public void authFailuresAreTerminalNotRetryable()
	{
		final ApiError unauthorized = new ApiError(401, "unauthorized", "");
		assertTrue(unauthorized.isUnauthorized());
		assertFalse("a revoked token will never start working again", unauthorized.isRetryable());

		assertTrue(new ApiError(403, "nonce_mismatch", "").isUnauthorized());
	}

	@Test
	public void ordinaryFailuresAreNotAuthFailures()
	{
		assertFalse(ApiError.network("timeout").isUnauthorized());
		assertFalse(new ApiError(500, "boom", "").isUnauthorized());
	}
}
