/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.bc2ir;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.NoSuchElementException;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.BytecodeStream;
import org.jikesrvm.classloader.ExceptionHandlerMap;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.OperationNotImplementedException;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.ExceptionHandlerBasicBlock;
import org.jikesrvm.compilers.opt.ir.ExceptionHandlerBasicBlockBag;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TypeOperand;

/**
 * A somewhat complex subtask of IR generation is to discover and maintain
 * the set of basic blocks that are being generated.
 * This class encapsulates that functionality.
 * The backing data store is a red/black tree, but there are a number of
 * very specialized operations that are performed during search/insertion
 * so we roll our own instead of using one from the standard library.
 */
final class BBSet implements IRGenOptions {
  /** root of the backing red/black tree*/
  private BasicBlockLE root;

  /**
   * is it the case that we can ignore JSR processing because
   * BC2IR has not yet generated a JSR bytecode in this method?
   */
  private boolean noJSR = true;

  /** entry block of the CFG */
  private final BasicBlockLE entry;

  /** associated generation context */
  private final GenerationContext gc;

  /** associated bytecodes */
  private final BytecodeStream bcodes;

  // Fields to support generation/identification of catch blocks
  /** Start bytecode index for each exception handler ranges */
  private int[] startPCs;

  /** End bytecode index for each exception handler range */
  private int[] endPCs;

  /** Start bytecode index of each the exception handler */
  private int[] handlerPCs;

  /** Type of exception handled by each exception handler range. */
  private TypeOperand[] exceptionTypes;

  /**
   * Initialize the BBSet to handle basic block generation for the argument
   * generation context and bytecode info.
   * @param gc the generation context to generate blocks for
   * @param bcodes the bytecodes of said generation context
   * @param localState the state of the local variables for the block
   *                   beginning at bytecode 0.
   */
  BBSet(GenerationContext gc, BytecodeStream bcodes, Operand[] localState) {
    this.gc = gc;
    this.bcodes = bcodes;

    // Set up internal data structures to deal with exception handlers
    parseExceptionTables();

    // Create the entry block, setting root as a sideffect.
    entry = _createBBLE(0, null, null, false);
    entry.setStackKnown();
    entry.copyIntoLocalState(localState);
  }

  /** return the entry BBLE */
  BasicBlockLE getEntry() { return entry; }

  /**
   * Notify the BBSet that BC2IR has encountered a JSR bytecode.
   * This enables more complex logic in getOrCreateBlock to drive
   * the basic block specialization that is the key to JSR inlining.
   */
  void seenJSR() { noJSR = false; }

  /**
   * Return a enumeration of the BasicBlockLE's currently in the BBSet.
   */
  Enumeration<BasicBlockLE> contents() {
    return TreeEnumerator.enumFromRoot(root);
  }

  /**
   * Gets the bytecode index of the block in the set which has the
   * next-higher bytecode index.
   * Returns bcodes.length() if x is currently the block with the highest
   * starting bytecode index.
   * @param x basic block to start at.
   */
  int getNextBlockBytecodeIndex(BasicBlockLE x) {
    BasicBlockLE nextBBLE = getSuccessor(x, x.low);
    return nextBBLE == null ? bcodes.length() : nextBBLE.low;
  }

  /**
   * Finds the next ungenerated block, starting at the argument
   * block and searching forward, wrapping around to the beginning.
   * If all blocks are generated, it returns null.
   * @param start the basic block at which to start looking.
   */
  BasicBlockLE getNextEmptyBlock(BasicBlockLE start) {
    if (DBG_BBSET) db("getting the next empty block after " + start);

    // Look for an ungenerated block after start.
    BBSet.TreeEnumerator e = TreeEnumerator.enumFromNode(start);
    while (e.hasMoreElements()) {
      BasicBlockLE block = e.next();
      if (DBG_BBSET) {
        db("Considering block " + block + " " + block.genState());
      }
      if (block.isReadyToGenerate()) {
        if (DBG_BBSET) db("block " + block + " is not yet generated");
        return block;
      }
    }

    // There were none. Start looking from the beginning.
    if (DBG_BBSET) db("at end of bytecodes, restarting at beginning");
    e = TreeEnumerator.enumFromRoot(root);
    while (true) {
      BasicBlockLE block = e.next();
      if (block == start) {
        if (DBG_BBSET) db("wrapped around, no more empty blocks");
        return null;
      }
      if (DBG_BBSET) {
        db("Considering block " + block + " " + block.genState());
      }
      if (block.isReadyToGenerate()) {
        if (DBG_BBSET) db("block " + block + " is not yet generated");
        return block;
      }
    }
  }

  /**
   * Get or create a block at the specified target.
   * If simStack is non-null, rectifies stack state with target stack state.
   * If simLocals is non-null, rectifies local state with target local state.
   * Any instructions needed to rectify stack/local state are appended to
   * from.
   *
   * @param target target index
   * @param from the block from which control is being transfered
   *                  and to which rectification instructions are added.
   * @param simStack stack state to rectify, or null
   * @param simLocals local state to rectify, or null
   */
  BasicBlockLE getOrCreateBlock(int target, BasicBlockLE from, OperandStack simStack, Operand[] simLocals) {
    if (DBG_BB || BC2IR.DBG_SELECTED) {
      db("getting block " +
         target +
         ", match stack: " +
         (simStack != null) +
         " match locals: " +
         (simLocals != null));
    }
    return getOrCreateBlock(root, true, target, from, simStack, simLocals);
  }

