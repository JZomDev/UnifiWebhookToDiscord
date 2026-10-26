package com.webhookmiddleman.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Placeholder home page; replaced by the Cameras page (delete this and templates/home.html). */
@Controller
public class HomeController
{
	@GetMapping("/")
	public String home()
	{
		return "home";
	}
}
