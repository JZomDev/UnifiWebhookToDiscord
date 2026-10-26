package com.webhookmiddleman.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsStoreTest
{
	@TempDir
	Path dir;

	@Test
	void normalizeMacAcceptsDashesAndLowercase()
	{
		assertEquals("AA:BB:CC:11:22:33", SettingsStore.normalizeMac("aa-bb-cc-11-22-33"));
		assertNull(SettingsStore.normalizeMac("nonsense"));
	}

	@Test
	void roundTripsToDisk()
	{
		Path file = dir.resolve("settings.json");
		SettingsStore store = new SettingsStore(file);
		store.update(s ->
		{
			s.webhook = "https://example.com/hook";
			s.cameras.put("AA:BB:CC:11:22:33", "Front Door");
			s.imported.add("webhook");
			s.mustChangePassword = false;
			s.passwordHash = "hash";
		});

		SettingsStore reloaded = new SettingsStore(file);
		Settings s = reloaded.snapshot();
		assertEquals("https://example.com/hook", s.webhook);
		assertEquals("Front Door", s.cameras.get("AA:BB:CC:11:22:33"));
		assertEquals("Front Door", reloaded.cameraName("AA:BB:CC:11:22:33"));
		assertTrue(s.imported.contains("webhook"));
		assertFalse(s.mustChangePassword);
		assertEquals("hash", s.passwordHash);
		assertTrue(reloaded.isPersistent());
	}

	@Test
	void missingFileGivesDefaults()
	{
		SettingsStore store = new SettingsStore(dir.resolve("none.json"));
		Settings s = store.snapshot();
		assertTrue(s.mustChangePassword);
		assertEquals(8080, s.webhookPort);
		assertTrue(s.showEnvReminder);
		assertNull(s.passwordHash);
		assertNull(store.cameraName("AA:BB:CC:11:22:33"));
		assertTrue(store.isPersistent());
	}

	@Test
	void corruptFileGivesDefaultsAndDoesNotThrow() throws Exception
	{
		Path file = dir.resolve("settings.json");
		Files.writeString(file, "{not json");
		SettingsStore store = new SettingsStore(file);
		assertTrue(store.snapshot().mustChangePassword);
		assertEquals(8080, store.snapshot().webhookPort);
	}

	@Test
	void unwritableLocationFallsBackToMemory() throws Exception
	{
		Path blocker = dir.resolve("file");
		Files.writeString(blocker, "x");
		SettingsStore store = new SettingsStore(blocker.resolve("settings.json"));
		store.update(s -> s.webhook = "w");
		assertFalse(store.isPersistent());
		assertEquals("w", store.snapshot().webhook);
	}
}
