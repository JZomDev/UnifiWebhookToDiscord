package com.webhookmiddleman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class WebhookClientMultipartTest
{
	@Test
	void buildsDiscordMultipartBody() throws Exception
	{
		JSONObject payload = new JSONObject().put("content", "hello");
		byte[] image = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x01, 0x02};
		String boundary = "testboundary123";

		byte[] body = WebhookClient.buildMultipartBody(payload, image, Thumbnails.FILENAME, boundary);
		String text = new String(body, StandardCharsets.UTF_8);

		assertTrue(text.contains("--" + boundary), "opens with boundary");
		assertTrue(text.contains("name=\"payload_json\""), "has payload_json part");
		assertTrue(text.contains("Content-Type: application/json"), "payload part is json");
		assertTrue(text.contains(payload.toString()), "embeds the JSON verbatim");
		assertTrue(text.contains("name=\"files[0]\"; filename=\"" + Thumbnails.FILENAME + "\""),
			"has the file part matching the attachment:// name");
		assertTrue(text.contains("Content-Type: image/jpeg"), "file part is jpeg");
		assertTrue(text.trim().endsWith("--" + boundary + "--"), "closes with terminating boundary");
	}

	@Test
	void multipartBodyContainsRawImageBytes() throws Exception
	{
		JSONObject payload = new JSONObject().put("x", 1);
		// includes 0x0D 0x0A and non-ASCII bytes to prove the image is written raw, not re-encoded
		byte[] image = {0x00, (byte) 0x99, 0x0D, 0x0A, (byte) 0xAB, 0x7F};
		byte[] body = WebhookClient.buildMultipartBody(payload, image, Thumbnails.FILENAME, "b");

		assertTrue(indexOf(body, image) >= 0, "raw image bytes present contiguously in body");
	}

	private static int indexOf(byte[] haystack, byte[] needle)
	{
		outer:
		for (int i = 0; i <= haystack.length - needle.length; i++)
		{
			for (int j = 0; j < needle.length; j++)
			{
				if (haystack[i + j] != needle[j])
				{
					continue outer;
				}
			}
			return i;
		}
		return -1;
	}
}