  /**
   * Mark a previously generated block for regeneration.
   * We define this method here so that in the future
   * we can implement a more efficient getNextEmptyBlock that
   * (1) avoids generating lots of blocks when a CFG predecessor has a
   * pending regeneration and (2) avoids the scan through all blocks when
   * there are no more blocks left to generate.
   */
  private void markBlockForRegeneration(BasicBlockLE p) {
    if (DBG_REGEN) db("marking " + p + " for regeneration");
    if (p.fallThrough != null && p.fallThrough instanceof InliningBlockLE) {
      // if the fallthrough out edge of this block is an
      // InlineMethodBasicBlock, then the inlined method must also be
      // regenerated.  In preparation for this, we must delete all out
      // edges from the inlined method to the caller.
      // (These arise from thrown/caught exceptions.)
      InliningBlockLE imbb = (InliningBlockLE) p.fallThrough;
      imbb.deleteAllOutEdges();
    }
    // discard any "real" instructions in the block
    if (!p.block.isEmpty()) {
      p.block.discardInstructions();
    }
    p.setSelfRegen();
    p.clearGenerated();
    p.fallThrough = null;
    // If p had a non-empty stack on entry, we need to go through it
    // and copy all of its operands (they may point to instructions
    // we just blew away, but then again they may not (may not be in p),
    // so we can't simply null out the instruction field);
    if (p.stackState != null) {
      int i = p.stackState.getSize();
      while (i-- > 0) {
        Operand op = p.stackState.getFromTop(i);
        p.stackState.replaceFromTop(i, op.copy());
      }
    }
  }

  /**
   * Rectify the given stack state with the state contained in the given
   * BBLE, adding the necessary move instructions to the end of the given
   * basic block to make register numbers agree and rectify mis-matched constants.
   * <p>
   * @param block basic block to append move instructions to
   * @param stack stack to copy
   * @param p BBLE to copy stack state into
   */
  void rectifyStacks(BasicBlock block, OperandStack stack, BasicBlockLE p) {
    if (stack == null || stack.isEmpty()) {
      if (VM.VerifyAssertions) VM._assert(p.stackState == null);
      if (!p.isStackKnown()) {
        p.setStackKnown();
      }
      if (DBG_STACK || BC2IR.DBG_SELECTED) {
        db("Rectified empty expression stack into " + p + "(" + p.block + ")");
      }
      return;
    }
    boolean generated = p.isGenerated();
    // (1) Rectify the stacks.
    if (!p.isStackKnown()) {
      // First time we reached p. Thus, its expression stack
      // is implicitly top and the meet degenerates to a copy operation
      // with possibly some register renaming.
      // (We need to ensure that non-local registers appear at
      // most once on each expression stack).
      if (DBG_STACK || BC2IR.DBG_SELECTED) {
        db("First stack rectifiction for " + p + "(" + p.block + ") simply saving");
      }
      if (VM.VerifyAssertions) VM._assert(p.stackState == null);
      p.stackState = new OperandStack(stack.getCapacity());
      for (int i = stack.getSize() - 1; i >= 0; i--) {
        Operand op = stack.getFromTop(i);
        if (op == BC2IR.DUMMY) {
          p.stackState.push(BC2IR.DUMMY);
        } else if (op instanceof RegisterOperand) {
          RegisterOperand rop = op.asRegister();
          if (rop.getRegister().isLocal()) {
            RegisterOperand temp = gc.temps.makeTemp(rop);
            temp.setInheritableFlags(rop);
            BC2IR.setGuard(temp, BC2IR.getGuard(rop));
            Instruction move = Move.create(IRTools.getMoveOp(rop.getType()), temp, rop.copyRO());
            move.bcIndex = BC2IR.RECTIFY_BCI;
            move.position = gc.inlineSequence;
            block.appendInstructionRespectingTerminalBranch(move);
            p.stackState.push(temp.copy());
            if (DBG_STACK || BC2IR.DBG_SELECTED) {
              db("Inserted " + move + " into " + block + " to rename local");
            }
          } else {
            p.stackState.push(rop.copy());
          }
        } else {
          p.stackState.push(op.copy());
        }
      }
      p.setStackKnown();
    } else {
      // A real rectification.
      // We need to update mergedStack such that
      // mergedStack[i] = meet(mergedStack[i], stack[i]).
      if (DBG_STACK || BC2IR.DBG_SELECTED) db("rectifying stacks");
      try {
        if (VM.VerifyAssertions) {
          VM._assert(stack.getSize() == p.stackState.getSize());
        }
      } catch (NullPointerException e) {
        System.err.println("stack size " + stack.getSize());
        System.err.println(stack);
        System.err.println(p.stackState);
        System.err.println(gc.method.toString());
        block.printExtended();
        p.block.printExtended();
        throw e;
      }
      for (int i = 0; i < stack.getSize(); ++i) {
        Operand sop = stack.getFromTop(i);
        Operand mop = p.stackState.getFromTop(i);
        if ((sop == BC2IR.DUMMY) || (sop instanceof ReturnAddressOperand)) {
          if (VM.VerifyAssertions) VM._assert(mop.similar(sop));
          continue;
        } else if (sop.isConstant() || mop.isConstant()) {
          if (mop.similar(sop)) {
            continue; // constants are similar; so we don't have to do anything.
          }
          // sigh. Non-similar constants.
          if (mop.isConstant()) {
            // Insert move instructions in all predecessor
            // blocks except 'block' to move mop into a register.
            RegisterOperand mopTmp = gc.temps.makeTemp(mop);
            if (DBG_STACK || BC2IR.DBG_SELECTED) db("Merged stack has constant operand " + mop);
            for (Enumeration<BasicBlock> preds = p.block.getIn(); preds.hasMoreElements();) {
              BasicBlock pred = preds.nextElement();
              if (pred == block) continue;
              injectMove(pred, mopTmp.copyRO(), mop.copy());
            }
            p.stackState.replaceFromTop(i, mopTmp.copy());
            if (generated) {
              if (DBG_STACK || BC2IR.DBG_SELECTED) {
                db("\t...forced to regenerate " + p + " (" + p.block + ") because of this");
              }
              markBlockForRegeneration(p);
              generated = false;
              p.block.deleteOut();
              if (DBG_CFG || BC2IR.DBG_SELECTED) db("Deleted all out edges of " + p.block);
            }
            mop = mopTmp;
          }
          if (sop.isConstant()) {
            // Insert move instruction into block.
            RegisterOperand sopTmp = gc.temps.makeTemp(sop);
            if (DBG_STACK || BC2IR.DBG_SELECTED) db("incoming stack has constant operand " + sop);
            injectMove(block, sopTmp, sop);
            sop = sopTmp.copyRO();
          }
        }

        // sop and mop are RegisterOperands (either originally or because
        // we forced them to be above due to incompatible constants.
        RegisterOperand rsop = sop.asRegister();
        RegisterOperand rmop = mop.asRegister();
        if (rmop.getRegister() != rsop.getRegister()) {
          // must insert move at end of block to get register #s to match
          RegisterOperand temp = rsop.copyRO();
          temp.setRegister(rmop.getRegister());
          injectMove(block, temp, rsop.copyRO());
        }
        Operand meet = Operand.meet(rmop, rsop, rmop.getRegister());
        if (DBG_STACK || BC2IR.DBG_SELECTED) db("Meet of " + rmop + " and " + rsop + " is " + meet);
        if (meet != rmop) {
          if (generated) {
            if (DBG_STACK || BC2IR.DBG_SELECTED) {
              db("\t...forced to regenerate " + p + " (" + p.block + ") because of this");
            }
            markBlockForRegeneration(p);
            generated = false;
            p.block.deleteOut();
            if (DBG_CFG || BC2IR.DBG_SELECTED) db("Deleted all out edges of " + p.block);
          }
          p.stackState.replaceFromTop(i, meet);
        }
      }
    }
  }

