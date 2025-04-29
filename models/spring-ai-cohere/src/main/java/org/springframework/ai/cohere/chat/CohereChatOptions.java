package org.springframework.ai.cohere.chat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.cohere.api.CohereChatApi;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;

import static org.springframework.ai.cohere.api.CohereChatApi.ChatCompletionRequest.ResponseFormat;

import java.util.*;

/**
 * Options for the Cohere Chat API.
 *
 * @author Ricken Bazolo
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CohereChatOptions implements ToolCallingChatOptions {

	/**
	 * ID of the model to use
	 */
	private @JsonProperty("model") String model;

	/**
	 * What sampling temperature to use, between 0.0 and 1.0. Higher values like 0.8 will
	 * make the output more random, while lower values like 0.2 will make it more focused
	 * and deterministic. We generally recommend altering this or top_p but not both.
	 */
	private @JsonProperty("temperature") Double temperature;

	/**
	 * Ensures that only the most likely tokens, with total probability mass of p, are
	 * considered for generation at each step. If both k and p are enabled, p acts after
	 * k. Defaults to 0.75. min value of 0.01, max value of 0.99.
	 */
	private @JsonProperty("p") Double p;

	/**
	 * The maximum number of tokens to generate in the chat completion. The total length
	 * of input tokens and generated tokens is limited by the model's context length.
	 */
	private @JsonProperty("max_tokens") Integer maxTokens;

	/**
	 * Min value of 0.0, max value of 1.0. Used to reduce repetitiveness of generated
	 * tokens. Similar to frequency_penalty, except that this penalty is applied equally
	 * to all tokens that have already appeared, regardless of their exact frequencies.
	 */
	private @JsonProperty("presence_penalty") Double presencePenalty;

	/**
	 * Nin value of 0.0, max value of 1.0. Used to reduce repetitiveness of generated
	 * tokens. Similar to frequency_penalty, except that this penalty is applied equally
	 * to all tokens that have already appeared, regardless of their exact frequencies.
	 */
	private @JsonProperty("frequency_penalty") Double frequencyPenalty;

	/**
	 * Ensures that only the top k most likely tokens are considered for generation at
	 * each step. When k is set to 0, k-sampling is disabled. Defaults to 0, min value of
	 * 0, max value of 500.
	 */
	private @JsonProperty("k") Double k;

	/**
	 * Options for streaming response. Included in the API only if streaming-mode
	 * completion is requested.
	 */
	private @JsonProperty("stream") Boolean stream;

	/**
	 * A list of tools the model may call. Currently, only functions are supported as a
	 * tool. Use this to provide a list of functions the model may generate JSON inputs
	 * for.
	 */
	private @JsonProperty("tools") List<CohereChatApi.FunctionTool> tools;

	/**
	 * An object specifying the format that the model must output. Setting to { "type":
	 * "json_object" } enables JSON mode, which guarantees the message the model generates
	 * is valid JSON.
	 */
	private @JsonProperty("response_format") ResponseFormat responseFormat;

	/**
	 * Used to select the safety instruction inserted into the prompt. Defaults to
	 * CONTEXTUAL. When OFF is specified, the safety instruction will be omitted.
	 */
	private @JsonProperty("safety_mode") CohereChatApi.SafetyMode safetyMode;

	/**
	 * A list of up to 5 strings that the model will use to stop generation. If the model
	 * generates a string that matches any of the strings in the list, it will stop
	 * generating tokens and return the generated text up to that point not including the
	 * stop sequence.
	 */
	private @JsonProperty("stop_sequences") List<String> stopSequences;

	/**
	 * If specified, the backend will make a best effort to sample tokens
	 * deterministically, such that repeated requests with the same seed and parameters
	 * should return the same result. However, determinism cannot be totally guaranteed.
	 */
	private @JsonProperty("seed") Integer seed;

	/**
	 * Defaults to false. When set to true, the log probabilities of the generated tokens
	 * will be included in the response.
	 */
	private @JsonProperty("logprobs") Boolean logprobs;

	/**
	 * Controls which (if any) function is called by the model. none means the model will
	 * not call a function and instead generates a message. auto means the model can pick
	 * between generating a message or calling a function. Specifying a particular
	 * function via {"type: "function", "function": {"name": "my_function"}} forces the
	 * model to call that function. none is the default when no functions are present.
	 * auto is the default if functions are present. Use the
	 * {@link org.springframework.ai.cohere.api.CohereChatApi.ToolChoiceBuilder} to create
	 * a tool choice object.
	 */
	private @JsonProperty("tool_choice") Object toolChoice;

	private @JsonProperty("strict_tools") Boolean strict_tools;

	/**
	 * Collection of {@link ToolCallback}s to be used for tool calling in the chat
	 * completion requests.
	 */
	@JsonIgnore
	private List<ToolCallback> toolCallbacks = new ArrayList<>();

	/**
	 * Collection of tool names to be resolved at runtime and used for tool calling in the
	 * chat completion requests.
	 */
	@JsonIgnore
	private Set<String> toolNames = new HashSet<>();

	/**
	 * Whether to enable the tool execution lifecycle internally in ChatModel.
	 */
	@JsonIgnore
	private Boolean internalToolExecutionEnabled;

	@JsonIgnore
	private Map<String, Object> toolContext = new HashMap<>();

	public static Builder builder() {
		return new Builder();
	}

	public static CohereChatOptions fromOptions(CohereChatOptions fromOptions) {
		return builder().build();
	}

	public static class Builder {

		private final CohereChatOptions options = new CohereChatOptions();

		public CohereChatOptions build() {
			return this.options;
		}

	}

}
