package org.springframework.ai.cohere.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.model.ChatModelDescription;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Java Client library for Cohere Platform. Provides implementation for the
 * <a href="https://docs.cohere.com/reference/chat"> Chat
 * and <a href="https://docs.cohere.com/reference/chat-stream"> Chat Stream
 * <a href="https://docs.cohere.com/reference/embed">Embedding API</a>.
 * <p>
 * Implements <b>Synchronous</b> and <b>Streaming</b> chat completion and supports latest
 * <b>Function Calling</b> features.
 * </p>
 *
 * @author Ricken Bazolo
 */
public class CohereApi {

	public static final String PROVIDER_NAME = AiProvider.COHERE.value();

	private static final String DEFAULT_BASE_URL = "https://api.cohere.com";

	private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

	private final RestClient restClient;

	private final WebClient webClient;

	// TODO ADD Stream helper

	/**
	 * Create a new client api with DEFAULT_BASE_URL
	 * @param cohereApiKey Cohere api Key.
	 */
	public CohereApi(String cohereApiKey) {
		this(DEFAULT_BASE_URL, cohereApiKey);
	}

	/**
	 * Create a new client api.
	 * @param baseUrl api base URL.
	 * @param cohereApiKey Cohere api Key.
	 */
	public CohereApi(String baseUrl, String cohereApiKey) {
		this(baseUrl, cohereApiKey, RestClient.builder(),  WebClient.builder(), RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
	}

	/**
	 * Create a new client api.
	 * @param baseUrl api base URL.
	 * @param cohereApiKey Cohere api Key.
	 * @param restClientBuilder RestClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public CohereApi(String baseUrl, String cohereApiKey, RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder,
					 ResponseErrorHandler responseErrorHandler) {

		Consumer<HttpHeaders> jsonContentHeaders = headers -> {
			headers.setBearerAuth(cohereApiKey);
			headers.setContentType(MediaType.APPLICATION_JSON);
		};

		this.restClient = restClientBuilder.baseUrl(baseUrl)
				.defaultHeaders(jsonContentHeaders)
				.defaultStatusHandler(responseErrorHandler)
				.build();

		this.webClient = webClientBuilder.clone().baseUrl(baseUrl).defaultHeaders(jsonContentHeaders).build();
	}

	/**
	 * <a href="https://docs.cohere.com/docs/models">List of well-known Cohere chat
	 * models.</a>
	 *
	 * <p>
	 * Cohere provides Command family of models includes: Command A, Command R7B, Command
	 * R+, Command R, and Command.
	 */
	public enum ChatModel implements ChatModelDescription {

		COMMAND_A("command-a-03-2025"), COMMAND_R7B("command-r7b-12-2024"),
		COMMAND_R_PLUS_08_2024("command-r-plus-08-2024"), COMMAND_R_PLUS("command-r-plus"), COMMAND_R("command-r"),
		COMMAND_03_2024("command-r-03-2024");

		private final String value;

		ChatModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

		@Override
		public String getName() {
			return this.value;
		}

	}

	/**
	 * Usage statistics.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Usage(@JsonProperty("billedUnits") BilledUnits billedUnits, @JsonProperty("tokens") Tokens tokens) {
		/**
		 * Bille units
		 *
		 * @param inputTokens The number of billed input tokens.
		 * @param outputTokens The number of billed output tokens.
		 * @param searchUnits The number of billed search units.
		 * @param classifications The number of billed classifications units.
		 */
		public record BilledUnits(@JsonProperty("input_tokens") Integer inputTokens,
								  @JsonProperty("output_tokens") Integer outputTokens, @JsonProperty("search_units") Double searchUnits,
								  @JsonProperty("classifications") Double classifications) {
		}

		/**
		 * The Tokens
		 *
		 * @param inputTokens The number of tokens used as input to the model.
		 * @param outputTokens The number of tokens produced by the model.
		 */
		public record Tokens(@JsonProperty("input_tokens") Integer inputTokens,
							 @JsonProperty("output_tokens") Integer outputTokens) {
		}
	}

