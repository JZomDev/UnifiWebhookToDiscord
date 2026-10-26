package com.webhookmiddleman.web;

import com.webhookmiddleman.settings.SettingsStore;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The Cameras page: list, add, rename and delete MAC to name mappings. */
@Controller
public class CameraController
{
	static final int MAX_NAME = 100;

	private final SettingsStore store;

	public CameraController(SettingsStore store)
	{
		this.store = store;
	}

	/** The name length cap, for the form inputs' {@code maxlength}. */
	@ModelAttribute("maxName")
	public int maxName()
	{
		return MAX_NAME;
	}

	@GetMapping("/")
	public String list(Model model)
	{
		model.addAttribute("cameras", store.snapshot().cameras);
		return "cameras";
	}

	@PostMapping("/cameras")
	public String add(@RequestParam(defaultValue = "") String mac, @RequestParam(defaultValue = "") String name, HttpServletResponse response, Model model)
	{
		String normalized = SettingsStore.normalizeMac(mac);
		String trimmed = name.trim();
		String error = null;
		if (normalized == null)
		{
			error = "Enter a valid MAC address, e.g. AA:BB:CC:11:22:33.";
		}
		else
		{
			error = checkName(trimmed);
		}
		if (error != null)
		{
			model.addAttribute("addError", error);
			model.addAttribute("mac", mac);
			model.addAttribute("name", name);
			return invalid(response, model);
		}
		store.update(s -> s.cameras.put(normalized, trimmed));
		return "redirect:/";
	}

	@PostMapping("/cameras/{mac}")
	public String rename(@PathVariable String mac, @RequestParam(defaultValue = "") String name, HttpServletResponse response, Model model)
	{
		String normalized = SettingsStore.normalizeMac(mac);
		String trimmed = name.trim();
		String error = checkName(trimmed);
		if (error != null)
		{
			model.addAttribute("editError", error);
			model.addAttribute("editMac", normalized);
			return invalid(response, model);
		}
		if (normalized != null)
		{
			store.update(s ->
			{
				if (s.cameras.containsKey(normalized))
				{
					s.cameras.put(normalized, trimmed);
				}
			});
		}
		return "redirect:/";
	}

	@PostMapping("/cameras/{mac}/delete")
	public String delete(@PathVariable String mac)
	{
		String normalized = SettingsStore.normalizeMac(mac);
		if (normalized != null)
		{
			store.update(s -> s.cameras.remove(normalized));
		}
		return "redirect:/";
	}

	private String invalid(HttpServletResponse response, Model model)
	{
		model.addAttribute("cameras", store.snapshot().cameras);
		response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		return "cameras";
	}

	private static String checkName(String name)
	{
		if (name.isEmpty())
		{
			return "Enter a camera name.";
		}
		if (name.length() > MAX_NAME)
		{
			return "Camera name must be at most " + MAX_NAME + " characters.";
		}
		return null;
	}
}