  private void injectMove(BasicBlock block, RegisterOperand res, Operand val) {
    Instruction move = Move.create(IRTools.getMoveOp(res.getType()), res, val);
    move.bcIndex = BC2IR.RECTIFY_BCI;
    move.position = gc.inlineSequence;
    block.appendInstructionRespectingTerminalBranch(move);
    if (DBG_STACK || BC2IR.DBG_SELECTED) {
      db("Inserted " + move + " into " + block);
    }
  }

  /**
   * Rectify the given local variable state with the local variable state
   * stored in the given BBLE.
   *
   * @param localState local variable state to rectify
   * @param p target BBLE to rectify state to
   */
  void rectifyLocals(Operand[] localState, BasicBlockLE p) {
    if (!p.isLocalKnown()) {
      if (DBG_LOCAL || BC2IR.DBG_SELECTED) {
        db("rectifying with heretofore unknown locals, changing to save");
      }
      p.copyIntoLocalState(localState);
      return;
    }
    if (DBG_LOCAL || BC2IR.DBG_SELECTED) db("rectifying current local state with " + p);
    boolean generated = p.isGenerated();
    Operand[] incomingState = localState;
    Operand[] presentState = p.localState;
    if (VM.VerifyAssertions) {
      VM._assert(incomingState.length == presentState.length);
    }
    for (int i = 0, n = incomingState.length; i < n; ++i) {
      Operand pOP = presentState[i];
      Operand iOP = incomingState[i];
      if (pOP == iOP) {
        if (DBG_LOCAL || BC2IR.DBG_SELECTED) {
          db("local states have the exact same operand " + pOP + " for local " + i);
        }
      } else {
        boolean untyped = (pOP == null || pOP == BC2IR.DUMMY || pOP instanceof ReturnAddressOperand);
        Operand mOP = Operand.meet(pOP, iOP, untyped ? null : gc.localReg(i, pOP.getType()));
        if (DBG_LOCAL || BC2IR.DBG_SELECTED) db("Meet of " + pOP + " and " + iOP + " is " + mOP);
        if (mOP != pOP) {
          if (generated) {
            if (DBG_LOCAL || BC2IR.DBG_SELECTED) {
              db("\t...forced to regenerate " + p + " (" + p.block + ") because of this");
            }
            markBlockForRegeneration(p);
            generated = false;
            p.block.deleteOut();
            if (DBG_CFG || BC2IR.DBG_SELECTED) db("Deleted all out edges of " + p.block);
          }
          presentState[i] = mOP;
        }
      }
    }
  }

