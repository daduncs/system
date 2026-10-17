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

package dlindustries.uk.daduncs.managers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dlindustries.uk.daduncs.gui.Theme;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class AccentManager {

	private static final String CLIENT_ID = "1543720626143887460";
	private static final float SATURATION = 0.42f;
	private static final float LIGHTNESS = 0.64f;

	private boolean scanned;
	private Identifier avatar;

	public Identifier getAvatar() {
		return avatar;
	}

	public void scan() {
		if (scanned)
			return;

		scanned = true;

		Thread thread = new Thread(this::run, "accent");
		thread.setDaemon(true);
		thread.start();
	}

	private int discord = 0xFFC8A57D;
	private volatile String name = "";

	public int getAccent() {
		return discord;
	}

	public String getName() {
		return name;
	}

	private void run() {
		try {
			String[] user = handshake();
			if (user == null)
				return;

			BufferedImage image = download(user[0], user[1]);
			if (image == null)
				return;

			if (user.length > 2)
				name = user[2];

			discord = shade(dominant(image));
			upload(image);
		} catch (Exception ignored) {
		}
	}

	private void upload(BufferedImage image) {
		MinecraftClient.getInstance().execute(() -> {
			try {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				ImageIO.write(image, "PNG", out);

				NativeImage native0 = NativeImage.read(new ByteArrayInputStream(out.toByteArray()));
				NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "system_avatar", native0);

				Identifier id = Identifier.of("system", "avatar");
				MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
				texture.upload();

				avatar = id;
			} catch (Exception ignored) {
			}
		});
	}

	private String[] handshake() {
		for (int pipe = 0; pipe < 10; pipe++) {
			try (RandomAccessFile socket = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + pipe, "rw")) {
				write(socket, 0, "{\"v\":1,\"client_id\":\"" + CLIENT_ID + "\"}");

				String payload = read(socket);
				if (payload == null)
					continue;

				for (int frame = 0; frame < 4; frame++) {
					if (payload == null)
						break;

					JsonObject root = JsonParser.parseString(payload).getAsJsonObject();

					if (root.has("data")) {
						JsonObject data = root.getAsJsonObject("data");

						if (data.has("user")) {
							JsonObject account = data.getAsJsonObject("user");

							if (!account.get("avatar").isJsonNull()) {
								String shown = account.get("username").getAsString();

								return new String[]{account.get("id").getAsString(), account.get("avatar").getAsString(), shown};
							}
						}
					}

					payload = read(socket);
				}
			} catch (Exception ignored) {
			}
		}

		return null;
	}

	private static void write(RandomAccessFile socket, int opcode, String payload) throws Exception {
		byte[] body = payload.getBytes(StandardCharsets.UTF_8);
		ByteBuffer frame = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);

		frame.putInt(opcode);
		frame.putInt(body.length);
		frame.put(body);

		socket.write(frame.array());
	}

	private static String read(RandomAccessFile socket) throws Exception {
		byte[] header = new byte[8];
		socket.readFully(header);

		int length = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(4);
		if (length <= 0 || length > 1 << 20)
			return null;

		byte[] body = new byte[length];
		socket.readFully(body);

		return new String(body, StandardCharsets.UTF_8);
	}

	private static BufferedImage download(String id, String avatar) {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create("https://cdn.discordapp.com/avatars/" + id + "/" + avatar + ".png?size=128"))
					.GET()
					.build();

			HttpResponse<byte[]> response = HttpClient.newHttpClient()
					.send(request, HttpResponse.BodyHandlers.ofByteArray());

			if (response.statusCode() != 200)
				return null;

			return ImageIO.read(new java.io.ByteArrayInputStream(response.body()));
		} catch (Exception ignored) {
			return null;
		}
	}

	private static int dominant(BufferedImage image) {
		int[] buckets = new int[36];
		long[] reds = new long[36];
		long[] greens = new long[36];
		long[] blues = new long[36];

		for (int x = 0; x < image.getWidth(); x++) {
			for (int y = 0; y < image.getHeight(); y++) {
				int argb = image.getRGB(x, y);
				if (((argb >> 24) & 0xFF) < 128)
					continue;

				int red = (argb >> 16) & 0xFF;
				int green = (argb >> 8) & 0xFF;
				int blue = argb & 0xFF;

				float[] hsb = Color.RGBtoHSB(red, green, blue, null);
				if (hsb[1] < 0.18f || hsb[2] < 0.15f || hsb[2] > 0.95f)
					continue;

				int slot = Math.min(35, (int) (hsb[0] * 36f));
				buckets[slot]++;
				reds[slot] += red;
				greens[slot] += green;
				blues[slot] += blue;
			}
		}

		int best = -1;
		for (int i = 0; i < buckets.length; i++)
			if (best == -1 || buckets[i] > buckets[best])
				best = i;

		if (best == -1 || buckets[best] == 0)
			return Theme.accent;

		int count = buckets[best];
		return 0xFF000000
				| ((int) (reds[best] / count) << 16)
				| ((int) (greens[best] / count) << 8)
				| (int) (blues[best] / count);
	}

	private static int shade(int argb) {
		float[] hsb = Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
		return 0xFF000000 | (Color.HSBtoRGB(hsb[0], SATURATION, LIGHTNESS) & 0xFFFFFF);
	}
}
