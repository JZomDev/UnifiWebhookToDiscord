package org.unifiwebhook;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.models.Embed;
import org.models.Message;
import org.springframework.stereotype.Service;

@Service
public class WebhookService
{

	private static final Logger logger = LogManager.getLogger(WebhookService.class);

	public void sendWebhook(String url, Message message, Embed embed)
	{
		new WebhookManager()
			.setMessage(message)
			.setChannelUrl(url)
			.setEmbeds(new Embed[]{embed})
			.setListener(new WebhookClient.Callback()
			{
				@Override
				public void onSuccess(String response)
				{
					logger.info("Message sent successfully");
				}

				@Override
				public void onFailure(int statusCode, String errorMessage)
				{
					logger.info("Code: " + statusCode + " error: " + errorMessage);
				}
			})
			.exec();
	}
}