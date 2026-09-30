package com.runeglass.runelite;

import java.util.LinkedHashMap;
import java.util.Map;

final class AppearanceSnapshotPayload
{
	private AppearanceSnapshotPayload()
	{
	}

	static Map<String, Object> create(
		SyncContext context,
		String connectionId,
		AppearanceSnapshot snapshot)
	{
		AppearanceMeshEncoder.EncodedMesh mesh = snapshot.getMesh();
		Map<String, Object> appearance = new LinkedHashMap<>();
		appearance.put("meshFormat", "runeglass-mesh-v1");
		appearance.put("modelHash", mesh.getModelHash());
		appearance.put("vertexCount", mesh.getVertexCount());
		appearance.put("faceCount", mesh.getFaceCount());
		appearance.put("equipment", mesh.getEquipment());
		appearance.put("bodyColors", mesh.getBodyColors());
		appearance.put("gender", mesh.getGender());
		appearance.put("hasTextures", mesh.hasTextures());
		appearance.put("meshBase64", mesh.getMeshBase64());

		Map<String, Object> payload = SemanticSnapshotPayload.create(
			context,
			connectionId,
			snapshot.getSnapshotId(),
			snapshot.getObservedAt(),
			"appearance",
			appearance);
		payload.put("consentVersion", 1);
		return payload;
	}
}
