package com.dillo.pathfinding.stevebot.core.pathfinding.actions.playeractions;

import com.dillo.pathfinding.stevebot.core.data.blockpos.BaseBlockPos;
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
import com.dillo.pathfinding.stevebot.core.player.PlayerUtils;

public class ActionDigDown extends Action {

  private enum State {
    PREPARING,
    BREAKING_BLOCK,
    FALLING,
    FINISHING,
  }

  private enum Transition {
    PREPARED,
    BLOCK_BROKEN,
    TOUCHED_GROUND,
  }

  private final StateMachine<State, Transition> stateMachine = new StateMachine<>();

  private final Modification[] modifications;

  private ActionDigDown(Node from, Node to, double cost, Modification[] modifications) {
    super(from, to, cost);
    this.modifications = modifications;
    stateMachine.defineTransition(State.PREPARING, Transition.PREPARED, State.BREAKING_BLOCK);
    stateMachine.defineTransition(State.BREAKING_BLOCK, Transition.BLOCK_BROKEN, State.FALLING);
    stateMachine.defineTransition(State.FALLING, Transition.TOUCHED_GROUND, State.FINISHING);
    stateMachine.addListener(Transition.PREPARED, (previous, next, transition) -> onPrepared());
    stateMachine.addListener(Transition.BLOCK_BROKEN, (previous, next, transition) -> onBlockBroken());
  }

  @Override
  public void resetAction() {
    stateMachine.setState(State.PREPARING);
  }

  @Override
  public ProcState tick(boolean firstTick) {
    ActionObserver.tickAction(this.getActionName());
    switch (stateMachine.getState()) {
      case PREPARING:
        {
          return tickPrepare();
        }
      case BREAKING_BLOCK:
        {
          return tickBreakBlock();
        }
      case FALLING:
        {
          return tickFall();
        }
      case FINISHING:
        {
          return tickFinish();
        }
      default:
        {
          return ProcState.FAILED;
        }
    }
  }

  /**
   * Prepare everything before digging.
   */
  private ProcState tickPrepare() {
    PlayerUtils.getInventory().selectItem(((BlockBreakModification) getModifications()[0]).getTool());
    stateMachine.fireTransition(Transition.PREPARED);
    return ProcState.EXECUTING;
  }

  /**
   * Done preparing.
   */
  private void onPrepared() {
    PlayerUtils.getCamera().enableForceCamera();
  }

  /**
   * Dig down.
   */
  private ProcState tickBreakBlock() {
    if (ActionUtils.breakBlock(getFrom().getPosCopy().add(Direction.DOWN))) {
      stateMachine.fireTransition(Transition.BLOCK_BROKEN);
    }
    return ProcState.EXECUTING;
  }

  /**
   * The block was broken.
   */
  private void onBlockBroken() {
    PlayerUtils.getCamera().disableForceCamera(true);
  }

  /**
   * Fall after breaking the block below.
   */
  private ProcState tickFall() {
    if (PlayerUtils.isOnGround()) {
      stateMachine.fireTransition(Transition.TOUCHED_GROUND);
    }
    return ProcState.EXECUTING;
  }

  /**
   * Finish the action.
   */
  private ProcState tickFinish() {
    if (PlayerUtils.getMovement().moveTowards(getTo().getPos(), true)) {
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
    return "dig-down";
  }

  public static class DigDownFactory implements ActionFactory {

    private final ActionFall.FallActionFactory fallActionFactory = new ActionFall.FallActionFactory();

    @Override
    public Action createAction(Node node, Result result) {
      return new ActionDigDown(node, result.to, result.estimatedCost, result.modifications);
    }

    @Override
    public Result check(Node node) {
      // check from-position
      if (!ActionUtils.canStandAt(node.getPos())) {
        return Result.invalid();
      }

      // check if block breakable
      final BaseBlockPos posBreakBlock = node.getPosCopy().add(Direction.DOWN);
      final BreakBlockCheckResult resultBreak = ActionUtils.checkBlockToBreak(posBreakBlock);
      if (!resultBreak.breakable) {
        return Result.invalid();
      }
      if (!ActionUtils.canSafelyBreak(posBreakBlock)) {
        return Result.invalid();
      }

      // create modification
      BlockBreakModification modification = (BlockBreakModification) Modification.breakBlock(
        posBreakBlock,
        (ItemToolWrapper) resultBreak.bestTool
      );

      // check fall
      final Result resultFall = fallActionFactory.checkWithModification(node, modification);
      if (ResultType.VALID != resultFall.type) {
        return Result.invalid();
      }

      // build valid result
      final Modification[] modifications = new Modification[] { modification };
      return Result.valid(
        Direction.DOWN,
        resultFall.to,
        resultBreak.ticksToBreak + resultFall.estimatedCost + ActionCosts.get().CONSTANT_BLOCK_BREAK_MOD,
        modifications
      );
    }

    @Override
    public Direction getDirection() {
      return Direction.DOWN;
    }

    @Override
    public Class<ActionDigDown> producesAction() {
      return ActionDigDown.class;
    }
  }
}
