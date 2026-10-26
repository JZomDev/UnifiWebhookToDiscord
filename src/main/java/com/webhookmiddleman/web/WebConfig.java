package com.webhookmiddleman.web;

import com.webhookmiddleman.settings.SettingsStore;
import java.io.IOException;
import jakarta.servlet.ServletException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer
{
	private final SettingsStore store;

	public WebConfig(SettingsStore store)
	{
		this.store = store;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry)
	{
		registry.addInterceptor(new AuthInterceptor(store))
			.addPathPatterns("/**")
			.excludePathPatterns("/app.css", "/favicon.ico", "/error");
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry)
	{
		registry.addResourceHandler("/guide-assets/**").addResourceLocations("classpath:/guides/");
	}

	@Bean
	BCryptPasswordEncoder passwordEncoder()
	{
		return new BCryptPasswordEncoder();
	}

	/**
	 * Marks the request secure when a proxy says {@code X-Forwarded-Proto: https},
	 * so Tomcat sets the session cookie {@code Secure} only in that case.
	 */
	@Bean
	WebServerFactoryCustomizer<TomcatServletWebServerFactory> forwardedProtoCustomizer()
	{
		return factory -> factory.addEngineValves(new ForwardedProtoValve());
	}

	static class ForwardedProtoValve extends ValveBase
	{
		ForwardedProtoValve()
		{
			super(true);
		}

		@Override
		public void invoke(Request request, Response response) throws IOException, ServletException
		{
			boolean original = request.isSecure();
			if ("https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")))
			{
				request.setSecure(true);
			}
			try
			{
				getNext().invoke(request, response);
			}
			finally
			{
				request.setSecure(original);
			}
		}
	}
}
