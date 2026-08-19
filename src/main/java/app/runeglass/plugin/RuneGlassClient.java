/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Talks to the RuneGlass backend.
 * <p>
 * Every call is asynchronous via OkHttp's dispatcher; nothing here ever blocks the client thread.
 * Callbacks are invoked on OkHttp's thread pool.
 */
@Slf4j
@Singleton
public class RuneGlassClient
{
	static final String PLUGIN_VERSION = "0.1.0";

	private static final MediaType JSON = MediaType.get("application/json");

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final RuneGlassConfig config;

	@Inject
	RuneGlassClient(OkHttpClient httpClient, Gson gson, RuneGlassConfig config)
	{
		this.httpClient = httpClient;
		this.gson = gson;
		this.config = config;
	}

	public void pairStart(String clientNonce, ApiCallback<RuneGlassApi.PairStartResponse> callback)
	{
		final RuneGlassApi.PairStartRequest body = new RuneGlassApi.PairStartRequest();
		body.clientNonce = clientNonce;
		body.pluginVersion = PLUGIN_VERSION;

		post(RuneGlassApi.PAIR_START, body, null, RuneGlassApi.PairStartResponse.class, callback);
	}

	public void pairPoll(String pairingId, String clientNonce, ApiCallback<RuneGlassApi.PairPollResponse> callback)
	{
		final RuneGlassApi.PairPollRequest body = new RuneGlassApi.PairPollRequest();
		body.pairingId = pairingId;
		body.clientNonce = clientNonce;

		post(RuneGlassApi.PAIR_POLL, body, null, RuneGlassApi.PairPollResponse.class, callback);
	}

	public void ingest(RuneGlassApi.IngestRequest body, ApiCallback<RuneGlassApi.IngestResponse> callback)
	{
		final String token = config.deviceToken();
		if (token.isEmpty())
		{
			callback.onFailure(new ApiError(401, "not_linked", "This installation is not linked to a RuneGlass account"));
			return;
		}

		post(RuneGlassApi.INGEST, body, token, RuneGlassApi.IngestResponse.class, callback);
	}

	private <T> void post(String path, Object body, @Nullable String token, Class<T> type, ApiCallback<T> callback)
	{
		final HttpUrl url = resolve(path);
		if (url == null)
		{
			callback.onFailure(new ApiError(0, "bad_base_url", "API base URL is not a valid URL: " + config.apiBaseUrl()));
			return;
		}

		final Request.Builder request = new Request.Builder()
			.url(url)
			.header(RuneGlassApi.HEADER_PLUGIN_VERSION, PLUGIN_VERSION)
			.post(RequestBody.create(JSON, gson.toJson(body)));

		if (token != null)
		{
			request.header("Authorization", "Bearer " + token);
		}

		httpClient.newCall(request.build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("{} failed", path, e);
				callback.onFailure(ApiError.network(e.getMessage() != null ? e.getMessage() : "network error"));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody responseBody = response.body())
				{
					final String raw = responseBody != null ? responseBody.string() : "";

					if (!response.isSuccessful())
					{
						callback.onFailure(parseError(response.code(), raw));
						return;
					}

					final T parsed = gson.fromJson(raw, type);
					if (parsed == null)
					{
						callback.onFailure(new ApiError(response.code(), "empty_response", "Server returned an empty body"));
						return;
					}

					callback.onSuccess(parsed);
				}
				catch (JsonSyntaxException e)
				{
					callback.onFailure(new ApiError(response.code(), "bad_response", "Server returned malformed JSON"));
				}
				catch (IOException e)
				{
					callback.onFailure(ApiError.network("Could not read response: " + e.getMessage()));
				}
			}
		});
	}

	private ApiError parseError(int status, String raw)
	{
		try
		{
			final RuneGlassApi.ErrorResponse parsed = gson.fromJson(raw, RuneGlassApi.ErrorResponse.class);
			if (parsed != null && parsed.message != null)
			{
				return new ApiError(status, parsed.error != null ? parsed.error : "error", parsed.message);
			}
		}
		catch (JsonSyntaxException ignored)
		{
			// Fall through to the generic message below.
		}

		return new ApiError(status, "http_" + status, "Server returned HTTP " + status);
	}

	@Nullable
	private HttpUrl resolve(String path)
	{
		final HttpUrl base = HttpUrl.parse(config.apiBaseUrl().trim());
		return base == null ? null : base.resolve(path);
	}
}