  /**
   * Do a final pass over the generated basic blocks to create
   * the initial code ordering. All blocks generated for the method
   * will be inserted after gc.prologue.<p>
   *
   * NOTE: Only some CFG edges are created here.....
   * we're mainly just patching together a code linearization.
   */
  void finalPass(boolean inlinedSomething) {
    BBSet.TreeEnumerator e = TreeEnumerator.enumFromRoot(root);
    BasicBlock cop = gc.prologue;
    BasicBlockLE curr = getEntry();
    BasicBlockLE next = null;
    top:
    while (true) {
      // Step 0: If curr is the first block in a catch block,
      // inject synthetic entry block too.
      if (curr instanceof HandlerBlockLE) {
        // tell our caller that we actually put a handler in the final CFG.
        gc.generatedExceptionHandlers = true;
        HandlerBlockLE hcurr = (HandlerBlockLE) curr;
        if (DBG_FLATTEN) {
          db("injecting handler entry block " + hcurr.entryBlock + " before " + hcurr);
        }
        gc.cfg.insertAfterInCodeOrder(cop, hcurr.entryBlock);
        cop = hcurr.entryBlock;
      }
      // Step 1: Insert curr in the code order (after cop, updating cop).
      if (DBG_FLATTEN) db("flattening: " + curr + " (" + curr.block + ")");
      curr.setInCodeOrder();
      gc.cfg.insertAfterInCodeOrder(cop, curr.block);
      cop = curr.block;
      if (DBG_FLATTEN) {
        db("Current Code order for " + gc.method + "\n");
        for (BasicBlock bb = gc.prologue; bb != null; bb = (BasicBlock) bb.getNext()) {
          VM.sysWrite(bb + "\n");
        }
      }
      // Step 1.1 Sometimes (rarely) there will be an inscope
      // exception handler that wasn't actually generated.  If this happens,
      // make a new, filtered EHBBB to avoid later confusion.
      if (curr.handlers != null) {
        int notGenerated = 0;
        for (HandlerBlockLE handler : curr.handlers) {
          if (!handler.isGenerated()) {
            if (DBG_EX || DBG_FLATTEN) {
              db("Will remove unreachable handler " + handler + " from " + curr);
            }
            notGenerated++;
          }
        }
        if (notGenerated > 0) {
          if (notGenerated == curr.handlers.length) {
            if (DBG_EX || DBG_FLATTEN) {
              db("No (local) handlers were actually reachable for " + curr + "; setting to caller");
            }
            curr.block.exceptionHandlers = curr.block.exceptionHandlers.getCaller();
          } else {
            ExceptionHandlerBasicBlock[] nlh =
                new ExceptionHandlerBasicBlock[curr.handlers.length - notGenerated];
            for (int i = 0, j = 0; i < curr.handlers.length; i++) {
              if (curr.handlers[i].isGenerated()) {
                nlh[j++] = curr.handlers[i].entryBlock;
              } else {
                if (VM.VerifyAssertions) {
                  VM._assert(curr.handlers[i].entryBlock.hasZeroIn(), "Non-generated handler with CFG edges");
                }
              }
            }
            curr.block.exceptionHandlers =
                new ExceptionHandlerBasicBlockBag(nlh, curr.block.exceptionHandlers.getCaller());
          }
        }
      }
      // Step 2: Identify the next basic block to add to the code order.
      // curr wants to fallthrough to an inlined method.
      // Inject the entire inlined CFG in the code order.
      // There's some fairly complicated coordination between this code,
      // GenerationContext, and maybeInlineMethod.  Sorry, but you'll
      // have to take a close look at all of these to see how it
      // all fits together....--dave
      if (curr.fallThrough != null && curr.fallThrough instanceof InliningBlockLE) {
        InliningBlockLE icurr = (InliningBlockLE) curr.fallThrough;
        BasicBlock forw = cop.nextBasicBlockInCodeOrder();
        BasicBlock calleeEntry = icurr.gc.cfg.firstInCodeOrder();
        BasicBlock calleeExit = icurr.gc.cfg.lastInCodeOrder();
        gc.cfg.breakCodeOrder(cop, forw);
        gc.cfg.linkInCodeOrder(cop, icurr.gc.cfg.firstInCodeOrder());
        gc.cfg.linkInCodeOrder(icurr.gc.cfg.lastInCodeOrder(), forw);
        if (DBG_CFG || BC2IR.DBG_SELECTED) {
          db("Added CFG edge from " + cop + " to " + calleeEntry);
        }
        if (icurr.epilogueBBLE != null) {
          if (DBG_FLATTEN) {
            db("injected " + icurr + " between " + curr + " and " + icurr.epilogueBBLE.fallThrough);
          }
          if (VM.VerifyAssertions) {
            VM._assert(icurr.epilogueBBLE.block == icurr.gc.cfg.lastInCodeOrder());
          }
          curr = icurr.epilogueBBLE;
          cop = curr.block;
        } else {
          if (DBG_FLATTEN) db("injected " + icurr + " after " + curr);
          curr = icurr;
          cop = calleeExit;
        }
      }
      next = curr.fallThrough;
      if (DBG_FLATTEN && next == null) {
        db(curr + " has no fallthrough case, getting next block");
      }
      if (next != null) {
        if (DBG_CFG || BC2IR.DBG_SELECTED) {
          db("Added CFG edge from " + curr.block + " to " + next.block);
        }
        if (next.isInCodeOrder()) {
          if (DBG_FLATTEN) {
            db("fallthrough " + next + " is already flattened, adding goto");
          }
          curr.block.appendInstruction(next.block.makeGOTO());
          // set next to null to indicate no "real" fall through
          next = null;
        }
      }
      if (next == null) {
        // Can't process fallthroughblock, so get next BBLE from enumeration
        while (true) {
          if (!e.hasMoreElements()) {
            // all done.
            if (DBG_FLATTEN) db("no more blocks! all done");
            break top;
          }
          next = e.next();
          if (DBG_FLATTEN) db("looking at " + next);
          if (!next.isGenerated()) {
            if (DBG_FLATTEN) db("block " + next + " was not generated");
            continue;
          }
          if (!next.isInCodeOrder()) {
            break;
          }
        }
        if (DBG_FLATTEN) db("found unflattened block: " + next);
      }
      curr = next;
    }
    // If the epilogue was unreachable, remove it from the code order and cfg
    // and set gc.epilogue to null.
    boolean removedSomethingFromCodeOrdering = inlinedSomething;
    if (gc.epilogue.hasZeroIn()) {
      if (DBG_FLATTEN || DBG_CFG) {
        db("Deleting unreachable epilogue " + gc.epilogue);
      }
      gc.cfg.removeFromCodeOrder(gc.epilogue);
      removedSomethingFromCodeOrdering = true;

      // remove the node from the graph AND adjust its edge info
      gc.epilogue.remove();
      gc.epilogue.deleteIn();
      gc.epilogue.deleteOut();
      if (VM.VerifyAssertions) VM._assert(gc.epilogue.hasZeroOut());
      gc.epilogue = null;
    }
    // if gc has an unlockAndRethrow block that was not used, then remove it
    if (gc.unlockAndRethrow != null && gc.unlockAndRethrow.hasZeroIn()) {
      gc.cfg.removeFromCFGAndCodeOrder(gc.unlockAndRethrow);
      removedSomethingFromCodeOrdering = true;
      gc.enclosingHandlers.remove(gc.unlockAndRethrow);
    }
    // if we removed a basic block then we should compact the node numbering
    if (removedSomethingFromCodeOrdering) {
      gc.cfg.compactNodeNumbering();
    }

    if (DBG_FLATTEN) {
      db("Current Code order for " + gc.method + "\n");
      for (BasicBlock bb = gc.prologue; bb != null; bb = (BasicBlock) bb.getNext()) {
        bb.printExtended();
      }
    }
    if (DBG_FLATTEN) {
      db("Final CFG for " + gc.method + "\n");
      gc.cfg.printDepthFirst();
    }
  }

