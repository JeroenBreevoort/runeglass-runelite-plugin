package com.runeglass.runelite;

public enum SnapshotReason
{
	LOGIN_BASELINE("login_baseline"),
	CHANGE_CHECKPOINT("change_checkpoint"),
	COVERAGE_HEARTBEAT("coverage_heartbeat"),
	LOGOUT_FLUSH("logout_flush"),
	PROFILE_SWITCH("profile_switch"),
	MANUAL_SYNC("manual_sync"),
	SCHEMA_RECONCILE("schema_reconcile");

	private final String wireValue;

	SnapshotReason(String wireValue)
	{
		this.wireValue = wireValue;
	}

	public String wireValue()
	{
		return wireValue;
	}
}
