package com.webhookmiddleman.web;

import com.webhookmiddleman.settings.SettingsStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Login, logout and (forced) password change. Single user {@code admin}. */
@Controller
public class AuthController
{
	static final String USERNAME = "admin";
	static final String DEFAULT_PASSWORD = "admin";
	static final int MIN_LENGTH = 8;
	/** BCrypt only uses the first 72 bytes. */
	static final int MAX_BYTES = 72;

	private final SettingsStore store;
	private final BCryptPasswordEncoder encoder;
	/** Delay after a failed login, in ms (property {@code auth.failure-delay-ms}). */
	final long failureDelayMs;

	public AuthController(SettingsStore store, BCryptPasswordEncoder encoder,
		@Value("${auth.failure-delay-ms:500}") long failureDelayMs)
	{
		this.store = store;
		this.encoder = encoder;
		this.failureDelayMs = failureDelayMs;
	}

	@GetMapping("/login")
	public String loginPage(HttpSession session)
	{
		if (Boolean.TRUE.equals(session.getAttribute(AuthInterceptor.AUTH)))
		{
			return "redirect:/";
		}
		return "login";
	}

	@PostMapping("/login")
	public String login(@RequestParam(defaultValue = "") String username, @RequestParam(defaultValue = "") String password,
		HttpServletRequest request, HttpServletResponse response, Model model) throws InterruptedException
	{
		if (!credentialsValid(username, password))
		{
			if (failureDelayMs > 0)
			{
				Thread.sleep(failureDelayMs);
			}
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			model.addAttribute("error", "Invalid username or password.");
			model.addAttribute("username", username);
			return "login";
		}
		request.getSession(true);
		request.changeSessionId();
		HttpSession session = request.getSession();
		session.setAttribute(AuthInterceptor.AUTH, Boolean.TRUE);
		session.setAttribute(AuthInterceptor.CSRF, AuthInterceptor.newToken());
		// AuthInterceptor sends "/" on to /change-password while a change is required.
		return "redirect:/";
	}

	private boolean credentialsValid(String username, String password)
	{
		if (!USERNAME.equals(username))
		{
			return false;
		}
		String hash = store.snapshot().passwordHash;
		if (hash == null)
		{
			return DEFAULT_PASSWORD.equals(password);
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
		{
			return false;
		}
		return encoder.matches(password, hash);
	}

	@GetMapping("/change-password")
	public String changePasswordPage(Model model)
	{
		model.addAttribute("forced", AuthInterceptor.passwordChangeRequired(store.snapshot()));
		return "change-password";
	}

	@PostMapping("/change-password")
	public String changePassword(@RequestParam(defaultValue = "") String password,
		@RequestParam(defaultValue = "") String confirm, HttpServletResponse response, Model model)
	{
		String error = validate(password, confirm);
		if (error != null)
		{
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			model.addAttribute("error", error);
			model.addAttribute("forced", AuthInterceptor.passwordChangeRequired(store.snapshot()));
			return "change-password";
		}
		String hash = encoder.encode(password);
		store.update(s ->
		{
			s.passwordHash = hash;
			s.mustChangePassword = false;
		});
		return "redirect:/";
	}

	static String validate(String password, String confirm)
	{
		if (password.length() < MIN_LENGTH)
		{
			return "Password must be at least " + MIN_LENGTH + " characters.";
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
		{
			return "Password must be at most " + MAX_BYTES + " bytes.";
		}
		if (DEFAULT_PASSWORD.equals(password))
		{
			return "Choose a password other than the default.";
		}
		if (!password.equals(confirm))
		{
			return "The two passwords do not match.";
		}
		return null;
	}

	@PostMapping("/logout")
	public String logout(HttpSession session)
	{
		session.invalidate();
		return "redirect:/login";
	}
}