  //////////////////////////////////////////
  // Gory implementation details of BBSet //
  //////////////////////////////////////////

  /**
   * Print a debug string to the sysWrite stream.
   * @param val string to print
   */
  private void db(String val) {
    VM.sysWrite("IRGEN " + bcodes.getDeclaringClass() + "." + gc.method.getName() + ":" + val + "\n");
  }

  /**
   * Initialize the global exception handler arrays for the method.<p>
   */
  private void parseExceptionTables() {
    ExceptionHandlerMap eMap = gc.method.getExceptionHandlerMap();
    if (DBG_EX) db("\texception handlers for " + gc.method + ": " + eMap);
    if (eMap == null) return;  // method has no exception handling ranges.
    startPCs = eMap.getStartPC();
    endPCs = eMap.getEndPC();
    handlerPCs = eMap.getHandlerPC();
    int numExceptionHandlers = startPCs.length;
    exceptionTypes = new TypeOperand[numExceptionHandlers];
    for (int i = 0; i < numExceptionHandlers; i++) {
      exceptionTypes[i] = new TypeOperand(eMap.getExceptionType(i));
      if (DBG_EX) db("\t\t[" + startPCs[i] + "," + endPCs[i] + "] " + eMap.getExceptionType(i));
    }
  }

  /**
   * Initialize bble's handlers array based on startPCs/endPCs.
   * In the process, new HandlerBlockLE's may be created
   * (by the call to getOrCreateBlock). <p>
   * PRECONDITION: bble.low and bble.max have already been correctly
   * set to reflect the invariant that a basic block is in exactly one
   * "handler range."<p>
   * Also initializes bble.block.exceptionHandlers.
   */
  private void initializeExceptionHandlers(BasicBlockLE bble, Operand[] simLocals) {
    if (startPCs != null) {
      HashSet<TypeReference> caughtTypes = new HashSet<TypeReference>();
      for (int i = 0; i < startPCs.length; i++) {
        TypeReference caughtType = exceptionTypes[i].getTypeRef();
        if (bble.low >= startPCs[i] && bble.max <= endPCs[i] && !caughtTypes.contains(caughtType)) {
          // bble's basic block is contained within this handler's range.
          HandlerBlockLE eh = (HandlerBlockLE) getOrCreateBlock(handlerPCs[i], bble, null, simLocals);
          if (DBG_EX) db("Adding handler " + eh + " to " + bble);
          caughtTypes.add(caughtType);
          bble.addHandler(eh);
        }
      }
    }
    if (bble.handlers != null) {
      ExceptionHandlerBasicBlock[] ehbbs = new ExceptionHandlerBasicBlock[bble.handlers.length];
      for (int i = 0; i < bble.handlers.length; i++) {
        ehbbs[i] = bble.handlers[i].entryBlock;
      }
      bble.block.exceptionHandlers = new ExceptionHandlerBasicBlockBag(ehbbs, gc.enclosingHandlers);
    } else {
      bble.block.exceptionHandlers = gc.enclosingHandlers;
    }
  }

  /**
   * Given a starting bytecode index, find the greatest bcIndex that
   * is still has the same inscope exception handlers.
   * @param bcIndex the start bytecode index
   */
  private int exceptionEndRange(int bcIndex) {
    int max = bcodes.length();
    if (startPCs != null) {
      for (int spc : startPCs) {
        if (bcIndex < spc && max > spc) {
          max = spc;
        }
      }
      for (int epc : endPCs) {
        if (bcIndex < epc && max > epc) {
          max = epc;
        }
      }
    }
    return max;
  }

