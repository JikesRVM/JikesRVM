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
package org.jikesrvm.adaptive.recompilation.instrumentation;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.jikesrvm.VM;
import org.jikesrvm.adaptive.AosEntrypoints;
import org.jikesrvm.compilers.opt.DefUse;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.Simple;
import org.jikesrvm.compilers.opt.controlflow.BranchOptimizations;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.GetStatic;
import org.jikesrvm.compilers.opt.ir.Goto;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.InstrumentedCounter;
import org.jikesrvm.compilers.opt.ir.Load;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.InstructionEnumeration;
import org.jikesrvm.compilers.opt.ir.Operator;
import static org.jikesrvm.compilers.opt.ir.Operators.GETSTATIC;
import static org.jikesrvm.compilers.opt.ir.Operators.GOTO;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_ADD;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_IFCMP;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_STORE;
import static org.jikesrvm.compilers.opt.ir.Operators.IR_PROLOGUE;
import static org.jikesrvm.compilers.opt.ir.Operators.LABEL;
import static org.jikesrvm.compilers.opt.ir.Operators.PUTSTATIC;
import static org.jikesrvm.compilers.opt.ir.Operators.YIELDPOINT_BACKEDGE;
import static org.jikesrvm.compilers.opt.ir.Operators.YIELDPOINT_PROLOGUE;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.PutStatic;
import org.jikesrvm.compilers.opt.ir.Store;
import org.jikesrvm.compilers.opt.ir.operand.AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.BranchOperand;
import org.jikesrvm.compilers.opt.ir.operand.BranchProfileOperand;
import org.jikesrvm.compilers.opt.ir.operand.ConditionOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;

/**
 *  InstrumentationSamplingFramework
 *
 *  Transforms the method so that instrumention is sampled, rather
 *  than executed exhaustively.  Includes both the full-duplication
 *  and no-duplication variations.  See Arnold-Ryder PLDI 2001, and
 *  the section 4.1 of Arnold's PhD thesis.
 *
 *  NOTE: This implementation uses yieldpoints to denote where checks
 *  should be placed (to transfer control into instrumented code).  It
 *  is currently assumed that these are on method entries and
 *  backedges.  When optimized yieldpoint placement exists either a)
 *  it should be turned off when using this phase, or b) this phase
 *  should detect its own check locations.
 *
 *  To avoid the complexity of duplicating exception handler maps,
 *  exception handler blocks are split and a check at the top of the
 *  handler.  Thus exceptions from both the checking and duplicated
 *  code are handled by the same catch block, however the check at the
 *  top of the catch block ensures that the hander code has the
 *  opportunity to be sampled.
 */
public final class InstrumentationSamplingFramework extends CompilerPhase {

  /**
   * These are local copies of optimizing compiler debug options that can be
   * set via the command line arguments.
   */
  private static boolean DEBUG = false;
  private static boolean DEBUG2 = false;

  /**
   * Temporary variables
   */
  private RegisterOperand cbsReg = null;

  /**
   * Constructor for this compiler phase
   */
  private static final Constructor<CompilerPhase> constructor =
      getCompilerPhaseConstructor(InstrumentationSamplingFramework.class);

  /**
   * Get a constructor object for this compiler phase
   * @return compiler phase constructor
   */
  public Constructor<CompilerPhase> getClassConstructor() {
    return constructor;
  }

  public boolean shouldPerform(OptOptions options) {
    return options.ADAPTIVE_INSTRUMENTATION_SAMPLING;
  }

  public String getName() { return "InstrumentationSamplingFramework"; }

