package com.runeglass.runelite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.client.util.Filepath;

final class PrivateFiles
{
	static final String TEMPORARY_SUFFIX = ".tmp";

	private PrivateFiles()
	{
	}

	static synchronized void createDirectory(Filepath directory) throws IOException
	{
		if (directory.isDirectory())
		{
			return;
		}
		if (directory.isRoot())
		{
			directory.createDirectories();
			return;
		}
		Filepath parent = directory.getParent();
		createDirectory(parent);
		Filepath staged = parent.createTempDir(directory.getFileName() + ".");
		try
		{
			staged.moveTo(directory);
		}
		catch (IOException exception)
		{
			staged.deleteIfExists();
			directory.createDirectories();
		}
	}

	static void write(Filepath target, String temporaryPrefix, byte[] data) throws IOException
	{
		Filepath temporary = target.getParent().createTempFile(temporaryPrefix, TEMPORARY_SUFFIX);
		try
		{
			try (FileChannel channel = temporary.openFileChannel(StandardOpenOption.WRITE))
			{
				ByteBuffer buffer = ByteBuffer.wrap(data);
				while (buffer.hasRemaining())
				{
					channel.write(buffer);
				}
				channel.force(true);
			}
			temporary.moveTo(
				target,
				StandardCopyOption.ATOMIC_MOVE,
				StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException | RuntimeException exception)
		{
			try
			{
				temporary.deleteIfExists();
			}
			catch (IOException cleanup)
			{
				exception.addSuppressed(cleanup);
			}
			throw exception;
		}
	}

	static String readUtf8(Filepath file) throws IOException
	{
		try (InputStream input = file.openInputStream())
		{
			return StandardCharsets.UTF_8.newDecoder()
				.decode(ByteBuffer.wrap(input.readAllBytes()))
				.toString();
		}
	}

	static void deleteTemporaryFiles(Filepath directory, String temporaryPrefix) throws IOException
	{
		if (!directory.isDirectory())
		{
			return;
		}
		List<Filepath> temporaryFiles;
		try (Stream<Filepath> files = directory.walk(1))
		{
			temporaryFiles = files
				.filter(file -> !file.equals(directory)
					&& file.getFileName().startsWith(temporaryPrefix)
					&& file.getFileName().endsWith(TEMPORARY_SUFFIX)
					&& file.isFile())
				.collect(Collectors.toList());
		}
		for (Filepath file : temporaryFiles)
		{
			file.deleteIfExists();
		}
	}
}
