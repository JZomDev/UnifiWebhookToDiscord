package com.webhookmiddleman.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.webhookmiddleman.settings.SettingsStore;
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
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class CameraControllerTest
{
	@TempDir
	static Path dir;

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r)
	{
		r.add("config.dir", () -> dir.toString());
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
		return List.of("webhook", "AA:BB:CC:11:22:33");
	}

	TestAuth.Login login;

	@BeforeEach
	void setUp() throws Exception
	{
		TestAuth.resetPassword(store);
		store.update(s ->
		{
			s.cameras.clear();
			s.showEnvReminder = true;
		});
		login = TestAuth.login(mvc);
	}

	ResultActions add(String mac, String name) throws Exception
	{
		return mvc.perform(post("/cameras").session(login.session()).param("_csrf", login.csrf())
			.param("mac", mac).param("name", name));
	}

	@Test
	void addNormalizesMac() throws Exception
	{
		add("aa-bb-cc-11-22-33", "Front Door").andExpect(redirectedUrl("/"));
		assertEquals("Front Door", store.snapshot().cameras.get("AA:BB:CC:11:22:33"));
	}

	@Test
	void duplicateMacUpdatesName() throws Exception
	{
		add("aa:bb:cc:11:22:33", "Old").andExpect(redirectedUrl("/"));
		add("AA-BB-CC-11-22-33", "New").andExpect(redirectedUrl("/"));
		assertEquals(1, store.snapshot().cameras.size());
		assertEquals("New", store.snapshot().cameras.get("AA:BB:CC:11:22:33"));
	}

	@Test
	void invalidMacRejectedWithMessage() throws Exception
	{
		add("not-a-mac", "Cam").andExpect(status().isBadRequest())
			.andExpect(content().string(Matchers.containsString("valid MAC address")));
		assertEquals(0, store.snapshot().cameras.size());
	}

	@Test
	void blankNameRejected() throws Exception
	{
		add("aa:bb:cc:11:22:33", "   ").andExpect(status().isBadRequest())
			.andExpect(content().string(Matchers.containsString("name")));
		assertEquals(0, store.snapshot().cameras.size());
	}

	@Test
	void tooLongNameRejected() throws Exception
	{
		add("aa:bb:cc:11:22:33", "x".repeat(101)).andExpect(status().isBadRequest())
			.andExpect(content().string(Matchers.containsString("100 characters")));
		assertEquals(0, store.snapshot().cameras.size());
		add("aa:bb:cc:11:22:33", "x".repeat(100)).andExpect(redirectedUrl("/"));
	}

	@Test
	void editRenamesCamera() throws Exception
	{
		add("aa:bb:cc:11:22:33", "Old");
		mvc.perform(post("/cameras/AA:BB:CC:11:22:33").session(login.session()).param("_csrf", login.csrf())
			.param("name", "Renamed")).andExpect(redirectedUrl("/"));
		assertEquals("Renamed", store.snapshot().cameras.get("AA:BB:CC:11:22:33"));
		mvc.perform(post("/cameras/AA:BB:CC:11:22:33").session(login.session()).param("_csrf", login.csrf())
			.param("name", " ")).andExpect(status().isBadRequest());
		assertEquals("Renamed", store.snapshot().cameras.get("AA:BB:CC:11:22:33"));
	}

	@Test
	void editUnknownMacDoesNotCreate() throws Exception
	{
		mvc.perform(post("/cameras/AA:BB:CC:11:22:33").session(login.session()).param("_csrf", login.csrf())
			.param("name", "Ghost")).andExpect(redirectedUrl("/"));
		assertEquals(0, store.snapshot().cameras.size());
	}

	@Test
	void deleteRemoves() throws Exception
	{
		add("aa:bb:cc:11:22:33", "Front");
		mvc.perform(post("/cameras/AA:BB:CC:11:22:33/delete").session(login.session()).param("_csrf", login.csrf()))
			.andExpect(redirectedUrl("/"));
		assertFalse(store.snapshot().cameras.containsKey("AA:BB:CC:11:22:33"));
	}

	@Test
	void postWithoutCsrfIs403() throws Exception
	{
		mvc.perform(post("/cameras").session(login.session()).param("mac", "aa:bb:cc:11:22:33").param("name", "x"))
			.andExpect(status().isForbidden());
		assertEquals(0, store.snapshot().cameras.size());
	}

	@Test
	void pageListsCamerasEscapingHtml() throws Exception
	{
		add("aa:bb:cc:11:22:33", "<b>x</b>");
		mvc.perform(get("/").session(login.session())).andExpect(status().isOk())
			.andExpect(content().string(Matchers.containsString("&lt;b&gt;x&lt;/b&gt;")))
			.andExpect(content().string(Matchers.not(Matchers.containsString("<b>x</b>"))))
			.andExpect(content().string(Matchers.containsString("AA:BB:CC:11:22:33")));
	}

	@Test
	void addFormNameLengthMatchesServerCap() throws Exception
	{
		mvc.perform(get("/").session(login.session()))
			.andExpect(content().string(Matchers.containsString("placeholder=\"Camera name\" maxlength=\"" + CameraController.MAX_NAME + "\"")))
			.andExpect(content().string(Matchers.not(Matchers.containsString("maxlength=\"200\""))));
	}

	@Test
	void bannerListsEnvVarsWhenPresent() throws Exception
	{
		mvc.perform(get("/").session(login.session()))
			.andExpect(content().string(Matchers.containsString("<code>webhook</code>")));
	}

	@Test
	void bannerHiddenWhenReminderOff() throws Exception
	{
		store.update(s -> s.showEnvReminder = false);
		mvc.perform(get("/").session(login.session()))
			.andExpect(content().string(Matchers.not(Matchers.containsString("class=\"banner\""))));
	}
}
