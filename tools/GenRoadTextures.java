import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Generates the road block textures and the Zoning Wand icon from scratch, so
 * every pixel is original (CC0). Run from the repo root:
 *
 *   java tools/GenRoadTextures.java [output textures dir]
 *
 * Block textures are 256x256 and tile seamlessly (noise, specks and cracks
 * wrap around the edges). Top views have north at the top of the image.
 */
public class GenRoadTextures {
	static final int N = 256;

	// ---- tiling noise -------------------------------------------------------

	static float[][] tileNoise(long seed, int cells) {
		Random r = new Random(seed);
		float[][] lat = new float[cells][cells];
		for (float[] row : lat) {
			for (int i = 0; i < cells; i++) {
				row[i] = r.nextFloat();
			}
		}
		float[][] out = new float[N][N];
		for (int y = 0; y < N; y++) {
			float fy = (float) y * cells / N;
			int iy = (int) Math.floor(fy);
			float ty = smooth(fy - iy);
			for (int x = 0; x < N; x++) {
				float fx = (float) x * cells / N;
				int ix = (int) Math.floor(fx);
				float tx = smooth(fx - ix);
				float a = lat[iy % cells][ix % cells];
				float b = lat[iy % cells][(ix + 1) % cells];
				float c = lat[(iy + 1) % cells][ix % cells];
				float d = lat[(iy + 1) % cells][(ix + 1) % cells];
				out[y][x] = lerp(lerp(a, b, tx), lerp(c, d, tx), ty);
			}
		}
		return out;
	}

	static float[][] fbm(long seed, int baseCells, int octaves) {
		float[][] sum = new float[N][N];
		float weight = 1;
		float total = 0;
		for (int o = 0; o < octaves; o++) {
			float[][] n = tileNoise(seed * 31 + o, baseCells << o);
			for (int y = 0; y < N; y++) {
				for (int x = 0; x < N; x++) {
					sum[y][x] += n[y][x] * weight;
				}
			}
			total += weight;
			weight *= 0.55f;
		}
		for (int y = 0; y < N; y++) {
			for (int x = 0; x < N; x++) {
				sum[y][x] /= total;
			}
		}
		return sum;
	}

	static float smooth(float t) {
		return t * t * (3 - 2 * t);
	}

	static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	static float clamp(float v) {
		return Math.max(0, Math.min(1, v));
	}

	// ---- float images (r, g, b in 0..1) ----------------------------------------

	static float[][][] image() {
		return new float[N][N][3];
	}

	static void fill(float[][][] img, float r, float g, float b) {
		for (float[][] row : img) {
			for (float[] px : row) {
				px[0] = r;
				px[1] = g;
				px[2] = b;
			}
		}
	}

	/** Soft disc that wraps around the edges so the texture keeps tiling. */
	static void disc(float[][][] img, float cx, float cy, float radius, float r, float g, float b, float alpha) {
		int reach = (int) Math.ceil(radius) + 1;
		for (int dy = -reach; dy <= reach; dy++) {
			for (int dx = -reach; dx <= reach; dx++) {
				float dist = (float) Math.hypot(dx - (cx - Math.round(cx)), dy - (cy - Math.round(cy)));
				float cover = clamp(radius - dist + 0.5f) * alpha;
				if (cover <= 0) {
					continue;
				}
				float[] px = img[Math.floorMod(Math.round(cy) + dy, N)][Math.floorMod(Math.round(cx) + dx, N)];
				px[0] = lerp(px[0], r, cover);
				px[1] = lerp(px[1], g, cover);
				px[2] = lerp(px[2], b, cover);
			}
		}
	}

	static void crack(float[][][] img, Random rnd, float darkness) {
		float x = rnd.nextInt(N);
		float y = rnd.nextInt(N);
		float angle = rnd.nextFloat() * 6.2832f;
		int steps = 60 + rnd.nextInt(90);
		for (int i = 0; i < steps; i++) {
			angle += (rnd.nextFloat() - 0.5f) * 0.7f;
			x += Math.cos(angle) * 1.5f;
			y += Math.sin(angle) * 1.5f;
			disc(img, x, y, 0.9f, 0.02f, 0.02f, 0.02f, darkness);
		}
	}

	// ---- surfaces --------------------------------------------------------------