  /**
   * Perform this phase
   *
   * @param ir the governing IR
   */
  public void perform(IR ir) {

    DEBUG = ir.options.DEBUG_INSTRU_SAMPLING;
    DEBUG2 = ir.options.DEBUG_INSTRU_SAMPLING_DETAIL;

    // Temp code for selective debugging.  Dump the whole IR if either DEBUG2 is set, or if a specific method name is specified.
    if (DEBUG2 || (DEBUG && ir.options.fuzzyMatchMETHOD_TO_PRINT(ir.method.toString()))) {
      dumpIR(ir, "Before instrumentation sampling transformation");
      dumpCFG(ir);
    }

    // Perform the actual phase here.
    if (ir.options.ADAPTIVE_NO_DUPLICATION) {
      performVariationNoDuplication(ir);
    } else {
      performVariationFullDuplication(ir, this);
    }

    // Dump method again after phase completes
    if (DEBUG2 || (DEBUG && ir.options.fuzzyMatchMETHOD_TO_PRINT(ir.method.toString()))) {
      dumpIR(ir, "After instrumentation sampling transformation");
      dumpCFG(ir);
    }

    cleanUp(ir);

  }

//  /**
//   * Initialization to perform before the transformation is applied
//   */
//  private static void initialize(IR ir) {
//
//  }

  //

  /**
   * Initialization to perform after the transformation is applied
   */
  private void cleanUp(IR ir) {

    // Clean up the ir with simple optimizations
    Simple simple = new Simple(-1, false, false, false, false);
    simple.perform(ir);

    // Perform branch optimizations (level 0 is passed because if we
    // always want to call it if we've used the sampling framework).
    BranchOptimizations branchOpt = new BranchOptimizations(0, true, true);
    branchOpt.perform(ir);

    // Clear the static variables
    cbsReg = null;

    //
    //    RegisterInfo.computeRegisterList(ir);
    DefUse.recomputeSpansBasicBlock(ir);
    DefUse.recomputeSSA(ir);
  }

  /**
   * Perform the full duplication algorithm
   *
   * @param ir the governing IR
   */
  private void performVariationFullDuplication(IR ir, CompilerPhase phaseObject) {

    // Initialize

    // Used to store mapping from original to duplicated blocks
    HashMap<BasicBlock, BasicBlock> origToDupMap = new HashMap<BasicBlock, BasicBlock>();
    // Used to remember all exception handler blocks
    HashSet<BasicBlock> exceptionHandlerBlocks = new HashSet<BasicBlock>();
    // The register containing the counter value to check
    cbsReg = ir.regpool.makeTempInt();

    // 1) Make a copy of all basic blocks
    duplicateCode(ir, origToDupMap, exceptionHandlerBlocks);

    // 2) Insert counter-based sampling checks  (record blocks with YP's)
    insertCBSChecks(ir, origToDupMap, exceptionHandlerBlocks);

    // 3) Fix control flow edges in duplicated code
    adjustPointersInDuplicatedCode(ir, origToDupMap);

    // 4) Remove instrumentation from checking code
    removeInstrumentationFromOrig(ir, origToDupMap);

    if (ir.options.fuzzyMatchMETHOD_TO_PRINT(ir.method.toString())) {
      ir.verify("End of Framework");
    }

  }

