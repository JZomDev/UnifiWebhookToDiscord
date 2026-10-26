package com.webhookmiddleman.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Real Tomcat on a random port: the session cookie is Secure only behind an HTTPS proxy. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionCookieTest
{
	@TempDir
	static Path dir;

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r)
	{
		r.add("config.dir", () -> dir.toString());
		r.add("listener.autostart", () -> "false");
	}

	@LocalServerPort
	int port;

	HttpClient client = HttpClient.newHttpClient();

	String sessionCookie(String proto) throws Exception
	{
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login"));
		if (proto != null)
		{
			b.header("X-Forwarded-Proto", proto);
		}
		HttpResponse<String> res = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
		return res.headers().allValues("Set-Cookie").stream()
			.filter(c -> c.startsWith("JSESSIONID="))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no session cookie: " + res.headers()));
	}

	@Test
	void plainHttpCookieIsNotSecure() throws Exception
	{
		String cookie = sessionCookie(null);
		assertFalse(cookie.toLowerCase().contains("secure"), cookie);
		assertTrue(cookie.toLowerCase().contains("httponly"), cookie);
	}

	@Test
	void httpsProxyCookieIsSecure() throws Exception
	{
		assertTrue(sessionCookie("https").toLowerCase().contains("; secure"));
	}

	String redirectLocation(String proto) throws Exception
	{
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/"));
		if (proto != null)
		{
			b.header("X-Forwarded-Proto", proto);
		}
		HttpResponse<String> res = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
		assertEquals(302, res.statusCode());
		return res.headers().firstValue("Location").orElseThrow();
	}

	@Test
	void httpsProxyRedirectDoesNotDowngradeToHttp() throws Exception
	{
		String location = redirectLocation("https");
		assertFalse(location.startsWith("http://"), location);
		assertTrue(location.equals("/login") || location.startsWith("https://"), location);
	}

	@Test
	void plainHttpRedirectStillReachesLogin() throws Exception
	{
		String location = redirectLocation(null);
		URI resolved = URI.create("http://localhost:" + port + "/").resolve(location);
		assertEquals("http://localhost:" + port + "/login", resolved.toString());
	}

	@Test
	void httpProxyCookieIsNotSecure() throws Exception
	{
		assertFalse(sessionCookie("http").toLowerCase().contains("secure"));
	}
}
