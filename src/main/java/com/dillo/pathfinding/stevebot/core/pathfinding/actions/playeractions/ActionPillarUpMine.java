package com.dillo.pathfinding.stevebot.core.pathfinding.actions.playeractions;

import com.dillo.pathfinding.stevebot.core.data.blockpos.BaseBlockPos;
import com.dillo.pathfinding.stevebot.core.data.blocks.BlockUtils;
import com.dillo.pathfinding.stevebot.core.data.items.wrapper.ItemToolWrapper;
import com.dillo.pathfinding.stevebot.core.data.modification.BlockBreakModification;
import com.dillo.pathfinding.stevebot.core.data.modification.Modification;
import com.dillo.pathfinding.stevebot.core.misc.Direction;
import com.dillo.pathfinding.stevebot.core.misc.ProcState;
import com.dillo.pathfinding.stevebot.core.misc.StateMachine;
import com.dillo.pathfinding.stevebot.core.pathfinding.actions.ActionCosts;
import com.dillo.pathfinding.stevebot.core.pathfinding.actions.ActionFactory;
import com.dillo.pathfinding.stevebot.core.pathfinding.actions.ActionObserver;
import com.dillo.pathfinding.stevebot.core.pathfinding.actions.ActionUtils;
import com.dillo.pathfinding.stevebot.core.pathfinding.actions.BreakBlockCheckResult;
import com.dillo.pathfinding.stevebot.core.pathfinding.nodes.Node;
import com.dillo.pathfinding.stevebot.core.pathfinding.nodes.NodeCache;
import com.dillo.pathfinding.stevebot.core.player.PlayerUtils;

public class ActionPillarUpMine extends Action {

  private enum State {
    SLOWING_DOWN,
    BREAKING,
    JUMPING,
    LANDING,
  }

  private enum Transition {
    SLOW_ENOUGH,
    BROKE_BLOCK,
    PLACED_BLOCK,
  }

  private final StateMachine<State, Transition> stateMachine = new StateMachine<>();
  private final Modification[] modifications;

  private ActionPillarUpMine(Node from, Node to, double cost, Modification[] modifications) {
    super(from, to, cost);
    this.modifications = modifications;
    stateMachine.defineTransition(State.SLOWING_DOWN, Transition.SLOW_ENOUGH, State.BREAKING);
    stateMachine.defineTransition(State.BREAKING, Transition.BROKE_BLOCK, State.JUMPING);
    stateMachine.defineTransition(State.JUMPING, Transition.PLACED_BLOCK, State.LANDING);
    stateMachine.addListener(Transition.SLOW_ENOUGH, ((previous, next, transition) -> onSlowEnough()));
    stateMachine.addListener(Transition.BROKE_BLOCK, ((previous, next, transition) -> onBrokeBlock()));
  }

  @Override
  public void resetAction() {
    stateMachine.setState(State.SLOWING_DOWN);
  }

  @Override
  public ProcState tick(boolean firstTick) {
    ActionObserver.tickAction(this.getActionName());
    switch (stateMachine.getState()) {
      case SLOWING_DOWN:
        {
          return tickSlowDown();
        }
      case BREAKING:
        return tickBreakBlock();
      case JUMPING:
        {
          return tickJump();
        }
      case LANDING:
        {
          return tickLand();
        }
      default:
        {
          return ProcState.FAILED;
        }
    }
  }

  /**
   * Prepare by slowing down enough.
   */
  private ProcState tickSlowDown() {
    boolean slowEnough = PlayerUtils.getMovement().slowDown(0.075);
    if (slowEnough) {
      stateMachine.fireTransition(Transition.SLOW_ENOUGH);
    } else {
      PlayerUtils
        .getCamera()
        .setLookAt(getTo().getPos().getX(), getTo().getPos().getY(), getTo().getPos().getZ(), true);
    }
    return ProcState.EXECUTING;
  }

  /**
   * Player reached the required speed and position.
   */
  private void onSlowEnough() {
    PlayerUtils.getCamera().enableForceCamera();
    PlayerUtils.getInventory().selectItem(((BlockBreakModification) getModifications()[0]).getTool());
  }

