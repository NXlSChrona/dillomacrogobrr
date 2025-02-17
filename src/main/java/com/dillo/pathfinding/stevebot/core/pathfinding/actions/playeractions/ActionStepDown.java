package com.dillo.pathfinding.stevebot.core.pathfinding.actions.playeractions;

import com.dillo.pathfinding.stevebot.core.data.blockpos.BaseBlockPos;
import com.dillo.pathfinding.stevebot.core.data.blockpos.FastBlockPos;
import com.dillo.pathfinding.stevebot.core.data.blocks.BlockUtils;
import com.dillo.pathfinding.stevebot.core.misc.Direction;
import com.dillo.pathfinding.stevebot.core.misc.ProcState;
import com.dillo.pathfinding.stevebot.core.pathfinding.actions.ActionCosts;
import com.dillo.pathfinding.stevebot.core.pathfinding.actions.ActionFactory;
import com.dillo.pathfinding.stevebot.core.pathfinding.actions.ActionObserver;
import com.dillo.pathfinding.stevebot.core.pathfinding.actions.ActionUtils;
import com.dillo.pathfinding.stevebot.core.pathfinding.nodes.Node;
import com.dillo.pathfinding.stevebot.core.pathfinding.nodes.NodeCache;
import com.dillo.pathfinding.stevebot.core.player.PlayerUtils;

public class ActionStepDown extends Action {

  private ActionStepDown(Node from, Node to, double cost) {
    super(from, to, cost);
  }

  @Override
  public String getActionName() {
    return "step-down";
  }

  @Override
  public String getActionNameExp() {
    return (
      this.getActionName() +
      (Direction.get(getFrom().getPos(), getTo().getPos(), true).diagonal ? "-diagonal" : "-straight")
    );
  }

  @Override
  public ProcState tick(boolean firstTick) {
    ActionObserver.tickAction(this.getActionNameExp());
    if (PlayerUtils.getMovement().moveTowards(getTo().getPos(), true)) {
      return ProcState.DONE;
    } else {
      return ProcState.EXECUTING;
    }
  }

  @Override
  public boolean isOnPath(BaseBlockPos position) {
    if (position.equals(getFrom().getPos()) || position.equals(getTo().getPos())) {
      return true;
    } else {
      return position.equals(getTo().getPosCopy().add(Direction.UP));
    }
  }

  private abstract static class StepDownActionFactory implements ActionFactory {

    ActionStepDown create(Node node, Direction direction, Result result) {
      // final ActionFactory.Result result = direction.diagonal ? checkDiagonal(node, direction) : checkStraight(node, direction);
      return new ActionStepDown(node, result.to, result.estimatedCost);
    }

    Result check(Node node, Direction direction) {
      if (direction.diagonal) {
        return checkDiagonal(node, direction);
      } else {
        return checkStraight(node, direction);
      }
    }

    Result checkStraight(Node node, Direction direction) {
      // check to-position
      final BaseBlockPos to = node.getPosCopy().add(direction.dx, -1, direction.dz);
      if (!BlockUtils.isLoaded(to)) {
        return Result.unloaded();
      }
      if (!ActionUtils.canStandAt(to, 3)) {
        return Result.invalid();
      }

      // check from-position
      if (!ActionUtils.canStandAt(node.getPos())) {
        return Result.invalid();
      }

      return Result.valid(direction, NodeCache.get(to), ActionCosts.get().STEP_DOWN_STRAIGHT);
    }

    Result checkDiagonal(Node node, Direction direction) {
      // check to-position
      final FastBlockPos to = node.getPosCopy().add(direction.dx, -1, direction.dz);
      if (!BlockUtils.isLoaded(to)) {
        return Result.unloaded();
      }
      if (!ActionUtils.canStandAt(to, 3)) {
        return Result.invalid();
      }

      // check from-position
      if (!ActionUtils.canStandAt(node.getPos())) {
        return Result.invalid();
      }

      // check diagonal
      Direction[] splitDirection = direction.split();
      final FastBlockPos p0 = node.getPosCopy().add(splitDirection[0].dx, 0, splitDirection[0].dz);
      final FastBlockPos p1 = node.getPosCopy().add(splitDirection[1].dx, 0, splitDirection[1].dz);

      boolean traversable0 = ActionUtils.canMoveThrough(p0) && BlockUtils.isLoaded(p0);
      boolean traversable1 = ActionUtils.canMoveThrough(p0) && BlockUtils.isLoaded(p1);
      boolean avoid0 = BlockUtils.avoidTouching(p0) || BlockUtils.avoidTouching(p0.add(Direction.UP));
      boolean avoid1 = BlockUtils.avoidTouching(p1) || BlockUtils.avoidTouching(p1.add(Direction.UP));

      if (ActionUtils.canStandAt(to) && (traversable0 || traversable1)) {
        if ((traversable0 && avoid1) || (traversable1 && avoid0)) {
          return Result.invalid();
        } else {
          return Result.valid(direction, NodeCache.get(to), ActionCosts.get().STEP_DOWN_DIAGONAL);
        }
      } else {
        return Result.invalid();
      }
    }
  }

  private abstract static class AbstractStepDownActionFactory extends StepDownActionFactory {

    @Override
    public Result check(Node node) {
      return check(node, getDirection());
    }

    @Override
    public Action createAction(Node node, Result result) {
      return create(node, getDirection(), result);
    }

    @Override
    public Class<ActionStepDown> producesAction() {
      return ActionStepDown.class;
    }
  }

  public static class StepDownFactoryNorth extends AbstractStepDownActionFactory {

    @Override
    public Direction getDirection() {
      return Direction.NORTH;
    }
  }

  public static class StepDownFactoryNorthEast extends AbstractStepDownActionFactory {

    @Override
    public Direction getDirection() {
      return Direction.NORTH_EAST;
    }
  }

  public static class StepDownFactoryEast extends AbstractStepDownActionFactory {

    @Override
    public Direction getDirection() {
      return Direction.EAST;
    }
  }

  public static class StepDownFactorySouthEast extends AbstractStepDownActionFactory {

    @Override
    public Direction getDirection() {
      return Direction.SOUTH_EAST;
    }
  }

  public static class StepDownFactorySouth extends AbstractStepDownActionFactory {

    @Override
    public Direction getDirection() {
      return Direction.SOUTH;
    }
  }

  public static class StepDownFactorySouthWest extends AbstractStepDownActionFactory {

    @Override
    public Direction getDirection() {
      return Direction.SOUTH_WEST;
    }
  }

  public static class StepDownFactoryWest extends AbstractStepDownActionFactory {

    @Override
    public Direction getDirection() {
      return Direction.WEST;
    }
  }

  public static class StepDownFactoryNorthWest extends AbstractStepDownActionFactory {

    @Override
    public Direction getDirection() {
      return Direction.NORTH_WEST;
    }
  }
}
