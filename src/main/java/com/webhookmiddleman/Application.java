package com.webhookmiddleman;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application
{

	private static final Logger logger = LogManager.getLogger(Application.class);

	private static final String MAC_ADDRESS_REGEX = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$";
	private static final Pattern MAC_ADDRESS_PATTERN = Pattern.compile(MAC_ADDRESS_REGEX);


	public static String DISCORD_WEBHOOK = "";
	public static String PORT = "8080";
	public static HashMap<String, String> macToDeviceName = new HashMap<>();

	static
	{
		Map<String, String> environmentVariables = System.getenv();

		for (String envName : environmentVariables.keySet())
		{
			if (envName.equals("webhook"))
			{
				DISCORD_WEBHOOK = environmentVariables.get(envName);
			}
			if (envName.equals("port"))
			{
				PORT = environmentVariables.get(envName);
			}

			if (MAC_ADDRESS_PATTERN.matcher(envName).matches())
			{
				macToDeviceName.put(envName, environmentVariables.get(envName));
			}
		}
	}

	// args for local development, not ran from dockerfile
	public static void main(String[] args) {
		if (args.length != 0)
		{
			DISCORD_WEBHOOK = args[0];
			PORT = args[1];
		}
		if (validateWebhook())
		{
			SpringApplication app = new SpringApplication(Application.class);

			// this should only run if in local development
			if (!PORT.equalsIgnoreCase("8080"))
			{
				app.setDefaultProperties(Collections
					.singletonMap("server.port", PORT));
			}
			try
			{
				app.run(args);
				logger.info("Started Unifi Webhook Discord Server");
			}
			catch (Exception e)
			{
				logger.error(e);
				e.printStackTrace();
			}
		}
	}

	private static boolean validateWebhook()
	{
		if (DISCORD_WEBHOOK.equalsIgnoreCase(""))
		{
			logger.error("Please supply a Discord webhook");
			return false;
		}
		else
		{
			boolean isValidUrl = DISCORD_WEBHOOK.matches("https://discord\\.com/api/webhooks/[0-9]+/[A-Za-z0-9_\\-]+");

			// If the provided URL is valid, update the channel's webhook URL.
			if (isValidUrl)
			{
				return true;
			}

			logger.error("Please supply a valid Discord webhook");
			return false;
		}
	}
}
