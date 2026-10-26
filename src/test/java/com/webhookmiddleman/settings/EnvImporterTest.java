package com.webhookmiddleman.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnvImporterTest
{
	private static final String HOOK = "https://discord.com/api/webhooks/123456/abc_DEF-9";

	@TempDir
	Path dir;

	private SettingsStore newStore()
	{
		return new SettingsStore(dir.resolve("settings.json"));
	}

	private static Map<String, String> env(String... kv)
	{
		Map<String, String> m = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2)
		{
			m.put(kv[i], kv[i + 1]);
		}
		return m;
	}

	@Test
	void importsWebhookPortUuidsAndCameras()
	{
		SettingsStore store = newStore();
		List<String> vars = EnvImporter.importFrom(env("PATH", "/bin", "BB:BB:BB:BB:BB:BB", "Garage", "webhook", HOOK,
				"port", "9090", "UUID_1", "u1", "UUID_2", "u2", "AA:AA:AA:AA:AA:AA", "Front", "webui_port", "1234"), store);
		Settings s = store.snapshot();
		assertEquals(HOOK, s.webhook);
		assertEquals(9090, s.webhookPort);
		assertEquals("u1", s.uuid1);
		assertEquals("u2", s.uuid2);
		assertEquals("Front", s.cameras.get("AA:AA:AA:AA:AA:AA"));
		assertEquals("Garage", s.cameras.get("BB:BB:BB:BB:BB:BB"));
		assertTrue(s.imported.containsAll(List.of("webhook", "port", "UUID_1", "UUID_2",
				"camera:AA:AA:AA:AA:AA:AA", "camera:BB:BB:BB:BB:BB:BB")));
		assertEquals(List.of("webhook", "port", "UUID_1", "UUID_2", "AA:AA:AA:AA:AA:AA", "BB:BB:BB:BB:BB:BB"), vars);
	}

	@Test
	void invalidWebhookIsNotImported()
	{
		SettingsStore store = newStore();
		List<String> vars = EnvImporter.importFrom(env("webhook", "http://example.com/x"), store);
		assertEquals("", store.snapshot().webhook);
		assertFalse(store.snapshot().imported.contains("webhook"));
		assertEquals(List.of("webhook"), vars);
	}

	@Test
	void importIsOnlyOncePerValue()
	{
		SettingsStore store = newStore();
		Map<String, String> e = env("UUID_1", "u1", "AA:AA:AA:AA:AA:AA", "Front");
		EnvImporter.importFrom(e, store);
		store.update(s ->
		{
			s.cameras.remove("AA:AA:AA:AA:AA:AA");
			s.uuid1 = "";
		});
		EnvImporter.importFrom(e, store);
		assertTrue(store.snapshot().cameras.isEmpty());
		assertEquals("", store.snapshot().uuid1);
	}

	@Test
	void uiEditWinsOverEnv()
	{
		SettingsStore store = newStore();
		String ui = "https://discord.com/api/webhooks/999/ui";
		store.update(s -> s.webhook = ui);
		EnvImporter.importFrom(env("webhook", HOOK), store);
		assertEquals(ui, store.snapshot().webhook);
	}

	@Test
	void newMacEnvImportedLater()
	{
		SettingsStore store = newStore();
		EnvImporter.importFrom(env("AA:AA:AA:AA:AA:AA", "Front"), store);
		EnvImporter.importFrom(env("AA:AA:AA:AA:AA:AA", "Front", "BB:BB:BB:BB:BB:BB", "Back"), store);
		assertEquals("Back", store.snapshot().cameras.get("BB:BB:BB:BB:BB:BB"));
	}

	@Test
	void lowercaseDashMacImportedNormalized()
	{
		SettingsStore store = newStore();
		List<String> vars = EnvImporter.importFrom(env("aa-bb-cc-11-22-33", "Porch"), store);
		assertEquals("Porch", store.snapshot().cameras.get("AA:BB:CC:11:22:33"));
		assertTrue(store.snapshot().imported.contains("camera:AA:BB:CC:11:22:33"));
		assertEquals(List.of("aa-bb-cc-11-22-33"), vars);
	}

	@Test
	void invalidPortIgnored()
	{
		SettingsStore store = newStore();
		EnvImporter.importFrom(env("port", "abc"), store);
		EnvImporter.importFrom(env("port", "70000"), store);
		assertEquals(8080, store.snapshot().webhookPort);
		assertFalse(store.snapshot().imported.contains("port"));
	}

	@Test
	void webUiPortDefaultsAndOverride()
	{
		assertEquals(45462, EnvImporter.webUiPort(env()));
		assertEquals(45462, EnvImporter.webUiPort(env("webui_port", "nope")));
		assertEquals(45462, EnvImporter.webUiPort(env("webui_port", "0")));
		assertEquals(7000, EnvImporter.webUiPort(env("webui_port", "7000")));
	}
}
