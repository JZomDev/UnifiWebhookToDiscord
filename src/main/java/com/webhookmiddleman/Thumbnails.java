package com.webhookmiddleman;

import java.util.Base64;

/**
 * Helpers for the optional camera thumbnail that UniFi Protect includes in the
 * alarm payload when "Use Thumbnails" is enabled. The value arrives as a
 * base64 string in {@code alarm.thumbnail}, optionally prefixed with a
 * {@code data:image/...;base64,} data URI header.
 */
public final class Thumbnails
{
	public static final String FILENAME = "thumb.jpg";

	private Thumbnails()
	{
	}

	/**
	 * Decodes a UniFi thumbnail string into raw image bytes.
	 *
	 * @param raw the base64 value from {@code alarm.thumbnail}, with or without
	 *            a {@code data:} URI prefix; may be null or empty.
	 * @return the decoded bytes, or null if the input is missing or not valid base64.
	 */
	public static byte[] decode(String raw)
	{
		if (raw == null)
		{
			return null;
		}

		String b64 = raw.trim();
		if (b64.isEmpty())
		{
			return null;
		}

		if (b64.startsWith("data:"))
		{
			int comma = b64.indexOf(',');
			if (comma < 0)
			{
				return null;
			}
			b64 = b64.substring(comma + 1).trim();
		}

		// MIME decoder tolerates line breaks/whitespace that a data URI or
		// pretty-printed payload may contain; fall back to the strict decoder.
		try
		{
			return Base64.getMimeDecoder().decode(b64);
		}
		catch (IllegalArgumentException mime)
		{
			try
			{
				return Base64.getDecoder().decode(b64);
			}
			catch (IllegalArgumentException strict)
			{
				return null;
			}
		}
	}
}
