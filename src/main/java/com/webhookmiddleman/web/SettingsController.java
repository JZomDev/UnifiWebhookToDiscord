package com.webhookmiddleman.web;

import com.webhookmiddleman.WebhookListener;
import com.webhookmiddleman.settings.Settings;
import com.webhookmiddleman.settings.SettingsStore;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The Settings page: webhook (with a test button), listener port, console UUIDs and the env reminder toggle. */
@Controller
public class SettingsController
{
	private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

	/** Same pattern {@code WebhookManager} accepts. */
	static final Pattern WEBHOOK = Pattern.compile("https://discord\\.com/api/webhooks/[0-9]+/[A-Za-z0-9_\\-]+");
	static final String WEBHOOK_ERROR = "Enter a Discord webhook URL like https://discord.com/api/webhooks/123/abc, or leave it empty.";
	static final String TEST_WEBHOOK_ERROR = "Enter a valid Discord webhook URL (https://discord.com/api/webhooks/...) to send a test message.";
	static final String PORT_ERROR = "Enter a port between 1 and 65535.";

	private final SettingsStore store;
	private final WebhookListener listener;
	private final TestMessageSender sender;
	private final int uiPort;

	public SettingsController(SettingsStore store, WebhookListener listener, TestMessageSender sender,
		@Value("${server.port:45462}") int uiPort)
	{
		this.store = store;
		this.listener = listener;
		this.sender = sender;
		this.uiPort = uiPort;
	}

	/** The values shown in the form. */
	public record Form(String webhook, String webhookPort, String uuid1, String uuid2, boolean showEnvReminder)
	{
	}

	@GetMapping("/settings")
	public String page(Model model)
	{
		Settings s = store.snapshot();
		if (!model.containsAttribute("form"))
		{
			model.addAttribute("form", new Form(s.webhook, String.valueOf(s.webhookPort), s.uuid1, s.uuid2, s.showEnvReminder));
		}
		return view(model);
	}

	@PostMapping("/settings")
	public String save(@RequestParam(defaultValue = "") String webhook, @RequestParam(defaultValue = "") String webhookPort,
		@RequestParam(defaultValue = "") String uuid1, @RequestParam(defaultValue = "") String uuid2,
		@RequestParam(required = false) String showEnvReminder, HttpServletResponse response, Model model,
		RedirectAttributes redirect)
	{
		Form form = new Form(webhook.strip(), webhookPort.strip(), uuid1.strip(), uuid2.strip(), showEnvReminder != null);
		model.addAttribute("form", form);

		if (!form.webhook().isEmpty() && !WEBHOOK.matcher(form.webhook()).matches())
		{
			return invalid(response, model, WEBHOOK_ERROR);
		}
		int port = parsePort(form.webhookPort());
		if (port < 1)
		{
			return invalid(response, model, PORT_ERROR);
		}
		if (port == uiPort)
		{
			return invalid(response, model, "Port " + port + " is used by the web UI. Choose a different port for the webhook listener.");
		}

		String bindError = null;
		synchronized (this)
		{
			if (port != listener.port())
			{
				// The listener is down on the saved port (e.g. it was busy at startup) and the port is unchanged:
				// save the other fields regardless and just retry the bind.
				boolean retryOnly = listener.port() == -1 && port == store.snapshot().webhookPort;
				try
				{
					listener.rebind(port);
				}
				catch (IOException e)
				{
					log.warn("Could not bind webhook listener to port {}: {}", port, e.toString());
					String reason = "Could not listen on port " + port + " (" + WebhookListener.describe(e) + "). It may be in use.";
					if (!retryOnly)
					{
						// A new port that cannot be bound: nothing is saved and the old port keeps running.
						return invalid(response, model, reason + " Settings were not saved.");
					}
					bindError = reason + " The other settings were saved; the webhook listener is still not running.";
				}
			}
			store.update(s ->
			{
				s.webhook = form.webhook();
				s.webhookPort = port;
				s.uuid1 = form.uuid1();
				s.uuid2 = form.uuid2();
				s.showEnvReminder = form.showEnvReminder();
			});
		}
		if (bindError != null)
		{
			redirect.addFlashAttribute("bindError", bindError);
		}
		redirect.addFlashAttribute("saved", true);
		return "redirect:/settings";
	}

	/** Sends a test message to the webhook currently typed in the form (saved or not). */
	@PostMapping("/settings/test-webhook")
	public String testWebhook(@RequestParam(defaultValue = "") String webhook, @RequestParam(required = false) String webhookPort,
		@RequestParam(required = false) String uuid1, @RequestParam(required = false) String uuid2,
		@RequestParam(required = false) String showEnvReminder, HttpServletResponse response, Model model)
	{
		Settings s = store.snapshot();
		// Keep whatever else was typed, so testing does not discard unsaved edits.
		Form form = new Form(webhook.strip(),
			webhookPort != null ? webhookPort.strip() : String.valueOf(s.webhookPort),
			uuid1 != null ? uuid1.strip() : s.uuid1,
			uuid2 != null ? uuid2.strip() : s.uuid2,
			webhookPort != null ? showEnvReminder != null : s.showEnvReminder);
		model.addAttribute("form", form);
		if (!WEBHOOK.matcher(form.webhook()).matches())
		{
			model.addAttribute("testError", TEST_WEBHOOK_ERROR);
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return view(model);
		}
		TestMessageSender.Result result = sender.send(form.webhook());
		model.addAttribute(result.ok() ? "testOk" : "testError", result.message());
		model.addAttribute("testUnsaved", !form.webhook().equals(s.webhook));
		return view(model);
	}

	private String invalid(HttpServletResponse response, Model model, String error)
	{
		model.addAttribute("error", error);
		response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		return view(model);
	}

	private String view(Model model)
	{
		Settings s = store.snapshot();
		model.addAttribute("webhookMissing", s.webhook.isBlank());
		model.addAttribute("uiPort", uiPort);
		return "settings";
	}

	/** Returns the port, or -1 if it is not a number between 1 and 65535. */
	static int parsePort(String raw)
	{
		if (raw.isEmpty() || raw.length() > 5 || !raw.chars().allMatch(c -> c >= '0' && c <= '9'))
		{
			return -1;
		}
		int port = Integer.parseInt(raw);
		return port >= 1 && port <= 65535 ? port : -1;
	}
}
