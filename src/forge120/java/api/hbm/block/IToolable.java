package api.hbm.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/** Modern block-use context for the source HBM tooling contract. */
public interface IToolable {
    boolean onScrew(UseOnContext context, ToolType tool);

    enum ToolType {
        SCREWDRIVER, HAND_DRILL, DEFUSER, WRENCH, TORCH, BOLT;

        public final List<ItemStack> stacksForDisplay = new ArrayList<>();

        public void register(ItemStack stack) {
            stacksForDisplay.add(stack.copyWithCount(1));
        }

        public static ToolType getType(ItemStack stack) {
            if (stack.isEmpty()) return null;
            // Legacy damage/metadata tooling variants become separate named items.
            for (ToolType type : values()) {
                for (ItemStack registered : type.stacksForDisplay) {
                    if (stack.is(registered.getItem())) return type;
                }
            }
            return null;
        }
    }
}