  /**
   * We specialize basic blocks with respect to the return addresses
   * they have on their expression stack and/or in their local variables
   * on entry to the block. This has the effect of inlining the
   * subroutine body at all of the JSR sites that invoke it.
   * This is the key routine: it determines whether or not the
   * argument simState (stack and locals) contains compatible
   * return addresses as the candidate BasicBlockLE.
   * <p>
   * The main motivation for inlining away all JSR's is that it eliminates
   * the "JSR problem" for type accurate GC.  It is also simpler to
   * implement and arguably results in more efficient generated code
   * (assuming that we don't get horrific code bloat).
   * To deal with the code bloat, we detect excessive code duplication and
   * stop IR generation (bail out to the baseline compiler).
   *
   * @param simStack  The expression stack to match
   * @param simLocals The local variables to match
   * @param candBBLE  The candidate BaseicBlockLE
   */
  private boolean matchingJSRcontext(OperandStack simStack, Operand[] simLocals, BasicBlockLE candBBLE) {
    if (DBG_INLINE_JSR) {
      db("Matching JSR context of argument stack/locals against " + candBBLE);
    }

    int numRA = 0;
    if (simStack != null && candBBLE.isStackKnown()) {
      for (int i = simStack.getSize() - 1; i >= 0; i--) {
        Operand op = simStack.getFromTop(i);
        if (op instanceof ReturnAddressOperand) {
          if (numRA++ > MAX_RETURN_ADDRESSES) {
            throw new OperationNotImplementedException("Too many subroutines");
          }
          if (DBG_INLINE_JSR) db("simStack operand " + i + " is " + op);
          Operand cop = candBBLE.stackState.getFromTop(i);
          if (!Operand.conservativelyApproximates(cop, op)) {
            if (DBG_INLINE_JSR) db("Not Matching: " + cop + " and " + op);
            return false;
          } else {
            if (DBG_INLINE_JSR) db("operand " + cop + " is compatible with " + op);
          }
        }
      }
    }

    if (simLocals != null && candBBLE.isLocalKnown()) {
      for (int i = 0; i < simLocals.length; i++) {
        Operand op = simLocals[i];
        if (op instanceof ReturnAddressOperand) {
          if (numRA++ > MAX_RETURN_ADDRESSES) {
            throw new OperationNotImplementedException("Too many subroutines");
          }
          if (DBG_INLINE_JSR) db("simLocal " + i + " is " + op);
          Operand cop = candBBLE.localState[i];
          if (!Operand.conservativelyApproximates(cop, op)) {
            if (DBG_INLINE_JSR) db("Not Matching: " + cop + " and " + op);
            return false;
          } else {
            if (DBG_INLINE_JSR) db("operand " + cop + " is compatible with " + op);
          }
        }
      }
    }

    if (DBG_INLINE_JSR) db("Found " + candBBLE + " to be compatible");
    return true;
  }

  /**
   * Get or create a block at the specified target.
   * If simStack is non-null, rectifies stack state with target stack state.
   * If simLocals is non-null, rectifies local state with target local state.
   * Any instructions needed to rectify stack/local state are appended to
   * from.
   * As blocks are created, they are added to the red/black tree below x.
   *
   * @param x starting node for search.
   * @param shouldCreate should we create the block if we run off the tree?
   * @param target target index
   * @param from the block from which control is being transfered
   *                  and to which rectification instructions are added.
   * @param simStack stack state to rectify, or {@code null}
   * @param simLocals local state to rectify, or {@code null}
   */
  private BasicBlockLE getOrCreateBlock(BasicBlockLE x, boolean shouldCreate, int target, BasicBlockLE from,
                                        OperandStack simStack, Operand[] simLocals) {
    if (target < x.low) {
      if (x.left == null) {
        return condCreateAndInit(x, shouldCreate, target, from, simStack, simLocals, true);
      } else {
        if (DBG_BBSET) db("following left branch from " + x + " to " + x.left);
        return getOrCreateBlock(x.left, shouldCreate, target, from, simStack, simLocals);
      }
    } else if (target > x.low) {
      if ((x.low < target) && (target <= x.high)) {
        // the target points to the middle of x; mark x for regen
        if (DBG_BBSET) db("target points to middle of " + x);
        markBlockForRegeneration(x);
        x.high = x.low;
        x.block.deleteOut();
        if (DBG_CFG || BC2IR.DBG_SELECTED) db("Deleted all out edges of " + x.block);
      }
      if (x.right == null) {
        return condCreateAndInit(x, shouldCreate, target, from, simStack, simLocals, false);
      } else {
        if (DBG_BBSET) db("following right branch from " + x + " to " + x.right);
        return getOrCreateBlock(x.right, shouldCreate, target, from, simStack, simLocals);
      }
    } else {
      // found a basic block at the target bytecode index.
      if (noJSR || matchingJSRcontext(simStack, simLocals, x)) {
        if (DBG_BBSET) db("found block " + x + " (" + x.block + ")");
        if (simStack != null) rectifyStacks(from.block, simStack, x);
        if (simLocals != null) rectifyLocals(simLocals, x);
        return x;
      }
      if (DBG_BBSET) db("found block " + x + ", but JSR context didn't match");
      if (x.left == null) {
        if (x.right == null) {
          return condCreateAndInit(x, shouldCreate, target, from, simStack, simLocals, true);
        } else {
          if (DBG_BBSET) {
            db(x + " has only right child, continuing down that branch");
          }
          return getOrCreateBlock(x.right, shouldCreate, target, from, simStack, simLocals);
        }
      } else {
        if (x.right == null) {
          if (DBG_BBSET) {
            db(x + " has only left child, continuing down that branch");
          }
          return getOrCreateBlock(x.left, shouldCreate, target, from, simStack, simLocals);
        } else {
          if (DBG_BBSET) {
            db(x + " has two children, searching left branch first");
          }
          BasicBlockLE bble = getOrCreateBlock(x.left, false, target, from, simStack, simLocals);
          if (bble != null) {
            return bble;
          } else {
            if (DBG_BBSET) {
              db("didn't find " + target + " on left branch, continuing down right branch");
            }
            return getOrCreateBlock(x.right, shouldCreate, target, from, simStack, simLocals);
          }
        }
      }
    }
  }

