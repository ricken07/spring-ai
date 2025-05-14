package org.springframework.ai.model.cohere.autoconfigure;

import org.springframework.ai.cohere.api.CohereApi;
import org.springframework.ai.cohere.chat.CohereChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for Cohere chat.
 *
 * @author Ricken Bazolo
 */
@ConfigurationProperties(CohereChatProperties.CONFIG_PREFIX)
public class CohereChatProperties extends CohereParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.mistralai.chat";

	public static final String DEFAULT_CHAT_MODEL = CohereApi.ChatModel.COMMAND_R7B.getValue();

	private static final Double DEFAULT_TEMPERATURE = 0.3;

	private static final Double DEFAULT_TOP_P = 1.0;

	private static final Boolean IS_ENABLED = false;

	@NestedConfigurationProperty
	private CohereChatOptions options = CohereChatOptions.builder().build();

	public CohereChatProperties() {
		super.setBaseUrl(CohereCommonProperties.DEFAULT_BASE_URL);
	}

	public CohereChatOptions getOptions() {
		return this.options;
	}

	public void setOptions(CohereChatOptions options) {
		this.options = options;
	}

}
