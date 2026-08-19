/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

/**
 * A failed API call, in a form the panel can show a user without leaking HTTP details.
 */
public final class ApiError
{
	/** HTTP status, or 0 when the request never got a response. */
	private final int status;
	private final String code;
	private final String message;

	public ApiError(int status, String code, String message)
	{
		this.status = status;
		this.code = code;
		this.message = message;
	}

	public static ApiError network(String message)
	{
		return new ApiError(0, "network", message);
	}

	public int getStatus()
	{
		return status;
	}

	public String getCode()
	{
		return code;
	}

	public String getMessage()
	{
		return message;
	}

	/** True when the device token is missing, revoked, or rejected — the link is dead. */
	public boolean isUnauthorized()
	{
		return status == 401 || status == 403;
	}

	/** True when retrying later might succeed: transport failures and server-side faults. */
	public boolean isRetryable()
	{
		return status == 0 || status == 429 || status >= 500;
	}

	@Override
	public String toString()
	{
		return status + " " + code + ": " + message;
	}
}
