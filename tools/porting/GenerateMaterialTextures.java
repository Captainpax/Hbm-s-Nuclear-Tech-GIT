import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Reproduces ItemAutogen's RGBMutatorInterpolatedComponentRemap for the ported materials. */
class GenerateMaterialTextures {
    private record Material(String name, int light, int dark, boolean plate) {}
    public static void main(String[] args) throws Exception {
        boolean check = args.length == 1 && args[0].equals("--check");
        int count = 0;
        for (Material material : new Material[] {
                new Material("copper", 0xFDCA88, 0x601E0D, true),
                new Material("iron", 0xFFFFFF, 0x353535, true),
                new Material("steel", 0xAFAFAF, 0x0F0F0F, true),
                new Material("carbon", 0x363636, 0x030303, false),
                new Material("flux", 0xF1E0BB, 0x6F6256, false)}) {
            remap("scraps", material, check);
            count++;
            if (material.plate) { remap("plate_cast", material, check); count++; }
        }
        System.out.println(count + " material textures " + (check ? "verified" : "generated") + " from source component remap");
    }
    private static void remap(String shape, Material material, boolean check) throws Exception {
        Path source = Path.of("src/main/resources/assets/hbm/textures/items/" + shape + ".png");
        Path target = Path.of("src/forge120/resources/assets/hbm/textures/item/" + shape + "_" + material.name + ".png");
        BufferedImage input = ImageIO.read(source.toFile());
        BufferedImage output = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_INT_ARGB);
        BufferedImage saved = check && Files.exists(target) ? ImageIO.read(target.toFile()) : null;
        if (check && (saved == null || saved.getWidth() != input.getWidth() || saved.getHeight() != input.getHeight())) {
            throw new IllegalStateException("Missing or wrong-sized material texture: " + target);
        }
        for (int y = 0; y < input.getHeight(); y++) for (int x = 0; x < input.getWidth(); x++) {
            int pixel = input.getRGB(x, y);
            int mapped = pixel & 0xff000000;
            for (int shift : new int[] {16, 8, 0}) {
                int value = pixel >> shift & 255;
                int light = material.light >> shift & 255;
                int dark = material.dark >> shift & 255;
                double fraction = (value - 255.0) / (80.0 - 255.0);
                mapped |= ((int) (light + fraction * (dark - light)) & 255) << shift;
            }
            output.setRGB(x, y, mapped);
            if (check && saved.getRGB(x, y) != mapped) {
                throw new IllegalStateException(target + " differs from source component remap at " + x + "," + y);
            }
        }
        if (!check) ImageIO.write(output, "png", target.toFile());
    }
}
