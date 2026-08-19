/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import java.util.Collections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class AccountIdentityTest
{
	/**
	 * The whole point of sending the hash as a string: a real account hash exceeds 2^53, so any
	 * round trip through a JSON number would corrupt it.
	 */
	@Test
	public void accountHashSurvivesAsStringBeyondDoublePrecision()
	{
		final long hash = 6041938472910385761L;
		assertTrue("test fixture must exceed 2^53 to be meaningful", hash > (1L << 53));

		final AccountIdentity identity =
			new AccountIdentity(hash, "Zezima", "NORMAL", Collections.emptyList());

		assertEquals("6041938472910385761", identity.getAccountHashString());
		assertEquals(hash, Long.parseLong(identity.getAccountHashString()));

		// What would happen if it ever travelled as a JSON number.
		assertFalse(String.valueOf((long) (double) hash).equals(identity.getAccountHashString()));
	}

	@Test
	public void mapsAccountTypeVarbitValues()
	{
		assertEquals("NORMAL", AccountIdentity.accountTypeOf(0));
		assertEquals("IRONMAN", AccountIdentity.accountTypeOf(1));
		assertEquals("ULTIMATE_IRONMAN", AccountIdentity.accountTypeOf(2));
		assertEquals("HARDCORE_IRONMAN", AccountIdentity.accountTypeOf(3));
		assertEquals("GROUP_IRONMAN", AccountIdentity.accountTypeOf(4));
		assertEquals("HARDCORE_GROUP_IRONMAN", AccountIdentity.accountTypeOf(5));
	}

	@Test
	public void unknownAccountTypeValuesDoNotThrow()
	{
		assertEquals("UNKNOWN", AccountIdentity.accountTypeOf(-1));
		assertEquals("UNKNOWN", AccountIdentity.accountTypeOf(99));
	}

	@Test
	public void loggedOutIdentityIsNotPresent()
	{
		final AccountIdentity identity =
			new AccountIdentity(AccountIdentity.NO_ACCOUNT, null, "UNKNOWN", Collections.emptyList());

		assertFalse(identity.isPresent());
	}

	@Test(expected = UnsupportedOperationException.class)
	public void worldTypesCannotBeMutatedAfterPublication()
	{
		new AccountIdentity(1L, "A", "NORMAL", Collections.singletonList("MEMBERS"))
			.getWorldTypes()
			.add("PVP");
	}
}
