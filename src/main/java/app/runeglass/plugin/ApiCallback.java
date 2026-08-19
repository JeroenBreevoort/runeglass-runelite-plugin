/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

/**
 * Result of an API call. Always delivered on an OkHttp dispatcher thread — never the client
 * thread and never the EDT, so implementations must hop threads before touching either.
 */
public interface ApiCallback<T>
{
	void onSuccess(T result);

	void onFailure(ApiError error);
}
