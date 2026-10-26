package com.webhookmiddleman.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.webhookmiddleman.settings.SettingsStore;
import java.nio.file.Path;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "guide.location=classpath:guides-test/guide.md")
class GuideControllerTest
{
	@TempDir
	static Path dir;

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r)
	{
		r.add("config.dir", () -> dir.toString());
		r.add("listener.autostart", () -> "false");
		r.add("auth.failure-delay-ms", () -> "0");
	}

	@Autowired
	MockMvc mvc;

	@Autowired
	SettingsStore store;

	@TestBean(name = "envVars")
	List<String> envVars;

	static List<String> envVars()
	{
		return List.of();
	}

	TestAuth.Login login;

	@BeforeEach
	void setUp() throws Exception
	{
		TestAuth.resetPassword(store);
		login = TestAuth.login(mvc);
	}

	@Test
	void rendersMarkdownHeading() throws Exception
	{
		mvc.perform(get("/guide").session(login.session())).andExpect(status().isOk())
			.andExpect(content().string(Matchers.containsString("<h1>Hello Guide</h1>")));
	}

	@Test
	void rawHtmlInMarkdownIsEscaped() throws Exception
	{
		mvc.perform(get("/guide").session(login.session()))
			.andExpect(content().string(Matchers.containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
			.andExpect(content().string(Matchers.not(Matchers.containsString("<script>alert(1)"))))
			.andExpect(content().string(Matchers.not(Matchers.containsString("javascript:"))));
	}

	@Test
	void relativeImagesRewrittenToGuideAssets() throws Exception
	{
		mvc.perform(get("/guide").session(login.session()))
			.andExpect(content().string(Matchers.containsString("src=\"/guide-assets/images/test.png\"")))
			.andExpect(content().string(Matchers.not(Matchers.containsString("secret.png"))));
	}

	@Test
	void guideAssetServedWhenLoggedIn() throws Exception
	{
		mvc.perform(get("/guide-assets/images/test.png").session(login.session()))
			.andExpect(status().isOk())
			.andExpect(content().string("PNGDATA"));
	}

	@Test
	void guideAssetPathTraversalRejected() throws Exception
	{
		mvc.perform(get("/guide-assets/../application.properties").session(login.session()))
			.andExpect(status().isNotFound());
		mvc.perform(get("/guide-assets/%2e%2e/application.properties").session(login.session()))
			.andExpect(status().isNotFound());
		mvc.perform(get("/guide-assets/images/..%2f..%2fapplication.properties").session(login.session()))
			.andExpect(status().isNotFound());
	}

	@Test
	void guideRequiresLogin() throws Exception
	{
		mvc.perform(get("/guide")).andExpect(redirectedUrl("/login"));
		mvc.perform(get("/guide-assets/images/test.png")).andExpect(redirectedUrl("/login"));
	}
}

/** Separate context: the configured guide resource does not exist. */
@SpringBootTest(properties = "guide.location=classpath:guides/does-not-exist.md")
@AutoConfigureMockMvc
class GuideMissingTest
{
	@TempDir
	static Path dir;

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r)
	{
		r.add("config.dir", () -> dir.toString());
		r.add("listener.autostart", () -> "false");
		r.add("auth.failure-delay-ms", () -> "0");
	}

	@Autowired
	MockMvc mvc;

	@Autowired
	SettingsStore store;

	@TestBean(name = "envVars")
	List<String> envVars;

	static List<String> envVars()
	{
		return List.of();
	}

	@Test
	void missingGuideShowsFriendlyMessage() throws Exception
	{
		TestAuth.resetPassword(store);
		TestAuth.Login login = TestAuth.login(mvc);
		mvc.perform(get("/guide").session(login.session())).andExpect(status().isOk())
			.andExpect(content().string(Matchers.containsString("The guide is not available in this build.")));
	}
}
