package com.webhookmiddleman;

import com.webhookmiddleman.settings.EnvImporter;
import com.webhookmiddleman.settings.SettingsStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application
{
	private static final Logger logger = LogManager.getLogger(Application.class);

	public static void main(String[] args)
	{
		SpringApplication app = new SpringApplication(Application.class);
		app.setDefaultProperties(Map.of("server.port", EnvImporter.webUiPort(System.getenv())));
		app.run(args);
	}

	/** Settings file {@code settings.json} in {@code config.dir} (env {@code config_dir}, default {@code /config}). */
	@Bean
	SettingsStore settingsStore(@Value("${config.dir:/config}") String configDir)
	{
		return new SettingsStore(Path.of(configDir, "settings.json"));
	}

	/** Runs the one-time env import; the result lists the legacy env vars currently set, for the UI banner. */
	@Bean
	List<String> envVars(SettingsStore store)
	{
		return List.copyOf(EnvImporter.importFrom(System.getenv(), store));
	}

	@Bean
	WebhookProcessor webhookProcessor(SettingsStore store)
	{
		return new WebhookProcessor(store, WebhookProcessor.discordSender());
	}

	@Bean(destroyMethod = "stop")
	WebhookListener webhookListener(WebhookProcessor processor)
	{
		return new WebhookListener(processor);
	}

	/** Starts the webhook listener once the app is up (after the env import, which {@code envVars} forces). */
	@Bean
	ApplicationListener<ApplicationReadyEvent> webhookListenerStarter(WebhookListener listener, SettingsStore store,
		@Qualifier("envVars") List<String> envVars, @Value("${listener.autostart:true}") boolean autostart)
	{
		return event ->
		{
			if (!autostart)
			{
				return;
			}
			int port = store.snapshot().webhookPort;
			try
			{
				listener.start(port);
			}
			catch (IOException e)
			{
				logger.error("Could not start webhook listener on port {}: {}. Change the port in Settings.", port, e.toString());
			}
		};
	}
}