  /**
   * Make a duplicate of all basic blocks down at the bottom of the
   * code order.  For now, this is done in a slightly ackward way.
   * After duplication, the control flow within the duplicated code is
   * not complete because each block immediately transfers you back to
   * the original code.  This gets fixed in
   * adjustPointersInDuplicatedCode
   *
   * @param ir the governing IR */
  private void duplicateCode(IR ir, HashMap<BasicBlock, BasicBlock> origToDupMap,
                             HashSet<BasicBlock> exceptionHandlerBlocks) {

    if (DEBUG) VM.sysWrite("In duplicate code\n");

    // Iterate through all blocks and duplicate them.  While
    // iterating, loop until you see the block that was the *original*
    // last block.  (Need to do it this way because we're adding
    // blocks to the end as we loop.)
    BasicBlock origLastBlock = ir.cfg.lastInCodeOrder();
    boolean done = false;
    BasicBlock curBlock = ir.cfg.firstInCodeOrder();
    while (!done && curBlock != null) {
      if (curBlock == origLastBlock) {
        // Stop after this iteration
        done = true;
      }

      // Don't duplicate the exit node
      if (curBlock == ir.cfg.exit()) {
        // Next block
        curBlock = curBlock.nextBasicBlockInCodeOrder();
        continue;
      }

      // Check if the block needs to be split for later insertion of
      // a check.  We split the entry basic block (first basic block
      // in the method) to insert the method-entry check, and also
      // exception handler blocks to simplify handling
      if (curBlock == ir.cfg.entry() || curBlock.isExceptionHandlerBasicBlock()) {

        Instruction splitInstr = null;

        if (curBlock == ir.cfg.entry()) {
          splitInstr = getFirstInstWithOperator(IR_PROLOGUE, curBlock);
        } else {
          splitInstr = getFirstInstWithOperator(LABEL, curBlock);
        }

        // Split entry node so the split instr isn't duplicated, but rest
        // of block is.
        BasicBlock blockTail = curBlock.splitNodeWithLinksAt(splitInstr, ir);
        curBlock.recomputeNormalOut(ir);

        if (curBlock.isExceptionHandlerBasicBlock()) {
          // Remember that the tail of the block we just split is an
          // exception handler and we want to add a check here
          exceptionHandlerBlocks.add(blockTail);
        }

        // proceed with the duplication using the second half of the split block.
        curBlock = blockTail;

        // Is this necessary?   TODO:  see if it can be removed.
        DefUse.recomputeSpansBasicBlock(ir);
      }

      // Copy the basic block
      BasicBlock dup = myCopyWithoutLinks(curBlock, ir);
      dup.setInfrequent();  // duplicated code is known to be infrequent.

      if (DEBUG2) {
        VM.sysWrite("Copying bb: " + curBlock + " to be " + dup + "\n");
      }

      ir.cfg.addLastInCodeOrder(dup);

      // Attach a goto to the duplicated code block, so that it
      // doesn't fall through within the duplicated code, and jumps
      // back to the checking code.  This is a bit of a convoluted way
      // of doing things and could be cleaned up.  It was done
      // originally done to make the remaining passes simpler.
      BasicBlock fallthrough = curBlock.getFallThroughBlock();
      if (fallthrough != null) {

        Instruction g = Goto.create(GOTO, fallthrough.makeJumpTarget());
        dup.appendInstruction(g);
      }

      dup.recomputeNormalOut(ir);

      origToDupMap.put(curBlock, dup); // Remember that basic block "dup"

      // Next block
      curBlock = curBlock.nextBasicBlockInCodeOrder();
    }
  }

  /**
   * In the checking code, insert CBS checks at each yieldpoint, to
   * transfer code into the duplicated (instrumented) code.
   * For now, checks are inserted at all yieldpoints (See comment at
   * top of file).
   *
   * @param ir the governing IR
   */
  private void insertCBSChecks(IR ir, HashMap<BasicBlock, BasicBlock> origToDupMap,
                               HashSet<BasicBlock> exceptionHandlerBlocks) {

    // Iterate through the basic blocks in the original code
    for (Map.Entry<BasicBlock, BasicBlock> entry : origToDupMap.entrySet()) {
      BasicBlock bb = entry.getKey();
      BasicBlock dup = entry.getValue();

      if (dup == null) {
        // Getting here means that for some reason the block was not duplicated.
        if (DEBUG) {
          VM.sysWrite("Debug: block " + bb + " was not duplicated\n");
        }
        continue;
      }

      // If you have a yieldpoint, or if you are the predecessor of a
      // split exception handler when duplicating code) then insert a
      // check

      if (getFirstInstWithYieldPoint(bb) != null ||   // contains yieldpoint
          exceptionHandlerBlocks.contains(bb)) { // is exception handler

        BasicBlock checkBB = null;
        BasicBlock prev = bb.prevBasicBlockInCodeOrder();
        // Entry has been split, so any node containing a yieldpoint
        // should not be first
        if (VM.VerifyAssertions) {
          VM._assert(prev != null);
        }

        // Make a new BB that contains the check itself (it needs to
        // be a basic block because the duplicated code branches back
        // to it).
        checkBB = new BasicBlock(-1, null, ir.cfg);

        // Break the code to insert the check logic
        ir.cfg.breakCodeOrder(prev, bb);
        ir.cfg.linkInCodeOrder(prev, checkBB);
        ir.cfg.linkInCodeOrder(checkBB, bb);
        if (DEBUG) {
          VM.sysWrite("Creating check " + checkBB + " to preceed " + bb + " \n");
        }

        // Step 2, Make all of bb's predecessors point to the check instead
        for (BasicBlockEnumeration preds = bb.getIn(); preds.hasMoreElements();) {
          BasicBlock pred = preds.next();
          pred.redirectOuts(bb, checkBB, ir);
        }

        // Insert the check logic
        createCheck(checkBB, bb, dup, false, ir);

      }
    }
  }

