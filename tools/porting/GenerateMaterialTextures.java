import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Reproduces ItemAutogen's copper RGBMutatorInterpolatedComponentRemap. */
class GenerateMaterialTextures {
    public static void main(String[] args) throws Exception {
        boolean check = args.length == 1 && args[0].equals("--check");
        Path source = Path.of("src/main/resources/assets/hbm/textures/items/scraps.png");
        Path target = Path.of("src/forge120/resources/assets/hbm/textures/item/scraps_copper.png");
        BufferedImage input = ImageIO.read(source.toFile());
        BufferedImage output = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_INT_ARGB);
        BufferedImage saved = check && Files.exists(target) ? ImageIO.read(target.toFile()) : null;
        if (check && (saved == null || saved.getWidth() != input.getWidth() || saved.getHeight() != input.getHeight())) {
            throw new IllegalStateException("Missing or wrong-sized copper scraps texture; run this generator without --check");
        }
        for (int y = 0; y < input.getHeight(); y++) for (int x = 0; x < input.getWidth(); x++) {
            int pixel = input.getRGB(x, y);
            int mapped = pixel & 0xff000000;
            for (int shift : new int[] {16, 8, 0}) {
                int value = pixel >> shift & 255;
                int light = 0xFDCA88 >> shift & 255;
                int dark = 0x601E0D >> shift & 255;
                double fraction = (value - 255.0) / (80.0 - 255.0);
                mapped |= ((int) (light + fraction * (dark - light)) & 255) << shift;
            }
            output.setRGB(x, y, mapped);
            if (check && saved.getRGB(x, y) != mapped) {
                throw new IllegalStateException("Copper scraps texture differs from source component remap at " + x + "," + y);
            }
        }
        if (!check) ImageIO.write(output, "png", target.toFile());
        System.out.println("Copper scraps texture " + (check ? "verified" : "generated") + " from source component remap");
    }
}