	/** Worn road asphalt: tonal variation, fine grain, stones, pits and (optionally) cracks and stains. */
	static float[][][] asphalt(long seed, boolean weathered, float brightness) {
		Random rnd = new Random(seed);
		float[][] tone = fbm(seed, 3, 4);
		float[][] patches = fbm(seed + 100, 6, 3);
		float[][][] img = image();
		for (int y = 0; y < N; y++) {
			for (int x = 0; x < N; x++) {
				float v = (0.215f + (tone[y][x] - 0.5f) * 0.10f + (patches[y][x] - 0.5f) * 0.05f) * brightness;
				v *= 1 + (rnd.nextFloat() - 0.5f) * 0.22f;
				img[y][x][0] = v * 0.99f;
				img[y][x][1] = v;
				img[y][x][2] = v * 1.04f;
			}
		}
		int stones = (int) (N * N * 0.009);
		for (int i = 0; i < stones; i++) {
			float shade = 0.27f + rnd.nextFloat() * 0.20f;
			boolean warm = rnd.nextInt(4) == 0;
			disc(img, rnd.nextFloat() * N, rnd.nextFloat() * N, 0.7f + rnd.nextFloat() * 1.0f,
				shade * (warm ? 1.08f : 0.98f), shade * (warm ? 0.97f : 0.99f), shade * (warm ? 0.86f : 1.02f),
				0.25f + rnd.nextFloat() * 0.35f);
		}
		int pits = stones / 3;
		for (int i = 0; i < pits; i++) {
			disc(img, rnd.nextFloat() * N, rnd.nextFloat() * N, 0.7f + rnd.nextFloat() * 1.1f, 0.05f, 0.05f, 0.055f, 0.6f);
		}
		if (weathered) {
			for (int i = 0; i < 3; i++) {
				crack(img, rnd, 0.55f);
			}
			for (int i = 0; i < 4; i++) {
				disc(img, rnd.nextFloat() * N, rnd.nextFloat() * N, 14 + rnd.nextFloat() * 20, 0.06f, 0.055f, 0.05f, 0.16f);
			}
		}
		return img;
	}

	/** Rough concrete, for kerbs. */
	static float[][][] concrete(long seed, float brightness, boolean grimy) {
		Random rnd = new Random(seed);
		float[][] tone = fbm(seed, 4, 4);
		float[][][] img = image();
		for (int y = 0; y < N; y++) {
			for (int x = 0; x < N; x++) {
				float v = (0.64f + (tone[y][x] - 0.5f) * 0.12f) * brightness;
				v *= 1 + (rnd.nextFloat() - 0.5f) * 0.10f;
				float grime = grimy ? clamp((y - N * 0.45f) / (N * 0.55f)) * 0.30f : 0;
				v *= 1 - grime;
				img[y][x][0] = v * 1.0f;
				img[y][x][1] = v * 0.985f;
				img[y][x][2] = v * 0.95f;
			}
		}
		for (int i = 0; i < 700; i++) {
			float shade = 0.35f + rnd.nextFloat() * 0.5f;
			disc(img, rnd.nextFloat() * N, rnd.nextFloat() * N, 0.6f + rnd.nextFloat() * 1.2f, shade, shade * 0.98f, shade * 0.94f, 0.4f);
		}
		for (int i = 0; i < 180; i++) {
			disc(img, rnd.nextFloat() * N, rnd.nextFloat() * N, 0.7f + rnd.nextFloat(), 0.15f, 0.15f, 0.14f, 0.5f);
		}
		if (grimy) {
			// tyre scuffs: short dark streaks near the bottom
			for (int i = 0; i < 5; i++) {
				float x = rnd.nextFloat() * N;
				float y = N * (0.62f + rnd.nextFloat() * 0.3f);
				for (int j = 0; j < 40; j++) {
					disc(img, x + j * 1.6f, y + (rnd.nextFloat() - 0.5f) * 2, 2.2f, 0.07f, 0.07f, 0.07f, 0.12f);
				}
			}
		}
		return img;
	}

	// ---- paint -----------------------------------------------------------------

	/** Blends worn paint into the image where {@code coverage[y][x]} is 1. */
	static void paint(float[][][] img, float[][] coverage, float[] colour, long seed) {
		Random rnd = new Random(seed);
		float[][] fade = fbm(seed, 4, 4);
		float[][] chips = tileNoise(seed + 7, 64);
		for (int y = 0; y < N; y++) {
			for (int x = 0; x < N; x++) {
				float c = coverage[y][x];
				if (c <= 0) {
					continue;
				}
				float alpha = c * (0.92f - 0.35f * smooth(clamp((fade[y][x] - 0.42f) * 3.5f)));
				if (chips[y][x] > 0.80f && rnd.nextFloat() < 0.7f) {
					alpha *= 0.55f;
				}
				float[] px = img[y][x];
				float lum = (px[0] + px[1] + px[2]) / 3f;
				float grain = 0.88f + lum * 0.55f;
				for (int k = 0; k < 3; k++) {
					px[k] = lerp(px[k], clamp(colour[k] * grain), alpha);
				}
			}
		}
	}

	interface Shape {
		/** Signed distance in pixels inside the shape (positive = inside). */
		float inside(int x, int y);
	}

	static float[][] coverage(Shape s, long seed) {
		float[][] wobble = fbm(seed, 8, 3);
		float[][] out = new float[N][N];
		for (int y = 0; y < N; y++) {
			for (int x = 0; x < N; x++) {
				float edgeNoise = (wobble[y][x] - 0.5f) * 3.0f;
				out[y][x] = clamp(s.inside(x, y) + edgeNoise + 0.5f);
			}
		}
		return out;
	}

