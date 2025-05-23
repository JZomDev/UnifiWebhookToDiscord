package com.webhookmiddleman;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
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
	static Map<String, String> TRIGGER_MAP = new HashMap<>();

	static
	{
		TRIGGER_MAP.put("motion", "Motion");
		TRIGGER_MAP.put("line_crossed", "LineCrossed");
		TRIGGER_MAP.put("person", "Person");
		TRIGGER_MAP.put("vehicle", "Vehicle");
		TRIGGER_MAP.put("animal", "Animal");
		TRIGGER_MAP.put("package", "Package");
		TRIGGER_MAP.put("audio_alarm_speak", "AudioAlarmSpeak");
		TRIGGER_MAP.put("audio_alarm_baby_cry", "AudioAlarmBabyCry");
		TRIGGER_MAP.put("audio_alarm_bark", "AudioAlarmBark");
		TRIGGER_MAP.put("audio_alarm_co", "AudioAlarmCo");
		TRIGGER_MAP.put("audio_alarm_smoke", "AudioAlarmSmoke");
		TRIGGER_MAP.put("audio_alarm_car_horn", "AudioAlarmCarHorn");
		TRIGGER_MAP.put("audio_alarm_glass_break", "AudioAlarmGlassBreak");
		TRIGGER_MAP.put("audio_alarm_siren", "AudioAlarmSiren");
		TRIGGER_MAP.put("audio_alarm_burglar", "AudioAlarmBurglar");
		TRIGGER_MAP.put("face_known", "FaceKnown");
		TRIGGER_MAP.put("face_unknown", "FaceUnknown");
		TRIGGER_MAP.put("face_of_interest", "FaceOfInterest");
		TRIGGER_MAP.put("license_plate_known", "LicensePlateKnown");
		TRIGGER_MAP.put("license_plate_unknown", "LicensePlateUnknown");
		TRIGGER_MAP.put("license_plate_of_interest", "LicensePlateOfInterest");
		TRIGGER_MAP.put("ring", "Ring");
		TRIGGER_MAP.put("sensor_battery_low", "SensorBatteryLow");
		TRIGGER_MAP.put("sensor_door_opened", "SensorDoorOpened");
		TRIGGER_MAP.put("sensor_door_closed", "SensorDoorClosed");
		TRIGGER_MAP.put("sensor_extreme_value", "SensorExtremeValue");
		TRIGGER_MAP.put("sensor_water_leak", "SensorWaterLeak");
		TRIGGER_MAP.put("sensor_alarm", "SensorAlarm");
		TRIGGER_MAP.put("sensor_motion", "SensorMotion");
		TRIGGER_MAP.put("device_issue", "DeviceIssue");
		TRIGGER_MAP.put("application_issue", "ApplicationIssue");
		TRIGGER_MAP.put("device_adoption_state_changed", "DeviceAdoptionStateChanged");
		TRIGGER_MAP.put("camera_utilization_limit", "CameraUtilizationLimit");
		TRIGGER_MAP.put("device_discovery", "DeviceDiscovery");
		TRIGGER_MAP.put("device_update_status_change", "DeviceUpdateStatusChange");
		TRIGGER_MAP.put("admin_access", "AdminAccess");
		TRIGGER_MAP.put("admin_settings_change", "AdminSettingsChange");
		TRIGGER_MAP.put("admin_recording_clips_manipulations", "AdminRecordingClipsManipulations");
		TRIGGER_MAP.put("admin_geolocation", "AdminGeolocation");
		TRIGGER_MAP.put("smart_loiter_detection", "Loitering");
		TRIGGER_MAP.put("fingerprint_registered", "FingerprintRegistered");
		TRIGGER_MAP.put("fingerprint_unknown", "FingerprintUnknown");
		TRIGGER_MAP.put("nfc_registered", "NfcRegistered");
		TRIGGER_MAP.put("nfc_unknown", "NfcUnknown");
		TRIGGER_MAP.put("classification_emergency", "ClassificationEmergency");
		TRIGGER_MAP.put("classification_suspicious_objects", "ClassificationSuspiciousObjects");
		TRIGGER_MAP.put("classification_face_covering", "ClassificationFaceCovering");
		TRIGGER_MAP.put("classification_dangerous_animals", "ClassificationDangerousAnimals");
		TRIGGER_MAP.put("classification_weapon", "ClassificationWeapon");
		TRIGGER_MAP.put("ai_nls", "AiKeyNls");
		TRIGGER_MAP.put("unknown", "Unknown");
	}

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
			String UUID_1 = Application.UUID_1;
			String UUID_2 = Application.UUID_2;
			String eventLocalLink = alarm.has("eventLocalLink") ? alarm.getString("eventLocalLink") : null;
			String eventPath = alarm.has("eventPath") ? alarm.getString("eventPath") : null;
			boolean useRemote = !UUID_1.isEmpty() && !UUID_2.isEmpty(); // leave UUIDs blank to use local instead
			if (eventPath != null && useRemote)
			{
				// replace local event with unifi protect full name
				// eventPath=/protect/events/event/id_of_event_here
				eventPath = String.format("https://unifi.ui.com/consoles/%s:%s" + eventPath, UUID_1, UUID_2);
			}
			Long timestamp = data.getLong("timestamp");
			String readable_timestamp = getUsableDate(timestamp);

			for (int i = 0; i < triggers.length(); i++)
			{
				String device = triggers.getJSONObject(i).getString("device");
				String trigger = TRIGGER_MAP.getOrDefault(triggers.getJSONObject(i).getString("key"), "Unknown");
				String macAddress = getMacAdrress(device);

				String deviceName = Application.macToDeviceName.getOrDefault(macAddress.toUpperCase(), "Unknown Device");
				Embed embed = createDiscordEmbed(trigger, deviceName, readable_timestamp, (!useRemote ? eventLocalLink : eventPath), useRemote);

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

	private Embed createDiscordEmbed(String trigger, String deviceName, String timestamp, String eventLink, boolean remote) throws JSONException
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
		Field deviceField = new Field("Device", deviceName, true);
		Field timestampeField = new Field("Timestamp", timestamp, false);

		embed.addField(triggerField);
		embed.addField(deviceField);
		if (eventLink != null)
		{
			String linkText = remote ?  "Remote Link" : "Local IP Link";
			Field eventLocalLinkField = new Field("Captured Event", String.format("[%s](%s)", linkText, eventLink), true);
			embed.addField(eventLocalLinkField);
		}
		embed.addField(timestampeField);
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