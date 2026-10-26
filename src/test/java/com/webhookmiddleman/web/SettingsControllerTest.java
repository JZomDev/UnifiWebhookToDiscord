package com.webhookmiddleman.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.webhookmiddleman.WebhookListener;
import com.webhookmiddleman.settings.Settings;
import com.webhookmiddleman.settings.SettingsStore;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class SettingsControllerTest
{
	static final String HOOK = "https://discord.com/api/webhooks/123456/abc_DEF-ghi";
	static final String OTHER_HOOK = "https://discord.com/api/webhooks/987/zzz";

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

	@Autowired
	WebhookListener listener;

	@MockitoBean
	TestMessageSender sender;

	@TestBean(name = "envVars")
	List<String> envVars;

	static List<String> envVars()
	{
		return List.of("webhook");
	}

	TestAuth.Login login;
	int listenPort;

	@BeforeEach
	void setUp() throws Exception
	{
		TestAuth.resetPassword(store);
		reset(sender);
		listener.stop();
		listener.start(0);
		listenPort = listener.port();
		store.update(s ->
		{
			s.webhook = "";
			s.uuid1 = "";
			s.uuid2 = "";
			s.showEnvReminder = true;
		});
		login = TestAuth.login(mvc);
	}

	@AfterEach
	void tearDown()
	{
		listener.stop();
	}

	MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder b)
	{
		return b.session(login.session()).param("_csrf", login.csrf());
	}

	ResultActions save(String webhook, String uuid1, String uuid2, boolean reminder) throws Exception
	{
		MockHttpServletRequestBuilder b = authed(post("/settings"))
			.param("webhook", webhook).param("uuid1", uuid1).param("uuid2", uuid2);
		if (reminder)
		{
			b.param("showEnvReminder", "on");
		}
		return mvc.perform(b);
	}

	ResultActions test(String webhook) throws Exception
	{
		return mvc.perform(authed(post("/settings/test-webhook")).param("webhook", webhook)
			.param("uuid1", "u-one").param("uuid2", ""));
	}

	@Test
	void pageShowsSavedValuesAndChangePasswordLink() throws Exception
	{
		store.update(s ->
		{
			s.webhook = HOOK;
			s.uuid1 = "<b>uuid</b>";
		});
		mvc.perform(get("/settings").session(login.session())).andExpect(status().isOk())
			.andExpect(content().string(Matchers.containsString(HOOK)))
			.andExpect(content().string(Matchers.containsString("&lt;b&gt;uuid&lt;/b&gt;")))
			.andExpect(content().string(Matchers.not(Matchers.containsString("<b>uuid</b>"))))
			.andExpect(content().string(Matchers.containsString("href=\"/change-password\"")))
			.andExpect(content().string(Matchers.containsString("Send test message")))
			.andExpect(content().string(Matchers.containsString("container")));
	}

	@Test
	void saveWebhookAndUuids() throws Exception
	{
		save(" " + HOOK + " ", " u1 ", "u2", true).andExpect(redirectedUrl("/settings"));
		Settings s = store.snapshot();
		assertEquals(HOOK, s.webhook);
		assertEquals("u1", s.uuid1);
		assertEquals("u2", s.uuid2);
		assertTrue(s.showEnvReminder);
	}

	@Test
	void blankWebhookAllowedWithWarning() throws Exception
	{
		store.update(s -> s.webhook = HOOK);
		save("", "", "", true).andExpect(redirectedUrl("/settings"));
		assertEquals("", store.snapshot().webhook);
		mvc.perform(get("/settings").session(login.session()))
			.andExpect(content().string(Matchers.containsString("will not be forwarded")));
	}

	@Test
	void invalidWebhookRejected() throws Exception
	{
		store.update(s -> s.webhook = HOOK);
		save("https://example.com/<x>", "u1", "", true).andExpect(status().isBadRequest())
			.andExpect(content().string(Matchers.containsString("Discord webhook URL")))
			.andExpect(content().string(Matchers.containsString("https://example.com/&lt;x&gt;")));
		assertEquals(HOOK, store.snapshot().webhook);
		assertEquals("", store.snapshot().uuid1);
	}

	@Test
	void pageShowsListenPortReadOnly() throws Exception
	{
		mvc.perform(get("/settings").session(login.session())).andExpect(status().isOk())
			.andExpect(content().string(Matchers.containsString("The webhook listener runs on port " + listenPort)))
			.andExpect(content().string(Matchers.containsString("<code>port</code> environment variable")))
			.andExpect(content().string(Matchers.not(Matchers.containsString("name=\"webhookPort\""))))
			.andExpect(content().string(Matchers.not(Matchers.containsString("is not running"))));
	}

	@Test
	void savingIgnoresAWebhookPortParameter() throws Exception
	{
		int other = listenPort == 65535 ? 65534 : listenPort + 1;
		mvc.perform(authed(post("/settings")).param("webhook", HOOK).param("webhookPort", String.valueOf(other))
			.param("uuid1", "").param("uuid2", "")).andExpect(redirectedUrl("/settings"));
		assertEquals(HOOK, store.snapshot().webhook);
		assertEquals(listenPort, listener.port());
	}

	/** Leaves the listener down after failing to start on {@code port}, as when env {@code port} was busy at boot. */
	void failStart(int port)
	{
		listener.stop();
		assertThrows(IOException.class, () -> listener.start(port));
	}

	@Test
	void listenerDownShowsNoticeInsteadOfPortLineAndSavingStillWorks() throws Exception
	{
		try (ServerSocket busy = new ServerSocket(0))
		{
			int port = busy.getLocalPort();
			failStart(port);
			mvc.perform(get("/settings").session(login.session()))
				.andExpect(content().string(Matchers.containsString("Webhook listener is not running on port " + port + ":")))
				.andExpect(content().string(Matchers.containsString("port environment variable")))
				.andExpect(content().string(Matchers.not(Matchers.containsString("The webhook listener runs on port"))));
			mvc.perform(get("/").session(login.session()))
				.andExpect(content().string(Matchers.containsString("Webhook listener is not running on port " + port)))
				.andExpect(content().string(Matchers.containsString("port environment variable")));

			save(HOOK, "u1", "u2", false).andExpect(redirectedUrl("/settings"))
				.andExpect(flash().attributeCount(1));
			Settings s = store.snapshot();
			assertEquals(HOOK, s.webhook);
			assertEquals("u1", s.uuid1);
			assertEquals("u2", s.uuid2);
			assertFalse(s.showEnvReminder);
			assertEquals(-1, listener.port());
		}
	}

	@Test
	void testButtonUsesFormValueNotSavedValue() throws Exception
	{
		store.update(s -> s.webhook = HOOK);
		when(sender.send(anyString())).thenReturn(new TestMessageSender.Result(true, "Test message sent."));
		test(OTHER_HOOK).andExpect(status().isOk())
			.andExpect(content().string(Matchers.containsString("Test message sent.")))
			// form keeps the typed (unsaved) values
			.andExpect(content().string(Matchers.containsString(OTHER_HOOK)))
			.andExpect(content().string(Matchers.containsString("u-one")));
		verify(sender).send(OTHER_HOOK);
		assertEquals(HOOK, store.snapshot().webhook);
	}

	@Test
	void testButtonRejectsInvalidUrlWithoutSending() throws Exception
	{
		test("https://evil.example/api/webhooks/1/x").andExpect(status().isBadRequest())
			.andExpect(content().string(Matchers.containsString("Discord webhook URL")));
		test("").andExpect(status().isBadRequest());
		verify(sender, never()).send(anyString());
	}

	static final String CONSOLE_URL = "https://unifi.ui.com/consoles/784558C04AB3000000000622CD6000066AF6370000000:124"
		+ "/protect/alarm-manager/edit/01a12223-2361-7081-892c-3c3ab3916428";
	static final String CONSOLE_ID = "784558C04AB3000000000622CD6000066AF6370000000";

	ResultActions console(String url) throws Exception
	{
		return mvc.perform(authed(post("/settings/console-url")).param("consoleUrl", url).param("webhook", OTHER_HOOK)
			.param("uuid1", "old-one").param("uuid2", "old-two"));
	}

	@Test
	void consoleUrlFillsBothUuidBoxesWithoutSaving() throws Exception
	{
		console(CONSOLE_URL).andExpect(status().isOk())
			.andExpect(content().string(Matchers.containsString("value=\"" + CONSOLE_ID + "\"")))
			.andExpect(content().string(Matchers.containsString("value=\"124\"")))
			// other typed (unsaved) values are kept
			.andExpect(content().string(Matchers.containsString(OTHER_HOOK)))
			.andExpect(content().string(Matchers.not(Matchers.containsString("old-one"))));
		assertEquals("", store.snapshot().uuid1, "filling the boxes must not save");
		assertEquals("", store.snapshot().uuid2);
	}

	@Test
	void consoleUrlWithoutPathAndWithSpacesIsAccepted() throws Exception
	{
		console("  https://unifi.ui.com/consoles/ABC123:7  ").andExpect(status().isOk())
			.andExpect(content().string(Matchers.containsString("value=\"ABC123\"")))
			.andExpect(content().string(Matchers.containsString("value=\"7\"")));
	}

	@Test
	void consoleUrlFromAnotherHostOrWithoutIdsIsRejected() throws Exception
	{
		for (String bad : List.of("", "not a url", "https://evil.example/consoles/A:1",
			"https://unifi.ui.com.evil.example/consoles/A:1", "https://evil.example/?x=https://unifi.ui.com/consoles/A:1",
			"http://unifi.ui.com/consoles/A:1", "https://unifi.ui.com/consoles/A", "https://unifi.ui.com/consoles/A:/protect",
			"https://unifi.ui.com/protect/dashboard"))
		{
			console(bad).andExpect(status().isBadRequest())
				.andExpect(content().string(Matchers.containsString("unifi.ui.com/consoles/")))
				// the boxes keep what was typed
				.andExpect(content().string(Matchers.containsString("value=\"old-one\"")));
		}
		assertEquals("", store.snapshot().uuid1);
	}

	@Test
	void consoleUrlRouteRequiresLoginAndCsrf() throws Exception
	{
		mvc.perform(post("/settings/console-url").param("consoleUrl", CONSOLE_URL)).andExpect(status().is3xxRedirection());
		mvc.perform(post("/settings/console-url").session(login.session()).param("consoleUrl", CONSOLE_URL))
			.andExpect(status().isForbidden());
	}

	@Test
	void testButtonShowsFailureMessage() throws Exception
	{
		when(sender.send(anyString())).thenReturn(new TestMessageSender.Result(false, "HTTP 401: <Unauthorized>"));
		test(HOOK).andExpect(status().isOk())
			.andExpect(content().string(Matchers.containsString("HTTP 401: &lt;Unauthorized&gt;")))
			.andExpect(content().string(Matchers.not(Matchers.containsString("<Unauthorized>"))));
	}

	@Test
	void testButtonRequiresCsrf() throws Exception
	{
		mvc.perform(post("/settings/test-webhook").session(login.session()).param("webhook", HOOK))
			.andExpect(status().isForbidden());
		verify(sender, never()).send(anyString());
	}

	@Test
	void saveRequiresCsrf() throws Exception
	{
		mvc.perform(post("/settings").session(login.session()).param("webhook", HOOK))
			.andExpect(status().isForbidden());
		assertEquals("", store.snapshot().webhook);
	}

	@Test
	void defaultSenderDescribesFailures()
	{
		assertEquals("Sending failed: Discord returned HTTP 401: {\"message\": \"Invalid Webhook Token\"}",
			DiscordTestMessageSender.describe(401, "{\"message\": \"Invalid Webhook Token\"}"));
		assertEquals("Sending failed: Read timed out", DiscordTestMessageSender.describe(-1, "Read timed out"));
		assertEquals("Sending failed: no details", DiscordTestMessageSender.describe(-1, null));
		assertTrue(DiscordTestMessageSender.describe(500, "x".repeat(1000)).length() < 400);
	}

	@Test
	void reminderToggleOffHidesBanner() throws Exception
	{
		mvc.perform(get("/settings").session(login.session()))
			.andExpect(content().string(Matchers.containsString("class=\"banner\"")));
		save("", "", "", false).andExpect(redirectedUrl("/settings"));
		assertFalse(store.snapshot().showEnvReminder);
		mvc.perform(get("/settings").session(login.session()))
			.andExpect(content().string(Matchers.not(Matchers.containsString("class=\"banner\""))));
	}
}
