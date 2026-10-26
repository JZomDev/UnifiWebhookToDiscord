package com.webhookmiddleman.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import com.webhookmiddleman.settings.SettingsStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;

/** Starts with an unparseable settings.json in the config dir. */
@SpringBootTest
@AutoConfigureMockMvc
class CorruptSettingsTest
{
	@TempDir
	static Path dir;

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r)
	{
		r.add("config.dir", () ->
		{
			try
			{
				Path f = dir.resolve("settings.json");
				if (!Files.exists(f))
				{
					Files.writeString(f, "garbage{");
				}
			}
			catch (IOException e)
			{
				throw new UncheckedIOException(e);
			}
			return dir.toString();
		});
		r.add("listener.autostart", () -> "false");
		r.add("auth.failure-delay-ms", () -> "0");
	}

	@Autowired
	MockMvc mvc;

	@Autowired
	SettingsStore store;

	@TestBean(name = "envVars")
	List<String> envVars;

	static List<String> envVars()
	{
		return List.of();
	}

	@Test
	void corruptFileIsSetAsideAndNoticeShown() throws Exception
	{
		List<Path> aside;
		try (Stream<Path> files = Files.list(dir))
		{
			aside = files.filter(p -> p.getFileName().toString().startsWith("settings.json.corrupt-")).toList();
		}
		assertEquals(1, aside.size());
		assertEquals("garbage{", Files.readString(aside.get(0)));
		mvc.perform(get("/login"))
			.andExpect(content().string(Matchers.containsString("could not be read")))
			.andExpect(content().string(Matchers.containsString(aside.get(0).getFileName().toString())));
		TestAuth.login(mvc);
		assertEquals("garbage{", Files.readString(aside.get(0)));
	}
}