  /**
   * Append a check to a basic block, and make it jump to the right places.
   *
   * @param checkBB The block to append the CBS check to.
   * @param noInstBB The basic block to jump to if the CBS check fails
   * @param instBB The basicBlock to jump to if the CBS check succeeds
   * @param fallthroughToInstBB Should checkBB fallthrough to instBB
   *                            (otherwise it must fallthrough to noInstBB)
   */
  private void createCheck(BasicBlock checkBB, BasicBlock noInstBB, BasicBlock instBB,
                           boolean fallthroughToInstBB, IR ir) {

    appendLoad(checkBB, ir);

    // Depending on where you fallthrough, set the condition correctly
    ConditionOperand cond = null;
    BranchOperand target = null;
    BranchProfileOperand profileOperand = null;
    if (fallthroughToInstBB) {
      // The instrumented basic block is the fallthrough of checkBB,
      // so make the branch jump to the non-instrumented block.
      cond = ConditionOperand.GREATER();
      target = noInstBB.makeJumpTarget();
      profileOperand = new BranchProfileOperand(1.0f); // Taken frequently
    } else {
      // The non-instrumented basic block is the fallthrough of checkBB,
      // so make the branch jump to the instrumented block.
      cond = ConditionOperand.LESS_EQUAL();
      target = instBB.makeJumpTarget();
      profileOperand = new BranchProfileOperand(0.0f); // Taken infrequently
    }
    RegisterOperand guard = ir.regpool.makeTempValidation();
    checkBB.appendInstruction(IfCmp.create(INT_IFCMP,
                                           guard,
                                           cbsReg.copyRO(),
                                           new IntConstantOperand(0),
                                           cond,
                                           target,
                                           profileOperand));
    checkBB.recomputeNormalOut(ir);

    // Insert the decrement and store in the block that is the
    // successor of the check
    prependStore(noInstBB, ir);
    prependDecrement(noInstBB, ir);

    // Insert a counter reset in the duplicated block.
    prependCounterReset(instBB, ir);

  }

  /**
   * Append a load of the global counter to the given basic block.
   *
   * WARNING: Tested for LIR only!
   *
   * @param bb The block to append the load to
   * @param ir The IR */
  private void appendLoad(BasicBlock bb, IR ir) {

    if (DEBUG) VM.sysWrite("Adding load to " + bb + "\n");
    Instruction load = null;
    if (ir.options.ADAPTIVE_PROCESSOR_SPECIFIC_COUNTER) {
      // Use one CBS counter per processor (better for multi threaded apps)

      if (ir.IRStage == IR.HIR) {
        // NOT IMPLEMENTED
      } else {
        // Phase is being used in LIR
        // Insert the load instruction.
        load =
            Load.create(INT_LOAD,
                        cbsReg.copyRO(),
                        ir.regpool.makeTROp(),
                        IRTools.AC(AosEntrypoints.threadCBSField.getOffset()),
                        new LocationOperand(AosEntrypoints.threadCBSField));

        bb.appendInstruction(load);
      }
    } else {
      // Use global counter
      if (ir.IRStage == IR.HIR) {
        Operand offsetOp = new AddressConstantOperand(AosEntrypoints.globalCBSField.getOffset());
        load =
            GetStatic.create(GETSTATIC,
                             cbsReg.copyRO(),
                             offsetOp,
                             new LocationOperand(AosEntrypoints.globalCBSField));
        bb.appendInstruction(load);
      } else {
        // LIR
        Instruction dummy = Load.create(INT_LOAD, null, null, null, null);

        bb.appendInstruction(dummy);
        load =
            Load.create(INT_LOAD,
                        cbsReg.copyRO(),
                        ir.regpool.makeJTOCOp(ir, dummy),
                        IRTools.AC(AosEntrypoints.globalCBSField.getOffset()),
                        new LocationOperand(AosEntrypoints.globalCBSField));

        dummy.insertBefore(load);
        dummy.remove();
      }
    }
  }

