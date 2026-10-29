package api.hbm.block;

import com.hbm.PortMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Return the accepted quanta; unsupported materials and blocked targets accept zero. */
public interface ICrucibleAcceptor {
    int acceptPour(Level level, BlockPos pos, PortMaterial material, int offered);
}
