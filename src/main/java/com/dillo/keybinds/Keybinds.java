package com.dillo.keybinds;

import static com.dillo.commands.RouteCommands.StructurePoints.render;
import static com.dillo.main.macro.refuel.ReFuelDrill.isStart;
import static com.dillo.main.teleport.macro.TeleportToNextBlock.alrMoved;
import static com.dillo.main.utils.looks.LookAt.reset;

import com.dillo.armadillomacro;
import com.dillo.main.macro.refuel.ReFuelDrill;
import com.dillo.main.route.Nuker.StartNuker;
import com.dillo.pathfinding.baritone.automine.handlers.KeybindHandler;
import com.dillo.utils.StartMacro;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class Keybinds {

  public static boolean isNuking = false;
  public static boolean wasDown = false;

  @SubscribeEvent
  public void onTick(TickEvent.ClientTickEvent event) {
    if (event.phase == TickEvent.Phase.END) {
      if (armadillomacro.keybinds.get(0).isPressed()) {
        reset();
        alrMoved = false;
        isStart = false;
        KeybindHandler.updateKeys(false, false, false, false, false, false, false, false);
        StartMacro.startMacro();
      }

      if (armadillomacro.keybinds.get(1).isPressed()) {
        isNuking = !isNuking;

        if (isNuking) {
          StartNuker.startNuker();
        } else {
          StartNuker.stopNuker();
        }
      }

      if (armadillomacro.keybinds.get(2).isKeyDown()) {
        render = true;
        wasDown = true;
      } else {
        if (wasDown) {
          render = false;
          wasDown = false;
        }
      }

      if (armadillomacro.keybinds.get(4).isKeyDown()) {
        ReFuelDrill.reFuelDrill();
      }
    }
  }
}