  /**
   * Append a store of the global counter to the given basic block.
   *
   * WARNING: Tested in LIR only!
   *
   * @param bb The block to append the load to
   * @param ir The IR */
  private void prependStore(BasicBlock bb, IR ir) {

    if (DEBUG) VM.sysWrite("Adding store to " + bb + "\n");
    Instruction store = null;
    if (ir.options.ADAPTIVE_PROCESSOR_SPECIFIC_COUNTER) {
      store =
          Store.create(INT_STORE,
                       cbsReg.copyRO(),
                       ir.regpool.makeTROp(),
                       IRTools.AC(AosEntrypoints.threadCBSField.getOffset()),
                       new LocationOperand(AosEntrypoints.threadCBSField));

      bb.prependInstruction(store);
    } else {
      if (ir.IRStage == IR.HIR) {
        store =
            PutStatic.create(PUTSTATIC,
                             cbsReg.copyRO(),
                             new AddressConstantOperand(AosEntrypoints.globalCBSField.getOffset()),
                             new LocationOperand(AosEntrypoints.globalCBSField));

        bb.prependInstruction(store);
      } else {
        Instruction dummy = Load.create(INT_LOAD, null, null, null, null);

        bb.prependInstruction(dummy);
        store =
            Store.create(INT_STORE,
                         cbsReg.copyRO(),
                         ir.regpool.makeJTOCOp(ir, dummy),
                         IRTools.AC(AosEntrypoints.globalCBSField.getOffset()),
                         new LocationOperand(AosEntrypoints.globalCBSField));

        dummy.insertBefore(store);
        dummy.remove();
      }
    }
  }

  /**
   * Append a decrement of the global counter to the given basic block.
   *
   * Tested in LIR only!
   *
   * @param bb The block to append the load to
   * @param ir The IR
   */
  private void prependDecrement(BasicBlock bb, IR ir) {
    if (DEBUG) VM.sysWrite("Adding Increment to " + bb + "\n");

    RegisterOperand use = cbsReg.copyRO();
    RegisterOperand def = use.copyU2D();
    Instruction inc = Binary.create(INT_ADD, def, use, IRTools.IC(-1));
    bb.prependInstruction(inc);
  }

  /**
   * Prepend the code to reset the global counter to the given basic
   * block.
   *
   * Warning:  Tested in LIR only!
   *
   * @param bb The block to append the load to
   * @param ir The IR */
  private void prependCounterReset(BasicBlock bb, IR ir) {
    Instruction load = null;
    Instruction store = null;

    if (ir.IRStage == IR.HIR) {
      // Not tested
      Operand offsetOp = new AddressConstantOperand(AosEntrypoints.cbsResetValueField.getOffset());
      load =
          GetStatic.create(GETSTATIC,
                           cbsReg.copyRO(),
                           offsetOp,
                           new LocationOperand(AosEntrypoints.cbsResetValueField));
      store =
          PutStatic.create(PUTSTATIC,
                           cbsReg.copyRO(),
                           new AddressConstantOperand(AosEntrypoints.globalCBSField.getOffset()),
                           new LocationOperand(AosEntrypoints.globalCBSField));
      bb.prependInstruction(store);
      bb.prependInstruction(load);
    } else {
      // LIR
      Instruction dummy = Load.create(INT_LOAD, null, null, null, null);
      bb.prependInstruction(dummy);
      // Load the reset value
      load =
          Load.create(INT_LOAD,
                      cbsReg.copyRO(),
                      ir.regpool.makeJTOCOp(ir, dummy),
                      IRTools.AC(AosEntrypoints.cbsResetValueField.getOffset()),
                      new LocationOperand(AosEntrypoints.cbsResetValueField));

      dummy.insertBefore(load);

      // Store it in the counter register
      if (ir.options.ADAPTIVE_PROCESSOR_SPECIFIC_COUNTER) {
        store =
            Store.create(INT_STORE,
                         cbsReg.copyRO(),
                         ir.regpool.makeTROp(),
                         IRTools.AC(AosEntrypoints.threadCBSField.getOffset()),
                         new LocationOperand(AosEntrypoints.threadCBSField));
      } else {
        // Use global counter
        store =
            Store.create(INT_STORE,
                         cbsReg.copyRO(),
                         ir.regpool.makeJTOCOp(ir, dummy),
                         IRTools.AC(AosEntrypoints.globalCBSField.getOffset()),
                         new LocationOperand(AosEntrypoints.globalCBSField));
      }
      dummy.insertBefore(store);
      dummy.remove();
    }

  }

