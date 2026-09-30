package com.runeglass.runelite;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import net.runelite.api.Model;
import net.runelite.api.PlayerComposition;

final class AppearanceMeshEncoder
{
	static final int MAX_MESH_BYTES = 512 * 1_024;
	static final int MAX_VERTEX_COUNT = 25_000;
	static final int MAX_FACE_COUNT = 30_000;
	private static final int HEADER_BYTES = 16;
	private static final int VERTEX_BYTES = 12;
	private static final int FACE_BYTES = 22;

	private AppearanceMeshEncoder()
	{
	}

	static EncodedMesh encode(Model model, PlayerComposition composition)
	{
		return capture(model, composition).encode();
	}

	static CapturedAppearance capture(Model model, PlayerComposition composition)
	{
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(composition, "composition");
		int vertexCount = model.getVerticesCount();
		int faceCount = model.getFaceCount();
		return new CapturedAppearance(
			Arrays.copyOf(model.getVerticesX(), vertexCount),
			Arrays.copyOf(model.getVerticesY(), vertexCount),
			Arrays.copyOf(model.getVerticesZ(), vertexCount),
			Arrays.copyOf(model.getFaceIndices1(), faceCount),
			Arrays.copyOf(model.getFaceIndices2(), faceCount),
			Arrays.copyOf(model.getFaceIndices3(), faceCount),
			Arrays.copyOf(model.getFaceColors1(), faceCount),
			Arrays.copyOf(model.getFaceColors2(), faceCount),
			Arrays.copyOf(model.getFaceColors3(), faceCount),
			copyOf(model.getFaceTransparencies(), faceCount),
			copyOf(model.getFaceRenderPriorities(), faceCount),
			copyOf(model.getFaceTextures(), faceCount),
			composition.getEquipmentIds().clone(),
			composition.getColors().clone(),
			composition.getGender());
	}

	private static byte[] copyOf(byte[] values, int length)
	{
		return values == null ? null : Arrays.copyOf(values, length);
	}

	private static short[] copyOf(short[] values, int length)
	{
		return values == null ? null : Arrays.copyOf(values, length);
	}

	static EncodedMesh encode(
		float[] verticesX,
		float[] verticesY,
		float[] verticesZ,
		int[] faceIndices1,
		int[] faceIndices2,
		int[] faceIndices3,
		int[] faceColors1,
		int[] faceColors2,
		int[] faceColors3,
		byte[] faceTransparencies,
		byte[] facePriorities,
		short[] faceTextures,
		int[] equipment,
		int[] bodyColors,
		int gender)
	{
		int vertexCount = requireMatchingVertices(verticesX, verticesY, verticesZ);
		if (vertexCount < 3 || vertexCount > MAX_VERTEX_COUNT)
		{
			throw new IllegalArgumentException("Unsupported appearance vertex count");
		}
		if (equipment == null || equipment.length < 1 || equipment.length > 16)
		{
			throw new IllegalArgumentException("Unsupported appearance equipment count");
		}
		if (bodyColors == null || bodyColors.length != 5 || (gender != 0 && gender != 1))
		{
			throw new IllegalArgumentException("Unsupported player composition");
		}

		int sourceFaceCount = requireMatchingFaces(
			faceIndices1,
			faceIndices2,
			faceIndices3,
			faceColors1,
			faceColors2,
			faceColors3);
		List<Integer> includedFaces = new ArrayList<>(sourceFaceCount);
		boolean hasTextures = false;
		for (int face = 0; face < sourceFaceCount; face++)
		{
			if (faceColors3[face] == -2)
			{
				continue;
			}
			validateVertexIndex(faceIndices1[face], vertexCount);
			validateVertexIndex(faceIndices2[face], vertexCount);
			validateVertexIndex(faceIndices3[face], vertexCount);
			includedFaces.add(face);
			hasTextures |= faceTextures != null
				&& face < faceTextures.length
				&& faceTextures[face] != -1;
		}
		int faceCount = includedFaces.size();
		if (faceCount < 1 || faceCount > MAX_FACE_COUNT)
		{
			throw new IllegalArgumentException("Unsupported appearance face count");
		}
		long byteCount = (long) HEADER_BYTES
			+ (long) vertexCount * VERTEX_BYTES
			+ (long) faceCount * FACE_BYTES
			+ (long) equipment.length * Integer.BYTES;
		if (byteCount > MAX_MESH_BYTES)
		{
			throw new IllegalArgumentException("Appearance mesh exceeds upload limit");
		}

		ByteBuffer buffer = ByteBuffer.allocate((int) byteCount).order(ByteOrder.LITTLE_ENDIAN);
		buffer.put((byte) 'R');
		buffer.put((byte) 'G');
		buffer.put((byte) 'M');
		buffer.put((byte) '1');
		buffer.put((byte) 1);
		buffer.put((byte) (hasTextures ? 1 : 0));
		buffer.putShort((short) equipment.length);
		buffer.putInt(vertexCount);
		buffer.putInt(faceCount);
		for (int vertex = 0; vertex < vertexCount; vertex++)
		{
			buffer.putFloat(verticesX[vertex]);
			buffer.putFloat(verticesY[vertex]);
			buffer.putFloat(verticesZ[vertex]);
		}
		for (int face : includedFaces)
		{
			int firstColor = faceColors1[face];
			int secondColor = faceColors2[face];
			int thirdColor = faceColors3[face] == -1 ? firstColor : faceColors3[face];
			if (faceColors3[face] == -1)
			{
				secondColor = firstColor;
			}
			buffer.putInt(faceIndices1[face]);
			buffer.putInt(faceIndices2[face]);
			buffer.putInt(faceIndices3[face]);
			buffer.putShort((short) firstColor);
			buffer.putShort((short) secondColor);
			buffer.putShort((short) thirdColor);
			buffer.put(faceTransparencies != null && face < faceTransparencies.length
				? faceTransparencies[face]
				: (byte) 0);
			buffer.put(facePriorities != null && face < facePriorities.length
				? facePriorities[face]
				: (byte) 0);
			buffer.putShort(faceTextures != null && face < faceTextures.length
				? faceTextures[face]
				: (short) -1);
		}
		for (int equipmentId : equipment)
		{
			if (equipmentId < 0 || equipmentId > 100_000)
			{
				throw new IllegalArgumentException("Unsupported equipment identifier");
			}
			buffer.putInt(equipmentId);
		}
		byte[] bytes = buffer.array();
		return new EncodedMesh(
			Base64.getEncoder().encodeToString(bytes),
			sha256(bytes),
			vertexCount,
			faceCount,
			equipment.clone(),
			bodyColors.clone(),
			gender,
			hasTextures);
	}