	static final float[] WHITE = {0.92f, 0.92f, 0.88f};
	static final float[] YELLOW = {0.88f, 0.69f, 0.10f};

	static BufferedImage toImage(float[][][] img) {
		BufferedImage out = new BufferedImage(N, N, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < N; y++) {
			for (int x = 0; x < N; x++) {
				int r = Math.round(clamp(img[y][x][0]) * 255);
				int g = Math.round(clamp(img[y][x][1]) * 255);
				int b = Math.round(clamp(img[y][x][2]) * 255);
				out.setRGB(x, y, (r << 16) | (g << 8) | b);
			}
		}
		return out;
	}

	static void save(BufferedImage image, File dir, String name) throws Exception {
		dir.mkdirs();
		ImageIO.write(image, "png", new File(dir, name + ".png"));
		System.out.println("wrote " + new File(dir, name + ".png"));
	}

	// ---- wand icon -------------------------------------------------------------

	static BufferedImage wandIcon() {
		int s = 128;
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

		// handle: dark walnut with a lighter highlight edge
		g.setStroke(new BasicStroke(11, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(70, 42, 24));
		g.draw(new Line2D.Float(22, 106, 88, 40));
		g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(128, 84, 50));
		g.draw(new Line2D.Float(20, 104, 86, 38));

		// brass bands
		for (float t : new float[] {0.18f, 0.34f, 0.80f}) {
			float cx = 22 + (88 - 22) * t;
			float cy = 106 + (40 - 106) * t;
			g.setStroke(new BasicStroke(5, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
			g.setColor(new Color(196, 154, 58));
			g.draw(new Line2D.Float(cx - 8, cy - 8, cx + 8, cy + 8));
			g.setStroke(new BasicStroke(1.6f));
			g.setColor(new Color(244, 214, 120));
			g.draw(new Line2D.Float(cx - 7, cy - 9, cx + 9, cy + 7));
		}

		// glowing teal survey bead on top
		float bx = 98;
		float by = 30;
		g.setPaint(new RadialGradientPaint(bx, by, 24, new float[] {0f, 1f},
			new Color[] {new Color(120, 255, 230, 120), new Color(120, 255, 230, 0)}));
		g.fill(new Ellipse2D.Float(bx - 24, by - 24, 48, 48));
		g.setPaint(new RadialGradientPaint(bx - 3, by - 3, 14, new float[] {0f, 0.55f, 1f},
			new Color[] {new Color(210, 255, 248), new Color(60, 200, 190), new Color(16, 104, 124)}));
		g.fill(new Ellipse2D.Float(bx - 12, by - 12, 24, 24));
		g.setColor(new Color(255, 255, 255, 200));
		g.fill(new Ellipse2D.Float(bx - 7, by - 8, 6, 5));
		g.dispose();
		return img;
	}

	// ---- main ------------------------------------------------------------------

	public static void main(String[] args) throws Exception {
		File textures = new File(args.length > 0 ? args[0] : "src/main/resources/assets/cydev/textures");
		File road = new File(textures, "block/road");

		save(toImage(asphalt(1, false, 1.0f)), road, "asphalt");
		save(toImage(asphalt(2, true, 0.96f)), road, "asphalt_b");
		save(toImage(asphalt(3, false, 0.78f)), road, "asphalt_side");

		// double yellow centre line, running north-south
		float[][][] centre = asphalt(1, false, 1.0f);
		paint(centre, coverage((x, y) -> Math.max(7 - Math.abs(x - 110.5f), 7 - Math.abs(x - 146.5f)), 11), YELLOW, 21);
		save(toImage(centre), road, "asphalt_center_line");

		// white lane dashes, running north-south
		float[][][] dashed = asphalt(1, false, 1.0f);
		paint(dashed, coverage((x, y) -> Math.min(9 - Math.abs(x - 127.5f), Math.min(y - 64f, 192f - y)), 12), WHITE, 22);
		save(toImage(dashed), road, "asphalt_lane_dashed");

		// white edge line along the north edge
		float[][][] edge = asphalt(1, false, 1.0f);
		paint(edge, coverage((x, y) -> 10 - Math.abs(y - 36.5f), 13), WHITE, 23);
		save(toImage(edge), road, "asphalt_edge_line");

		// zebra crossing: four bars running north-south
		float[][][] zebra = asphalt(1, false, 1.0f);
		paint(zebra, coverage((x, y) -> 16 - Math.abs(Math.floorMod(x, 64) - 32f), 14), WHITE, 24);
		save(toImage(zebra), road, "asphalt_crosswalk");

		save(toImage(concrete(5, 1.0f, false)), road, "kerb_top");
		save(toImage(concrete(6, 0.92f, true)), road, "kerb_side");

		save(wandIcon(), new File(textures, "item"), "zoning_wand");
	}
}