  /**
   * Break the block above
   */
  private ProcState tickBreakBlock() {
    final BlockBreakModification modification = (BlockBreakModification) getModifications()[0];
    if (ActionUtils.breakBlock(modification.getPosition())) {
      stateMachine.fireTransition(Transition.BROKE_BLOCK);
    }
    return ProcState.EXECUTING;
  }

  /**
   * When the block above was broken
   */
  private void onBrokeBlock() {}

  /**
   * Pillar up by jumping and placing a block below.
   */
  private ProcState tickJump() {
    PlayerUtils.getMovement().moveTowards(getTo().getPos(), true);
    if (PlayerUtils.getPlayerBlockPos().equals(getFrom().getPos())) {
      PlayerUtils.getInput().setJump();
    }
    if (PlayerUtils.getPlayerBlockPos().equals(getTo().getPos())) {
      if (!PlayerUtils.getInventory().selectThrowawayBlock(true)) {
        return ProcState.FAILED;
      }
      ActionUtils.placeBlockAgainst(getFrom().getPosCopy().add(Direction.DOWN), Direction.UP);
      stateMachine.fireTransition(Transition.PLACED_BLOCK);
    }
    return ProcState.EXECUTING;
  }

  /**
   * Land on the new block.
   */
  private ProcState tickLand() {
    if (PlayerUtils.isOnGround() && PlayerUtils.getPlayerBlockPos().equals(getTo().getPos())) {
      PlayerUtils.getCamera().disableForceCamera(true);
      return ProcState.DONE;
    } else {
      return ProcState.EXECUTING;
    }
  }

  @Override
  public boolean isOnPath(BaseBlockPos position) {
    return position.equals(getFrom().getPos()) || position.equals(getTo().getPos());
  }

  @Override
  public boolean hasModifications() {
    return true;
  }

  @Override
  public Modification[] getModifications() {
    return this.modifications;
  }

  @Override
  public String getActionName() {
    return "pillar-up-mine";
  }

  public static class PillarUpMineFactory implements ActionFactory {

    @Override
    public Action createAction(Node node, Result result) {
      return new ActionPillarUpMine(node, result.to, result.estimatedCost, result.modifications);
    }

    @Override
    public Result check(Node node) {
      // check inventory
      if (!PlayerUtils.getActiveSnapshot().hasThrowawayBlockInHotbar(true)) {
        return Result.invalid();
      }

      // check from-position
      if (!ActionUtils.canStandAt(node.getPos())) {
        return Result.invalid();
      }

      // check to-position
      final BaseBlockPos blockAbove = node.getPosCopy().add(0, 2, 0);
      if (BlockUtils.canWalkThrough(blockAbove)) {
        return Result.invalid();
      }

      // check block to break
      final BaseBlockPos posAbove = node.getPosCopy().add(0, 2, 0);
      if (!ActionUtils.canSafelyBreak(posAbove)) {
        return Result.invalid();
      }
      float ticksToBreak = 0;
      Modification modificationBreakBlock = null;
      if (!BlockUtils.canWalkThrough(posAbove)) {
        final BreakBlockCheckResult resultBottom = ActionUtils.checkBlockToBreak(posAbove);
        if (!resultBottom.breakable) {
          return Result.invalid();
        } else {
          ticksToBreak += resultBottom.ticksToBreak;
          modificationBreakBlock = Modification.breakBlock(posAbove, (ItemToolWrapper) resultBottom.bestTool);
        }
      }

      // build valid result
      final int indexThrowaway = PlayerUtils.getActiveSnapshot().findThrowawayBlock(true);
      final Modification[] modifications = new Modification[] {
        modificationBreakBlock,
        Modification.placeBlock(node.getPos(), PlayerUtils.getActiveSnapshot().getAsBlock(indexThrowaway)),
      };
      return Result.valid(
        Direction.UP,
        NodeCache.get(node.getPosCopy().add(Direction.UP)),
        ActionCosts.get().PILLAR_UP + ticksToBreak + ActionCosts.get().CONSTANT_BLOCK_BREAK_MOD,
        modifications
      );
    }

    @Override
    public Direction getDirection() {
      return Direction.UP;
    }

    @Override
    public Class<ActionPillarUpMine> producesAction() {
      return ActionPillarUpMine.class;
    }
  }
}
