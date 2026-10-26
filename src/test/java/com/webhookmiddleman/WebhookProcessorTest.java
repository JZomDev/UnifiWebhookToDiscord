package com.webhookmiddleman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.webhookmiddleman.models.Embed;
import com.webhookmiddleman.settings.SettingsStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebhookProcessorTest
{
	private static final String HOOK = "https://discord.com/api/webhooks/123/abc";

	@TempDir
	Path dir;

	SettingsStore store;
	List<Embed> sentEmbeds = new ArrayList<>();
	List<String> sentUrls = new ArrayList<>();
	WebhookProcessor processor;

	@BeforeEach
	void setUp()
	{
		store = new SettingsStore(dir.resolve("settings.json"));
		store.update(s ->
		{
			s.webhook = HOOK;
			s.cameras.put("AA:BB:CC:11:22:33", "Front Door");
		});
		processor = new WebhookProcessor(store, (url, message, embed, thumb) ->
		{
			sentUrls.add(url);
			sentEmbeds.add(embed);
		});
	}

	private Map<String, Object> payload(String device, String eventPath)
	{
		JSONObject alarm = new JSONObject();
		alarm.put("triggers", new JSONArray().put(new JSONObject().put("device", device).put("key", "motion")));
		if (eventPath != null)
		{
			alarm.put("eventPath", eventPath);
			alarm.put("eventLocalLink", "http://local" + eventPath);
		}
		return new JSONObject().put("alarm", alarm).put("timestamp", 1700000000000L).toMap();
	}

	private String field(Embed e, String name)
	{
		JSONArray fields = e.get().getJSONArray("fields");
		for (int i = 0; i < fields.length(); i++)
		{
			if (fields.getJSONObject(i).getString("name").equals(name))
			{
				return fields.getJSONObject(i).getString("value");
			}
		}
		return null;
	}

	@Test
	void usesCameraNameFromSettings()
	{
		assertEquals(200, processor.handle(payload("aabbcc112233", null)));
		assertEquals(1, sentEmbeds.size());
		assertEquals("Front Door", field(sentEmbeds.get(0), "Device"));
		assertEquals("Motion", field(sentEmbeds.get(0), "Trigger"));
		assertEquals(HOOK, sentUrls.get(0));
	}

	@Test
	void unknownMacGivesUnknownDevice()
	{
		processor.handle(payload("001122334455", null));
		assertEquals("Unknown Device", field(sentEmbeds.get(0), "Device"));
	}

	@Test
	void noWebhookConfiguredReturns200AndSendsNothing()
	{
		store.update(s -> s.webhook = "");
		assertEquals(200, processor.handle(payload("aabbcc112233", null)));
		assertTrue(sentEmbeds.isEmpty());
	}

	@Test
	void payloadWithoutAlarmReturns200()
	{
		assertEquals(200, processor.handle(new JSONObject().put("x", 1).toMap()));
		assertTrue(sentEmbeds.isEmpty());
	}

	@Test
	void unparseableAlarmReturns400()
	{
		assertEquals(400, processor.handle(new JSONObject().put("alarm", "nope").toMap()));
	}

	@Test
	void remoteLinkUsedOnlyWhenBothUuidsSet()
	{
		store.update(s -> s.uuid1 = "U1");
		processor.handle(payload("aabbcc112233", "/protect/events/event/1"));
		assertEquals("[Local IP Link](http://local/protect/events/event/1)", field(sentEmbeds.get(0), "Captured Event"));

		store.update(s -> s.uuid2 = "U2");
		processor.handle(payload("aabbcc112233", "/protect/events/event/1"));
		assertEquals("[Remote Link](https://unifi.ui.com/consoles/U1:U2/protect/events/event/1)",
			field(sentEmbeds.get(1), "Captured Event"));
	}
}
