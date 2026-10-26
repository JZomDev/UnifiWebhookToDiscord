package com.webhookmiddleman.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import com.webhookmiddleman.settings.SettingsStore;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Shared login helper for web tests: logs in as admin/admin, completes the
 * forced password change and returns the authenticated session and its CSRF token.
 */
public final class TestAuth
{
	/** The password set by {@link #login(MockMvc)} during the forced change. */
	public static final String PASSWORD = "correct-horse-1";

	public record Login(MockHttpSession session, String csrf)
	{
	}

	private TestAuth()
	{
	}

	/** Puts the store back into the default admin/admin, must-change state. */
	public static void resetPassword(SettingsStore store)
	{
		store.update(s ->
		{
			s.passwordHash = null;
			s.mustChangePassword = true;
		});
	}

	/** Logs in with admin/admin and changes the password to {@link #PASSWORD}. Requires the default state. */
	public static Login login(MockMvc mvc) throws Exception
	{
		MvcResult res = mvc.perform(post("/login").param("username", "admin").param("password", "admin"))
			.andExpect(redirectedUrl("/"))
			.andReturn();
		MockHttpSession session = (MockHttpSession) res.getRequest().getSession(false);
		String csrf = (String) session.getAttribute(AuthInterceptor.CSRF);
		mvc.perform(post("/change-password").session(session)
			.param("_csrf", csrf)
			.param("password", PASSWORD)
			.param("confirm", PASSWORD))
			.andExpect(redirectedUrl("/"));
		return new Login(session, csrf);
	}
}
