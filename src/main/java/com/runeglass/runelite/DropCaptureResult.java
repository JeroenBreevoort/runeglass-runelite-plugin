package com.runeglass.runelite;

import java.util.Optional;

final class DropCaptureResult
{
	enum Stage
	{
		BLOCKED,
		FILTERED,
		INVALID,
		ELIGIBLE,
		SUBMITTED,
		NOT_SUBMITTED
	}

	enum Reason
	{
		PLUGIN_STOPPED(Stage.BLOCKED),
		SYNC_OFF(Stage.BLOCKED),
		DROP_SYNC_OFF(Stage.BLOCKED),
		LOOT_TRACKER_OFF(Stage.BLOCKED),
		LOGGED_OUT(Stage.BLOCKED),
		SESSION_INACTIVE(Stage.BLOCKED),
		BASELINE_PENDING(Stage.BLOCKED),
		PROFILE_CHANGED(Stage.BLOCKED),
		NOT_PAIRED(Stage.BLOCKED),
		UNSUPPORTED_PROFILE(Stage.BLOCKED),
		TRANSPORT_NOT_READY(Stage.BLOCKED),
		PLUGIN_INJECTION(Stage.FILTERED),
		UNSUPPORTED_SOURCE(Stage.FILTERED),
		ALREADY_HANDLED(Stage.FILTERED),
		NO_ITEMS(Stage.FILTERED),
		BELOW_THRESHOLD(Stage.FILTERED),
		INVALID_REWARD(Stage.INVALID),
		ELIGIBLE(Stage.ELIGIBLE),
		SUBMITTED_FOR_LOCAL_SAVE(Stage.SUBMITTED),
		LOCAL_SAVE_UNAVAILABLE(Stage.NOT_SUBMITTED);

		private final Stage stage;

		Reason(Stage stage)
		{
			this.stage = stage;
		}

		Stage stage()
		{
			return stage;
		}
	}

	private final Reason reason;
	private final String sourceType;
	private final int itemStacks;
	private final int valuedStacks;
	private final Long highestStackGp;
	private final boolean notable;
	private final DropEvent event;

	private DropCaptureResult(Reason reason, String sourceType, int itemStacks, int valuedStacks,
		Long highestStackGp, boolean notable, DropEvent event)
	{
		this.reason = reason;
		this.sourceType = sourceType;
		this.itemStacks = itemStacks;
		this.valuedStacks = valuedStacks;
		this.highestStackGp = highestStackGp;
		this.notable = notable;
		this.event = event;
	}

	static DropCaptureResult blocked(Reason reason)
	{
		if (reason.stage() != Stage.BLOCKED) throw new IllegalArgumentException("Not a guard reason");
		return new DropCaptureResult(reason, null, 0, 0, null, false, null);
	}

	static DropCaptureResult filtered(Reason reason, String sourceType)
	{
		if (reason.stage() != Stage.FILTERED) throw new IllegalArgumentException("Not a filter reason");
		return new DropCaptureResult(reason, sourceType, 0, 0, null, false, null);
	}

	static DropCaptureResult invalid()
	{
		return new DropCaptureResult(Reason.INVALID_REWARD, null, 0, 0, null, false, null);
	}

	static DropCaptureResult evaluated(String sourceType, int itemStacks, int valuedStacks,
		Long highestStackGp, boolean notable, DropEvent event)
	{
		return new DropCaptureResult(event == null ? Reason.BELOW_THRESHOLD : Reason.ELIGIBLE,
			sourceType, itemStacks, valuedStacks, highestStackGp, notable, event);
	}

	DropCaptureResult submitted(boolean accepted)
	{
		if (event == null) throw new IllegalStateException("No eligible drop");
		return new DropCaptureResult(accepted ? Reason.SUBMITTED_FOR_LOCAL_SAVE : Reason.LOCAL_SAVE_UNAVAILABLE,
			sourceType, itemStacks, valuedStacks, highestStackGp, notable, event);
	}

	Reason reason()
	{
		return reason;
	}

	Stage stage()
	{
		return reason.stage();
	}

	Optional<String> sourceType()
	{
		return Optional.ofNullable(sourceType);
	}

	int itemStacks()
	{
		return itemStacks;
	}

	int valuedStacks()
	{
		return valuedStacks;
	}

	Optional<Long> highestStackGp()
	{
		return Optional.ofNullable(highestStackGp);
	}

	boolean notable()
	{
		return notable;
	}

	Optional<DropEvent> event()
	{
		return Optional.ofNullable(event);
	}
}
