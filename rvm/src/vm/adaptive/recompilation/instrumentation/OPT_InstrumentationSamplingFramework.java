/*
 * (C) Copyright IBM Corp 2003
 */
//$Id$

package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.adaptive.*;
import com.ibm.JikesRVM.opt.ir.*;
import com.ibm.JikesRVM.opt.*;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.Iterator;
import java.util.HashMap;
import java.util.HashSet;

/** 
 *  OPT_InstrumentationSamplingFramework
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
 *
 *
 * @author Matthew Arnold 
 * */

public final class OPT_InstrumentationSamplingFramework extends OPT_CompilerPhase
  implements OPT_Operators, VM_Constants, OPT_Constants 
{

  /**
   * These are local copies of optimizing compiler debug options that can be
   * set via the command line arguments.
   */
  private static  boolean DEBUG = false;
  private static  boolean DEBUG2 = false;

  /**
   *  
   */
  public final boolean shouldPerform(OPT_Options options) {
    return options.INSTRUMENTATION_SAMPLING;
  }

  /**
   *
   */
  public final String getName() { return "InstrumentationSamplingFramework"; }

  /**
   * Perform this phase
   * 
   * @param ir the governing IR
   */
  final public void perform(OPT_IR ir) {

    DEBUG = ir.options.DEBUG_INSTRU_SAMPLING;
    DEBUG2 = ir.options.DEBUG_INSTRU_SAMPLING_DETAIL;

    // Temp code for selective debugging.  Dump the whole IR if either DEBUG2 is set, or if a specific method name is specified.
    if (DEBUG2 || 
        (DEBUG &&
         ir.options.fuzzyMatchMETHOD_TO_PRINT(ir.method.toString()))) {
      dumpIR(ir, "Before instrumentation sampling transformation");
      dumpCFG(ir);
    }


    // Perform the actual phase here.
    if (ir.options.NO_DUPLICATION) 
      performVariationNoDuplication(ir);
    else
      performVariationFullDuplication(ir,this);
    

    // Dump method again after phase completes
    if (DEBUG2 ||
        (DEBUG && 
         ir.options.fuzzyMatchMETHOD_TO_PRINT(ir.method.toString())))  {
      dumpIR(ir, "After instrumentation sampling transformation");
      dumpCFG(ir);
    }

    cleanUp(ir);

  }

  /** 
   * Initialization to perform before the transformation is applied
   */
  private static void initialize(OPT_IR ir) {

  }
  
  /**
   * Initialization to perform after the transformation is applied
   */
  private static void cleanUp(OPT_IR ir) {

    // Clean up the ir with simple optimizations
    OPT_Simple simple = new OPT_Simple(-1, false, false);
    simple.perform(ir);

    // Perform branch optimizations (level 0 is passed because if we 
    // always want to call it if we've used the sampling framework).
    OPT_BranchOptimizations branchOpt = new OPT_BranchOptimizations(0,true,true);
    branchOpt.perform(ir);

    // Clear the static variables 
    cbsReg=null;
    cbsLoc=null;
    resetLoc=null;
    origLastBlock = null;

    // 
    //    OPT_RegisterInfo.computeRegisterList(ir);
    OPT_DefUse.recomputeSpansBasicBlock(ir);
    OPT_DefUse.recomputeSSA(ir);
  }

  /**
   * Perform the full duplication algorithm
   * 
   * @param ir the governing IR
   */
  final private static void performVariationFullDuplication(OPT_IR ir, OPT_CompilerPhase phaseObject) {

    // Initialize
    HashMap origToDupMap = new HashMap();           // Used to store mapping from original to duplicated blocks
    HashSet exceptionHandlerBlocks = new HashSet(); // Used to remember all exception handler blocks
    cbsReg = ir.regpool.makeTempInt();              // The register containing the counter value to check
    cbsLoc = new OPT_LocationOperand(VM_Entrypoints.globalCBSField);
    resetLoc = new OPT_LocationOperand(VM_Entrypoints.cbsResetValueField);

    
    // 1) Make a copy of all basic blocks
    duplicateCode(ir, origToDupMap, exceptionHandlerBlocks);
    
    // 2) Insert counter-based sampling checks  (record blocks with YP's)
    insertCBSChecks(ir, origToDupMap, exceptionHandlerBlocks);
    
    // 3) Fix control flow edges in duplicated code
    adjustPointersInDuplicatedCode(ir, origToDupMap);
      
    // 4) Remove instrumentation from checking code
    removeInstrumentationFromOrig(ir, origToDupMap);

    if (ir.options.fuzzyMatchMETHOD_TO_PRINT(ir.method.toString())) 
      ir.verify("End of Framework");

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
  private final static void duplicateCode (OPT_IR ir, HashMap origToDupMap, HashSet exceptionHandlerBlocks){

    if (DEBUG) VM.sysWrite("In duplicate code\n");

    // Iterate through all blocks and duplicate them.  While
    // iterating, loop until you see the block that was the *original*
    // last block.  (Need to do it this way because we're adding
    // blocks to the end as we loop.)
    OPT_BasicBlock origLastBlock = ir.cfg.lastInCodeOrder();
    boolean done = false;
    OPT_BasicBlock curBlock = ir.cfg.firstInCodeOrder();
    while (!done && curBlock != null) {
      if (curBlock == origLastBlock)  // Stop after this iteration
        done = true;

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
      if (curBlock == ir.cfg.entry() ||
          curBlock.isExceptionHandlerBasicBlock()) {
        
        OPT_Instruction splitInstr = null;
        
        if (curBlock == ir.cfg.entry()) 
          splitInstr = getFirstInstWithOperator(IR_PROLOGUE,curBlock);
        else 
          splitInstr = getFirstInstWithOperator(LABEL,curBlock);
        
        // Split entry node so the split instr isn't duplicated, but rest
        // of block is.   
        OPT_BasicBlock blockTail = 
          curBlock.splitNodeWithLinksAt(splitInstr,ir); 
        curBlock.recomputeNormalOut(ir);
        
        if (curBlock.isExceptionHandlerBasicBlock()) {
          // Remember that the tail of the block we just split is an
          // exception handler and we want to add a check here
          exceptionHandlerBlocks.add(blockTail);
        }
        
        // proceed with the duplication using the second half of the split block.
        curBlock = blockTail;  
        
        // Is this necessary?   TODO:  see if it can be removed.
        OPT_DefUse.recomputeSpansBasicBlock(ir);
      }
      
      
      // Copy the basic block
      OPT_BasicBlock dup = myCopyWithoutLinks(curBlock,ir); 
      dup.setInfrequent();  // duplicated code is known to be infrequent.
      
      if (DEBUG2) VM.sysWrite("Copying bb: " + curBlock + 
                              " to be " + dup + "\n");
      
      ir.cfg.addLastInCodeOrder(dup);
      
      // Attach a goto to the duplicated code block, so that it
      // doesn't fall through within the duplicated code, and jumps
      // back to the checking code.  This is a bit of a convoluted way
      // of doing things and could be cleaned up.  It was done
      // originally done to make the remaining passes simpler.
      OPT_BasicBlock fallthrough = curBlock.getFallThroughBlock();
      if (fallthrough != null) {
        
        OPT_Instruction g = 
          Goto.create(GOTO, 
                      fallthrough.makeJumpTarget()); 
        dup.appendInstruction(g);
      }

      dup.recomputeNormalOut(ir);

      origToDupMap.put(curBlock,dup); // Remember that basic block "dup"
    
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
  private final static void insertCBSChecks(OPT_IR ir, HashMap origToDupMap, HashSet exceptionHandlerBlocks){
    
    // Iterate through the basic blocks in the original code
    Iterator origBlocks = origToDupMap.keySet().iterator();
    while (origBlocks.hasNext()) {
      OPT_BasicBlock bb = (OPT_BasicBlock) origBlocks.next();
      
      OPT_BasicBlock dup = (OPT_BasicBlock) origToDupMap.get(bb);

      if (dup == null) {
        // Getting here means that for some reason the block was not duplicated.  
        if (DEBUG) 
          VM.sysWrite("Debug: block " + bb + " was not duplicated\n");
        continue;
      }

      // If you have a yieldpoint, or if you are the predecessor of a
      // split exception handler when duplicating code) then insert a
      // check 

      if (getFirstInstWithYieldPoint(bb) != null ||   // contains yieldpoint
          exceptionHandlerBlocks.contains(bb)) { // is exception handler

        OPT_BasicBlock checkBB = null;
        OPT_BasicBlock prev = bb.prevBasicBlockInCodeOrder();
        // Entry has been split, so any node containing a yieldpoint
        // should not be first
        if (VM.VerifyAssertions) 
          VM._assert(prev != null);  
        
        // Make a new BB that contains the check itself (it needs to
        // be a basic block because the duplicated code branches back
        // to it).  
        checkBB = new OPT_BasicBlock(-1,null,ir.cfg);

        // Break the code to insert the check logic
        ir.cfg.breakCodeOrder(prev, bb);
        ir.cfg.linkInCodeOrder(prev, checkBB);
        ir.cfg.linkInCodeOrder(checkBB, bb);        
        if (DEBUG)
          VM.sysWrite("Creating check " + checkBB + 
                      " to preceed " + bb +" \n");
        
        // Step 2, Make all of bb's predecessors point to the check instead
        for (OPT_BasicBlockEnumeration preds = bb.getIn();
             preds.hasMoreElements();) {
          OPT_BasicBlock pred = preds.next();   
          pred.redirectOuts(bb,checkBB,ir);
        }

        // Insert the check logic
        createCheck(checkBB,bb,dup,false,ir);

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
  private final static void createCheck(OPT_BasicBlock checkBB, 
                                OPT_BasicBlock noInstBB,
                                OPT_BasicBlock instBB,
                                boolean fallthroughToInstBB,
                                OPT_IR ir) {

    appendLoad(checkBB,ir);

    // Depending on where you fallthrough, set the condition correctly
    OPT_ConditionOperand cond = null;
    OPT_BranchOperand target = null;
    OPT_BranchProfileOperand profileOperand = null;
    if (fallthroughToInstBB) {
      // The instrumented basic block is the fallthrough of checkBB,
      // so make the branch jump to the non-instrumented block.
      cond = OPT_ConditionOperand.GREATER();
      target = noInstBB.makeJumpTarget();
      profileOperand = new OPT_BranchProfileOperand(1.0f); // Taken frequently
    }
    else {
      // The non-instrumented basic block is the fallthrough of checkBB,
      // so make the branch jump to the instrumented block.
      cond = OPT_ConditionOperand.LESS_EQUAL();
      target = instBB.makeJumpTarget();
      profileOperand = new OPT_BranchProfileOperand(0.0f); // Taken infrequently
    }
    OPT_RegisterOperand guard = ir.regpool.makeTempValidation();
    checkBB.appendInstruction(IfCmp.create(INT_IFCMP, 
                                           guard,
                                           cbsReg.copyRO(),
                                           new OPT_IntConstantOperand(0),
                                           cond,
                                           target,
                                           profileOperand));
    checkBB.recomputeNormalOut(ir);

    // Insert the decrement and store in the block that is the
    // successor of the check
    prependStore(noInstBB,ir);
    prependDecrement(noInstBB,ir);
    
    // Insert a counter reset in the duplicated block.
    prependCounterReset(instBB,ir);
    
    }


  /**
   * Append a load of the global counter to the given basic block.
   *
   * WARNING: Tested for LIR only!
   *
   * @param bb The block to append the load to
   * @param ir The IR */
  private static void appendLoad(OPT_BasicBlock bb, 
                                OPT_IR ir) {

    if (DEBUG)VM.sysWrite("Adding load to "+ bb + "\n");
    OPT_Instruction load = null;
    if (ir.options.PROCESSOR_SPECIFIC_COUNTER) {
      // Use one CBS counter per processor (better for multi threaded apps)

      if (ir.IRStage == ir.HIR) {
        // NOT IMPLEMENTED
      }
      else {
        // Phase is being used in LIR
        OPT_Instruction dummy = Load.create(INT_LOAD,null,null,null,null);
        
        // Insert the load instruction. 
        load = 
          Load.create(INT_LOAD, cbsReg.copyRO(), 
                      OPT_IRTools.R(ir.regpool.getPhysicalRegisterSet().getPR()),
                      OPT_IRTools.I(VM_Entrypoints.processorCBSField.getOffset()),
                      new OPT_LocationOperand(VM_Entrypoints.processorCBSField));
        
        bb.appendInstruction(load);
      }
    }
    else {
      // Use global counter
      if (ir.IRStage == ir.HIR) {
        OPT_Operand offsetOp = new OPT_IntConstantOperand(VM_Entrypoints.globalCBSField.getOffset());
        load = GetStatic.create(GETSTATIC,cbsReg.copyRO(),offsetOp, new OPT_LocationOperand(VM_Entrypoints.globalCBSField));
        bb.appendInstruction(load);
      }
      else {
        // LIR
        OPT_Instruction dummy = Load.create(INT_LOAD,null,null,null,null);
        
        bb.appendInstruction(dummy);
        load = Load.create(INT_LOAD, cbsReg.copyRO(), 
                           ir.regpool.makeJTOCOp(ir,dummy), 
                           OPT_IRTools.I(VM_Entrypoints.globalCBSField.getOffset()),
                           new OPT_LocationOperand(VM_Entrypoints.globalCBSField));
        
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
  private static void prependStore(OPT_BasicBlock bb, OPT_IR ir) {

    if (DEBUG)VM.sysWrite("Adding store to "+ bb + "\n");
    OPT_Instruction store = null;
    if (ir.options.PROCESSOR_SPECIFIC_COUNTER) {
      OPT_Instruction dummy = Load.create(INT_LOAD,null,null,null,null);
      store = Store.create(INT_STORE, 
                           cbsReg.copyRO(),
                           OPT_IRTools.R(ir.regpool.getPhysicalRegisterSet().getPR()),
                           OPT_IRTools.I(VM_Entrypoints.processorCBSField.getOffset()),
                           new OPT_LocationOperand(VM_Entrypoints.processorCBSField));


      bb.prependInstruction(store);
    }
    else {
      if (ir.IRStage == ir.HIR) {
        store = PutStatic.create(PUTSTATIC,cbsReg.copyRO(),new OPT_IntConstantOperand(VM_Entrypoints.globalCBSField.getOffset()),
                                 new OPT_LocationOperand(VM_Entrypoints.globalCBSField));
        
        bb.prependInstruction(store);
      } else {
        OPT_Instruction dummy = Load.create(INT_LOAD,null,null,null,null);

        bb.prependInstruction(dummy);
        store = Store.create(INT_STORE, 
                             cbsReg.copyRO(),
                             ir.regpool.makeJTOCOp(ir,dummy), 
                             OPT_IRTools.I(VM_Entrypoints.globalCBSField.getOffset()),
                             new OPT_LocationOperand(VM_Entrypoints.globalCBSField));
        
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
  private final static void prependDecrement(OPT_BasicBlock bb,
                                                OPT_IR ir) {
    if (DEBUG)VM.sysWrite("Adding Increment to "+ bb + "\n");

    OPT_RegisterOperand use = cbsReg.copyRO();
    OPT_RegisterOperand def = use.copyU2D();
    OPT_Instruction inc = Binary.create(INT_ADD,
                                        def,use, 
                                        OPT_IRTools.I(-1));
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
  private final static void prependCounterReset(OPT_BasicBlock bb,
                                       OPT_IR ir) {
    OPT_Instruction load = null;
    OPT_Instruction store = null;

    if (ir.IRStage == ir.HIR) {
      // Not tested
      OPT_Operand offsetOp = new OPT_IntConstantOperand(VM_Entrypoints.cbsResetValueField.getOffset());
      load = GetStatic.create(GETSTATIC,cbsReg.copyRO(),offsetOp,
                              new OPT_LocationOperand(VM_Entrypoints.cbsResetValueField));
      store = PutStatic.create(PUTSTATIC,cbsReg.copyRO(), new OPT_IntConstantOperand(VM_Entrypoints.globalCBSField.getOffset()),
                              new OPT_LocationOperand(VM_Entrypoints.globalCBSField));
      bb.prependInstruction(store);
      bb.prependInstruction(load);
    }
    else {
      // LIR
      OPT_Instruction dummy = Load.create(INT_LOAD,null,null,null,null);
      bb.prependInstruction(dummy);
      // Load the reset value
      load = Load.create(INT_LOAD, cbsReg.copyRO(), 
                         ir.regpool.makeJTOCOp(ir,dummy), 
                         OPT_IRTools.I(VM_Entrypoints.cbsResetValueField.getOffset()),
                         new OPT_LocationOperand(VM_Entrypoints.cbsResetValueField));
      
      dummy.insertBefore(load);

      // Store it in the counter register
      if (ir.options.PROCESSOR_SPECIFIC_COUNTER) {
        store = Store.create(INT_STORE, 
                             cbsReg.copyRO(),
                             OPT_IRTools.R(ir.regpool.getPhysicalRegisterSet().getPR()),
                             OPT_IRTools.I(VM_Entrypoints.processorCBSField.getOffset()),
                             new OPT_LocationOperand(VM_Entrypoints.processorCBSField));
      }
      else {
        // Use global counter
        store = Store.create(INT_STORE, 
                             cbsReg.copyRO(),
                             ir.regpool.makeJTOCOp(ir,dummy), 
                             OPT_IRTools.I(VM_Entrypoints.globalCBSField.getOffset()),
                             new OPT_LocationOperand(VM_Entrypoints.globalCBSField));
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
  private final static 
    OPT_Instruction getFirstInstWithOperator(OPT_Operator operator,
                                             OPT_BasicBlock bb) {
    for (OPT_InstructionEnumeration ie
           = bb.forwardInstrEnumerator();
         ie.hasMoreElements();) {
      OPT_Instruction i = ie.next();
      
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
  public final static 
    OPT_Instruction getFirstInstWithYieldPoint(OPT_BasicBlock bb) {
    for (OPT_InstructionEnumeration ie
           = bb.forwardInstrEnumerator();
         ie.hasMoreElements();) {
      OPT_Instruction i = ie.next();
      
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
  public final static boolean isYieldpoint(OPT_Instruction i) {
    if (i.operator() == YIELDPOINT_PROLOGUE ||
        // Skip epilogue yieldpoints.   No checks needed for them
        //        i.operator() == YIELDPOINT_EPILOGUE ||
        i.operator() == YIELDPOINT_BACKEDGE) 
      return true;

    return false;
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
  private final static void adjustPointersInDuplicatedCode(OPT_IR ir, HashMap origToDupMap){

    Hashtable handlerCache = new Hashtable();

    // Iterate over the original version of all duplicate blocks
    Iterator dupBlocks = origToDupMap.values().iterator();
    while (dupBlocks.hasNext()) {
      OPT_BasicBlock dupBlock = (OPT_BasicBlock) dupBlocks.next();

      // Look at the successors of duplicated Block. 
      for (OPT_BasicBlockEnumeration out = dupBlock.getNormalOut();
           out.hasMoreElements();) {
        OPT_BasicBlock origSucc = out.next();

        OPT_BasicBlock dupSucc =  (OPT_BasicBlock)origToDupMap.get(origSucc);

        // If the successor is not in the duplicated code, then
        // redirect it to stay in the duplicated code. (dupSucc !=
        // null implies that origSucc has a corresponding duplicated
        // block, and thus origSucc is in the checking code.

        if (dupSucc != null) {
          
          dupBlock.redirectOuts(origSucc,dupSucc,ir);
          if (DEBUG) {
            VM.sysWrite("Source: " + dupBlock + "\n");
            VM.sysWrite("============= FROM " + origSucc + 
                        " =============\n");
            //origSucc.printExtended();
            VM.sysWrite("============= TO " + dupSucc + 
                        "=============\n");
            //dupSucc.printExtended();
          }
        }
        else {
          if (DEBUG) 
            VM.sysWrite("Not adjusting pointer from " + dupBlock + 
                        " to " + origSucc + " because dupSucc is null\n");
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
  private final static void removeInstrumentationFromOrig(OPT_IR ir, HashMap origToDupMap){
    // Iterate over the original version of all duplicate blocks

    Iterator origBlocks = origToDupMap.keySet().iterator();
    while (origBlocks.hasNext()) {
      OPT_BasicBlock origBlock = (OPT_BasicBlock) origBlocks.next();
    
      // Remove all instrumentation instructions.  They already have
      // been transfered to the duplicated code.
      for (OPT_InstructionEnumeration ie
             = origBlock.forwardInstrEnumerator();
           ie.hasMoreElements();) {
        OPT_Instruction i = ie.next();
        if (isInstrumentationInstruction(i) ||
            (isYieldpoint(i) && ir.options.REMOVE_YP_FROM_CHECKING)) {

          if (DEBUG)VM.sysWrite("Removing " + i + "\n");
          i.remove(); 
        }
      }
    }  
  }



  /**
   * The same as OPT_BasicBlock.copyWithoutLinks except that it
   * renames all temp variables that are never used outside the basic
   * block.  This 1) helps eliminate the artificially large live
   * ranges that might have been created (assuming the reg allocator
   * isn't too smart) and 2) it prevents BURS from crashing.
   *
   * PRECONDITION:  the spansBasicBlock bit must be correct by calling
   *                OPT_DefUse.recomputeSpansBasicBlock(OPT_IR);
   * 
   */
  private final static OPT_BasicBlock myCopyWithoutLinks(OPT_BasicBlock bb,
                                                 OPT_IR ir) {

    OPT_BasicBlock newBlock = bb.copyWithoutLinks(ir);

    // Without this, there were sanity errors at one point.
    updateTemps(newBlock,ir);

    return newBlock;
  }

  /**
   * Given an basic block, rename all of the temporary registers that are local to the block.
   */
  private final static void updateTemps(OPT_BasicBlock bb, OPT_IR ir) {

    // Need to clear the scratch objects before we start using them
    clearScratchObjects(bb,ir);

    // For each instruction in the block
    for (OPT_InstructionEnumeration ie = bb.forwardInstrEnumerator();
         ie.hasMoreElements();) {
      OPT_Instruction inst = ie.next();

      // Look at each register operand
      int numOperands = inst.getNumberOfOperands();
      for (int i = 0; i < numOperands; i++) {
        OPT_Operand op = inst.getOperand(i);
        if (op instanceof OPT_RegisterOperand) {
          OPT_RegisterOperand ro = (OPT_RegisterOperand) op;
          if (ro.register.isTemp() &&
              !ro.register.spansBasicBlock()) {
            // This register does not span multiple basic blocks, so 
            // replace it with a temp.
            OPT_RegisterOperand newReg = 
              getOrCreateDupReg(ro, ir);
            if (DEBUG2) 
              VM.sysWrite( "Was " + ro + " and now it's " + newReg + "\n");
            inst.putOperand(i,newReg);
          }
        }
      }
    }

    // Clear them afterward also, otherwise register allocation fails.
    // (TODO: Shouldn't they just be cleared before use in register
    // allocation?)
    clearScratchObjects(bb,ir);
  }

  /**
   *  Go through all statements in the basic block and clear the
   *  scratch objects.
   */
  private final static void clearScratchObjects(OPT_BasicBlock bb, OPT_IR ir) {
    // For each instruction in the block
    for (OPT_InstructionEnumeration ie = bb.forwardInstrEnumerator();
         ie.hasMoreElements();) {
      OPT_Instruction inst = ie.next();

      // Look at each register operand
      int numOperands = inst.getNumberOfOperands();
      for (int i = 0; i < numOperands; i++) {
        OPT_Operand op = inst.getOperand(i);
        if (op instanceof OPT_RegisterOperand) {
          OPT_RegisterOperand ro = (OPT_RegisterOperand) op;
          if (ro.register.isTemp() &&
              !ro.register.spansBasicBlock()) {

            // This register does not span multiple basic blocks.  It
            // will be touched by the register duplication, so clear
            // its scratch reg.
            ro.register.scratchObject=null;
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
  private final static 
    OPT_RegisterOperand getOrCreateDupReg(OPT_RegisterOperand ro, 
                                                     OPT_IR ir) {

    // Check if the register associated with this regOperand already
    // has a paralles operand
    if (ro.register.scratchObject == null) {
      // If no dup register exists, make a new one and remember it.
      OPT_RegisterOperand dupRegOp = ir.regpool.makeTemp(ro.type);
      ro.register.scratchObject = dupRegOp.register;
    }
    return new
      OPT_RegisterOperand((OPT_Register)ro.register.scratchObject,
                          ro.type);
  }

  /**
   * Perform the NoDuplication version of the framework (see
   * Arnold-Ryder PLDI 2001).  Instrumentation operations are wrapped
   * in a conditional, but no code duplication is performed.
   */
  private static void performVariationNoDuplication(OPT_IR ir) {
    // The register containing the counter value to check
    cbsReg = ir.regpool.makeTempInt();

    Vector v = new Vector();
    for (OPT_BasicBlockEnumeration allBB = ir.getBasicBlocks(); 
         allBB.hasMoreElements(); ) {
      OPT_BasicBlock bb = allBB.next();

      for (OPT_InstructionEnumeration ie
             = bb.forwardInstrEnumerator();
           ie.hasMoreElements();) {
        OPT_Instruction i = ie.next();          

        // If it's an instrumentation operation, remember the instruction 
        if (isInstrumentationInstruction(i))
          v.add(i);
      }
    }

    // for each instrumentation operation.
    Enumeration e = v.elements();
    while (e.hasMoreElements()) {
      OPT_Instruction i = (OPT_Instruction)e.nextElement();

      OPT_BasicBlock bb = i.getBasicBlock();
      conditionalizeInstrumentationOperation(ir,i,bb);
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
  private static final void 
    conditionalizeInstrumentationOperation(OPT_IR ir,
                                           OPT_Instruction i,
                                           OPT_BasicBlock bb) {

    // Create bb after instrumentation ('C', in comment above)
    OPT_BasicBlock C = bb.splitNodeWithLinksAt(i,ir); 
    bb.recomputeNormalOut(ir);

    // Create bb before instrumentation ('A', in comment above)
    OPT_Instruction prev = i.prevInstructionInCodeOrder();

    OPT_BasicBlock B = null;
    try {
      B = bb.splitNodeWithLinksAt(prev,ir); 
    }
    catch (RuntimeException e) {
      VM.sysWrite("Bombed when I: " + i + " prev: " + prev + "\n");
      bb.printExtended();
      throw e;
    }

    bb.recomputeNormalOut(ir);
    
    OPT_BasicBlock A = bb;
    
    // Now, add check to A, that jumps to C
    createCheck(A,C,B,true,ir);
  }

  /**
   * How to determine whether a given instruction is an
   * "instrumentation instruction".  In other words, it should be
   * executed only when a sample is being taken.  
   *
   */
  final private static boolean isInstrumentationInstruction(OPT_Instruction i) {
    
    //    if (i.bcIndex == INSTRUMENTATION_BCI &&
    // (Call.conforms(i) ||
    // if (InstrumentedCounter.conforms(i)))

    if (InstrumentedCounter.conforms(i))
      return true;

    return false;
  }

  /**
   * Temp debugging code 
   */
  public static void dumpCFG(OPT_IR ir) {

    for (OPT_BasicBlockEnumeration allBB = ir.getBasicBlocks();
         allBB.hasMoreElements(); ) {
      OPT_BasicBlock curBB = allBB.next();
      curBB.printExtended();
    }
  }        

  
  /**
   * Temporary static variables
   */
  private static OPT_RegisterOperand cbsReg = null;
  private static OPT_LocationOperand cbsLoc = null;
  private static OPT_LocationOperand resetLoc = null;
  private static OPT_BasicBlock origLastBlock = null;

}



