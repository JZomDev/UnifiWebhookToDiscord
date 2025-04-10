package com.webhookmiddleman;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.webhookmiddleman.models.Embed;
import com.webhookmiddleman.models.Field;
import com.webhookmiddleman.models.Footer;
import com.webhookmiddleman.models.Message;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebhookController
{

	private static final Logger logger = LogManager.getLogger(WebhookController.class);

	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	@PostMapping("/data")
	public ResponseEntity<String> receiveWebhook(
		@RequestBody Map<String, Object> payload,
		@RequestHeader Map<String, String> headers) throws JSONException
	{

		logger.info("Received webhook with payload: " + payload);
		logger.info("Received webhook with headers: " + headers);

		JSONObject data = new JSONObject(payload);
		if (data.has("alarm"))
		{
			JSONObject alarm = data.getJSONObject("alarm");
			JSONArray triggers = alarm.getJSONArray("triggers");
			Long timestamp = data.getLong("timestamp");
			String readable_timestamp = getUsableDate(timestamp);

			for (int i = 0; i < triggers.length(); i++)
			{
				String device = triggers.getJSONObject(i).getString("device");
				String key = triggers.getJSONObject(i).getString("key");
				String macAddress = getMacAdrress(device);

				String deviceName = Application.macToDeviceName.getOrDefault(macAddress.toUpperCase(), "Unknown Device");
				Embed embed = createDiscordEmbed(key, deviceName, readable_timestamp);

				postToDiscord(embed);
			}
		}

		return ResponseEntity.ok("Webhook received successfully");
	}

	private void postToDiscord(Embed embed)
	{
		Message message = new Message()
			.setUsername("Unifi Protect")
			.setAvatarUrl("https://pbs.twimg.com/profile_images/1610157462321254402/tMCv8T-y_400x400.png");

		sendWebhook(Application.DISCORD_WEBHOOK, message, embed);
	}

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

	private Embed createDiscordEmbed(String trigger, String device, String timestamp) throws JSONException
	{
		Embed embed = new Embed();
		// I am not a fan of the repeated author within an embed
//		Author author = new Author("Unifi Protect",  "https://pbs.twimg.com/profile_images/1610157462321254402/tMCv8T-y_400x400.png");
//		embed.setAuthor(author);
		embed.setTitle("Alert Triggered :camera_with_flash:");
		Footer footer = new Footer("Unifi Protect", "https://pbs.twimg.com/profile_images/1610157462321254402/tMCv8T-y_400x400.png");
		embed.setFooter(footer);
		embed.setColor(16711680);
		Field triggerField = new Field("Trigger", trigger, true);
		Field deviceField = new Field("Device", device, true);
		Field timestampeField = new Field("Timestamp", timestamp, false);
		embed.setFields(new Field[]{triggerField, deviceField, timestampeField});
		embed.setTimestamp(java.time.Instant.now().toString());
		return embed;
	}

	private String getUsableDate(Long timestamp)
	{
		return sdf.format(new Date(timestamp));
	}

	// coverts ABCDEFGHJ -> AB:DE:FG:HJ
	private String getMacAdrress(String device)
	{
		return device.replaceAll("(..)(?!$)", "$1:");
	}
}