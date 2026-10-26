package com.webhookmiddleman.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One-time import of legacy environment variables into {@link Settings}. A value
 * is imported only if it has not been imported before and the user has not
 * already set the target field. Also resolves the two ports, which come only
 * from the environment: {@code webui_port} and {@code port} (the webhook listener).
 */
public final class EnvImporter
{
	public static final int DEFAULT_WEB_UI_PORT = 45462;
	public static final int DEFAULT_LISTEN_PORT = 8080;
	private static final Logger log = LoggerFactory.getLogger(EnvImporter.class);
	private static final Pattern WEBHOOK = Pattern.compile("https://discord\\.com/api/webhooks/[0-9]+/[A-Za-z0-9_\\-]+");

	private EnvImporter()
	{
	}

	/**
	 * Imports not-yet-imported values and returns the names of the legacy env vars
	 * present in {@code env} that can be removed once imported (webhook, UUID_1,
	 * UUID_2, then MACs sorted). {@code port} is not one of them: it still sets the listen port.
	 */
	public static List<String> importFrom(Map<String, String> env, SettingsStore store)
	{
		List<String> present = new ArrayList<>();
		for (String name : new String[] { "webhook", "UUID_1", "UUID_2" })
		{
			if (env.containsKey(name))
			{
				present.add(name);
			}
		}
		// normalized MAC -> original env name, sorted by normalized MAC
		TreeMap<String, String> macs = new TreeMap<>();
		for (String name : env.keySet())
		{
			String mac = SettingsStore.normalizeMac(name);
			if (mac != null)
			{
				macs.putIfAbsent(mac, name);
			}
		}
		present.addAll(macs.values());

		// Every change below goes with a new entry in s.imported, so a no-op import does not rewrite the file.
		store.updateIf(s ->
		{
			int before = s.imported.size();
			String webhook = env.get("webhook");
			if (webhook != null && WEBHOOK.matcher(webhook.trim()).matches() && s.imported.add("webhook"))
			{
				if (s.webhook.isEmpty())
				{
					s.webhook = webhook.trim();
				}
			}

			String uuid1 = env.get("UUID_1");
			if (uuid1 != null && !uuid1.trim().isEmpty() && s.imported.add("UUID_1"))
			{
				if (s.uuid1.isEmpty())
				{
					s.uuid1 = uuid1.trim();
				}
			}
			String uuid2 = env.get("UUID_2");
			if (uuid2 != null && !uuid2.trim().isEmpty() && s.imported.add("UUID_2"))
			{
				if (s.uuid2.isEmpty())
				{
					s.uuid2 = uuid2.trim();
				}
			}

			for (Map.Entry<String, String> e : macs.entrySet())
			{
				String value = env.get(e.getValue());
				// A blank value is skipped (not marked imported), so it is picked up once it has a name.
				if (value != null && !value.isBlank() && s.imported.add("camera:" + e.getKey()))
				{
					if (!s.cameras.containsKey(e.getKey()))
					{
						s.cameras.put(e.getKey(), value.trim());
					}
				}
			}
			return s.imported.size() != before;
		});
		return present;
	}

	public static int webUiPort(Map<String, String> env)
	{
		Integer p = parsePort(env.get("webui_port"));
		return p == null ? DEFAULT_WEB_UI_PORT : p;
	}

	/**
	 * The webhook listener port: env {@code port} (the port inside the container), or
	 * {@value #DEFAULT_LISTEN_PORT} when it is unset, not a port number, or the web UI port.
	 */
	public static int listenPort(Map<String, String> env, int uiPort)
	{
		String raw = env.get("port");
		if (raw == null)
		{
			return DEFAULT_LISTEN_PORT;
		}
		Integer p = parsePort(raw);
		if (p == null)
		{
			log.warn("Environment variable port=\"{}\" is not a port number (1-65535); using {}", raw, DEFAULT_LISTEN_PORT);
			return DEFAULT_LISTEN_PORT;
		}
		if (p == uiPort)
		{
			log.warn("Environment variable port={} is the web UI port; using {} for the webhook listener", p, DEFAULT_LISTEN_PORT);
			return DEFAULT_LISTEN_PORT;
		}
		return p;
	}

	private static Integer parsePort(String raw)
	{
		if (raw == null)
		{
			return null;
		}
		try
		{
			int p = Integer.parseInt(raw.trim());
			return p >= 1 && p <= 65535 ? p : null;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
}
