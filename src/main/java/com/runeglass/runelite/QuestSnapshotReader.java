package com.runeglass.runelite;

import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.VarPlayerID;

final class QuestSnapshotReader
{
	static Map<Integer, String> readStates(Client client)
	{
		Map<Integer, String> states = new LinkedHashMap<>();
		for (int id : QuestCatalog.ids())
		{
			try
			{
				client.runScript(ScriptID.QUEST_STATUS_GET, id);
				int status = client.getIntStack()[0];
				if (status == 2)
				{
					states.put(id, "completed");
				}
				else if (status == 1)
				{
					states.put(id, "not_started");
				}
				else if (status == 0)
				{
					states.put(id, "in_progress");
				}
			}
			catch (RuntimeException exception)
			{
			}
		}
		return states;
	}

	static int readQuestPoints(Client client)
	{
		return client.getVarpValue(VarPlayerID.QP);
	}
}
