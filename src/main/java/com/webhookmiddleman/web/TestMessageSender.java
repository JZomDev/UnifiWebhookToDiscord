package com.webhookmiddleman.web;

/** Sends the Settings page's test message to a Discord webhook. Replaceable in tests. */
public interface TestMessageSender
{
	String CONTENT = "Test message from Unifi Webhook to Discord";

	/** Sends {@link #CONTENT} to {@code webhookUrl} (already validated) and reports the outcome. */
	Result send(String webhookUrl);

	record Result(boolean ok, String message)
	{
	}
}
