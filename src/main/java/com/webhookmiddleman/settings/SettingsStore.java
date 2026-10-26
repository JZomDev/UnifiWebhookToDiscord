package com.webhookmiddleman.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSON-file-backed store for {@link Settings}. Falls back to memory only if the
 * file cannot be written.
 */
public class SettingsStore
{
	private static final Logger log = LoggerFactory.getLogger(SettingsStore.class);
	private static final Pattern MAC = Pattern.compile("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");

	private final Path file;
	private Settings settings = new Settings();
	private volatile boolean persistent;

	public SettingsStore(Path file)
	{
		this.file = file;
		load();
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

	public boolean isPersistent()
	{
		return persistent;
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
			s.webhookPort = o.optInt("webhookPort", 8080);
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
			log.warn("Could not read settings file {}; using defaults: {}", file, e.toString());
			settings = new Settings();
			Path parent = file.toAbsolutePath().getParent();
			persistent = parent != null && Files.isDirectory(parent) && Files.isWritable(parent);
		}
	}

	private void persist()
	{
		JSONObject o = new JSONObject();
		o.put("passwordHash", settings.passwordHash == null ? JSONObject.NULL : settings.passwordHash);
		o.put("mustChangePassword", settings.mustChangePassword);
		o.put("webhook", settings.webhook);
		o.put("webhookPort", settings.webhookPort);
		o.put("uuid1", settings.uuid1);
		o.put("uuid2", settings.uuid2);
		o.put("showEnvReminder", settings.showEnvReminder);
		o.put("cameras", new JSONObject(settings.cameras));
		o.put("imported", new JSONArray(settings.imported));

		try
		{
			Path abs = file.toAbsolutePath();
			Path tmp = abs.resolveSibling(abs.getFileName() + ".tmp");
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
