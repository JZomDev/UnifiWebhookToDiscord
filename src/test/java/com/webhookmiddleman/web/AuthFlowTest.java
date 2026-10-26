package com.webhookmiddleman.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.webhookmiddleman.WebhookListener;
import com.webhookmiddleman.settings.SettingsStore;
import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest
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

	@Autowired
	WebhookListener listener;

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

	MockHttpSession loginDefault() throws Exception
	{
		MvcResult res = mvc.perform(post("/login").param("username", "admin").param("password", "admin"))
			.andExpect(redirectedUrl("/"))
			.andReturn();
		return (MockHttpSession) res.getRequest().getSession(false);
	}

	@Test
	void anonymousIsRedirectedToLogin() throws Exception
	{
		for (String path : new String[] { "/", "/settings", "/guide", "/change-password" })
		{
			mvc.perform(get(path)).andExpect(redirectedUrl("/login"));
		}
		mvc.perform(post("/logout")).andExpect(redirectedUrl("/login"));
	}

	@Test
	void loginPageAndCssArePublic() throws Exception
	{
		mvc.perform(get("/login")).andExpect(status().isOk())
			.andExpect(content().string(Matchers.containsString("name=\"password\"")));
		mvc.perform(get("/app.css")).andExpect(status().isOk());
	}

	@Test
	void listenerNotStartedInTests()
	{
		assertEquals(-1, listener.port());
	}

	@Test
	void defaultLoginForcesPasswordChange() throws Exception
	{
		MockHttpSession session = loginDefault();
		assertEquals(Boolean.TRUE, session.getAttribute(AuthInterceptor.AUTH));
		assertNotNull(session.getAttribute(AuthInterceptor.CSRF));
		mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/change-password"));
		mvc.perform(get("/settings").session(session)).andExpect(redirectedUrl("/change-password"));
		mvc.perform(get("/change-password").session(session)).andExpect(status().isOk())
			.andExpect(content().string(Matchers.containsString("Choose a new password")));
	}

	@Test
	void passwordChangeThenAdminAdminNoLongerWorks() throws Exception
	{
		TestAuth.Login login = TestAuth.login(mvc);
		mvc.perform(get("/").session(login.session())).andExpect(status().isOk())
			.andExpect(content().string(Matchers.containsString("Log out")));
		assertFalse(store.snapshot().mustChangePassword);

		mvc.perform(post("/login").param("username", "admin").param("password", "admin"))
			.andExpect(status().isUnauthorized());
		MvcResult res = mvc.perform(post("/login").param("username", "admin").param("password", TestAuth.PASSWORD))
			.andExpect(redirectedUrl("/"))
			.andReturn();
		MockHttpSession session = (MockHttpSession) res.getRequest().getSession(false);
		mvc.perform(get("/").session(session)).andExpect(status().isOk());
	}

	@Test
	void shortPasswordRejected() throws Exception
	{
		MockHttpSession session = loginDefault();
		String csrf = (String) session.getAttribute(AuthInterceptor.CSRF);
		mvc.perform(post("/change-password").session(session).param("_csrf", csrf)
			.param("password", "short1").param("confirm", "short1"))
			.andExpect(status().isBadRequest())
			.andExpect(content().string(Matchers.containsString("at least 8 characters")));
		assertNull(store.snapshot().passwordHash);
		assertTrue(store.snapshot().mustChangePassword);
	}

	@Test
	void mismatchedOrAdminPasswordRejected() throws Exception
	{
		MockHttpSession session = loginDefault();
		String csrf = (String) session.getAttribute(AuthInterceptor.CSRF);
		mvc.perform(post("/change-password").session(session).param("_csrf", csrf)
			.param("password", "longenough1").param("confirm", "longenough2"))
			.andExpect(status().isBadRequest())
			.andExpect(content().string(Matchers.containsString("do not match")));
		mvc.perform(post("/change-password").session(session).param("_csrf", csrf)
			.param("password", "admin").param("confirm", "admin"))
			.andExpect(status().isBadRequest());
		assertNull(store.snapshot().passwordHash);
	}

	@Test
	void postWithoutCsrfIs403() throws Exception
	{
		MockHttpSession session = loginDefault();
		mvc.perform(post("/change-password").session(session)
			.param("password", "longenough1").param("confirm", "longenough1"))
			.andExpect(status().isForbidden());
		mvc.perform(post("/change-password").session(session).param("_csrf", "wrong")
			.param("password", "longenough1").param("confirm", "longenough1"))
			.andExpect(status().isForbidden());
		mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
		assertNull(store.snapshot().passwordHash);
	}

	@Test
	void wrongPasswordShowsErrorAndDoesNotAuthenticate() throws Exception
	{
		MvcResult res = mvc.perform(post("/login").param("username", "admin").param("password", "nope"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().string(Matchers.containsString("Invalid username or password")))
			.andReturn();
		MockHttpSession session = (MockHttpSession) res.getRequest().getSession(false);
		if (session != null)
		{
			assertNull(session.getAttribute(AuthInterceptor.AUTH));
			mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/login"));
		}
		mvc.perform(post("/login").param("username", "root").param("password", "admin"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void logoutEndsSession() throws Exception
	{
		TestAuth.Login login = TestAuth.login(mvc);
		mvc.perform(post("/logout").session(login.session()).param("_csrf", login.csrf()))
			.andExpect(redirectedUrl("/login"));
		assertTrue(login.session().isInvalid());
	}

	@Test
	void settingsFileStoresHashNotPlaintext() throws Exception
	{
		TestAuth.login(mvc);
		String json = Files.readString(dir.resolve("settings.json"), StandardCharsets.UTF_8);
		assertFalse(json.contains(TestAuth.PASSWORD));
		assertTrue(store.snapshot().passwordHash.startsWith("$2"));
	}

	@Test
	void notPersistentWarningAndCsrfInPages() throws Exception
	{
		TestAuth.Login login = TestAuth.login(mvc);
		mvc.perform(get("/").session(login.session()))
			.andExpect(content().string(Matchers.containsString("value=\"" + login.csrf() + "\"")))
			.andExpect(content().string(Matchers.not(Matchers.containsString("will be lost"))));
	}

	@Test
	void envBannerOnlyShownWhenLoggedIn() throws Exception
	{
		mvc.perform(get("/login"))
			.andExpect(content().string(Matchers.not(Matchers.containsString("AA:BB:CC:11:22:33"))));
		TestAuth.Login login = TestAuth.login(mvc);
		mvc.perform(get("/").session(login.session()))
			.andExpect(content().string(Matchers.containsString("<code>AA:BB:CC:11:22:33</code>")))
			.andExpect(content().string(Matchers.containsString("can be removed")))
			.andExpect(content().string(Matchers.not(Matchers.containsString("updated or recreated"))));
	}
}
