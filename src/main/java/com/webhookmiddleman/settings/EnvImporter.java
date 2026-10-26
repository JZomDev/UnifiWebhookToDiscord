package com.webhookmiddleman.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * One-time import of legacy environment variables into {@link Settings}. A value
 * is imported only if it has not been imported before and the user has not
 * already set the target field.
 */
public final class EnvImporter
{
	public static final int DEFAULT_WEB_UI_PORT = 45462;
	private static final Pattern WEBHOOK = Pattern.compile("https://discord\\.com/api/webhooks/[0-9]+/[A-Za-z0-9_\\-]+");
	private static final int DEFAULT_WEBHOOK_PORT = 8080;

	private EnvImporter()
	{
	}

	/**
	 * Imports not-yet-imported values and returns the names of the legacy env vars
	 * present in {@code env} (webhook, port, UUID_1, UUID_2, then MACs sorted).
	 */
	public static List<String> importFrom(Map<String, String> env, SettingsStore store)
	{
		List<String> present = new ArrayList<>();
		for (String name : new String[] { "webhook", "port", "UUID_1", "UUID_2" })
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

		store.update(s ->
		{
			String webhook = env.get("webhook");
			if (webhook != null && WEBHOOK.matcher(webhook.trim()).matches() && s.imported.add("webhook"))
			{
				if (s.webhook.isEmpty())
				{
					s.webhook = webhook.trim();
				}
			}

			String port = env.get("port");
			Integer parsed = parsePort(port);
			if (parsed != null && s.imported.add("port"))
			{
				if (s.webhookPort == DEFAULT_WEBHOOK_PORT)
				{
					s.webhookPort = parsed;
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
				if (value != null && s.imported.add("camera:" + e.getKey()))
				{
					if (!s.cameras.containsKey(e.getKey()))
					{
						s.cameras.put(e.getKey(), value.trim());
					}
				}
			}
		});
		return present;
	}

	public static int webUiPort(Map<String, String> env)
	{
		Integer p = parsePort(env.get("webui_port"));
		return p == null ? DEFAULT_WEB_UI_PORT : p;
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
