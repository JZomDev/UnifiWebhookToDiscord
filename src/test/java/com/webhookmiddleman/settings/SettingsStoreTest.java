package com.webhookmiddleman.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

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
	void corruptFileIsSetAsideBeforeAnyWrite() throws Exception
	{
		Path file = dir.resolve("settings.json");
		Files.writeString(file, "{not json");
		SettingsStore store = new SettingsStore(file);
		List<Path> aside;
		try (Stream<Path> files = Files.list(dir))
		{
			aside = files.filter(p -> p.getFileName().toString().matches("settings\\.json\\.corrupt-\\d{14}")).toList();
		}
		assertEquals(1, aside.size());
		assertEquals("{not json", Files.readString(aside.get(0)));
		assertFalse(Files.exists(file));
		assertNotNull(store.loadNotice());
		assertTrue(store.loadNotice().contains(aside.get(0).getFileName().toString()));

		store.update(s -> s.webhook = "w");
		assertEquals("{not json", Files.readString(aside.get(0)));
		assertEquals("w", new SettingsStore(file).snapshot().webhook);
	}

	@Test
	void readableFileHasNoLoadNotice()
	{
		Path file = dir.resolve("settings.json");
		new SettingsStore(file).update(s -> s.webhook = "w");
		assertNull(new SettingsStore(file).loadNotice());
		assertNull(new SettingsStore(dir.resolve("none.json")).loadNotice());
	}

	@Test
	void unmountedDirectoryIsNotPersistentButStillWorks() throws Exception
	{
		Path file = dir.resolve("settings.json");
		SettingsStore store = new SettingsStore(file, d -> false);
		assertFalse(store.isPersistent());
		store.update(s -> s.webhook = "w");
		assertFalse(store.isPersistent());
		assertEquals("w", store.snapshot().webhook);
		assertEquals(dir.toAbsolutePath().normalize(), store.directory());
	}

	@Test
	void mountCheckReceivesConfigDirectory() throws Exception
	{
		Path[] seen = new Path[1];
		SettingsStore store = new SettingsStore(dir.resolve("settings.json"), d ->
		{
			seen[0] = d;
			return true;
		});
		assertTrue(store.isPersistent());
		assertEquals(dir.toAbsolutePath().normalize(), seen[0]);
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
