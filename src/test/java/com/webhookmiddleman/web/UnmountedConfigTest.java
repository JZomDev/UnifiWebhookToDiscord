package com.webhookmiddleman.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import com.webhookmiddleman.settings.SettingsStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;

/** With {@code config.require-mount=true} and a mountinfo that does not list the config dir. */
@SpringBootTest
@AutoConfigureMockMvc
class UnmountedConfigTest
{
	@TempDir
	static Path dir;

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r)
	{
		r.add("config.dir", () -> prepare().resolve("cfg").toString());
		r.add("config.require-mount", () -> "true");
		r.add("config.mountinfo", () -> prepare().resolve("mountinfo").toString());
		r.add("listener.autostart", () -> "false");
		r.add("auth.failure-delay-ms", () -> "0");
	}

	/** Creates the (writable) config dir and a mountinfo that lists only {@code /}. */
	static Path prepare()
	{
		try
		{
			Files.createDirectories(dir.resolve("cfg"));
			Files.writeString(dir.resolve("mountinfo"), "22 1 0:21 / / rw - overlay overlay rw\n");
			return dir;
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	@Autowired
	MockMvc mvc;

	@Autowired
	SettingsStore store;

	@TestBean(name = "envVars")
	List<String> envVars;

	static List<String> envVars()
	{
		return List.of("webhook", "AA:BB:CC:11:22:33");
	}

	@BeforeEach
	void reset()
	{
		TestAuth.resetPassword(store);
	}

	@Test
	void warnsThatSettingsAreLostOnUpdateAndNamesConfigDir() throws Exception
	{
		assertFalse(store.isPersistent());
		TestAuth.Login login = TestAuth.login(mvc);
		String cfg = dir.resolve("cfg").toAbsolutePath().normalize().toString();
		mvc.perform(get("/").session(login.session()))
			.andExpect(content().string(Matchers.containsString("updated or recreated")))
			.andExpect(content().string(Matchers.containsString("password")))
			.andExpect(content().string(Matchers.containsString("<code>" + cfg + "</code>")));
	}

	@Test
	void envBannerSaysMapVolumeFirstInsteadOfRemove() throws Exception
	{
		TestAuth.Login login = TestAuth.login(mvc);
		mvc.perform(get("/").session(login.session()))
			.andExpect(content().string(Matchers.containsString("<code>AA:BB:CC:11:22:33</code>")))
			.andExpect(content().string(Matchers.containsString("Keep them")))
			.andExpect(content().string(Matchers.containsString("map a volume")))
			.andExpect(content().string(Matchers.not(Matchers.containsString("can be removed"))));
	}
}
