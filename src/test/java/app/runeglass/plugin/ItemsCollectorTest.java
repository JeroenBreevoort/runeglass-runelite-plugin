/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import java.util.List;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Node;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class ItemsCollectorTest
{
	private SkillsCollectorTest.StubConfig config;
	private SkillsCollectorTest.RecordingSync sync;
	private ItemsCollector collector;

	@Before
	public void setUp()
	{
		config = new SkillsCollectorTest.StubConfig();
		sync = new SkillsCollectorTest.RecordingSync(config);
		collector = new ItemsCollector(config, sync);
	}

	/**
	 * The reason this class copies rather than storing the container: the client reuses and
	 * mutates its item arrays in place, so a retained reference would change underneath us.
	 */
	@Test
	public void contentsAreCopiedNotAliasedToTheClientsArray()
	{
		final Item[] items = {new Item(995, 1000)};
		changed(InventoryID.INV, items);

		final List<RuneGlassApi.ItemStack> captured = collector.getInventory();
		assertEquals(995, captured.get(0).id);
		assertEquals(1000, captured.get(0).qty);

		// Simulate the client reusing the array for something else entirely.
		items[0] = new Item(4151, 1);

		assertEquals("stored contents must not follow the client's mutation", 995, captured.get(0).id);
		assertEquals(1000, captured.get(0).qty);
	}

	@Test
	public void inventoryIsSnapshotOnlyAndNeverAnEvent()
	{
		changed(InventoryID.INV, new Item[]{new Item(995, 1)});

		assertEquals("inventory changes on every action; it would flood the queue", 0, sync.events.size());
		assertEquals(1, collector.getInventory().size());
	}

	@Test
	public void equipmentChangesEmitAnEvent()
	{
		changed(InventoryID.WORN, new Item[]{new Item(11802, 1)});

		assertEquals(1, sync.events.size());
		assertEquals(RuneGlassApi.Kind.CONTAINER, sync.events.get(0).kind);
		assertEquals("WORN", sync.events.get(0).data.get("container"));
		assertEquals(1, collector.getEquipment().size());
	}

	@Test
	public void bankChangesEmitAnEvent()
	{
		changed(InventoryID.BANK, new Item[]{new Item(995, 5_000_000)});

		assertEquals(1, sync.events.size());
		assertEquals("BANK", sync.events.get(0).data.get("container"));
		assertEquals(5_000_000, collector.getBank().get(0).qty);
	}

	@Test
	public void emptySlotsAreDroppedButSlotIndicesSurvive()
	{
		// Equipment slots are sparse, so the index has to mean something.
		changed(InventoryID.WORN, new Item[]{
			new Item(-1, 0),
			new Item(-1, 0),
			new Item(11802, 1),
		});

		final List<RuneGlassApi.ItemStack> equipment = collector.getEquipment();
		assertEquals(1, equipment.size());
		assertEquals(11802, equipment.get(0).id);
		assertEquals("slot index must survive the compaction", 2, equipment.get(0).slot);
	}

	@Test
	public void zeroQuantityStacksAreDropped()
	{
		changed(InventoryID.INV, new Item[]{new Item(995, 0)});

		assertTrue(collector.getInventory().isEmpty());
	}

	@Test
	public void unrelatedContainersAreIgnored()
	{
		changed(93_000, new Item[]{new Item(995, 1)});

		assertTrue(sync.events.isEmpty());
		assertTrue(collector.getInventory().isEmpty());
	}

	@Test
	public void capturedListsCannotBeMutatedByCallers()
	{
		changed(InventoryID.INV, new Item[]{new Item(995, 1)});

		try
		{
			collector.getInventory().add(new RuneGlassApi.ItemStack(1, 1, 1));
			org.junit.Assert.fail("published lists must be immutable");
		}
		catch (UnsupportedOperationException expected)
		{
			// Published across threads, so it must not be mutable.
		}
	}

	@Test
	public void nothingIsCollectedWhileTheItemsToggleIsOff()
	{
		config.syncItems = false;

		changed(InventoryID.WORN, new Item[]{new Item(11802, 1)});

		assertTrue(sync.events.isEmpty());
		assertTrue(collector.getEquipment().isEmpty());
	}

	@Test
	public void resetClearsEveryContainer()
	{
		changed(InventoryID.INV, new Item[]{new Item(995, 1)});
		changed(InventoryID.WORN, new Item[]{new Item(11802, 1)});
		changed(InventoryID.BANK, new Item[]{new Item(995, 1)});

		collector.reset();

		assertTrue(collector.getInventory().isEmpty());
		assertTrue(collector.getEquipment().isEmpty());
		assertTrue(collector.getBank().isEmpty());
	}

	private void changed(int containerId, Item[] items)
	{
		collector.onItemContainerChanged(new ItemContainerChanged(containerId, new StubContainer(items)));
	}

	/** Minimal ItemContainer; only getItems() is exercised by the collector. */
	private static final class StubContainer implements ItemContainer
	{
		private final Item[] items;

		StubContainer(Item[] items)
		{
			this.items = items;
		}

		@Override
		public Item[] getItems()
		{
			return items;
		}

		@Override
		public int getId()
		{
			return 0;
		}

		@Override
		public Item getItem(int slot)
		{
			return items[slot];
		}

		@Override
		public boolean contains(int itemId)
		{
			return false;
		}

		@Override
		public int count(int itemId)
		{
			return 0;
		}

		@Override
		public int size()
		{
			return items.length;
		}

		@Override
		public int count()
		{
			return items.length;
		}

		@Override
		public int find(int itemId)
		{
			return -1;
		}

		@Override
		public Node getNext()
		{
			return null;
		}

		@Override
		public Node getPrevious()
		{
			return null;
		}

		@Override
		public long getHash()
		{
			return 0L;
		}
	}
}
