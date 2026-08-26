package com.runeglass.runelite;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class ProtocolJson
{
	private ProtocolJson()
	{
	}

	static JsonObject readObject(Response response, int maximumCharacters) throws IOException
	{
		ResponseBody responseBody = response.body();
		if (maximumCharacters <= 0
			|| responseBody == null
			|| responseBody.contentLength() > maximumCharacters)
		{
			throw new IOException("Invalid RuneGlass response");
		}

		StringBuilder text = new StringBuilder();
		try (Reader reader = responseBody.charStream())
		{
			char[] buffer = new char[1_024];
			int count;
			while ((count = reader.read(buffer)) != -1)
			{
				if (text.length() + count > maximumCharacters)
				{
					throw new IOException("RuneGlass response is too large");
				}
				text.append(buffer, 0, count);
			}
		}

		JsonElement parsed = new JsonParser().parse(text.toString());
		if (!parsed.isJsonObject())
		{
			throw new IOException("RuneGlass response must be an object");
		}
		return parsed.getAsJsonObject();
	}

	static boolean hasExactKeys(JsonObject body, String... keys)
	{
		Set<String> actual = new HashSet<>();
		body.entrySet().forEach(entry -> actual.add(entry.getKey()));
		return actual.equals(new HashSet<>(Arrays.asList(keys)));
	}

	static String stringValue(JsonObject body, String key)
	{
		JsonElement value = body.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
		{
			return "";
		}
		return value.getAsString();
	}

	static int intValue(JsonObject body, String key)
	{
		JsonElement value = body.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
		{
			return -1;
		}
		String serialized = value.toString();
		if (!serialized.matches("^(0|[1-9][0-9]{0,9})$"))
		{
			return -1;
		}
		try
		{
			return Integer.parseInt(serialized);
		}
		catch (NumberFormatException exception)
		{
			return -1;
		}
	}
}
