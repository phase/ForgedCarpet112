package carpet.forge.mixin;

import carpet.forge.interfaces.IItem;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Item.class)
public abstract class MixinItem implements IItem {

    /*
     * [FCM] Fix for stack changes when doing NBT checks on shoulkers.
     */
    @Override
    public boolean itemGroundStacking(boolean hasTagCompound) {
        return false;
    }

}
