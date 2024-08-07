/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.stabilityai;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.stabilityai.api.StabilityAiApi;
import org.springframework.ai.stabilityai.api.StabilityAiImageOptions;
import org.springframework.util.Assert;

/**
 * StabilityAiImageModel is a class that implements the ImageModel interface. It provides
 * a client for calling the StabilityAI image generation API.
 */
public class StabilityAiImageModel implements ImageModel {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private StabilityAiImageOptions options;

	private final StabilityAiApi stabilityAiApi;

	public StabilityAiImageModel(StabilityAiApi stabilityAiApi) {
		this(stabilityAiApi, StabilityAiImageOptions.builder().build());
	}

	public StabilityAiImageModel(StabilityAiApi stabilityAiApi, StabilityAiImageOptions options) {
		Assert.notNull(stabilityAiApi, "StabilityAiApi must not be null");
		Assert.notNull(options, "StabilityAiImageOptions must not be null");
		this.stabilityAiApi = stabilityAiApi;
		this.options = options;
	}

	public StabilityAiImageOptions getOptions() {
		return this.options;
	}

	/**
	 * Calls the StabilityAiImageModel with the given StabilityAiImagePrompt and returns
	 * the ImageResponse. This overloaded call method lets you pass the full set of Prompt
	 * instructions that StabilityAI supports.
	 * @param imagePrompt the StabilityAiImagePrompt containing the prompt and image model
	 * options
	 * @return the ImageResponse generated by the StabilityAiImageModel
	 */
	public ImageResponse call(ImagePrompt imagePrompt) {

		ImageOptions runtimeOptions = imagePrompt.getOptions();

		// Merge the runtime options passed via the prompt with the StabilityAiImageModel
		// options configured via Autoconfiguration.
		// Runtime options overwrite StabilityAiImageModel options
		StabilityAiImageOptions optionsToUse = ModelOptionsUtils.merge(runtimeOptions, this.options,
				StabilityAiImageOptions.class);

		// Copy the org.springframework.ai.model derived ImagePrompt and ImageOptions data
		// types to the data types used in StabilityAiApi
		StabilityAiApi.GenerateImageRequest generateImageRequest = getGenerateImageRequest(imagePrompt, optionsToUse);

		// Make the request
		StabilityAiApi.GenerateImageResponse generateImageResponse = this.stabilityAiApi
			.generateImage(generateImageRequest);

		// Convert to org.springframework.ai.model derived ImageResponse data type
		return convertResponse(generateImageResponse);

	}

	private static StabilityAiApi.GenerateImageRequest getGenerateImageRequest(ImagePrompt stabilityAiImagePrompt,
			StabilityAiImageOptions optionsToUse) {
		StabilityAiApi.GenerateImageRequest.Builder builder = new StabilityAiApi.GenerateImageRequest.Builder();
		StabilityAiApi.GenerateImageRequest generateImageRequest = builder
			.withTextPrompts(stabilityAiImagePrompt.getInstructions()
				.stream()
				.map(message -> new StabilityAiApi.GenerateImageRequest.TextPrompts(message.getText(),
						message.getWeight()))
				.collect(Collectors.toList()))
			.withHeight(optionsToUse.getHeight())
			.withWidth(optionsToUse.getWidth())
			.withCfgScale(optionsToUse.getCfgScale())
			.withClipGuidancePreset(optionsToUse.getClipGuidancePreset())
			.withSampler(optionsToUse.getSampler())
			.withSamples(optionsToUse.getN())
			.withSeed(optionsToUse.getSeed())
			.withSteps(optionsToUse.getSteps())
			.withStylePreset(optionsToUse.getStylePreset())
			.build();
		return generateImageRequest;
	}

	private ImageResponse convertResponse(StabilityAiApi.GenerateImageResponse generateImageResponse) {
		List<ImageGeneration> imageGenerationList = generateImageResponse.artifacts().stream().map(entry -> {
			return new ImageGeneration(new Image(null, entry.base64()),
					new StabilityAiImageGenerationMetadata(entry.finishReason(), entry.seed()));
		}).toList();

		return new ImageResponse(imageGenerationList, new ImageResponseMetadata());
	}

	private StabilityAiImageOptions convertOptions(ImageOptions runtimeOptions) {
		StabilityAiImageOptions.Builder builder = StabilityAiImageOptions.builder();
		if (runtimeOptions == null) {
			return builder.build();
		}
		if (runtimeOptions.getN() != null) {
			builder.withN(runtimeOptions.getN());
		}
		if (runtimeOptions.getModel() != null) {
			builder.withModel(runtimeOptions.getModel());
		}
		if (runtimeOptions.getResponseFormat() != null) {
			builder.withResponseFormat(runtimeOptions.getResponseFormat());
		}
		if (runtimeOptions.getWidth() != null) {
			builder.withWidth(runtimeOptions.getWidth());
		}
		if (runtimeOptions.getHeight() != null) {
			builder.withHeight(runtimeOptions.getHeight());
		}
		if (runtimeOptions instanceof StabilityAiImageOptions) {
			StabilityAiImageOptions stabilityAiImageOptions = (StabilityAiImageOptions) runtimeOptions;
			if (stabilityAiImageOptions.getCfgScale() != null) {
				builder.withCfgScale(stabilityAiImageOptions.getCfgScale());
			}
			if (stabilityAiImageOptions.getClipGuidancePreset() != null) {
				builder.withClipGuidancePreset(stabilityAiImageOptions.getClipGuidancePreset());
			}
			if (stabilityAiImageOptions.getSampler() != null) {
				builder.withSampler(stabilityAiImageOptions.getSampler());
			}
			if (stabilityAiImageOptions.getSeed() != null) {
				builder.withSeed(stabilityAiImageOptions.getSeed());
			}
			if (stabilityAiImageOptions.getSteps() != null) {
				builder.withSteps(stabilityAiImageOptions.getSteps());
			}
			if (stabilityAiImageOptions.getStylePreset() != null) {
				builder.withStylePreset(stabilityAiImageOptions.getStylePreset());
			}
		}
		return builder.build();
	}

}
