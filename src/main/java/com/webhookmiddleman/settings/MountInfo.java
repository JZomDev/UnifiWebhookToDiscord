package com.webhookmiddleman.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads {@code /proc/self/mountinfo} to tell whether a directory is a mount
 * point, i.e. whether the container's config dir is a mapped volume.
 */
public final class MountInfo
{
	private static final Logger log = LoggerFactory.getLogger(MountInfo.class);

	public static final Path PROC_SELF = Path.of("/proc/self/mountinfo");

	private MountInfo()
	{
	}

	/** A mount check backed by the given mountinfo file. */
	public static Predicate<Path> checker(Path mountinfo)
	{
		return dir -> isMountPoint(mountinfo, dir);
	}

	/**
	 * True if {@code dir} is listed as a mount point in {@code mountinfo}. If the
	 * file cannot be read, assumes it is mounted (so non-Linux hosts never warn).
	 */
	public static boolean isMountPoint(Path mountinfo, Path dir)
	{
		List<String> lines;
		try
		{
			lines = Files.readAllLines(mountinfo, StandardCharsets.UTF_8);
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Could not read {} to check the config volume; assuming it is mounted: {}", mountinfo, e.toString());
			return true;
		}
		Path target = canonical(dir);
		for (String line : lines)
		{
			// Fields: id parent major:minor root mountPoint options ...
			String[] f = line.split(" ");
			if (f.length < 5)
			{
				continue;
			}
			try
			{
				if (Path.of(unescape(f[4])).toAbsolutePath().normalize().equals(target))
				{
					return true;
				}
			}
			catch (RuntimeException e)
			{
				// unparseable path: skip the line
			}
		}
		return false;
	}

	/** Decodes mountinfo's octal escapes such as {@code \040} (space). */
	static String unescape(String s)
	{
		StringBuilder b = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++)
		{
			char c = s.charAt(i);
			if (c == '\\' && isOctal(s, i + 1) && isOctal(s, i + 2) && isOctal(s, i + 3))
			{
				b.append((char) Integer.parseInt(s.substring(i + 1, i + 4), 8));
				i += 3;
			}
			else
			{
				b.append(c);
			}
		}
		return b.toString();
	}

	private static boolean isOctal(String s, int i)
	{
		return i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '7';
	}

	private static Path canonical(Path dir)
	{
		Path abs = dir.toAbsolutePath().normalize();
		try
		{
			return abs.toRealPath();
		}
		catch (IOException | RuntimeException e)
		{
			return abs;
		}
	}
}
