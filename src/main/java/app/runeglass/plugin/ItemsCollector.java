/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Tracks inventory, equipment and bank contents.
 * <p>
 * The client reuses and mutates its {@code Item[]} arrays in place, so contents are copied into
 * our own immutable lists the moment the event arrives. Holding a reference and reading it later
 * from another thread would produce contents that silently change underneath us.
 * <p>
 * Inventory and bank ride on snapshots only. Inventory changes on every action a player takes,
 * and the bank is simply too large to be an event — a full bank serializes to roughly 80 KB, so a
 * batch of them would blow past the backend's per-document limit. Equipment is small and changes
 * rarely, so it is worth an event. A bank change instead asks for an early snapshot, which
 * carries the contents once rather than repeatedly.
 */
@Singleton
public class ItemsCollector
{
	private final RuneGlassConfig config;
	private final SyncService sync;

	private volatile List<RuneGlassApi.ItemStack> inventory = Collections.emptyList();
	private volatile List<RuneGlassApi.ItemStack> equipment = Collections.emptyList();
	/**
	 * Only observable while the bank interface is open, so this is always "last known" rather
	 * than live. Survives logout so the app can still show the most recent view.
	 */
	private volatile List<RuneGlassApi.ItemStack> bank = Collections.emptyList();

	/** Set when contents changed enough to be worth a snapshot sooner than the usual cadence. */
	private volatile boolean snapshotRequested;

	@Inject
	ItemsCollector(RuneGlassConfig config, SyncService sync)
	{
		this.config = config;
		this.sync = sync;
	}

	public void reset()
	{
		inventory = Collections.emptyList();
		equipment = Collections.emptyList();
		bank = Collections.emptyList();
		snapshotRequested = false;
	}

	/**
	 * Returns whether a snapshot was requested since the last call, clearing the flag.
	 */
	public boolean consumeSnapshotRequest()
	{
		if (!snapshotRequested)
		{
			return false;
		}
		snapshotRequested = false;
		return true;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (!config.syncEnabled() || !config.syncItems())
		{
			return;
		}

		final int id = event.getContainerId();
		if (id != InventoryID.INV && id != InventoryID.WORN && id != InventoryID.BANK)
		{
			return;
		}

		final List<RuneGlassApi.ItemStack> items = copyOf(event.getItemContainer());

		if (id == InventoryID.INV)
		{
			inventory = items;
			return; // snapshot only — far too noisy to be an event
		}

		if (id == InventoryID.WORN)
		{
			equipment = items;
			emitContainerEvent("WORN", items);
			return;
		}

		// Bank contents are far too large to travel as an event; ask for a snapshot instead.
		bank = items;
		snapshotRequested = true;
	}

	private void emitContainerEvent(String container, List<RuneGlassApi.ItemStack> items)
	{
		final Map<String, Object> data = new HashMap<>();
		data.put("container", container);
		data.put("items", items);
		sync.record(RuneGlassApi.Kind.CONTAINER, data);
	}

	/**
	 * Snapshots the container into immutable stacks. Empty slots are dropped; the slot index is
	 * kept so equipment slots stay meaningful.
	 */
	private static List<RuneGlassApi.ItemStack> copyOf(@Nullable ItemContainer container)
	{
		if (container == null)
		{
			return Collections.emptyList();
		}

		final Item[] items = container.getItems();
		final List<RuneGlassApi.ItemStack> copy = new ArrayList<>(items.length);

		for (int slot = 0; slot < items.length; slot++)
		{
			final Item item = items[slot];
			if (item == null || item.getId() < 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			copy.add(new RuneGlassApi.ItemStack(item.getId(), item.getQuantity(), slot));
		}

		return Collections.unmodifiableList(copy);
	}

	public List<RuneGlassApi.ItemStack> getInventory()
	{
		return inventory;
	}

	public List<RuneGlassApi.ItemStack> getEquipment()
	{
		return equipment;
	}

	public List<RuneGlassApi.ItemStack> getBank()
	{
		return bank;
	}
}
