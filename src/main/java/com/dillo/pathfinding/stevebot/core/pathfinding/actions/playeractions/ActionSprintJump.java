package com.dillo.pathfinding.stevebot.core.pathfinding.actions.playeractions;

import com.dillo.pathfinding.stevebot.core.data.blockpos.BaseBlockPos;
import com.dillo.pathfinding.stevebot.core.data.blocks.BlockUtils;
import com.dillo.pathfinding.stevebot.core.misc.Direction;
import com.dillo.pathfinding.stevebot.core.misc.ProcState;
import com.dillo.pathfinding.stevebot.core.misc.StateMachine;
import com.dillo.pathfinding.stevebot.core.pathfinding.actions.ActionCosts;
import com.dillo.pathfinding.stevebot.core.pathfinding.actions.ActionFactory;
import com.dillo.pathfinding.stevebot.core.pathfinding.actions.ActionObserver;
import com.dillo.pathfinding.stevebot.core.pathfinding.actions.ActionUtils;
import com.dillo.pathfinding.stevebot.core.pathfinding.nodes.Node;
import com.dillo.pathfinding.stevebot.core.pathfinding.nodes.NodeCache;
import com.dillo.pathfinding.stevebot.core.player.PlayerUtils;

public class ActionSprintJump extends Action {

  private enum State {
    PREPARING,
    JUMPING,
    LANDING,
  }

  private enum Transition {
    PREPARATION_DONE,
    TOUCHED_GROUND,
  }

  private final StateMachine<State, Transition> stateMachine = new StateMachine<>();

  private ActionSprintJump(Node from, Node to, double cost) {
    super(from, to, cost);
    stateMachine.defineTransition(State.PREPARING, Transition.PREPARATION_DONE, State.JUMPING);
    stateMachine.defineTransition(State.JUMPING, Transition.TOUCHED_GROUND, State.LANDING);
  }

  @Override
  public void resetAction() {
    stateMachine.setState(State.PREPARING);
  }

  @Override
  public String getActionName() {
    return "sprint-jump";
  }

  @Override
  public ProcState tick(boolean firstTick) {
    ActionObserver.tickAction(this.getActionName());
    switch (stateMachine.getState()) {
      case PREPARING:
        {
          return tickPreparation();
        }
      case JUMPING:
        {
          return tickJump();
        }
      case LANDING:
        {
          return tickLanding();
        }
      default:
        {
          return ProcState.FAILED;
        }
    }
  }

  /**
   * Prepare everything before jumping
   */
  private ProcState tickPreparation() {
    if (
      PlayerUtils
        .getMovement()
        .moveTowardsSpeed(getFrom().getPos().getCenterX(), getFrom().getPos().getCenterZ(), 0.055)
    ) {
      stateMachine.fireTransition(Transition.PREPARATION_DONE);
    }
    return ProcState.EXECUTING;
  }

  /**
   * Move into position and jump.
   */
  private ProcState tickJump() {
    PlayerUtils.getMovement().moveTowards(getTo().getPos(), true);
    PlayerUtils.getInput().setSprint();
    final double distToCenter = BlockUtils.distToCenter(getFrom().getPos(), PlayerUtils.getPlayerPosition());
    if (distToCenter > 0.4) {
      PlayerUtils.getInput().setJump();
    }
    if (PlayerUtils.isOnGround() && PlayerUtils.getPlayerBlockPos().equals(getTo().getPos())) {
      stateMachine.fireTransition(Transition.TOUCHED_GROUND);
    }
    return ProcState.EXECUTING;
  }

  /**
   * After landing on the target block.
   */
  private ProcState tickLanding() {
    if (PlayerUtils.getMovement().moveTowards(getTo().getPos(), true)) {
      return ProcState.DONE;
    } else {
      return ProcState.EXECUTING;
    }
  }

  @Override
  public boolean isOnPath(BaseBlockPos position) {
    return position.getY() >= getFrom().getPos().getY();
  }

  private abstract static class SprintJumpActionFactory implements ActionFactory {

    ActionSprintJump create(Node node, Direction direction, Result result) {
      return new ActionSprintJump(node, result.to, result.estimatedCost);
    }

    Result check(Node node, Direction direction) {
      if (direction.diagonal) {
        return Result.invalid();
      }

      // check to-position
      final BaseBlockPos to = node.getPosCopy().add(direction.dx * 4, 0, direction.dz * 4);
      if (!BlockUtils.isLoaded(to)) {
        return Result.unloaded();
      }
      if (!ActionUtils.canJumpAt(to)) {
        return Result.invalid();
      }

      // check from-position
      if (!ActionUtils.canJumpAt(node.getPos())) {
        return Result.invalid();
      }

      // check gap
      for (int i = 0; i < 3; i++) {
        final BaseBlockPos gap = node.getPosCopy().add(direction.dx * (i + 1), 0, direction.dz * (i + 1));
        if (i == 2) {
          if (!BlockUtils.canWalkThrough(gap.copyAsFastBlockPos().add(0, 3, 0))) {
            return Result.invalid();
          }
          if (!ActionUtils.canJump(gap)) {
            return Result.invalid();
          }
        } else {
          if (!ActionUtils.canJumpThrough(gap)) {
            return Result.invalid();
          }
        }
      }

      return Result.valid(Direction.NONE, NodeCache.get(to), ActionCosts.get().SPRINT_JUMP);
    }
  }

  private abstract static class AbstractSprintJumpActionFactory extends SprintJumpActionFactory {

    @Override
    public Result check(Node node) {
      return check(node, getDirection());
    }

    @Override
    public Action createAction(Node node, Result result) {
      return create(node, getDirection(), result);
    }

    @Override
    public Class<ActionPillarUp> producesAction() {
      return ActionPillarUp.class;
    }
  }

  public static class SprintJumpFactoryNorth extends AbstractSprintJumpActionFactory {

    @Override
    public Direction getDirection() {
      return Direction.NORTH;
    }
  }

  public static class SprintJumpFactoryEast extends AbstractSprintJumpActionFactory {

    @Override
    public Direction getDirection() {
      return Direction.EAST;
    }
  }

  public static class SprintJumpFactorySouth extends AbstractSprintJumpActionFactory {

    @Override
    public Direction getDirection() {
      return Direction.SOUTH;
    }
  }

  public static class SprintJumpFactoryWest extends AbstractSprintJumpActionFactory {

    @Override
    public Direction getDirection() {
      return Direction.WEST;
    }
  }
}
