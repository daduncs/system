/*
 * This file is part of System Client (https://github.com/daduncs/system)
 * Website: https://download.daduncs.cloud/
 *
 * Copyright (c) 2024 - 2026 DLindustries and project contributors.
 *
 * System Client is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * System Client is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with System Client. If not, see <https://www.gnu.org/licenses/>.
 */

package dlindustries.uk.daduncs.render.renderers.pipeline;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;

public final class PipelineRegistry {

	public static RenderPipeline RECTANGLE_PIPELINE;
	public static RenderPipeline BORDER_PIPELINE;
	public static RenderPipeline TEXTURE_PIPELINE;
	public static RenderPipeline HEAD_TEXTURE_PIPELINE;
	public static RenderPipeline BLUR_PIPELINE;
	public static RenderPipeline GLASS_PIPELINE;
	public static RenderPipeline BUBBLE_PIPELINE;
	public static RenderPipeline TEXT_GLOW_PIPELINE;
	public static RenderPipeline ROUND_IMAGE_PIPELINE;
	public static RenderPipeline STAIR_PIPELINE;
	public static RenderPipeline STAIR_BLUR_PIPELINE;
	public static RenderPipeline STAIR_GLASS_PIPELINE;
	public static RenderPipeline STAIR_BORDER_PIPELINE;
	public static RenderPipeline HEAD_PIPELINE;
	public static RenderPipeline LYRICS_PIPELINE;
	public static RenderPipeline COMIC_PIPELINE;
	public static RenderPipeline LINES_PIPELINE;
	public static RenderPipeline SKY_PIPELINE;
	public static RenderPipeline PICKER_PIPELINE;
	public static RenderPipeline LINE_PIPELINE;
	public static RenderPipeline SHAPE_PIPELINE;
	public static RenderPipeline FROST_PIPELINE;

	private static VertexFormat format() {
		return VertexFormat.builder()
				.add("Position", VertexFormatElement.POSITION)
				.add("Color", VertexFormatElement.COLOR)
				.add("UV0", VertexFormatElement.UV0)
				.add("UV1", VertexFormatElement.UV1)
				.add("UV2", VertexFormatElement.UV2)
				.add("Normal", VertexFormatElement.NORMAL)
				.build();
	}

	private static RenderPipeline build(String location, String shader, boolean sampler) {
		RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
				.withLocation(Identifier.of("system", "pipeline/" + location))
				.withVertexShader(Identifier.of("system", "core/" + shader))
				.withFragmentShader(Identifier.of("system", "core/" + shader))
				.withVertexFormat(format(), VertexFormat.DrawMode.QUADS)
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withDepthWrite(false)
				.withCull(false);

		if (sampler)
			builder.withSampler("Sampler0");

		return RenderPipelines.register(builder.build());
	}

	private static RenderPipeline world(String location, String shader) {
		return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
				.withLocation(Identifier.of("system", "pipeline/" + location))
				.withVertexShader(Identifier.of("system", "core/" + shader))
				.withFragmentShader(Identifier.of("system", "core/" + shader))
				.withVertexFormat(format(), VertexFormat.DrawMode.QUADS)
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withDepthWrite(false)
				.withCull(false)
				.withSampler("Sampler0")
				.build());
	}

	public static void init() {
		RECTANGLE_PIPELINE = build("rectangle", "rectangle", false);
		BORDER_PIPELINE = build("border", "border", false);
		TEXTURE_PIPELINE = build("texture", "texture", true);
		HEAD_TEXTURE_PIPELINE = build("head_texture", "texture", true);
		ROUND_IMAGE_PIPELINE = build("round_image", "round_image", true);
		BLUR_PIPELINE = build("blur", "blur", true);
		GLASS_PIPELINE = build("glass", "glass", true);
		BUBBLE_PIPELINE = build("bubble", "bubble", true);
		TEXT_GLOW_PIPELINE = build("text_glow", "text_glow", true);
		STAIR_PIPELINE = build("stair", "stair", false);
		STAIR_BLUR_PIPELINE = build("stair_blur", "stair_blur", true);
		STAIR_GLASS_PIPELINE = build("stair_glass", "stair_glass", true);
		STAIR_BORDER_PIPELINE = build("stair_border", "stair_border", false);
		HEAD_PIPELINE = build("head", "head", true);
		LYRICS_PIPELINE = world("lyrics", "lyrics");
		COMIC_PIPELINE = world("comic", "comic");
		LINES_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
				.withLocation(Identifier.of("system", "pipeline/lines"))
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.build());
		SKY_PIPELINE = build("sky", "sky", false);
		PICKER_PIPELINE = build("picker", "picker", false);
		LINE_PIPELINE = build("line", "line", false);
		SHAPE_PIPELINE = build("shape", "shape", false);
		FROST_PIPELINE = build("frost", "frost", true);
	}

	private PipelineRegistry() {
	}
}
