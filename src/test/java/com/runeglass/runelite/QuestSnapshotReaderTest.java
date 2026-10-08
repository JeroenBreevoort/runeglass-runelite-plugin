package com.runeglass.runelite;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class QuestSnapshotReaderTest
{
	@Test
	public void readsExplicitIdsMapsStatusSemanticsAndLeavesUnrecognizedEntriesUnknown()
	{
		AtomicInteger currentId = new AtomicInteger();
		List<Integer> seenIds = new ArrayList<>();
		Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, (proxy, method, args) ->
		{
			if ("runScript".equals(method.getName()))
			{
				Object[] script = (Object[]) args[0];
				assertEquals(ScriptID.QUEST_STATUS_GET, script[0]);
				currentId.set((Integer) script[1]);
				seenIds.add(currentId.get());
				if (currentId.get() == 3) throw new IllegalStateException("Unreadable fixture");
				return null;
			}
			if ("getIntStack".equals(method.getName()))
			{
				if (currentId.get() == 0) return new int[]{2};
				if (currentId.get() == 1) return new int[]{1};
				if (currentId.get() == 17) return new int[]{0};
				if (currentId.get() == 4) return new int[0];
				return new int[]{-1};
			}
			throw new AssertionError("Unexpected client read " + method.getName());
		});
		Map<Integer, String> states = QuestSnapshotReader.readStates(client);
		assertEquals(213, seenIds.size());
		assertEquals("completed", states.get(0));
		assertEquals("not_started", states.get(1));
		assertEquals("in_progress", states.get(17));
		assertEquals(3, states.size());
		assertFalse(states.containsKey(3));
		assertFalse(states.containsKey(4));
		assertFalse(seenIds.contains(2));
	}

	@Test
	public void readsCurrentQuestPointsFromThePinnedModernConstant()
	{
		Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, (proxy, method, args) ->
		{
			assertEquals("getVarpValue", method.getName());
			assertEquals(101, VarPlayerID.QP);
			assertEquals(VarPlayerID.QP, args[0]);
			return 343;
		});
		assertEquals(343, QuestSnapshotReader.readQuestPoints(client));
	}
}
