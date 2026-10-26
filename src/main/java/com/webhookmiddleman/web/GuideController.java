package com.webhookmiddleman.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** The Guide page: renders the markdown guide (default {@code guides/setup.md}) inside the layout. */
@Controller
public class GuideController
{
	private static final Logger log = LoggerFactory.getLogger(GuideController.class);

	static final String ASSET_PREFIX = "/guide-assets/";

	private final Parser parser = Parser.builder().build();
	private final HtmlRenderer renderer = HtmlRenderer.builder()
		.escapeHtml(true)
		.sanitizeUrls(true)
		.attributeProviderFactory(context -> new ImageSrcRewriter())
		.build();

	private final ResourceLoader loader;
	private final String location;

	public GuideController(ResourceLoader loader, @Value("${guide.location:classpath:guides/setup.md}") String location)
	{
		this.loader = loader;
		this.location = location;
	}

	@GetMapping("/guide")
	public String guide(Model model)
	{
		model.addAttribute("html", render());
		return "guide";
	}

	/** Returns the rendered guide, or null when the resource is missing or unreadable. */
	private String render()
	{
		Resource resource = loader.getResource(location);
		if (!resource.exists())
		{
			return null;
		}
		try (InputStream in = resource.getInputStream())
		{
			String markdown = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			Node doc = parser.parse(markdown);
			return renderer.render(doc);
		}
		catch (IOException e)
		{
			log.warn("Could not read guide {}: {}", location, e.toString());
			return null;
		}
	}

	/** Points relative image paths at {@code /guide-assets/} and blanks ones that try to traverse upward. */
	static class ImageSrcRewriter implements AttributeProvider
	{
		@Override
		public void setAttributes(Node node, String tagName, Map<String, String> attributes)
		{
			if (!(node instanceof Image))
			{
				return;
			}
			String src = attributes.get("src");
			if (src == null || src.isEmpty() || src.contains(":") || src.startsWith("/"))
			{
				return;
			}
			String path = src.replace('\\', '/');
			for (String part : path.split("/"))
			{
				if (part.equals("..") || part.toLowerCase().contains("%2e"))
				{
					attributes.put("src", "");
					return;
				}
			}
			while (path.startsWith("./"))
			{
				path = path.substring(2);
			}
			attributes.put("src", ASSET_PREFIX + path);
		}
	}
}