	/**
	 * Creates a model request for chat conversation.
	 *
	 * @param model The name of a compatible Cohere model or the ID of a fine-tuned model.
	 * @param messages The prompt(s) to generate completions for, encoded as a list of
	 * dict with role and rawContent. The first prompt role should be user or system.
	 * @param tools A list of tools the model may call. Currently, only functions are
	 * supported as a tool. Use this to provide a list of functions the model may generate
	 * JSON inputs for.
	 * @param documents A list of relevant documents that the model can cite to generate a
	 * more accurate reply. Each document is either a string or document object with
	 * rawContent and metadata.
	 * @param citationOptions Options for controlling citation generation.
	 * @param responseFormat An object specifying the format or schema that the model must
	 * output. Setting to { "type": "json_object" } enables JSON mode, which guarantees
	 * the message the model generates is valid JSON. Setting to { "type": "json_object" ,
	 * "json_schema": schema} allows you to ensure the model provides an answer in a very
	 * specific JSON format by supplying a clear JSON schema.
	 * @param safetyMode Safety modes are not yet configurable in combination with tools,
	 * tool_results and documents parameters.
	 * @param maxTokens The maximum number of tokens to generate in the completion. The
	 * token count of your prompt plus max_tokens cannot exceed the model's context
	 * length.
	 * @param stopSequences A list of tokens that the model should stop generating after.
	 * If set,
	 * @param temperature What sampling temperature to use, between 0.0 and 1.0. Higher
	 * values like 0.8 will make the output more random, while lower values like 0.2 will
	 * make it more focused and deterministic. We generally recommend altering this or p
	 * but not both.
	 * @param seed If specified, the backend will make a best effort to sample tokens
	 * deterministically, such that repeated requests with the same seed and parameters
	 * should return the same result. However, determinism cannot be totally guaranteed.
	 * @param frequencyPenalty Number between 0.0 and 1.0. Used to reduce repetitiveness
	 * of generated tokens. The higher the value, the stronger a penalty is applied to
	 * previously present tokens, proportional to how many times they have already
	 * appeared in the prompt or prior generation.
	 * @param presencePenalty min value of 0.0, max value of 1.0. Used to reduce
	 * repetitiveness of generated tokens. Similar to frequency_penalty, except that this
	 * penalty is applied equally to all tokens that have already appeared, regardless of
	 * their exact frequencies.
	 * @param stream When true, the response will be a SSE stream of events. The final
	 * event will contain the complete response, and will have an event_type of
	 * "stream-end".
	 * @param k Ensures that only the top k most likely tokens are considered for
	 * generation at each step. When k is set to 0, k-sampling is disabled. Defaults to 0,
	 * min value of 0, max value of 500.
	 * @param p Ensures that only the most likely tokens, with total probability mass of
	 * p, are considered for generation at each step. If both k and p are enabled, p acts
	 * after k. Defaults to 0.75. min value of 0.01, max value of 0.99.
	 * @param logprobs Defaults to false. When set to true, the log probabilities of the
	 * generated tokens will be included in the response.
	 * @param toolChoice Used to control whether or not the model will be forced to use a
	 * tool when answering. When REQUIRED is specified, the model will be forced to use at
	 * least one of the user-defined tools, and the tools parameter must be passed in the
	 * request. When NONE is specified, the model will be forced not to use one of the
	 * specified tools, and give a direct response. If tool_choice isn’t specified, then
	 * the model is free to choose whether to use the specified tools or not.
	 * @param strictTools When set to true, tool calls in the Assistant message will be
	 * forced to follow the tool definition strictly.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ChatCompletionRequest(@JsonProperty("model") String model,
										@JsonProperty("messages") List<ChatCompletionMessage> messages,
										@JsonProperty("tools") List<FunctionTool> tools, @JsonProperty("documents") List<Document> documents,
										@JsonProperty("citation_options") CitationOptions citationOptions,
										@JsonProperty("response_format") ResponseFormat responseFormat,
										@JsonProperty("safety_mode") SafetyMode safetyMode, @JsonProperty("max_tokens") Integer maxTokens,
										@JsonProperty("stop_sequences") List<String> stopSequences, @JsonProperty("temperature") Double temperature,
										@JsonProperty("seed") Integer seed, @JsonProperty("frequency_penalty") Double frequencyPenalty,
										@JsonProperty("stream") Boolean stream, @JsonProperty("k") Integer k, @JsonProperty("p") Double p,
										@JsonProperty("logprobs") Boolean logprobs, @JsonProperty("tool_choice") ToolChoice toolChoice,
										@JsonProperty("strict_tools") Boolean strictTools,
										@JsonProperty("presence_penalty") Double presencePenalty) {

		/**
		 * Shortcut constructor for a chat completion request with the given messages and
		 * model.
		 * @param messages The prompt(s) to generate completions for, encoded as a list of
		 * dict with role and rawContent. The first prompt role should be user or system.
		 * @param model ID or name of the model to use.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model) {
			this(model, messages, null, null, new CitationOptions(CitationMode.FAST), null, SafetyMode.CONTEXTUAL, null, null, 0.3,
					null, null, false, 0, 0.75, false, null, false, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages,
		 * model and temperature.
		 * @param messages The prompt(s) to generate completions for, encoded as a list of
		 * dict with role and rawContent. The first prompt role should be user or system.
		 * @param model ID or model of the model to use.
		 * @param temperature What sampling temperature to use, between 0.0 and 1.0.
		 * @param stream Whether to stream back partial progress. If set, tokens will be
		 * sent
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Double temperature,
									 boolean stream) {
			this(model, messages, null, null, new CitationOptions(CitationMode.FAST),  null, SafetyMode.CONTEXTUAL, null, null,
					temperature, null, null, stream, 0, 0.75, false, null, false, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages,
		 * model and temperature.
		 * @param messages The prompt(s) to generate completions for, encoded as a list of
		 * dict with role and rawContent. The first prompt role should be user or system.
		 * @param model ID of the model to use.
		 * @param temperature What sampling temperature to use, between 0.0 and 1.0.
		 *
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Double temperature) {
			this(model, messages, null, null, new CitationOptions(CitationMode.FAST), null, SafetyMode.CONTEXTUAL, null, null,
					temperature, null, null, false, 0, 0.75, false, null, false, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages,
		 * model, tools and tool choice. Streaming is set to false, temperature to 0.8 and
		 * all other parameters are null.
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use.
		 * @param tools A list of tools the model may call. Currently, only functions are
		 * supported as a tool.
		 * @param toolChoice Controls which (if any) function is called by the model.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, List<FunctionTool> tools,
									 ToolChoice toolChoice) {
			this(model, messages, tools, null, new CitationOptions(CitationMode.FAST), null, SafetyMode.CONTEXTUAL, null, null, 0.75,
					null, null, false, 0, 0.75, false, toolChoice, false, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages and
		 * stream.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, Boolean stream) {
			this(null, messages, null, null, new CitationOptions(CitationMode.FAST), null, SafetyMode.CONTEXTUAL, null, null, 0.75,
					null, null, stream, 0, 0.75, false, null, false, null);
		}

		/**
		 * An object specifying the format that the model must output.
		 *
		 * @param type Must be one of 'text' or 'json_object'.
		 * @param jsonSchema A specific JSON schema to match, if 'type' is 'json_object'.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record ResponseFormat(@JsonProperty("type") String type,
									 @JsonProperty("json_schema") Map<String, Object> jsonSchema) {
		}

		/**
		 * Specifies a tool the model should use
		 */
		public enum ToolChoice {