  /**
   * Conditionally create a block at the specified target as a child of x.
   * If simStack is non-{@code null}, rectifies stack state with target stack state.
   * If simLocals is non-{@code null}, rectifies local state with target local state.
   * Any instructions needed to rectify stack/local state are appended to
   * from.
   *
   * @param x starting node for search.
   * @param shouldCreate should we create the block if we run off the tree?
   * @param target target index
   * @param from the block from which control is being transfered
   *                  and to which rectification instructions are added.
   * @param simStack stack state to rectify, or {@code null}
   * @param simLocals local state to rectify, or {@code null}
   * @param left are we creating a left child of parent?
   * @return the newly created block, or {@code null} if !shouldCreate
   */
  private BasicBlockLE condCreateAndInit(BasicBlockLE x, boolean shouldCreate, int target, BasicBlockLE from,
                                         OperandStack simStack, Operand[] simLocals, boolean left) {
    BasicBlockLE bble = null;
    if (shouldCreate) {
      bble = _createBBLE(target, simLocals, x, left);
      if (simStack != null) {
        rectifyStacks(from.block, simStack, bble);
      }
      if (simLocals != null) {
        bble.copyIntoLocalState(simLocals);
      }
    }
    return bble;
  }

  /**
   * Allocate a new BBLE at the given bcIndex.
   * If bcIndex is the start of an handler block,
   * then a HandlerBlockLE is created.
   * After the BBLE is created, its handlers data structure is initialized
   * (which may cause other blocks to be created).
   * @param bcIndex the bytecode index at which the block should be created.
   * @param simLocals the localState to pass (via initializeExceptionHandler)to
   *                  to getOrCreateBlock if we need to create BBLEs for
   *                  exception handlers.  This is only actually used if
   *                  !noJSR.  We don't need the expression stack, since
   *                  the only thing on the expression stack on entry to
   *                  a handler block is the exception object (and thus
   *                  we can skip scanning the expression stack for
   *                  return addresses when creating a handler block).
   * @param parent parent in Red/Black tree
   * @param left are we creating a left child of parent?
   */
  private BasicBlockLE _createBBLE(int bcIndex, Operand[] simLocals, BasicBlockLE parent, boolean left) {
    BasicBlockLE newBBLE = null;
    if (handlerPCs != null) {
      for (int i = 0; i < handlerPCs.length; i++) {
        if (handlerPCs[i] == bcIndex) {
          if (newBBLE == null) {
            newBBLE =
                new HandlerBlockLE(bcIndex,
                                   gc.inlineSequence,
                                   exceptionTypes[i],
                                   gc.temps,
                                   gc.method.getOperandWords(),
                                   gc.cfg);
            ((HandlerBlockLE) newBBLE).entryBlock.firstRealInstruction().
                position = gc.inlineSequence;
          } else {
            ((HandlerBlockLE) newBBLE).addCaughtException(exceptionTypes[i]);
          }
        }
      }
    }
    if (newBBLE == null) {
      newBBLE = new BasicBlockLE(bcIndex, gc.inlineSequence, gc.cfg);
    }

    // Set newBBLE.max to encode exception ranges
    newBBLE.max = exceptionEndRange(bcIndex);

    if (DBG_BBSET) db("Created " + newBBLE);

    // Now, insert newBBLE into our backing Red/Black tree before we call
    // initializeExceptionHandlers.
    // We must do it in this order because initExHand may in turn call
    // _createBBLE to create new handler blocks, and our tree must contain
    // newBBLE before we can correctly insert another block.
    treeInsert(parent, newBBLE, left);

    initializeExceptionHandlers(newBBLE, simLocals);
    return newBBLE;
  }

  /**
   * Returns the basic block which has the next-higher bytecode index.
   * Returns {@code null} if x is the highest block.
   * @param x basic block at which to start the search for a higher block
   * @param value the contents of x.low (makes tail call elim work better
   *              if we avoid the obvious 1 argument wrapper function)
   */
  private BasicBlockLE getSuccessor(BasicBlockLE x, int value) {
    if (x.right != null) return minimumBB(x.right, value);
    BasicBlockLE y = x.parent;
    while ((y != null) && (x == y.right)) {
      x = y;
      y = x.parent;
    }
    // at this point either x is the root, or x is the left child of y
    if ((y == null) || (y.low != value)) return y;
    return getSuccessor(y, value);
  }

  private BasicBlockLE minimumBB(BasicBlockLE x, int value) {
    if (x.left != null) return minimumBB(x.left, value);
    if (value == x.low) return getSuccessor(x, value);
    return x;
  }

