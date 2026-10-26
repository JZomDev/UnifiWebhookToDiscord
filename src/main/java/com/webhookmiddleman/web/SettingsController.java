package com.webhookmiddleman.web;

import com.webhookmiddleman.WebhookListener;
import com.webhookmiddleman.settings.Settings;
import com.webhookmiddleman.settings.SettingsStore;
import jakarta.servlet.http.HttpServletResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The Settings page: webhook (with a test button), console UUIDs and the env reminder toggle. The webhook
 * listen port is only shown: it is set with env {@code port}, the container side of the Docker port mapping.
 */
@Controller
public class SettingsController
{
	/** Same pattern {@code WebhookManager} accepts. */
	static final Pattern WEBHOOK = Pattern.compile("https://discord\\.com/api/webhooks/[0-9]+/[A-Za-z0-9_\\-]+");
	static final String WEBHOOK_ERROR = "Enter a Discord webhook URL like https://discord.com/api/webhooks/123/abc, or leave it empty.";
	static final String TEST_WEBHOOK_ERROR = "Enter a valid Discord webhook URL (https://discord.com/api/webhooks/...) to send a test message.";

	/** A unifi.ui.com console link: {@code https://unifi.ui.com/consoles/<UUID 1>:<UUID 2>[/...]}. */
	static final Pattern CONSOLE_URL = Pattern.compile("https://unifi\\.ui\\.com/consoles/([A-Za-z0-9_\\-]+):([A-Za-z0-9_\\-]+)(?:[/?#].*)?");
	static final String CONSOLE_URL_ERROR = "Paste a link like https://unifi.ui.com/consoles/<id>:<number>/protect/... from your browser's address bar.";

	private final SettingsStore store;
	private final WebhookListener listener;
	private final TestMessageSender sender;

	public SettingsController(SettingsStore store, WebhookListener listener, TestMessageSender sender)
	{
		this.store = store;
		this.listener = listener;
		this.sender = sender;
	}

	/** The values shown in the form. */
	public record Form(String webhook, String uuid1, String uuid2, boolean showEnvReminder)
	{
	}

	@GetMapping("/settings")
	public String page(Model model)
	{
		Settings s = store.snapshot();
		if (!model.containsAttribute("form"))
		{
			model.addAttribute("form", new Form(s.webhook, s.uuid1, s.uuid2, s.showEnvReminder));
		}
		return view(model);
	}

	@PostMapping("/settings")
	public String save(@RequestParam(defaultValue = "") String webhook, @RequestParam(defaultValue = "") String uuid1,
		@RequestParam(defaultValue = "") String uuid2, @RequestParam(required = false) String showEnvReminder,
		HttpServletResponse response, Model model, RedirectAttributes redirect)
	{
		Form form = new Form(webhook.strip(), uuid1.strip(), uuid2.strip(), showEnvReminder != null);
		model.addAttribute("form", form);

		if (!form.webhook().isEmpty() && !WEBHOOK.matcher(form.webhook()).matches())
		{
			model.addAttribute("error", WEBHOOK_ERROR);
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return view(model);
		}
		store.update(s ->
		{
			s.webhook = form.webhook();
			s.uuid1 = form.uuid1();
			s.uuid2 = form.uuid2();
			s.showEnvReminder = form.showEnvReminder();
		});
		redirect.addFlashAttribute("saved", true);
		return "redirect:/settings";
	}

	/** Sends a test message to the webhook currently typed in the form (saved or not). */
	@PostMapping("/settings/test-webhook")
	public String testWebhook(@RequestParam(defaultValue = "") String webhook, @RequestParam(required = false) String uuid1,
		@RequestParam(required = false) String uuid2, @RequestParam(required = false) String showEnvReminder,
		HttpServletResponse response, Model model)
	{
		Settings s = store.snapshot();
		// Keep whatever else was typed, so testing does not discard unsaved edits.
		Form form = new Form(webhook.strip(),
			uuid1 != null ? uuid1.strip() : s.uuid1,
			uuid2 != null ? uuid2.strip() : s.uuid2,
			submittedForm(uuid1) ? showEnvReminder != null : s.showEnvReminder);
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

	/**
	 * Fills the two console UUID boxes from a unifi.ui.com console URL. Nothing is saved; the other typed values are kept.
	 */
	@PostMapping("/settings/console-url")
	public String fillFromConsoleUrl(@RequestParam(defaultValue = "") String consoleUrl, @RequestParam(required = false) String webhook,
		@RequestParam(required = false) String uuid1, @RequestParam(required = false) String uuid2,
		@RequestParam(required = false) String showEnvReminder, HttpServletResponse response, Model model)
	{
		Settings s = store.snapshot();
		Matcher m = CONSOLE_URL.matcher(consoleUrl.strip());
		boolean found = m.matches();
		model.addAttribute("form", new Form(webhook != null ? webhook.strip() : s.webhook,
			found ? m.group(1) : uuid1 != null ? uuid1.strip() : s.uuid1,
			found ? m.group(2) : uuid2 != null ? uuid2.strip() : s.uuid2,
			submittedForm(uuid1) ? showEnvReminder != null : s.showEnvReminder));
		if (!found)
		{
			model.addAttribute("consoleError", CONSOLE_URL_ERROR);
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return view(model);
		}
		model.addAttribute("consoleFilled", true);
		return view(model);
	}

	/**
	 * True when the request came from the whole Settings form (its text boxes are always sent), so a missing
	 * {@code showEnvReminder} means the box is unchecked rather than not sent.
	 */
	private static boolean submittedForm(String uuid1)
	{
		return uuid1 != null;
	}

	private String view(Model model)
	{
		Settings s = store.snapshot();
		model.addAttribute("webhookMissing", s.webhook.isBlank());
		// -1 while the listener is down; the layout then shows why instead.
		model.addAttribute("listenPort", listener.port());
		return "settings";
	}
}
