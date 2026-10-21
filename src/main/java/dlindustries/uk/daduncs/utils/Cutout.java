package dlindustries.uk.daduncs.utils;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Map;

public final class Cutout {

	private static final int SIZE = 320;
	private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
	private static final float[] STD = {0.229f, 0.224f, 0.225f};

	private static OrtSession session;

	private Cutout() {
	}

	public static synchronized BufferedImage cut(BufferedImage photo) throws Exception {
		OrtEnvironment env = OrtEnvironment.getEnvironment();

		if (session == null) {
			try (InputStream model = Cutout.class.getResourceAsStream("/assets/system/model/u2netp.onnx")) {
				session = env.createSession(model.readAllBytes(), new OrtSession.SessionOptions());
			}
		}

		int[] pixels = scale(photo, SIZE, SIZE).getRGB(0, 0, SIZE, SIZE, null, 0, SIZE);
		float max = 1f;

		for (int pixel : pixels)
			max = Math.max(max, Math.max((pixel >> 16) & 0xFF, Math.max((pixel >> 8) & 0xFF, pixel & 0xFF)));

		int area = SIZE * SIZE;
		float[] input = new float[3 * area];

		for (int i = 0; i < area; i++) {
			int pixel = pixels[i];
			input[i] = (((pixel >> 16) & 0xFF) / max - MEAN[0]) / STD[0];
			input[area + i] = (((pixel >> 8) & 0xFF) / max - MEAN[1]) / STD[1];
			input[2 * area + i] = ((pixel & 0xFF) / max - MEAN[2]) / STD[2];
		}

		float[][] mask;
		try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), new long[]{1, 3, SIZE, SIZE});
			 OrtSession.Result result = session.run(Map.of(session.getInputNames().iterator().next(), tensor))) {
			mask = ((float[][][][]) result.get(0).getValue())[0][0];
		}

		float low = Float.MAX_VALUE;
		float high = -Float.MAX_VALUE;
		for (float[] row : mask)
			for (float value : row) {
				low = Math.min(low, value);
				high = Math.max(high, value);
			}

		BufferedImage alpha = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < SIZE; y++)
			for (int x = 0; x < SIZE; x++) {
				int value = Math.round((mask[y][x] - low) / Math.max(high - low, 1e-6f) * 255f);
				alpha.setRGB(x, y, value << 24);
			}

		int width = photo.getWidth();
		int height = photo.getHeight();
		BufferedImage big = scale(alpha, width, height);
		BufferedImage cut = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		int left = width, top = height, right = -1, bottom = -1;

		for (int y = 0; y < height; y++)
			for (int x = 0; x < width; x++) {
				int shown = big.getRGB(x, y) >>> 24;
				cut.setRGB(x, y, shown << 24 | (photo.getRGB(x, y) & 0xFFFFFF));

				if (shown < 25) continue;
				left = Math.min(left, x);
				top = Math.min(top, y);
				right = Math.max(right, x);
				bottom = Math.max(bottom, y);
			}

		if (right < left || bottom < top) return null;

		return cut.getSubimage(left, top, right - left + 1, bottom - top + 1);
	}

	private static BufferedImage scale(BufferedImage image, int width, int height) {
		BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = scaled.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics.drawImage(image, 0, 0, width, height, null);
		graphics.dispose();
		return scaled;
	}
}
