package com.robspecs.live.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class RedisConfig {

	private static final Logger logger = LoggerFactory.getLogger(RedisConfig.class);

	@Value("${spring.data.redis.host:localhost}") // Default to localhost if not specified
	private String redisHost;

	@Value("${spring.data.redis.port:6379}") // Default to 6379 if not specified
	private int redisPort;

	@Value("${spring.data.redis.password:}") // Default to empty string (no password) if not specified
	private String redisPassword;

	@Bean
	public RedisConnectionFactory redisConnectionFactory() {
		logger.info("Configuring RedisConnectionFactory with host: {}, port: {}, password provided: {}", redisHost,
				redisPort, !redisPassword.isEmpty()); // Log configuration details

		RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
		redisStandaloneConfiguration.setHostName(redisHost);
		redisStandaloneConfiguration.setPort(redisPort);

		if (!redisPassword.isEmpty()) {
			redisStandaloneConfiguration.setPassword(redisPassword);
			logger.debug("Redis password set for connection factory.");
		} else {
			logger.debug("No Redis password configured.");
		}

		LettuceConnectionFactory lettuceConnectionFactory = new LettuceConnectionFactory(redisStandaloneConfiguration);
		logger.debug("LettuceConnectionFactory bean created for Redis at {}:{}", redisHost, redisPort);
		lettuceConnectionFactory.setValidateConnection(true);
		return lettuceConnectionFactory;
	}

	@Bean
	public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
		RedisTemplate<String, String> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);
		template.setKeySerializer(new StringRedisSerializer());
		template.setValueSerializer(new StringRedisSerializer());
		template.afterPropertiesSet(); // Call afterPropertiesSet for proper initialization
		logger.debug("RedisTemplate<String, String> bean created");
		return template;
	}

	// NEW BEAN: RedisTemplate for raw binary data (byte[])
	@Bean(name = "redisRawDataTemplate") // Give it a distinct name
	public RedisTemplate<String, byte[]> redisRawDataTemplate(RedisConnectionFactory connectionFactory) {
		RedisTemplate<String, byte[]> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);
		template.setKeySerializer(RedisSerializer.string());
		template.setValueSerializer(RedisSerializer.byteArray()); // REPLACEMENT
		template.afterPropertiesSet(); // Ensure serializers are set up before use
		logger.debug("RedisTemplate<String, byte[]> 'redisRawDataTemplate' bean created with byteArray serializer.");
		return template;
	}

//	@Bean
//	public RedisMessageListenerContainer redisContainer(RedisConnectionFactory cf,
//			MessageListenerAdapter listenerAdapter, @Qualifier("redisMessageExecutor") TaskExecutor redisMessageExecutor) { // Add @Qualifier
//		logger.info("Configuring RedisMessageListenerContainer.");
//		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
//		container.setConnectionFactory(cf);
//		logger.debug("RedisMessageListenerContainer set connection factory.");
//
//		// Add the listener adapter to listen on the "inbox.*" pattern topic
//		container.addMessageListener(listenerAdapter, new PatternTopic("inbox.*")); // This is for RedisSubscriberImpl, keep as is
//		container.setTaskExecutor(redisMessageExecutor);
//		logger.info("RedisMessageListenerContainer added listener for topic 'inbox.*'.");
//
//		logger.debug("RedisMessageListenerContainer bean created.");
//		return container;
//	}

	@Bean
	public ObjectMapper objectMapper() {
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());
		objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		logger.info("ObjectMapper bean created and configured for JSON serialization.");
		return objectMapper;
	}

	@Bean(name = "redisMessageListenerContainer") // Ensure correct name for injection by type
	public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory cf,
			@Qualifier("redisMessageExecutor") TaskExecutor redisMessageExecutor) {

		logger.info("Configuring RedisMessageListenerContainer.");
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(cf);
		container.setTaskExecutor(redisMessageExecutor);

		logger.debug("RedisMessageListenerContainer bean created.");
		return container;
	}

	@Bean(name = "redisMessageExecutor") // Explicitly named for clarity
	public TaskExecutor redisMessageExecutor() {
		logger.info("Configuring dedicated TaskExecutor 'redisMessageExecutor' for Redis message listeners.");
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(10); // Increased from 5
		executor.setMaxPoolSize(20); // Increased from 10
		executor.setQueueCapacity(100); // Increased from 25
		executor.setThreadNamePrefix("redis-listener-"); // Clear prefix for debugging
		executor.initialize();
		logger.debug(
				"TaskExecutor 'redisMessageExecutor' initialized with corePoolSize={}, maxPoolSize={}, queueCapacity={}",
				executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
		return executor;
	}

	@Bean(name = "redisJsonTemplate") // Give it a distinct name to avoid ambiguity
	public RedisTemplate<String, Object> redisJsonTemplate(RedisConnectionFactory connectionFactory) {
		RedisTemplate<String, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);
		template.setKeySerializer(new StringRedisSerializer()); // Keys are still strings
		template.setHashKeySerializer(new StringRedisSerializer()); // If you use hash operations

		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule()); // Register the JavaTimeModule

		GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
		template.setValueSerializer(jsonSerializer); // Use the configured JSON serializer
		template.setHashValueSerializer(jsonSerializer); // If you use hash operations

		template.afterPropertiesSet(); // Ensure serializers are set up before use
		logger.debug("RedisTemplate<String, Object> 'redisJsonTemplate' bean created with JSON serializer.");
		return template;
	}
}