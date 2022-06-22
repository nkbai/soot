package soot.dotnet.instructions;

import soot.*;
import soot.dotnet.exceptions.NoExpressionInstructionException;
import soot.dotnet.members.method.DotnetBody;
import soot.dotnet.proto.ProtoIlInstructions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Execute Fault Block only if an exception was thrown, if try-block succeed no execution of fault-block
 */
public class CilTryFaultInstruction extends AbstractCilnstruction {
    public CilTryFaultInstruction(ProtoIlInstructions.IlInstructionMsg instruction, DotnetBody dotnetBody, CilBlock cilBlock) {
        super(instruction, dotnetBody, cilBlock);
    }

    @Override
    public void jimplify(Body jb) {
        // try block
        CilBlockContainer tryContainer = new CilBlockContainer(instruction.getTryBlock(), dotnetBody, CilBlockContainer.BlockContainerKind.TRY);
        Body tryContainerBlock = tryContainer.jimplify();

        // finally block
        CilBlockContainer faultBlockContainer = new CilBlockContainer(instruction.getFaultBlock(), dotnetBody, CilBlockContainer.BlockContainerKind.FAULT);
        Body faultBlockContainerBody = faultBlockContainer.jimplify();

        // add fault block to cases, where an exception was thrown
        tryContainerBlock.getLocals().addAll(faultBlockContainerBody.getLocals());
        // Store EndUnits of all traps to restore to the right endunit of trap, due to insert call
        Map<Trap, Unit> tmpTrapEnds = new HashMap<>();
        // begin at first handler, down to end of body - for, 'cause may no traps available
        for (Trap trap : tryContainerBlock.getTraps()) {
            tmpTrapEnds.put(trap, trap.getEndUnit());
            Unit handlerUnit = trap.getHandlerUnit();
            Iterator<Unit> iterator = tryContainerBlock.getUnits().iterator(handlerUnit);

            ArrayList<Unit> tmpUnits = new ArrayList<>();
            while (iterator.hasNext()) {
                Unit next = iterator.next();
                if (CilBlockContainer.isExitStmt(next))
                    tmpUnits.add(next);
            }
            for (Unit unit : tmpUnits) {
                faultBlockContainerBody.setMethod(new SootMethod("", new ArrayList<>(), RefType.v(""))); // Set dummy method
                Body cloneFaultBlock = (Body) faultBlockContainerBody.clone(true);
                tryContainerBlock.getUnits().insertBefore(cloneFaultBlock.getUnits(), unit);
                // tryContainerBlock.getLocals().addAll(cloneFaultBlock.getLocals());
                tryContainerBlock.getTraps().addAll(cloneFaultBlock.getTraps());
            }
            break;
        }
        // restore endunits to original one
        for (Map.Entry<Trap, Unit> trapMap : tmpTrapEnds.entrySet())
            trapMap.getKey().setEndUnit(trapMap.getValue());

        jb.getLocals().addAll(tryContainerBlock.getLocals());
        jb.getUnits().addAll(tryContainerBlock.getUnits());
        jb.getTraps().addAll(tryContainerBlock.getTraps());
    }

    @Override
    public Value jimplifyExpr(Body jb) {
        throw new NoExpressionInstructionException(instruction);
    }
}