package com.webhookmiddleman.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MountInfoTest
{
	@TempDir
	Path dir;

	static String line(String mountPoint)
	{
		return "1234 29 0:52 / " + mountPoint + " rw,relatime shared:1 - btrfs /dev/sda1 rw\n";
	}

	Path mountinfo(String content) throws Exception
	{
		Path f = dir.resolve("mountinfo");
		Files.writeString(f, content);
		return f;
	}

	@Test
	void listedMountPointIsMounted() throws Exception
	{
		Path f = mountinfo(line("/") + line("/config") + line("/etc/hosts"));
		assertTrue(MountInfo.isMountPoint(f, Path.of("/config")));
		assertTrue(MountInfo.isMountPoint(f, Path.of("/config/")));
		assertTrue(MountInfo.isMountPoint(f, Path.of("/srv/../config")));
	}

	@Test
	void unlistedDirectoryIsNotMounted() throws Exception
	{
		Path f = mountinfo(line("/") + line("/config/sub") + line("/configs"));
		assertFalse(MountInfo.isMountPoint(f, Path.of("/config")));
	}

	@Test
	void octalEscapesAreDecoded() throws Exception
	{
		Path f = mountinfo(line("/my\\040config\\134x"));
		assertTrue(MountInfo.isMountPoint(f, Path.of("/my config\\x")));
		assertFalse(MountInfo.isMountPoint(f, Path.of("/my\\040config\\134x")));
		assertEquals("/a b\tc", MountInfo.unescape("/a\\040b\\011c"));
	}

	@Test
	void unreadableMountinfoAssumesMounted()
	{
		assertTrue(MountInfo.isMountPoint(dir.resolve("missing"), Path.of("/config")));
	}

	@Test
	void malformedLinesAreSkipped() throws Exception
	{
		Path f = mountinfo("garbage\n\n" + line("/config"));
		assertTrue(MountInfo.isMountPoint(f, Path.of("/config")));
	}
}