  /**
   *  Go through all instructions and find the first with the given
   *  operator.
   *
   * @param operator The operator to look for
   * @param bb The basic block in which to look
   * @return The first instruction in bb that has operator operator.  */
  private static Instruction getFirstInstWithOperator(Operator operator, BasicBlock bb) {
    for (InstructionEnumeration ie = bb.forwardInstrEnumerator(); ie.hasMoreElements();) {
      Instruction i = ie.next();

      if (i.operator() == operator) {
        return i;
      }
    }
    return null;
  }

  /**
   *  Go through all instructions and find one that is a yield point
   *
   * @param bb The basic block in which to look
   * @return The first instruction in bb that has a yield point
   */
  public static Instruction getFirstInstWithYieldPoint(BasicBlock bb) {
    for (InstructionEnumeration ie = bb.forwardInstrEnumerator(); ie.hasMoreElements();) {
      Instruction i = ie.next();

      if (isYieldpoint(i)) {
        return i;
      }
    }
    return null;
  }

  /**
   *  Is the given instruction a yieldpoint?
   *
   *  For now we ignore epilogue yieldpoints since we are only concerned with
   *  method entries and backedges.
   */
  public static boolean isYieldpoint(Instruction i) {
    return i.operator() == YIELDPOINT_PROLOGUE ||
           // Skip epilogue yieldpoints.   No checks needed for them
           //        i.operator() == YIELDPOINT_EPILOGUE ||
           i.operator() == YIELDPOINT_BACKEDGE;

  }

  /**
   * Go through all blocks in duplicated code and adjust the edges as
   * follows:
   *
   * 1) All edges (in duplicated code) that go into a block with a
   * yieldpoint must jump to back to the original code.  This is
   * actually already satisfied because we appended goto's to all
   * duplicated bb's (the goto's go back to orig code)
   *
   * 2) All edges that do NOT go into a yieldpoint block must stay
   * (either fallthrough or jump to a block in) in the duplicated
   * code.
   *
   * @param ir the governing IR */
  private static void adjustPointersInDuplicatedCode(IR ir, HashMap<BasicBlock, BasicBlock> origToDupMap) {

    // Iterate over the original version of all duplicate blocks
    for (BasicBlock dupBlock : origToDupMap.values()) {

      // Look at the successors of duplicated Block.
      for (BasicBlockEnumeration out = dupBlock.getNormalOut(); out.hasMoreElements();) {
        BasicBlock origSucc = out.next();

        BasicBlock dupSucc = origToDupMap.get(origSucc);

        // If the successor is not in the duplicated code, then
        // redirect it to stay in the duplicated code. (dupSucc !=
        // null implies that origSucc has a corresponding duplicated
        // block, and thus origSucc is in the checking code.

        if (dupSucc != null) {

          dupBlock.redirectOuts(origSucc, dupSucc, ir);
          if (DEBUG) {
            VM.sysWrite("Source: " + dupBlock + "\n");
            VM.sysWrite("============= FROM " + origSucc + " =============\n");
            //origSucc.printExtended();
            VM.sysWrite("============= TO " + dupSucc + "=============\n");
            //dupSucc.printExtended();
          }
        } else {
          if (DEBUG) {
            VM.sysWrite("Not adjusting pointer from " + dupBlock + " to " + origSucc + " because dupSucc is null\n");
          }
        }
      }

    }
  }

