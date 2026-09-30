package com.runeglass.runelite;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

final class AppearanceSnapshot
{
	private final String snapshotId;
	private final String observedAt;
	private final AppearanceMeshEncoder.EncodedMesh mesh;

	AppearanceSnapshot(Instant observedAt, AppearanceMeshEncoder.EncodedMesh mesh)
	{
		this.snapshotId = UUID.randomUUID().toString();
		this.observedAt = Objects.requireNonNull(observedAt, "observedAt").toString();
		this.mesh = Objects.requireNonNull(mesh, "mesh");
	}

	String getSnapshotId()
	{
		return snapshotId;
	}

	String getObservedAt()
	{
		return observedAt;
	}

	AppearanceMeshEncoder.EncodedMesh getMesh()
	{
		return mesh;
	}
}