	private static int requireMatchingVertices(float[] x, float[] y, float[] z)
	{
		if (x == null || y == null || z == null || x.length != y.length || x.length != z.length)
		{
			throw new IllegalArgumentException("Invalid appearance vertices");
		}
		return x.length;
	}

	private static int requireMatchingFaces(int[]... arrays)
	{
		if (arrays.length == 0 || arrays[0] == null)
		{
			throw new IllegalArgumentException("Invalid appearance faces");
		}
		int length = arrays[0].length;
		for (int[] array : arrays)
		{
			if (array == null || array.length != length)
			{
				throw new IllegalArgumentException("Invalid appearance faces");
			}
		}
		return length;
	}

	private static void validateVertexIndex(int index, int vertexCount)
	{
		if (index < 0 || index >= vertexCount)
		{
			throw new IllegalArgumentException("Invalid appearance face index");
		}
	}

	private static String sha256(byte[] bytes)
	{
		try
		{
			return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException exception)
		{
			throw new IllegalStateException("SHA-256 unavailable", exception);
		}
	}

	static final class EncodedMesh
	{
		private final String meshBase64;
		private final String modelHash;
		private final int vertexCount;
		private final int faceCount;
		private final int[] equipment;
		private final int[] bodyColors;
		private final int gender;
		private final boolean hasTextures;

		private EncodedMesh(
			String meshBase64,
			String modelHash,
			int vertexCount,
			int faceCount,
			int[] equipment,
			int[] bodyColors,
			int gender,
			boolean hasTextures)
		{
			this.meshBase64 = meshBase64;
			this.modelHash = modelHash;
			this.vertexCount = vertexCount;
			this.faceCount = faceCount;
			this.equipment = equipment;
			this.bodyColors = bodyColors;
			this.gender = gender;
			this.hasTextures = hasTextures;
		}

		String getMeshBase64()
		{
			return meshBase64;
		}

		String getModelHash()
		{
			return modelHash;
		}

		int getVertexCount()
		{
			return vertexCount;
		}

		int getFaceCount()
		{
			return faceCount;
		}

		int[] getEquipment()
		{
			return equipment.clone();
		}

		int[] getBodyColors()
		{
			return bodyColors.clone();
		}

		int getGender()
		{
			return gender;
		}

		boolean hasTextures()
		{
			return hasTextures;
		}
	}

	static final class CapturedAppearance
	{
		private final float[] verticesX;
		private final float[] verticesY;
		private final float[] verticesZ;
		private final int[] faceIndices1;
		private final int[] faceIndices2;
		private final int[] faceIndices3;
		private final int[] faceColors1;
		private final int[] faceColors2;
		private final int[] faceColors3;
		private final byte[] faceTransparencies;
		private final byte[] facePriorities;
		private final short[] faceTextures;
		private final int[] equipment;
		private final int[] bodyColors;
		private final int gender;

		private CapturedAppearance(
			float[] verticesX,
			float[] verticesY,
			float[] verticesZ,
			int[] faceIndices1,
			int[] faceIndices2,
			int[] faceIndices3,
			int[] faceColors1,
			int[] faceColors2,
			int[] faceColors3,
			byte[] faceTransparencies,
			byte[] facePriorities,
			short[] faceTextures,
			int[] equipment,
			int[] bodyColors,
			int gender)
		{
			this.verticesX = verticesX;
			this.verticesY = verticesY;
			this.verticesZ = verticesZ;
			this.faceIndices1 = faceIndices1;
			this.faceIndices2 = faceIndices2;
			this.faceIndices3 = faceIndices3;
			this.faceColors1 = faceColors1;
			this.faceColors2 = faceColors2;
			this.faceColors3 = faceColors3;
			this.faceTransparencies = faceTransparencies;
			this.facePriorities = facePriorities;
			this.faceTextures = faceTextures;
			this.equipment = equipment;
			this.bodyColors = bodyColors;
			this.gender = gender;
		}

		EncodedMesh encode()
		{
			return AppearanceMeshEncoder.encode(
				verticesX,
				verticesY,
				verticesZ,
				faceIndices1,
				faceIndices2,
				faceIndices3,
				faceColors1,
				faceColors2,
				faceColors3,
				faceTransparencies,
				facePriorities,
				faceTextures,
				equipment,
				bodyColors,
				gender);
		}
	}
}
