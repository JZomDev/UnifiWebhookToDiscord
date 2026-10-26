package com.webhookmiddleman.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.ui.ExtendedModelMap;

@ExtendWith(OutputCaptureExtension.class)
class GuideReadErrorTest
{
	@Test
	void unreadableGuideIsLoggedAtWarn(CapturedOutput output)
	{
		Resource broken = new AbstractResource()
		{
			@Override
			public boolean exists()
			{
				return true;
			}

			@Override
			public String getDescription()
			{
				return "broken guide";
			}

			@Override
			public InputStream getInputStream() throws IOException
			{
				throw new IOException("disk on fire");
			}
		};
		DefaultResourceLoader loader = new DefaultResourceLoader()
		{
			@Override
			public Resource getResource(String location)
			{
				return broken;
			}
		};
		GuideController controller = new GuideController(loader, "classpath:guides/setup.md");
		ExtendedModelMap model = new ExtendedModelMap();
		assertEquals("guide", controller.guide(model));
		assertNull(model.get("html"));
		String log = output.getOut() + output.getErr();
		assertTrue(log.contains("WARN"), log);
		assertTrue(log.contains("Could not read guide classpath:guides/setup.md"), log);
		assertTrue(log.contains("disk on fire"), log);
		assertFalse(log.contains("at com.webhookmiddleman"), "no stack trace expected");
	}
}