  /**
   * Insert <code>newBBLE</code> as a child of parent in our Red/Black tree.
   * @param parent the parent node
   * @param newBBLE  the new child node
   * @param left   is the child the left or right child of parent?
   */
  private void treeInsert(BasicBlockLE parent, BasicBlockLE newBBLE, boolean left) {
    if (parent == null) {
      if (VM.VerifyAssertions) VM._assert(root == null);
      root = newBBLE;
      root.setBlack();
      if (DBG_BBSET) db("inserted " + newBBLE + " as root of tree");
    } else {
      if (left) {
        parent.left = newBBLE;
      } else {
        parent.right = newBBLE;
      }
      newBBLE.parent = parent;
      if (DBG_BBSET) {
        db("inserted new block " + newBBLE + " as " + (left ? "left" : "right") + " child of " + parent);
      }
      fixupBBSet(newBBLE);
    }
  }

  /**
   * Performs tree fixup (restore Red/Black invariants) after adding a
   * new node to the tree.
   * @param x node that was added.
   */
  private void fixupBBSet(BasicBlockLE x) {
    if (DBG_BBSET) db("fixing up tree after inserting " + x);
    x.setRed();
    while (x != root) {
      BasicBlockLE xp = x.parent;
      if (xp.isBlack()) {
        break;
      }
      if (DBG_BBSET) db(x + " and its parent " + xp + " are both red");
      BasicBlockLE xpp = xp.parent;
      if (DBG_BBSET) db(xp + "'s parent is " + xpp);
      if (xp == xpp.left) {
        BasicBlockLE y = xpp.right;
        if ((y != null) && y.isRed()) {
          xp.setBlack();
          y.setBlack();
          xpp.setRed();
          x = xpp;
        } else {
          if (x == xp.right) {
            x = xp;
            leftRotateBBSet(xp);
            xp = x.parent;
            xpp = xp.parent;
          }
          xp.setBlack();
          xpp.setRed();
          rightRotateBBSet(xpp);
        }
      } else {
        BasicBlockLE y = xpp.left;
        if ((y != null) && y.isRed()) {
          xp.setBlack();
          y.setBlack();
          xpp.setRed();
          x = xpp;
        } else {
          if (x == xp.left) {
            x = xp;
            rightRotateBBSet(xp);
            xp = x.parent;
            xpp = xp.parent;
          }
          xp.setBlack();
          xpp.setRed();
          leftRotateBBSet(xpp);
        }
      }
    }
    root.setBlack();
    // verifyTree();
  }

  private void leftRotateBBSet(BasicBlockLE x) {
    if (DBG_BBSET) db("performing left tree rotation");
    BasicBlockLE y = x.right;
    BasicBlockLE yl = y.left;
    x.right = yl;
    if (yl != null) {
      yl.parent = x;
    }
    BasicBlockLE xp = x.parent;
    y.parent = xp;
    if (xp == null) {
      root = y;
    } else if (x == xp.left) {
      xp.left = y;
    } else {
      xp.right = y;
    }
    y.left = x;
    x.parent = y;
  }

  private void rightRotateBBSet(BasicBlockLE x) {
    if (DBG_BBSET) db("performing right tree rotation");
    BasicBlockLE y = x.left;
    BasicBlockLE yr = y.right;
    x.left = yr;
    if (yr != null) {
      yr.parent = x;
    }
    BasicBlockLE xp = x.parent;
    y.parent = xp;
    if (xp == null) {
      root = y;
    } else if (x == xp.right) {
      xp.right = y;
    } else {
      xp.left = y;
    }
    y.right = x;
    x.parent = y;
  }

  @SuppressWarnings("unused")
  // here for debugging
  private void verifyTree() {
    if (VM.VerifyAssertions) {
      VM._assert(root.isBlack());
      verifyTree(root, -1, bcodes.length());
      countBlack(root);
    }
  }

  private void verifyTree(BasicBlockLE node, int min, int max) {
    if (VM.VerifyAssertions) {
      VM._assert(node.low >= min);
      VM._assert(node.low <= max);
      if (node.left != null) {
        VM._assert(node.isBlack() || node.left.isBlack());
        VM._assert(node.left.parent == node);
        verifyTree(node.left, min, node.low);
      }
      if (node.right != null) {
        VM._assert(node.isBlack() || node.right.isBlack());
        VM._assert(node.right.parent == node);
        verifyTree(node.right, node.low, max);
      }
    }
  }

  private int countBlack(BasicBlockLE node) {
    if (node == null) return 1;
    int left = countBlack(node.left);
    int right = countBlack(node.right);
    if (VM.VerifyAssertions) VM._assert(left == right);
    if (node.isBlack()) {
      left++;
    }
    return left;
  }

  private static final class TreeEnumerator implements Enumeration<BasicBlockLE> {
    BasicBlockLE node;

    static BBSet.TreeEnumerator enumFromRoot(BasicBlockLE root) {
      if (root.left != null) {
        do {
          root = root.left;
        } while (root.left != null);
      }
      return new TreeEnumerator(root);
    }

    static BBSet.TreeEnumerator enumFromNode(BasicBlockLE node) {
      return new TreeEnumerator(node);
    }

    private TreeEnumerator(BasicBlockLE node) {
      this.node = node;
    }

    @Override
    public boolean hasMoreElements() {
      return (node != null);
    }

    public BasicBlockLE next() {
      BasicBlockLE retVal = node;
      if (retVal == null) {
        throw new NoSuchElementException();
      }
      if (retVal.right != null) {
        node = retVal.right;
        while (node.left != null) {
          node = node.left;
        }
      } else {
        BasicBlockLE x = retVal;
        node = x.parent;
        while ((node != null) && (node.right == x)) {
          x = node;
          node = x.parent;
        }
      }
      return retVal;
    }

    @Override
    public BasicBlockLE nextElement() {
      return next();
    }
  }
}
