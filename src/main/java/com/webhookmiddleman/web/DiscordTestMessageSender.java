package com.webhookmiddleman.web;

import com.webhookmiddleman.WebhookClient;
import com.webhookmiddleman.WebhookManager;
import com.webhookmiddleman.models.Message;
import org.springframework.stereotype.Component;

/** Sends the test message through the normal {@link WebhookManager}/{@link WebhookClient} path. */
@Component
public class DiscordTestMessageSender implements TestMessageSender
{
	/** Longest error text shown on the page. */
	static final int MAX_MESSAGE = 300;

	@Override
	public Result send(String webhookUrl)
	{
		Result[] result = new Result[1];
		try
		{
			new WebhookManager()
				.setChannelUrl(webhookUrl)
				.setMessage(new Message().setUsername("Unifi Protect").setContent(CONTENT))
				.setListener(new WebhookClient.Callback()
				{
					@Override
					public void onSuccess(String response)
					{
						result[0] = new Result(true, "Test message sent. Check your Discord channel.");
					}

					@Override
					public void onFailure(int statusCode, String errorMessage)
					{
						result[0] = new Result(false, describe(statusCode, errorMessage));
					}
				})
				.exec();
		}
		catch (RuntimeException e)
		{
			return new Result(false, describe(-1, e.getMessage()));
		}
		return result[0] != null ? result[0] : new Result(false, "Sending failed: no response.");
	}

	static String describe(int statusCode, String errorMessage)
	{
		String text = errorMessage == null || errorMessage.isBlank() ? "no details" : errorMessage.strip();
		if (text.length() > MAX_MESSAGE)
		{
			text = text.substring(0, MAX_MESSAGE) + "…";
		}
		return statusCode > 0
			? "Sending failed: Discord returned HTTP " + statusCode + ": " + text
			: "Sending failed: " + text;
	}
}