  /**
   * Remove instrumentation from the orignal version of all duplicated
   * basic blocks.
   *
   * The yieldpoints can also be removed from either the original or
   * the duplicated code.  If you remove them from the original, it
   * will make it run faster (since it is executed more than the
   * duplicated) however that means that you can't turn off the
   * instrumentation or you could infinitely loop without executing
   * yieldpoints!
   *
   * @param ir the governing IR */
  private static void removeInstrumentationFromOrig(IR ir, HashMap<BasicBlock, BasicBlock> origToDupMap) {
    // Iterate over the original version of all duplicate blocks

    for (BasicBlock origBlock : origToDupMap.keySet()) {

      // Remove all instrumentation instructions.  They already have
      // been transfered to the duplicated code.
      for (InstructionEnumeration ie = origBlock.forwardInstrEnumerator(); ie.hasMoreElements();) {
        Instruction i = ie.next();
        if (isInstrumentationInstruction(i) || (isYieldpoint(i) && ir.options.ADAPTIVE_REMOVE_YP_FROM_CHECKING)) {

          if (DEBUG) VM.sysWrite("Removing " + i + "\n");
          i.remove();
        }
      }
    }
  }

  /**
   * The same as BasicBlock.copyWithoutLinks except that it
   * renames all temp variables that are never used outside the basic
   * block.  This 1) helps eliminate the artificially large live
   * ranges that might have been created (assuming the reg allocator
   * isn't too smart) and 2) it prevents BURS from crashing.
   *
   * PRECONDITION:  the spansBasicBlock bit must be correct by calling
   *                DefUse.recomputeSpansBasicBlock(IR);
   */
  private static BasicBlock myCopyWithoutLinks(BasicBlock bb, IR ir) {

    BasicBlock newBlock = bb.copyWithoutLinks(ir);

    // Without this, there were sanity errors at one point.
    updateTemps(newBlock, ir);

    return newBlock;
  }

  /**
   * Given an basic block, rename all of the temporary registers that are local to the block.
   */
  private static void updateTemps(BasicBlock bb, IR ir) {

    // Need to clear the scratch objects before we start using them
    clearScratchObjects(bb, ir);

    // For each instruction in the block
    for (InstructionEnumeration ie = bb.forwardInstrEnumerator(); ie.hasMoreElements();) {
      Instruction inst = ie.next();

      // Look at each register operand
      int numOperands = inst.getNumberOfOperands();
      for (int i = 0; i < numOperands; i++) {
        Operand op = inst.getOperand(i);
        if (op instanceof RegisterOperand) {
          RegisterOperand ro = (RegisterOperand) op;
          if (ro.getRegister().isTemp() && !ro.getRegister().spansBasicBlock()) {
            // This register does not span multiple basic blocks, so
            // replace it with a temp.
            RegisterOperand newReg = getOrCreateDupReg(ro, ir);
            if (DEBUG2) {
              VM.sysWrite("Was " + ro + " and now it's " + newReg + "\n");
            }
            inst.putOperand(i, newReg);
          }
        }
      }
    }

    // Clear them afterward also, otherwise register allocation fails.
    // (TODO: Shouldn't they just be cleared before use in register
    // allocation?)
    clearScratchObjects(bb, ir);
  }

  /**
   *  Go through all statements in the basic block and clear the
   *  scratch objects.
   */
  private static void clearScratchObjects(BasicBlock bb, IR ir) {
    // For each instruction in the block
    for (InstructionEnumeration ie = bb.forwardInstrEnumerator(); ie.hasMoreElements();) {
      Instruction inst = ie.next();

      // Look at each register operand
      int numOperands = inst.getNumberOfOperands();
      for (int i = 0; i < numOperands; i++) {
        Operand op = inst.getOperand(i);
        if (op instanceof RegisterOperand) {
          RegisterOperand ro = (RegisterOperand) op;
          if (ro.getRegister().isTemp() && !ro.getRegister().spansBasicBlock()) {

            // This register does not span multiple basic blocks.  It
            // will be touched by the register duplication, so clear
            // its scratch reg.
            ro.getRegister().scratchObject = null;
          }
        }
      }
    }

  }

