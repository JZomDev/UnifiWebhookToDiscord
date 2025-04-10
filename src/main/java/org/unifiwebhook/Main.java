package org.unifiwebhook;

import com.google.gson.Gson;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class Main
{

	private static final Logger logger = LogManager.getLogger(Main.class);

	private static final String MAC_ADDRESS_REGEX = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$";
	private static final Pattern MAC_ADDRESS_PATTERN = Pattern.compile(MAC_ADDRESS_REGEX);


	public static String DIR = "";
	public static String DISCORD_WEBHOOK = "";
	public static String PORT = "";
	public static HashMap<String, String> macToDeviceName = new HashMap<>();

	static
	{
		Map<String, String> environmentVariables = System.getenv();

		for (String envName : environmentVariables.keySet())
		{
			if (envName.equals("logs"))
			{
				DIR = environmentVariables.get(envName) + File.separator;
			}
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

	public static Gson gson;

	public static void main(String[] args)
	{
		String s = setUp();
		if (s.equalsIgnoreCase(""))
		{
			SpringApplication app = new SpringApplication(Main.class);
			app.setDefaultProperties(Collections
				.singletonMap("server.port", PORT));
			app.run(args);

			logger.info("Started Unifi Webhook Discord Server");
		}
		else
		{
			logger.error(s);
		}
	}

	private static String setUp()
	{
		gson = new Gson();
		if (DIR.equalsIgnoreCase(""))
		{
			return "Please configure logs folder";
		}
		if (DISCORD_WEBHOOK.equalsIgnoreCase(""))
		{
			return "Please Discord webhook";
		}
		if (PORT.equalsIgnoreCase(""))
		{
			return "Please configure a port";
		}

		return "";
	}

	@Bean(name = "defaultRestTemplate")
	public RestTemplate getRestTemplate()
	{
		RestTemplate restTemplate = new RestTemplate();
		return restTemplate;
	}
}