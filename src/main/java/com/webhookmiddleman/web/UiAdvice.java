package com.webhookmiddleman.web;

import com.webhookmiddleman.WebhookListener;
import com.webhookmiddleman.settings.SettingsStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Adds {@code csrf}, {@code envVars} (banner, logged-in only), {@code notPersistent}, {@code configDir}, {@code settingsLoadNotice} and {@code listenerDown} (logged-in only) to every view. */
@ControllerAdvice
public class UiAdvice
{
	private final SettingsStore store;
	private final List<String> envVars;
	private final WebhookListener listener;

	public UiAdvice(SettingsStore store, @Qualifier("envVars") List<String> envVars, WebhookListener listener)
	{
		this.store = store;
		this.envVars = envVars;
		this.listener = listener;
	}

	@ModelAttribute
	public void addCommon(HttpServletRequest request, Model model)
	{
		HttpSession session = request.getSession(true);
		model.addAttribute("csrf", AuthInterceptor.csrfToken(session));
		// Env var names include camera MACs, so only show them once logged in.
		boolean authed = Boolean.TRUE.equals(session.getAttribute(AuthInterceptor.AUTH));
		if (authed && store.snapshot().showEnvReminder && !envVars.isEmpty())
		{
			model.addAttribute("envVars", envVars);
		}
		String down = listener.downReason();
		if (authed && down != null)
		{
			model.addAttribute("listenerDown", "Webhook listener is not running on port " + listener.failedPort() + ": " + down
				+ ". Unifi alarms are not being received; free that port, or set the port environment variable to another port (and update the Docker port mapping), then restart the container.");
		}
		model.addAttribute("notPersistent", !store.isPersistent());
		model.addAttribute("configDir", store.directory().toString());
		model.addAttribute("settingsLoadNotice", store.loadNotice());
	}
}
