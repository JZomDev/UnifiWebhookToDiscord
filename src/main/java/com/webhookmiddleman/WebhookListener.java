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
 * Serves {@code POST /data} on its own port, independent of Spring, and can
 * move to another port at runtime.
 */
public class WebhookListener
{
	private static final Logger logger = LogManager.getLogger(WebhookListener.class);

	private final WebhookProcessor processor;
	private HttpServer server;
	private ExecutorService executor;

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

	/** Binds the new port first; if that fails, throws and leaves the old port running. */
	public synchronized void rebind(int port) throws IOException
	{
		HttpServer oldServer = server;
		ExecutorService oldExecutor = executor;
		open(port);
		if (oldServer != null)
		{
			oldServer.stop(0);
			oldExecutor.shutdown();
		}
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
	}

	public synchronized int port()
	{
		return server == null ? -1 : server.getAddress().getPort();
	}

	private void open(int port) throws IOException
	{
		HttpServer s = HttpServer.create(new InetSocketAddress(port), 0);
		ExecutorService e = Executors.newCachedThreadPool();
		s.createContext("/", this::handle);
		s.setExecutor(e);
		s.start();
		server = s;
		executor = e;
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
