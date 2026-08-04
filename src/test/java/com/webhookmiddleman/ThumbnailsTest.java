package com.webhookmiddleman;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ThumbnailsTest
{
	private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

	@Test
	void decodesPlainBase64()
	{
		String b64 = Base64.getEncoder().encodeToString(JPEG_MAGIC);
		assertArrayEquals(JPEG_MAGIC, Thumbnails.decode(b64));
	}

	@Test
	void decodesDataUriPrefixedBase64()
	{
		String b64 = Base64.getEncoder().encodeToString(JPEG_MAGIC);
		assertArrayEquals(JPEG_MAGIC, Thumbnails.decode("data:image/jpeg;base64," + b64));
	}

	@Test
	void toleratesEmbeddedNewlines()
	{
		byte[] payload = "a longer thumbnail payload to wrap".getBytes(StandardCharsets.UTF_8);
		String b64 = Base64.getEncoder().encodeToString(payload);
		String wrapped = b64.substring(0, 8) + "\r\n" + b64.substring(8);
		assertArrayEquals(payload, Thumbnails.decode(wrapped));
	}

	@Test
	void returnsNullForNull()
	{
		assertNull(Thumbnails.decode(null));
	}

	@Test
	void returnsNullForEmptyOrBlank()
	{
		assertNull(Thumbnails.decode(""));
		assertNull(Thumbnails.decode("   "));
	}

	@Test
	void returnsNullForDataUriWithoutComma()
	{
		assertNull(Thumbnails.decode("data:image/jpeg;base64"));
	}
}
