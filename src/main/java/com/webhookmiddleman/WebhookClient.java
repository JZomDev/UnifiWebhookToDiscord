package com.webhookmiddleman;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

public class WebhookClient
{
	private static final Logger logger = LogManager.getLogger(WebhookClient.class);

	private final Callback callback;

	/**
	 * Constructs a new instance of WebhookClient with a specified callback.
	 *
	 * @param callback The callback to handle webhook execution responses.
	 */
	public WebhookClient(Callback callback)
	{
		this.callback = callback;
	}

	/**
	 * Sends a message to a specified webhook URL.
	 *
	 * @param webhookUrl The URL of the webhook to send the message to.
	 * @param message    The JSON message to be sent.
	 */
	public void send(String webhookUrl, JSONObject message)
	{
		send(webhookUrl, message, null);
	}

	/**
	 * Sends a message to a webhook URL, optionally attaching an image.
	 *
	 * When {@code image} is non-empty the request is sent as
	 * {@code multipart/form-data} with the embed JSON in the {@code payload_json}
	 * part and the image in {@code files[0]}, so Discord can render it via an
	 * {@code attachment://} embed reference. Otherwise a plain JSON POST is used.
	 *
	 * @param webhookUrl The URL of the webhook to send the message to.
	 * @param message    The JSON message to be sent.
	 * @param image      Raw image bytes to attach, or null for a JSON-only send.
	 */
	public void send(String webhookUrl, JSONObject message, byte[] image)
	{
		try
		{
			// Create a URI object from the provided webhook URL
			URI uri = new URI(webhookUrl);

			// Open a connection to the webhook URL
			HttpsURLConnection connection = (HttpsURLConnection) uri.toURL().openConnection();
			String userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.103 Safari/537.36";
			connection.setRequestProperty("User-Agent", userAgent);

			// Enable output for sending POST data
			connection.setDoOutput(true);

			boolean multipart = image != null && image.length > 0;
			if (multipart)
			{
				String boundary = "unifiwebhook" + Long.toHexString(System.nanoTime());
				connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
				byte[] body = buildMultipartBody(message, image, Thumbnails.FILENAME, boundary);
				try (OutputStream stream = connection.getOutputStream())
				{
					stream.write(body);
				}
			}
			else
			{
				connection.setRequestProperty("Content-Type", "application/json");
				try (OutputStream stream = connection.getOutputStream())
				{
					stream.write(message.toString().getBytes(StandardCharsets.UTF_8));
				}
			}

			// Get the HTTP response code
			int responseCode = connection.getResponseCode();
			logger.info("Response Code: " + responseCode);

			// Handle response based on response code
			if (responseCode == HttpsURLConnection.HTTP_OK || responseCode == HttpsURLConnection.HTTP_NO_CONTENT)
			{
				handleSuccessfulResponse(connection);
			}
			else
			{
				handleErrorResponse(connection, responseCode);
			}
		}
		catch (IOException | URISyntaxException e)
		{
			logger.error("I/O Error: " + e.getMessage());
			callback.onFailure(-1, e.getMessage());
		}
	}

	/**
	 * Builds a Discord {@code multipart/form-data} body: the embed JSON in the
	 * {@code payload_json} field and the image in {@code files[0]}. The image is
	 * referenced from the embed as {@code attachment://<filename>}.
	 *
	 * @param payloadJson the message/embed JSON.
	 * @param image       the raw image bytes.
	 * @param filename    the attachment filename (must match the embed's attachment:// url).
	 * @param boundary    the multipart boundary.
	 * @return the encoded request body.
	 */
	static byte[] buildMultipartBody(JSONObject payloadJson, byte[] image, String filename, String boundary)
		throws IOException
	{
		final String CRLF = "\r\n";
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		StringBuilder head = new StringBuilder();
		head.append("--").append(boundary).append(CRLF);
		head.append("Content-Disposition: form-data; name=\"payload_json\"").append(CRLF);
		head.append("Content-Type: application/json").append(CRLF).append(CRLF);
		head.append(payloadJson.toString()).append(CRLF);
		head.append("--").append(boundary).append(CRLF);
		head.append("Content-Disposition: form-data; name=\"files[0]\"; filename=\"")
			.append(filename).append("\"").append(CRLF);
		head.append("Content-Type: image/jpeg").append(CRLF).append(CRLF);

		out.write(head.toString().getBytes(StandardCharsets.UTF_8));
		out.write(image);
		out.write((CRLF + "--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8));
		return out.toByteArray();
	}

	/**
	 * Handles successful responses from the webhook server.
	 *
	 * @param connection The connection from which to read the response.
	 * @throws IOException If an I/O error occurs while reading the response.
	 */
	private void handleSuccessfulResponse(HttpsURLConnection connection) throws IOException
	{
		try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream())))
		{
			String inputLine;
			StringBuilder response = new StringBuilder();

			while ((inputLine = in.readLine()) != null)
			{
				response.append(inputLine);
			}

			callback.onSuccess(response.toString());
		}
	}

	/**
	 * Handles error responses from the webhook server.
	 *
	 * @param connection   The connection from which to read the error response.
	 * @param responseCode The HTTP response code indicating the type of error.
	 * @throws IOException If an I/O error occurs while reading the error response.
	 */
	private void handleErrorResponse(HttpsURLConnection connection, int responseCode) throws IOException
	{
		try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(
			responseCode == HttpsURLConnection.HTTP_NOT_FOUND ? connection.getErrorStream() : connection.getInputStream())))
		{

			String errorInputLine;
			StringBuilder errorResponse = new StringBuilder();

			while ((errorInputLine = errorReader.readLine()) != null)
			{
				errorResponse.append(errorInputLine);
			}

			callback.onFailure(-1, errorResponse.toString());
		}
	}

	/**
	 * An interface for handling webhook execution responses.
	 */
	public interface Callback
	{
		/**
		 * Called when the webhook execution is successful.
		 *
		 * @param response The response received from the server.
		 */
		void onSuccess(String response);

		/**
		 * Called when the webhook execution encounters an error.
		 *
		 * @param statusCode   The HTTP status code indicating the type of error.
		 * @param errorMessage The error message received from the server.
		 */
		void onFailure(int statusCode, String errorMessage);
	}
}