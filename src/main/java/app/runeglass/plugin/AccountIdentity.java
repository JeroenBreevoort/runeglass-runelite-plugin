/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/**
 * An immutable snapshot of which account is logged in.
 * <p>
 * Built on the client thread and then handed to other threads, so every field is final and the
 * world type list is unmodifiable.
 */
public final class AccountIdentity
{
	/** Returned by {@code Client#getAccountHash()} when no account is logged in. */
	public static final long NO_ACCOUNT = -1L;

	/**
	 * Account types indexed by the value of {@code VarbitID.IRONMAN}.
	 * <p>
	 * RuneLite's own {@code AccountType} enum is deprecated, and these strings are part of the
	 * wire contract with the backend, so the vocabulary is defined here rather than borrowed.
	 */
	private static final String[] ACCOUNT_TYPES = {
		"NORMAL",
		"IRONMAN",
		"ULTIMATE_IRONMAN",
		"HARDCORE_IRONMAN",
		"GROUP_IRONMAN",
		"HARDCORE_GROUP_IRONMAN",
		"UNRANKED_GROUP_IRONMAN",
	};

	public static String accountTypeOf(int varbitValue)
	{
		return varbitValue >= 0 && varbitValue < ACCOUNT_TYPES.length
			? ACCOUNT_TYPES[varbitValue]
			: "UNKNOWN";
	}

	private final long accountHash;
	private final String displayName;
	private final String accountType;
	private final List<String> worldTypes;

	public AccountIdentity(long accountHash, @Nullable String displayName, String accountType, List<String> worldTypes)
	{
		this.accountHash = accountHash;
		this.displayName = displayName;
		this.accountType = accountType;
		this.worldTypes = Collections.unmodifiableList(worldTypes);
	}

	public long getAccountHash()
	{
		return accountHash;
	}

	/**
	 * The account hash as a decimal string, which is how it must travel on the wire — see
	 * {@link RuneGlassApi.IngestRequest#accountHash}.
	 */
	public String getAccountHashString()
	{
		return Long.toString(accountHash);
	}

	@Nullable
	public String getDisplayName()
	{
		return displayName;
	}

	public String getAccountType()
	{
		return accountType;
	}

	public List<String> getWorldTypes()
	{
		return worldTypes;
	}

	public boolean isPresent()
	{
		return accountHash != NO_ACCOUNT;
	}

	@Override
	public String toString()
	{
		return "AccountIdentity{hash=" + accountHash + ", name=" + displayName + ", type=" + accountType + '}';
	}
}