			REQUIRED, NONE

		}

	}

	/**
	 * Message comprising the conversation. A message from the assistant role can contain
	 * text and tool call information.
	 *
	 * @param role The role of the messages author. Could be one of the {@link Role} types
	 * "assistant".
	 * @param toolCalls The tool calls generated by the model, such as function calls.
	 * Applicable only for {@link Role#ASSISTANT} role and null otherwise.
	 * @param toolPlan A chain-of-thought style reflection and plan that the model
	 * generates when working with Tools.
	 * @param rawContent The contents of the message. Can be either a {@link MediaContent} or
	 * a {@link MessageContent}.
	 * @param citations Tool call that this message is responding to. Only applicable for
	 * the {@link ChatCompletionFinishReason#TOOL_CALL} role and null otherwise.
	 */
	public record ChatCompletionMessage(
			@JsonProperty("content") Object rawContent,
			@JsonProperty("role") Role role,
			//@JsonProperty("name") String name,
			@JsonProperty("tool_plan") String toolPlan,
			@JsonProperty("tool_calls") List<ToolCall> toolCalls,
			@JsonProperty("citations") List<ChatCompletionCitation> citations) {

		public ChatCompletionMessage(Object content, Role role) {
			this(content, role, null, null, null);
		}

		public ChatCompletionMessage(Object content, Role role, List<ToolCall> toolCalls) {
			this(content, role, null, toolCalls, null);
		}

		/**
		 * An array of rawContent parts with a defined type. Each MediaContent can be of
		 * either "text" or "image_url" type. Only one option allowed.
		 *
		 * @param type Content type, each can be of type text or image_url.
		 * @param text The text rawContent of the message.
		 * @param imageUrl The image rawContent of the message.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record MediaContent(@JsonProperty("type") String type, @JsonProperty("text") String text,
								   @JsonProperty("image_url") ImageUrl imageUrl) {

			/**
			 * Shortcut constructor for a text rawContent.
			 * @param text The text rawContent of the message.
			 */
			public MediaContent(String text) {
				this("text", text, null);
			}

			/**
			 * Shortcut constructor for an image rawContent.
			 * @param imageUrl The image rawContent of the message.
			 */
			public MediaContent(ImageUrl imageUrl) {
				this("image_url", null, imageUrl);
			}

			/**
			 * Shortcut constructor for an image rawContent.
			 *
			 * @param url Either a URL of the image or the base64 encoded image data. The
			 * base64 encoded image data must have a special prefix in the following
			 * format: "data:{mimetype};base64,{base64-encoded-image-data}".
			 */
			@JsonInclude(JsonInclude.Include.NON_NULL)
			public record ImageUrl(@JsonProperty("url") String url) {

			}
		}


		/**
		 * Message rawContent that can be either a text or a value.
		 *
		 * @param type The type of the message rawContent, such as "text" or "thinking".
		 * @param text The text rawContent of the message.
		 * @param value The value of the thinking, which can be any object.
		 */
		public record MessageContent(
				@JsonProperty("type") String type,
				@JsonProperty("text") String text,
				@JsonProperty("value") Object value) {}

		/**
		 * The role of the author of this message.
		 */
		public enum Role {

			/**
			 * User message.
			 */
			@JsonProperty("user")
			USER,
			/**
			 * Assistant message.
			 */
			@JsonProperty("assistant")
			ASSISTANT,
			/**
			 * System message.
			 */
			@JsonProperty("system")
			SYSTEM,
			/**
			 * Tool message.
			 */
			@JsonProperty("tool")
			TOOL

		}

		/**
		 * The relevant tool call.
		 *
		 * @param id The ID of the tool call. This ID must be referenced when you submit
		 * the tool outputs in using the Submit tool outputs to run endpoint.
		 * @param type The type of tool call the output is required for. For now, this is
		 * always function.
		 * @param function The function definition.
		 * @param index The index of the tool call in the list of tool calls.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record ToolCall(@JsonProperty("id") String id, @JsonProperty("type") String type,
							   @JsonProperty("function") ChatCompletionFunction function, @JsonProperty("index") Integer index) {
		}

		/**
		 * The function definition.
		 *
		 * @param name The name of the function.
		 * @param arguments The arguments that the model expects you to pass to the
		 * function.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record ChatCompletionFunction(@JsonProperty("name") String name,
											 @JsonProperty("arguments") String arguments) {
		}

		public record ChatCompletionCitation(
				/**
				 * Start index of the cited snippet in the original source text.
				 */
				@JsonProperty("start") Integer start,
				/**
				 * End index of the cited snippet in the original source text.
				 */
				@JsonProperty("end") Integer end,
				/**
				 * Text snippet that is being cited.
				 */
				@JsonProperty("text") String text, @JsonProperty("sources") List<Source> sources,
				@JsonProperty("type") Type type) {
			/**
			 * The type of citation which indicates what part of the response the citation
			 * is for.
			 */
			public enum Type {

				TEXT_CONTENT, PLAN

			}

			/**
			 * @param type Tool or A document source object containing the unique
			 * identifier of the document and the document itself.
			 * @param id The unique identifier of the document
			 * @param toolOutput map from strings to any Optional if type == tool
			 * @param document map from strings to any Optional if type == document
			 */
			public record Source(@JsonProperty("type") String type, @JsonProperty("id") String id,
								 @JsonProperty("tool_output") Map<String, Object> toolOutput,
								 @JsonProperty("document") Map<String, Object> document) {
			}
		}

		public record Provider(
				@JsonProperty("content") List<MessageContent> content,
				@JsonProperty("role") Role role,
				@JsonProperty("tool_plan") String toolPlan,
				@JsonProperty("tool_calls") List<ToolCall> toolCalls,
				@JsonProperty("citations") List<ChatCompletionCitation> citations
		) {}
	}

	/**
	 * Used to select the safety instruction inserted into the prompt. Defaults to
	 * CONTEXTUAL. When OFF is specified, the safety instruction will be omitted. Safety
	 * modes are not yet configurable in combination with tools, tool_results and
	 * documents parameters. Note: This parameter is only compatible newer Cohere models,
	 * starting with Command R 08-2024 and Command R+ 08-2024. Note: command-r7b-12-2024
	 * and newer models only support "CONTEXTUAL" and "STRICT" modes.
	 */
	public enum SafetyMode {

		CONTEXTUAL, STRICT, OFF

	}

	/**
	 * Options for controlling citation generation. Defaults to "accurate". Dictates the
	 * approach taken to generating citations as part of the RAG flow by allowing the user
	 * to specify whether they want "accurate" results, "fast" results or no results.
	 * Note: command-r7b-12-2024 and command-a-03-2025 only support "fast" and "off"
	 * modes. The default is "fast".
	 */
	public record CitationOptions(@JsonProperty("mode") CitationMode mode) {}

	/**
	 * Options for controlling citation generation. Defaults to "accurate". Dictates the
	 * approach taken to generating citations as part of the RAG flow by allowing the user
	 * to specify whether they want "accurate" results, "fast" results or no results.
	 * Note: command-r7b-12-2024 and command-a-03-2025 only support "fast" and "off"
	 * modes. The default is "fast".
	 */
	public enum CitationMode {

		FAST, ACCURATE, OFF

	}

	/**
	 * relevant documents that the model can cite to generate a more accurate reply. Each
	 * document is either a string or document object with rawContent and metadata.
	 *
	 * @param id An optional Unique identifier for this document which will be referenced
	 * in citations. If not provided an ID will be automatically generated.
	 * @param data A relevant document that the model can cite to generate a more accurate
	 * reply. Each document is a string-any dictionary.
	 */
	public record Document(@JsonProperty("id") String id, @JsonProperty("data") String data) {
	}

	/**
	 * Represents a tool the model may call. Currently, only functions are supported as a
	 * tool.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class FunctionTool {

		// The type of the tool. Currently, only 'function' is supported.
		@JsonProperty("type")
		Type type = Type.FUNCTION;

		// The function definition.
		@JsonProperty("function")
		Function function;

		public FunctionTool() {

		}

		/**
		 * Create a tool of type 'function' and the given function definition.
		 * @param function function definition.
		 */
		public FunctionTool(Function function) {
			this(Type.FUNCTION, function);
		}

		public FunctionTool(Type type, Function function) {
			this.type = type;
			this.function = function;
		}

		public Type getType() {
			return this.type;
		}

		public Function getFunction() {
			return this.function;
		}

		public void setType(Type type) {
			this.type = type;
		}

		public void setFunction(Function function) {
			this.function = function;
		}

		/**
		 * Create a tool of type 'function' and the given function definition.
		 */
		public enum Type {

			/**
			 * Function tool type.
			 */
			@JsonProperty("function")
			FUNCTION

		}

		/**
		 * Function definition.
		 */
		public static class Function {

			@JsonProperty("description")
			private String description;

			@JsonProperty("name")
			private String name;

			@JsonProperty("parameters")
			private Map<String, Object> parameters;

			@JsonIgnore
			private String jsonSchema;

			private Function() {

			}

			/**
			 * Create tool function definition.
			 * @param description A description of what the function does, used by the
			 * model to choose when and how to call the function.
			 * @param name The name of the function to be called. Must be a-z, A-Z, 0-9,
			 * or contain underscores and dashes, with a maximum length of 64.
			 * @param parameters The parameters the functions accepts, described as a JSON
			 * Schema object. To describe a function that accepts no parameters, provide
			 * the value {"type": "object", "properties": {}}.
			 */
			public Function(String description, String name, Map<String, Object> parameters) {
				this.description = description;
				this.name = name;
				this.parameters = parameters;
			}

			/**
			 * Create tool function definition.
			 * @param description tool function description.
			 * @param name tool function name.
			 * @param jsonSchema tool function schema as json.
			 */
			public Function(String description, String name, String jsonSchema) {
				this(description, name, ModelOptionsUtils.jsonToMap(jsonSchema));
			}

			public String getDescription() {
				return this.description;
			}

			public String getName() {
				return this.name;
			}

			public Map<String, Object> getParameters() {
				return this.parameters;
			}

			public void setDescription(String description) {
				this.description = description;
			}

			public void setName(String name) {
				this.name = name;
			}

			public void setParameters(Map<String, Object> parameters) {
				this.parameters = parameters;
			}

			public String getJsonSchema() {
				return this.jsonSchema;
			}

			public void setJsonSchema(String jsonSchema) {
				this.jsonSchema = jsonSchema;
				if (jsonSchema != null) {
					this.parameters = ModelOptionsUtils.jsonToMap(jsonSchema);
				}
			}

		}

	}

	/**
	 * Represents a chat completion response returned by model, based on the provided
	 * input.
	 *
	 * @param id A unique identifier for the chat completion.
	 * @param finishReason The reason the model stopped generating tokens.
	 * @param message A chat completion message generated by streamed model responses.
	 * @param logprobs Log probability information for the choice.
	 * @param usage Usage statistics for the completion request.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ChatCompletion(@JsonProperty("id") String id,
								 @JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,
								 @JsonProperty("message") ChatCompletionMessage.Provider message,
								 @JsonProperty("logprobs") LogProbs logprobs,
								 @JsonProperty("usage") Usage usage) { }

	/**
	 * The reason the model stopped generating tokens.
	 */
	public enum ChatCompletionFinishReason {

		/**
		 * The model finished sending a complete message.
		 */
		COMPLETE,

		/**
		 * One of the provided stop_sequence entries was reached in the model’s
		 * generation.
		 */
		STOP_SEQUENCE,

		/**
		 * The number of generated tokens exceeded the model’s context length or the value
		 * specified via the max_tokens parameter.
		 */
		MAX_TOKENS,

		/**
		 * The model generated a Tool Call and is expecting a Tool Message in return
		 */
		TOOL_CALL,

		/**
		 * The generation failed due to an internal error
		 */
		ERROR

	}

	/**
	 * Log probability information
	 *
	 * @param tokenIds The token ids of each token used to construct the text chunk.
	 * @param text The text chunk for which the log probabilities was calculated.
	 * @param logprobs The log probability of each token used to construct the text chunk.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record LogProbs(@JsonProperty("token_ids") List<Integer> tokenIds, @JsonProperty("text") String text,
						   @JsonProperty("logprobs") List<Double> logprobs) {

	}

	/**
	 * Helper factory that creates a tool_choice of type 'REQUIRED', 'NONE' or selected
	 * function by name.
	 */
	public static class ToolChoiceBuilder {

		public static final String NONE = "NONE";

		public static final String REQUIRED = "REQUIRED";

		/**
		 * Specifying a particular function forces the model to call that function.
		 */
		public static Object FUNCTION(String functionName) {
			return Map.of("type", "function", "function", Map.of("name", functionName));
		}

	}

	/**
	 * Creates a model response for the given chat conversation.
	 * @param chatRequest The chat completion request.
	 * @return Entity response with {@link ChatCompletion} as a body and HTTP status code
	 * and headers.
	 */
	public ResponseEntity<ChatCompletion> chatCompletionEntity(ChatCompletionRequest chatRequest) {

		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(!chatRequest.stream(), "Request must set the stream property to false.");

		return this.restClient.post()
				.uri("/v2/chat/")
				.body(chatRequest)
				.retrieve()
				.toEntity(ChatCompletion.class);
	}

	/**
	 * Creates a streaming chat response for the given chat conversation.
	 * @param chatRequest The chat completion request. Must have the stream property set
	 * to true.
	 * @return Returns a {@link Flux} stream from chat completion chunks.
	 */
	public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest chatRequest) {

		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(chatRequest.stream(), "Request must set the stream property to true.");

		AtomicBoolean isInsideTool = new AtomicBoolean(false);

		return this.webClient.post()
				.uri("/v2/chat/")
				.body(Mono.just(chatRequest), ChatCompletionRequest.class)
				.retrieve()
				.bodyToFlux(String.class)
				.takeUntil(SSE_DONE_PREDICATE)
				.filter(SSE_DONE_PREDICATE.negate())
				.map(content -> ModelOptionsUtils.jsonToObject(content, ChatCompletionChunk.class))
				.map(chunk -> {
					if (this.chunkMerger.isStreamingToolFunctionCall(chunk)) {
						isInsideTool.set(true);
					}
					return chunk;
				})
				.windowUntil(chunk -> {
					if (isInsideTool.get() && this.chunkMerger.isStreamingToolFunctionCallFinish(chunk)) {
						isInsideTool.set(false);
						return true;
					}
					return !isInsideTool.get();
				})
				.concatMapIterable(window -> {
					Mono<ChatCompletionChunk> mono1 = window.reduce(
							new ChatCompletionChunk(null, null, null, null, null, null),
							(previous, current) -> this.chunkMerger.merge(previous, current));
					return List.of(mono1);
				})
				.flatMap(mono -> mono);
	}

	/**
	 * Represents a streamed chunk of a chat completion response returned by model, based
	 * on the provided input.
	 *
	 * @param id A unique identifier for the chat completion. Each chunk has the same ID.
	 * @param object The object type, which is always 'chat.completion.chunk'.
	 * @param created The Unix timestamp (in seconds) of when the chat completion was
	 * created. Each chunk has the same timestamp.
	 * @param model The model used for the chat completion.
	 * @param choices A list of chat completion choices. Can be more than one if n is
	 * greater than 1.
	 * @param usage usage metrics for the chat completion.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ChatCompletionChunk(
			// @formatter:off
			@JsonProperty("id") String id,
			@JsonProperty("object") String object,
			@JsonProperty("created") Long created,
			@JsonProperty("model") String model,
			@JsonProperty("choices") List<ChunkChoice> choices,
			@JsonProperty("usage") Usage usage) {
		// @formatter:on

		/**
		 * Chat completion choice.
		 *
		 * @param index The index of the choice in the list of choices.
		 * @param delta A chat completion delta generated by streamed model responses.
		 * @param finishReason The reason the model stopped generating tokens.
		 * @param logprobs Log probability information for the choice.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record ChunkChoice(
				// @formatter:off
				@JsonProperty("index") Integer index,
				@JsonProperty("delta") ChatCompletionMessage delta,
				@JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,
				@JsonProperty("logprobs") LogProbs logprobs) {
			// @formatter:on
		}

	}


}
