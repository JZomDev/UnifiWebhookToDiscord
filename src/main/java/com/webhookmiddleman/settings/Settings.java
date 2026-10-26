package com.webhookmiddleman.settings;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Mutable settings managed through the web UI and persisted by {@link SettingsStore}.
 */
public class Settings
{
	/** BCrypt hash; null until changed, meaning the default admin/admin state. */
	public String passwordHash;
	public boolean mustChangePassword = true;
	public String webhook = "";
	public int webhookPort = 8080;
	public String uuid1 = "";
	public String uuid2 = "";
	/** Normalized MAC to camera name. */
	public LinkedHashMap<String, String> cameras = new LinkedHashMap<>();
	public boolean showEnvReminder = true;
	/** Keys imported from environment variables (webhook, port, UUID_1, UUID_2, camera:MAC). */
	public Set<String> imported = new LinkedHashSet<>();

	public Settings copy()
	{
		Settings c = new Settings();
		c.passwordHash = passwordHash;
		c.mustChangePassword = mustChangePassword;
		c.webhook = webhook;
		c.webhookPort = webhookPort;
		c.uuid1 = uuid1;
		c.uuid2 = uuid2;
		c.cameras = new LinkedHashMap<>(cameras);
		c.showEnvReminder = showEnvReminder;
		c.imported = new LinkedHashSet<>(imported);
		return c;
	}
}