  /**
   * The given register a) does not span multiple basic block, and b)
   * is used in a basic block that is being duplicated.   It is
   * therefore safe to replace all occurences of the register with a
   * new register.  This method returns the duplicated
   * register that is associated with the given register.  If a
   * duplicated register does not exist, it is created and recorded.
   */
  private static RegisterOperand getOrCreateDupReg(RegisterOperand ro, IR ir) {

    // Check if the register associated with this regOperand already
    // has a paralles operand
    if (ro.getRegister().scratchObject == null) {
      // If no dup register exists, make a new one and remember it.
      RegisterOperand dupRegOp = ir.regpool.makeTemp(ro.getType());
      ro.getRegister().scratchObject = dupRegOp.getRegister();
    }
    return new RegisterOperand((Register) ro.getRegister().scratchObject, ro.getType());
  }

  /**
   * Perform the NoDuplication version of the framework (see
   * Arnold-Ryder PLDI 2001).  Instrumentation operations are wrapped
   * in a conditional, but no code duplication is performed.
   */
  private void performVariationNoDuplication(IR ir) {
    // The register containing the counter value to check
    cbsReg = ir.regpool.makeTempInt();

    ArrayList<Instruction> instrumentationOperations = new ArrayList<Instruction>();
    for (BasicBlockEnumeration allBB = ir.getBasicBlocks(); allBB.hasMoreElements();) {
      BasicBlock bb = allBB.next();

      for (InstructionEnumeration ie = bb.forwardInstrEnumerator(); ie.hasMoreElements();) {
        Instruction i = ie.next();

        // If it's an instrumentation operation, remember the instruction
        if (isInstrumentationInstruction(i)) {
          instrumentationOperations.add(i);
        }
      }
    }

    // for each instrumentation operation.
    for (Instruction i : instrumentationOperations) {
      BasicBlock bb = i.getBasicBlock();
      conditionalizeInstrumentationOperation(ir, i, bb);
    }
  }

  /**
   * Take an instrumentation operation (an instruction) and guard it
   * with a counter-based check.
   *
   * 1) split the basic block before and after the i
   *    to get A -> B -> C
   * 2) Add check to A, making it go to B if it succeeds, otherwise C
   */
  private void conditionalizeInstrumentationOperation(IR ir, Instruction i, BasicBlock bb) {

    // Create bb after instrumentation ('C', in comment above)
    BasicBlock C = bb.splitNodeWithLinksAt(i, ir);
    bb.recomputeNormalOut(ir);

    // Create bb before instrumentation ('A', in comment above)
    Instruction prev = i.prevInstructionInCodeOrder();

    BasicBlock B = null;
    try {
      B = bb.splitNodeWithLinksAt(prev, ir);
    } catch (RuntimeException e) {
      VM.sysWrite("Bombed when I: " + i + " prev: " + prev + "\n");
      bb.printExtended();
      throw e;
    }

    bb.recomputeNormalOut(ir);

    BasicBlock A = bb;

    // Now, add check to A, that jumps to C
    createCheck(A, C, B, true, ir);
  }

  /**
   * How to determine whether a given instruction is an
   * "instrumentation instruction".  In other words, it should be
   * executed only when a sample is being taken.
   */
  private static boolean isInstrumentationInstruction(Instruction i) {

    //    if (i.bcIndex == INSTRUMENTATION_BCI &&
    // (Call.conforms(i) ||
    // if (InstrumentedCounter.conforms(i)))

    return InstrumentedCounter.conforms(i);
  }

  /**
   * Temp debugging code
   */
  public static void dumpCFG(IR ir) {

    for (BasicBlockEnumeration allBB = ir.getBasicBlocks(); allBB.hasMoreElements();) {
      BasicBlock curBB = allBB.next();
      curBB.printExtended();
    }
  }
}



