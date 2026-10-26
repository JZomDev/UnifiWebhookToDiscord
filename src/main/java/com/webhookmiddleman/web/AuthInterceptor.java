package com.webhookmiddleman.web;

import com.webhookmiddleman.settings.Settings;
import com.webhookmiddleman.settings.SettingsStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Session auth and CSRF for every UI request: anonymous requests go to
 * {@code /login}, a pending forced password change sends everything except
 * {@code /change-password} and {@code /logout} there, and every POST except
 * {@code /login} must carry {@code _csrf} matching the session token.
 */
public class AuthInterceptor implements HandlerInterceptor
{
	/** Session attribute: {@link Boolean#TRUE} once logged in. */
	public static final String AUTH = "AUTH";
	/** Session attribute: the CSRF token forms send as {@code _csrf}. */
	public static final String CSRF = "CSRF";

	private static final SecureRandom RANDOM = new SecureRandom();

	private final SettingsStore store;

	public AuthInterceptor(SettingsStore store)
	{
		this.store = store;
	}

	/** Returns the session's CSRF token, creating the session and token if needed. */
	public static String csrfToken(HttpSession session)
	{
		Object t = session.getAttribute(CSRF);
		if (t instanceof String s)
		{
			return s;
		}
		String token = newToken();
		session.setAttribute(CSRF, token);
		return token;
	}

	static String newToken()
	{
		byte[] b = new byte[32];
		RANDOM.nextBytes(b);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception
	{
		String path = request.getRequestURI().substring(request.getContextPath().length());
		if ("/login".equals(path))
		{
			return true;
		}
		HttpSession session = request.getSession(false);
		if (session == null || !Boolean.TRUE.equals(session.getAttribute(AUTH)))
		{
			response.sendRedirect(request.getContextPath() + "/login");
			return false;
		}
		if ("POST".equalsIgnoreCase(request.getMethod()) && !csrfMatches(session, request.getParameter("_csrf")))
		{
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid or missing CSRF token");
			return false;
		}
		if (passwordChangeRequired(store.snapshot()) && !"/change-password".equals(path) && !"/logout".equals(path))
		{
			response.sendRedirect(request.getContextPath() + "/change-password");
			return false;
		}
		return true;
	}

	/** The password must be changed while flagged, or while no hash is stored (the default admin/admin). */
	static boolean passwordChangeRequired(Settings s)
	{
		return s.mustChangePassword || s.passwordHash == null;
	}

	private static boolean csrfMatches(HttpSession session, String sent)
	{
		Object expected = session.getAttribute(CSRF);
		if (!(expected instanceof String e) || sent == null)
		{
			return false;
		}
		return MessageDigest.isEqual(e.getBytes(StandardCharsets.UTF_8), sent.getBytes(StandardCharsets.UTF_8));
	}
}
