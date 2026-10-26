package com.webhookmiddleman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.webhookmiddleman.settings.SettingsStore;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebhookListenerTest
{
	@TempDir
	Path dir;

	WebhookListener listener;
	HttpClient client = HttpClient.newHttpClient();

	@BeforeEach
	void setUp()
	{
		SettingsStore store = new SettingsStore(dir.resolve("settings.json"));
		WebhookProcessor processor = new WebhookProcessor(store, (u, m, e, t) -> { });
		listener = new WebhookListener(processor);
	}

	@AfterEach
	void tearDown()
	{
		listener.stop();
	}

	static int freePort() throws IOException
	{
		try (ServerSocket s = new ServerSocket(0))
		{
			return s.getLocalPort();
		}
	}

	int post(int port, String path, String body) throws Exception
	{
		HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
			.POST(HttpRequest.BodyPublishers.ofString(body)).build();
		return client.send(req, HttpResponse.BodyHandlers.ofString()).statusCode();
	}

	@Test
	void failedStartIsRecordedUntilABindSucceeds() throws Exception
	{
		assertNull(listener.downReason());
		try (ServerSocket busy = new ServerSocket(0))
		{
			int port = busy.getLocalPort();
			assertThrows(IOException.class, () -> listener.start(port));
			assertEquals(-1, listener.port());
			assertEquals(port, listener.failedPort());
			assertNotNull(listener.downReason());
		}
		listener.start(0);
		assertNull(listener.downReason());
		assertEquals(-1, listener.failedPort());
	}

	@Test
	void acceptsPostData() throws Exception
	{
		int p = freePort();
		listener.start(p);
		assertEquals(p, listener.port());
		assertEquals(200, post(p, "/data", "{\"a\":1}"));
	}

	@Test
	void rejectsOtherPaths() throws Exception
	{
		int p = freePort();
		listener.start(p);
		assertEquals(404, post(p, "/other", "{}"));
		HttpRequest get = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + p + "/data")).GET().build();
		assertTrue(client.send(get, HttpResponse.BodyHandlers.ofString()).statusCode() >= 400);
	}

	@Test
	void badJsonReturns400NotCrash() throws Exception
	{
		int p = freePort();
		listener.start(p);
		assertEquals(400, post(p, "/data", "{not json"));
		assertEquals(200, post(p, "/data", "{}"));
	}
}
