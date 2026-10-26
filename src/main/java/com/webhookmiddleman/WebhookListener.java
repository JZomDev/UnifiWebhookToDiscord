package com.webhookmiddleman;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Serves {@code POST /data} on its own port, independent of Spring.
 */
public class WebhookListener
{
	private static final Logger logger = LogManager.getLogger(WebhookListener.class);

	private final WebhookProcessor processor;
	private HttpServer server;
	private ExecutorService executor;
	private int failedPort = -1;
	private String downReason;

	public WebhookListener(WebhookProcessor processor)
	{
		this.processor = processor;
	}

	public synchronized void start(int port) throws IOException
	{
		if (server != null)
		{
			throw new IllegalStateException("Listener already started");
		}
		open(port);
	}

	public synchronized void stop()
	{
		if (server != null)
		{
			server.stop(0);
			executor.shutdown();
			server = null;
			executor = null;
		}
		failedPort = -1;
		downReason = null;
	}

	/** The port of the last failed start, or -1. */
	public synchronized int failedPort()
	{
		return failedPort;
	}

	/** Why the listener is not running after a failed start, or null if it is running or never tried. */
	public synchronized String downReason()
	{
		return server == null ? downReason : null;
	}

	/** The exception's message, or its type when it has none. */
	public static String describe(Exception e)
	{
		String m = e.getMessage();
		return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
	}

	public synchronized int port()
	{
		return server == null ? -1 : server.getAddress().getPort();
	}

	private void open(int port) throws IOException
	{
		HttpServer s;
		try
		{
			s = HttpServer.create(new InetSocketAddress(port), 0);
		}
		catch (IOException | RuntimeException e)
		{
			// Nothing is listening: remember why, for the UI.
			failedPort = port;
			downReason = describe(e);
			throw e;
		}
		ExecutorService e = Executors.newCachedThreadPool();
		s.createContext("/", this::handle);
		s.setExecutor(e);
		s.start();
		server = s;
		executor = e;
		failedPort = -1;
		downReason = null;
		logger.info("Webhook listener started on port {}", s.getAddress().getPort());
	}

	private void handle(HttpExchange exchange) throws IOException
	{
		try
		{
			if (!"/data".equals(exchange.getRequestURI().getPath()))
			{
				respond(exchange, 404, "Not found");
				return;
			}
			if (!"POST".equals(exchange.getRequestMethod()))
			{
				respond(exchange, 405, "Method not allowed");
				return;
			}
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			Map<String, Object> payload;
			try
			{
				payload = new JSONObject(body).toMap();
			}
			catch (JSONException ex)
			{
				logger.warn("Rejected malformed JSON body: {}", ex.getMessage());
				respond(exchange, 400, "Bad request");
				return;
			}
			int status = processor.handle(payload);
			respond(exchange, status, status == 200 ? "Webhook received successfully" : "Bad request");
		}
		catch (RuntimeException ex)
		{
			logger.error("Error handling webhook", ex);
			respond(exchange, 500, "Internal error");
		}
		finally
		{
			exchange.close();
		}
	}

	private void respond(HttpExchange exchange, int status, String text) throws IOException
	{
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = exchange.getResponseBody())
		{
			out.write(bytes);
		}
	}
}
