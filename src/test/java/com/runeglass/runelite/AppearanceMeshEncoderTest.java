package com.runeglass.runelite;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AppearanceMeshEncoderTest
{
	@Test
	public void encodesVersionedMeshAndOmitsHiddenFaces()
	{
		AppearanceMeshEncoder.EncodedMesh mesh = AppearanceMeshEncoder.encode(
			new float[]{-10, 10, 0},
			new float[]{0, 0, -20},
			new float[]{0, 0, 5},
			new int[]{0, 0},
			new int[]{1, 1},
			new int[]{2, 2},
			new int[]{4_000, 4_100},
			new int[]{4_001, 4_101},
			new int[]{4_002, -2},
			new byte[]{0, 0},
			new byte[]{1, 1},
			new short[]{-1, -1},
			new int[]{0, 512, 768},
			new int[]{0, 1, 2, 3, 4},
			0);

		assertEquals(3, mesh.getVertexCount());
		assertEquals(1, mesh.getFaceCount());
		assertArrayEquals(new int[]{0, 512, 768}, mesh.getEquipment());
		assertFalse(mesh.hasTextures());
		assertEquals(43, mesh.getModelHash().length());

		byte[] bytes = Base64.getDecoder().decode(mesh.getMeshBase64());
		ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		assertEquals('R', buffer.get());
		assertEquals('G', buffer.get());
		assertEquals('M', buffer.get());
		assertEquals('1', buffer.get());
		assertEquals(1, buffer.get());
		assertEquals(0, buffer.get());
		assertEquals(3, buffer.getShort());
		assertEquals(3, buffer.getInt());
		assertEquals(1, buffer.getInt());
	}

	@Test
	public void recordsTexturedFacesWithoutEmbeddingGameTextures()
	{
		AppearanceMeshEncoder.EncodedMesh mesh = AppearanceMeshEncoder.encode(
			new float[]{0, 10, 0},
			new float[]{0, 0, -10},
			new float[]{0, 0, 0},
			new int[]{0},
			new int[]{1},
			new int[]{2},
			new int[]{1},
			new int[]{1},
			new int[]{1},
			null,
			null,
			new short[]{42},
			new int[]{0},
			new int[]{0, 0, 0, 0, 0},
			1);

		assertTrue(mesh.hasTextures());
	}

	@Test
	public void rejectsOutOfBoundsGeometryAndComposition()
	{
		assertThrows(IllegalArgumentException.class, () -> AppearanceMeshEncoder.encode(
			new float[]{0, 10, 0},
			new float[]{0, 0, -10},
			new float[]{0, 0, 0},
			new int[]{0},
			new int[]{1},
			new int[]{3},
			new int[]{1},
			new int[]{1},
			new int[]{1},
			null,
			null,
			null,
			new int[]{0},
			new int[]{0, 0, 0, 0, 0},
			0));
	}
}
