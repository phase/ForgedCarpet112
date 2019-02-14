package carpet.forge.commands;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class CommandGMS extends CarpetCommandBase {
    @Override
    public String getName() {
        return "s";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "Change to survival mode";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {

        if (!command_enabled("commandCameramode", sender)) return;
        if (args.length > 0) {
            throw new WrongUsageException(getUsage(sender), new Object[0]);
        } else {
            GameType gametype = GameType.parseGameTypeWithDefault("survival", GameType.NOT_SET);
            EntityPlayer entityplayer = getCommandSenderAsPlayer(sender);
            entityplayer.setGameType(gametype);
            entityplayer.removePotionEffect(Potion.getPotionFromResourceLocation("night_vision"));
        }

    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        return Collections.emptyList();
    }

}
