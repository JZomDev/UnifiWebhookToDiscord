package com.webhookmiddleman.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSON-file-backed store for {@link Settings}. Falls back to memory only if the
 * file cannot be written. With a mount check, the store also reports itself as
 * not persistent when the config directory is not a mounted volume (the file is
 * still written, but lives in the container layer and is lost on recreate).
 */
public class SettingsStore
{
	private static final Logger log = LoggerFactory.getLogger(SettingsStore.class);
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private static final Pattern MAC = Pattern.compile("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");

	private final Path file;
	private Settings settings = new Settings();
	private volatile boolean persistent;
	private final boolean mounted;
	private volatile String loadNotice;
	/** Set when an unreadable settings file could not be moved aside, so it is never overwritten. */
	private boolean writesBlocked;

	public SettingsStore(Path file)
	{
		this(file, null);
	}

	/**
	 * @param mountCheck tells whether the config directory is a mounted volume, or
	 *        null to skip the check
	 */
	public SettingsStore(Path file, Predicate<Path> mountCheck)
	{
		this.file = file;
		this.mounted = mountCheck == null || mountCheck.test(directory());
		load();
	}

	/** The absolute, normalized directory that holds the settings file. */
	public Path directory()
	{
		Path parent = file.toAbsolutePath().normalize().getParent();
		return parent == null ? file.toAbsolutePath().normalize() : parent;
	}

	public static String normalizeMac(String raw)
	{
		if (raw == null)
		{
			return null;
		}
		String t = raw.trim();
		if (!MAC.matcher(t).matches())
		{
			return null;
		}
		return t.toUpperCase(Locale.ROOT).replace('-', ':');
	}

	public synchronized Settings snapshot()
	{
		return settings.copy();
	}

	public synchronized void update(Consumer<Settings> change)
	{
		change.accept(settings);
		persist();
	}

	/** Applies {@code change} and saves only if it returns true (it changed something). */
	public synchronized boolean updateIf(Predicate<Settings> change)
	{
		boolean changed = change.test(settings);
		if (changed)
		{
			persist();
		}
		return changed;
	}

	/** True if saved settings survive a container update: the file is writable and, if checked, on a mounted volume. */
	public boolean isPersistent()
	{
		return persistent && mounted;
	}

	/**
	 * A message for the UI when the settings file could not be read at startup
	 * (it is then moved aside, never overwritten), or null.
	 */
	public String loadNotice()
	{
		return loadNotice;
	}

	public synchronized String cameraName(String normalizedMac)
	{
		return settings.cameras.get(normalizedMac);
	}

	private void load()
	{
		try
		{
			if (!Files.exists(file))
			{
				Path parent = file.toAbsolutePath().getParent();
				persistent = parent != null && Files.isDirectory(parent) && Files.isWritable(parent);
				return;
			}
			JSONObject o = new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
			Settings s = new Settings();
			s.passwordHash = o.isNull("passwordHash") ? null : o.optString("passwordHash", null);
			s.mustChangePassword = o.optBoolean("mustChangePassword", true);
			s.webhook = o.optString("webhook", "");
			s.uuid1 = o.optString("uuid1", "");
			s.uuid2 = o.optString("uuid2", "");
			s.showEnvReminder = o.optBoolean("showEnvReminder", true);
			JSONObject cams = o.optJSONObject("cameras");
			if (cams != null)
			{
				for (String k : cams.keySet())
				{
					s.cameras.put(k, cams.optString(k, ""));
				}
			}
			JSONArray imp = o.optJSONArray("imported");
			if (imp != null)
			{
				for (int i = 0; i < imp.length(); i++)
				{
					s.imported.add(imp.getString(i));
				}
			}
			settings = s;
			persistent = true;
		}
		catch (Exception e)
		{
			settings = new Settings();
			Path parent = file.toAbsolutePath().getParent();
			persistent = parent != null && Files.isDirectory(parent) && Files.isWritable(parent);
			setAside(e);
		}
	}

	/** Moves an unreadable settings file to {@code settings.json.corrupt-<yyyyMMddHHmmss>} so it is not overwritten. */
	private void setAside(Exception readError)
	{
		Path abs = file.toAbsolutePath();
		String base = abs.getFileName() + ".corrupt-" + LocalDateTime.now().format(STAMP);
		Path target = abs.resolveSibling(base);
		for (int i = 1; Files.exists(target, LinkOption.NOFOLLOW_LINKS); i++)
		{
			target = abs.resolveSibling(base + "-" + i);
		}
		try
		{
			Files.move(abs, target);
			log.warn("Could not read settings file {} ({}); moved it to {} and started with default settings",
				abs, readError.toString(), target.getFileName());
			loadNotice = "The settings file could not be read and was set aside as " + target.getFileName()
				+ " in " + directory() + ". Default settings are in use.";
		}
		catch (IOException | RuntimeException moveError)
		{
			log.warn("Could not read settings file {} ({}) and could not move it aside ({}); not saving any changes",
				abs, readError.toString(), moveError.toString());
			writesBlocked = true;
			persistent = false;
			loadNotice = "The settings file " + abs.getFileName() + " in " + directory()
				+ " could not be read or set aside. Default settings are in use and changes are not saved"
				+ " until the file is fixed or removed.";
		}
	}

	private void persist()
	{
		if (writesBlocked)
		{
			persistent = false;
			return;
		}
		JSONObject o = new JSONObject();
		o.put("passwordHash", settings.passwordHash == null ? JSONObject.NULL : settings.passwordHash);
		o.put("mustChangePassword", settings.mustChangePassword);
		o.put("webhook", settings.webhook);
		o.put("uuid1", settings.uuid1);
		o.put("uuid2", settings.uuid2);
		o.put("showEnvReminder", settings.showEnvReminder);
		o.put("cameras", new JSONObject(settings.cameras));
		o.put("imported", new JSONArray(settings.imported));

		try
		{
			Path abs = file.toAbsolutePath();
			Path tmp = abs.resolveSibling(abs.getFileName() + ".tmp");
			Files.deleteIfExists(tmp);
			if (abs.getFileSystem().supportedFileAttributeViews().contains("posix"))
			{
				// Owner-only: the file holds the password hash and the webhook token.
				Files.createFile(tmp, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
			}
			Files.writeString(tmp, o.toString(2), StandardCharsets.UTF_8);
			try
			{
				Files.move(tmp, abs, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			}
			catch (AtomicMoveNotSupportedException e)
			{
				Files.move(tmp, abs, StandardCopyOption.REPLACE_EXISTING);
			}
			persistent = true;
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Could not save settings to {}; keeping in memory only: {}", file, e.toString());
			persistent = false;
		}
	}
}
