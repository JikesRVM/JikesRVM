/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.*;
import java.util.Enumeration;

import org.vmmagic.pragma.*;

/**
 * An <code>OPT_IR</code> object (IR is short for Intermediate Representation)
 * contains all the per-compilation information associated with 
 * a method that is being compiled. 
 * <p>
 * <code>OPT_IR</code> objects are intended to be transitory. 
 * They are created to compile a particular method under a 
 * given {@link OPT_CompilationPlan compilation plan}
 * and are discarded once the compilation plan has been completed.
 * <p>
 * The primary component of the IR is the 
 * {@link OPT_ControlFlowGraph <em>FCFG</em>} (factored control flow graph)
 * The FCFG contains 
 * {@link OPT_Instruction intermediate language instructions}
 * grouped into {@link OPT_BasicBlock factored basic blocks}.
 * In addition to the FCFG, an <code>OPT_IR</code> object also 
 * contains a variety of other supporting and derived data structures.
 * <p>
 * 
 * @see OPT_ControlFlowGraph
 * @see OPT_BasicBlock
 * @see OPT_Instruction
 * @see OPT_Operator
 * @see OPT_Operand
 * 
 * @author Dave Grove
 * @author John Whaley
 * @author Stephen Fink
 * @author Mauricio J. Serrano
 * @author Martin Trapp
 */
public final class OPT_IR implements OPT_Operators {

  /**
   * Control for (dynamic) IR invariant checking.
   * By default, SANITY_CHECK == {@link VM#VerifyAssertions}.
   * When SANITY_CHECK is <code>true</code>, critical invariants
   * are checked by complex routines that depend on them, 
   * and {@link #verify(String) verify} is invoked several times 
   * during compilation.
   */
  public static final boolean SANITY_CHECK = VM.VerifyAssertions;

  /**
   * Control for (dynamic) IR invariant checking.
   * By default PARANOID is <code>false</code>. 
   * PARANOID must not be true unless {@link VM#VerifyAssertions}
   * is also <code>true</code>.
   * When PARANOID is <code>true</code> many IR utility functions
   * check the invariants on which they depend, and 
   * {@link #verify(String, boolean)} is invoked as each 
   * compilation phase is 
   * {@link OPT_CompilerPhase#performPhase(OPT_IR) performed}.
   */
  public static final boolean PARANOID = false && SANITY_CHECK;

  /** Part of an enumerated type used to encode IR Level */
  public static final byte UNFORMED = 0;
  /** Part of an enumerated type used to encode IR Level */
  public static final byte HIR = 1;
  /** Part of an enumerated type used to encode IR Level */
  public static final byte LIR = 2;
  /** Part of an enumerated type used to encode IR Level */
  public static final byte MIR = 3;


  /**
   * The {@link VM_NormalMethod} object corresponding to the 
   * method being compiled. Other methods may have been inlined into
   * the IR during compilation, so method really only represents the 
   * primary or outermost method being compiled.
   */
  public VM_NormalMethod method;

  /**
   * @return The {@link VM_NormalMethod} object corresponding to the 
   * method being compiled. Other methods may have been inlined into
   * the IR during compilation, so method really only represents the 
   * primary or outermost method being compiled.
   */
  public VM_NormalMethod getMethod() { 
    return method;
  }

  /**
   * The compiled method created to hold the result of this compilation.
   */
  public VM_OptCompiledMethod compiledMethod;

  /**
   * The compiler {@link OPT_Options options} that apply
   * to the current compilation.
   */
  public OPT_Options options;

  /** 
   * {@link OPT_SSAOptions Options} that define the SSA properties
   * desired the next time we enter SSA form.
   */
  public OPT_SSAOptions desiredSSAOptions; 

  /** 
   * {@link OPT_SSAOptions Options} that define the SSA properties
   * currently carried by the IR.  Compiler phases that are invoked
   * on SSA form should update this object to reflect transformations
   * on SSA form.
   */
  public OPT_SSAOptions actualSSAOptions; 

  /**
   * The root {@link OPT_GenerationContext generation context}
   * for the current compilation.
   */
  public OPT_GenerationContext gc;

  /**
   * The {@link OPT_InlineOracle inlining oracle} to be used for the 
   * current compilation. 
   * TODO: It would make more sense to have the inlining oracle be 
   * a component of the generation context, but as things currently
   * stand the IR is created before the generation context.  We might be 
   * able to restructure things such that the generation context is 
   * created in the OPT_IR constructor and then eliminate this field,
   * replacing all uses with gc.inlinePlan instead.
   */
  public OPT_InlineOracle inlinePlan;

  /**
   * Information specifying what instrumentation should be performed
   * during compilation of this method.  
   */
  public OPT_InstrumentationPlan instrumentationPlan;

  /**
   * The {@link OPT_ControlFlowGraph FCFG} (Factored Control Flow Graph)
   */
  public OPT_ControlFlowGraph cfg;

  /**
   * The {@link OPT_RegisterPool Register pool}
   */
  public OPT_RegisterPool regpool;

  /**
   * The {@link OPT_GenericStackManager stack manager}.
   */
  public OPT_GenericStackManager stackManager = new OPT_StackManager();

  /**
   * The IR is tagged to identify its level (stage).
   * As compilation continues, the level monotonically
   * increases from {@link #UNFORMED} to {@link #HIR}
   * to {@link #LIR} to {@link #MIR}.
   */
  public byte IRStage = UNFORMED;

  /**
   *  Was liveness for handlers computed?
   */
  private boolean handlerLivenessComputed = false;

  /**
   * Pointer to the HIRInfo for this method.  
   * Valid only if {@link #IRStage}>=HIR
   */
  public OPT_HIRInfo HIRInfo;

  /**
   * Pointer to the LIRInfo for this method.
   * Valid only if {@link #IRStage}>=LIR.
   */
  public OPT_LIRInfo LIRInfo;

  /**
   * Pointer to the MIRInfo for this method.  
   * Valid only if {@link #IRStage}>=MIR.
   */
  public OPT_MIRInfo MIRInfo;

  /**
   * Backing store for {@link #getBasicBlock(int)}.
   */
  private OPT_BasicBlock[] basicBlockMap;

  /**
   * Does this IR include a syscall?
   * Initialized during lir to mir conversion;
   */
  private boolean hasSysCall = false;
  public boolean hasSysCall() { return hasSysCall; }
  public void setHasSysCall(boolean b) { hasSysCall = b; }
  
  /**
   * @param m    The method to compile
   * @param ip   The inlining oracle to use for the compilation
   * @param opts The options to use for the compilation
   */
  public OPT_IR(VM_NormalMethod m, OPT_InlineOracle ip, OPT_Options opts) {
    method = m;
    options = opts;
    inlinePlan = ip;
  }

  /**
   * @param m    The method to compile
   * @param cp   The compilation plan to execute
   */
  public OPT_IR(VM_NormalMethod m, OPT_CompilationPlan cp) {
    method = m;
    options = cp.options;
    inlinePlan = cp.inlinePlan;
    instrumentationPlan = cp.instrumentationPlan;
  }

  
  /**
   * Print the instructions in this IR to System.out.
   */
  public void printInstructions() {
    for (OPT_InstructionEnumeration e = forwardInstrEnumerator(); 
         e.hasMoreElements(); ) {
      OPT_Instruction i = e.next();
      System.out.print(i.bcIndex+"\t"+i);

      // Print block frequency with the label instruction
      if (i.operator() == LABEL) {
        OPT_BasicBlock bb = i.getBasicBlock();
        System.out.print("   Frequency:  " + bb.getExecutionFrequency());
      }

      System.out.println();
    }
  }


  /**
   * Return the first instruction with respect to 
   * the current code linearization order.
   * 
   * @return the first instruction in the code order
   */
  public final OPT_Instruction firstInstructionInCodeOrder() {
    return firstBasicBlockInCodeOrder().firstInstruction();
  }

  /**
   * Return the last instruction with respect to 
   * the current code linearization order.
   * 
   * @return the last instruction in the code order
   */
  public final OPT_Instruction lastInstructionInCodeOrder() {
    return lastBasicBlockInCodeOrder().lastInstruction();
  }


  /**
   * Return the first basic block with respect to 
   * the current code linearization order.
   * 
   * @return the first basic block in the code order
   */
  public OPT_BasicBlock firstBasicBlockInCodeOrder() {
    return cfg.firstInCodeOrder();
  }


  /**
   * Return the last basic block with respect to 
   * the current code linearization order.
   * 
   * @return the last basic block in the code order
   */
  public OPT_BasicBlock lastBasicBlockInCodeOrder() {
    return cfg.lastInCodeOrder();
  }

  
  /**
   * Forward (with respect to the current code linearization order) 
   * iteration over all the instructions in this IR.
   * The IR must <em>not</em> be modified during the iteration.
   *
   * @return an OPT_InstructionEnumeration that enumerates the
   *         instructions in forward code order.
   */
  public OPT_InstructionEnumeration forwardInstrEnumerator() {
    return OPT_IREnumeration.forwardGlobalIE(this);
  }

  /**
   * Reverse (with respect to the current code linearization order) 
   * iteration over all the instructions in this IR.
   * The IR must <em>not</em> be modified during the iteration.
   *
   * @return an OPT_InstructionEnumeration that enumerates the
   *         instructions in reverse code order.
   */
  public OPT_InstructionEnumeration reverseInstrEnumerator() {
    return OPT_IREnumeration.reverseGlobalIE(this);
  }


  /**
   * Enumerate the basic blocks in the IR in an arbitrary order.
   * 
   * @return an OPT_BasicBlockEnumeration that enumerates the
   *         basic blocks in an arbitrary order.
   */
  public OPT_BasicBlockEnumeration getBasicBlocks() {
    return OPT_IREnumeration.forwardBE(this);
  }

  /**
   * Forward (with respect to the current code linearization order)
   * iteration overal all the basic blocks in the IR.
   * 
   * @return an OPT_BasicBlockEnumeration that enumerates the
   *         basic blocks in forward code order.
   */
  public OPT_BasicBlockEnumeration forwardBlockEnumerator() {
    return OPT_IREnumeration.forwardBE(this);
  }

  /**
   * Reverse (with respect to the current code linearization order)
   * iteration overal all the basic blocks in the IR.
   * 
   * @return an OPT_BasicBlockEnumeration that enumerates the
   *         basic blocks in reverse code order.
   */
  public OPT_BasicBlockEnumeration reverseBlockEnumerator() {
    return OPT_IREnumeration.reverseBE(this);
  }


  /**
   * Return an enumeration of the parameters to the IR
   * Warning: Only valid before register allocation (see OPT_CallingConvention)
   *
   * @return the parameters of the IR.
   */
  public OPT_OperandEnumeration getParameters() {
    for (OPT_Instruction s = firstInstructionInCodeOrder(); 
         true; 
         s = s.nextInstructionInCodeOrder()) {
      if (s.operator() == IR_PROLOGUE) {
        return s.getDefs();
      }
    }    
  }


  /**
   * Is the operand a parameter of the IR?
   * Warning: Only valid before register allocation (see OPT_CallingConvention)
   * 
   * @param op the operand to check
   * @return true if the op is a parameter to the IR, false otherwise
   */
  public boolean isParameter(OPT_Operand op) {
    for (OPT_OperandEnumeration e = getParameters(); e.hasMoreElements();) {
      if (e.next().similar(op)) return true;
    }
    return false;
  }

  
  /**
   * How many bytes of parameters does this method take?
   */
  public int incomingParameterBytes() {
    int nWords = method.getParameterWords();
    // getParameterWords() does not include the implicit 'this' parameter.
    if (!method.isStatic()) nWords++;
    return nWords << 2;
  }


  /** 
   * Recompute the basic block map, so can use getBasicBlock(int)
   * to index into the basic blocks quickly.
   * TODO: think about possibly keeping the basic block map up-to-date 
   *       automatically (Use a hashtable, perhaps?).
   */
  public void resetBasicBlockMap() {
    basicBlockMap = new OPT_BasicBlock[getMaxBasicBlockNumber()+1] ;
    for (Enumeration enum = cfg.nodes(); enum.hasMoreElements();) {
      OPT_BasicBlock block = (OPT_BasicBlock) enum.nextElement();
      basicBlockMap[block.getNumber()] = block;
    }
  }

  /** 
   * Get the basic block with a given number. 
   * PRECONDITION: {@link #resetBasicBlockMap} has been called 
   * before calling this function, but after making any changes to 
   * the set of basic blocks in the IR. 
   * 
   * @param number the number of the basic block to retrieve
   * @return that requested block
   */
  public OPT_BasicBlock getBasicBlock(int number){
    if (VM.VerifyAssertions) VM._assert(basicBlockMap != null);
    return basicBlockMap[number];
  }

  /** 
   * Get an enumeration of all the basic blocks whose numbers 
   * appear in the given BitSet.
   * PRECONDITION: {@link #resetBasicBlockMap} has been called 
   * before calling this function, but after making any changes to 
   * the set of basic blocks in the IR. 
   * 
   * @param bits The BitSet that defines which basic blocks to
   *             enumerate.
   * @return an enumeration of said blocks.
   */
  public OPT_BasicBlockEnumeration getBasicBlocks(OPT_BitVector bits) {
    return new BitSetBBEnum(this,bits);
  }

  // TODO: It would be easy to avoid creating the Stack if we switch to
  //       the "advance" pattern used in OPT_BasicBlock.BBEnum.
  // TODO: Make this an anonymous local class.
  private static class BitSetBBEnum implements OPT_BasicBlockEnumeration {
    private java.util.Stack stack;
    protected BitSetBBEnum(OPT_IR ir, OPT_BitVector bits) {
      stack = new java.util.Stack();
      int size = bits.length();
      Enumeration enum = ir.getBasicBlocks();
      for ( ; enum.hasMoreElements(); ) {
        OPT_BasicBlock block = (OPT_BasicBlock) enum.nextElement();
        int number = block.getNumber();
        if (number < size && bits.get(number)) stack.push(block);
      }
    }
    public final boolean hasMoreElements() { return !stack.empty(); }
    public final Object nextElement() { return stack.pop(); }
    public final OPT_BasicBlock next() {
      return (OPT_BasicBlock)stack.pop();
    }
  }


  /**
   * Densely number all the instructions currently in this IR 
   * from 0...numInstr-1.
   * Returns the number of instructions in the IR.
   * Intended style of use:
   * <pre>
   *    passInfo = new passInfoObjects[ir.numberInstructions()];
   *    ...do analysis using passInfo as a look aside 
   *            array holding pass specific info...
   * </pre>
   * 
   * @return the number of instructions
   */
  public int numberInstructions() {
    int num = 0;
    for (OPT_Instruction instr = firstInstructionInCodeOrder();
         instr != null;
         instr = instr.nextInstructionInCodeOrder(), num++) {
      instr.scratch = num;
    }
    return num;
  }


  /**
   * Set the scratch word on all instructions currently in this 
   * IR to a given value.
   *
   * @param value value to store in all instruction scratch words
   */
  public void setInstructionScratchWord(int value) {
    for (OPT_Instruction instr = firstInstructionInCodeOrder();
         instr != null;
         instr = instr.nextInstructionInCodeOrder()) {
      instr.scratch = value;
    }
  }

  /**
   * Clear (set to zero) the scratch word on all 
   * instructions currently in this IR.
   */
  public void clearInstructionScratchWord() {
    setInstructionScratchWord(0);
  }

  /**
   * Clear (set to null) the scratch object on 
   * all instructions currently in this IR.
   */
  public void clearInstructionScratchObject() {
    for (OPT_Instruction instr = firstInstructionInCodeOrder();
         instr != null;
         instr = instr.nextInstructionInCodeOrder()) {
      instr.scratchObject = null;
    }
  }


  /**
   * Clear (set to null) the scratch object on 
   * all basic blocks currently in this IR.
   */
  public void clearBasicBlockScratchObject() {
    OPT_BasicBlockEnumeration e = getBasicBlocks();
    while (e.hasMoreElements()) {
      e.next().scratchObject = null;
    }
  }


  /**
   * Return the number of symbolic registers for this IR
   */
  public int getNumberOfSymbolicRegisters() {
    return regpool.getNumberOfSymbolicRegisters();
  }


  /**
   * @return the largest basic block number assigned to
   *         a block in the IR. Will return -1 if no 
   *         block numbers have been assigned.
   */
  public int getMaxBasicBlockNumber() {
    if (cfg == null)
      return -1;
    else
      return cfg.numberOfNodes();
  }

  /**
   * Prune the exceptional out edges for each basic block in the IR.
   */
  public void pruneExceptionalOut() {
    if (hasReachableExceptionHandlers()) {
      for (Enumeration e = getBasicBlocks(); e.hasMoreElements(); ) {
        OPT_BasicBlock bb = (OPT_BasicBlock)e.nextElement();
        bb.pruneExceptionalOut(this);
      } 
    }      
  }


  /**
   * @return <code>true</code> if it is possible that the IR contains
   *         an exception handler, <code>false</code> if it is not.
   *         Note this method may conservatively return <code>true</code>
   *         even if the IR does not actually contain a reachable 
   *         exception handler.
   */
  public boolean hasReachableExceptionHandlers() {
    return gc.generatedExceptionHandlers;
  }

  /**
   * Partially convert the FCFG into a more traditional
   * CFG by splitting all nodes that contain PEIs and that
   * have reachable exception handlers into multiple basic
   * blocks such that the instructions in the block have 
   * the expected post-dominance relationship. Note, we do
   * not bother to unfactor basic blocks that do not have reachable
   * exception handlers because the fact that the post-dominance
   * relationship between instructions does not hold in these blocks
   * does not matter (at least for intraprocedural analyses).
   * For more information {@link OPT_BasicBlock see}.
   */
  public void unfactor() {
    OPT_BasicBlockEnumeration e = getBasicBlocks();
    while  (e.hasMoreElements()) {
      OPT_BasicBlock b = e.next();
      b.unfactor(this);
    }
  }

  /**
   * States whether liveness for handlers is available
   & @return whether liveness for handlers is available
   */
  public boolean getHandlerLivenessComputed() {
    return handlerLivenessComputed;
  }

  /**
   * Signfies that liveness for handlers is available
   */
  public void setHandlerLivenessComputed() {
    handlerLivenessComputed = true;
  }

  /**
   * Verify that the IR is well-formed.
   * NB: this is expensive -- be sure to guard invocations with 
   * debugging flags.
   * 
   * @param where phrase identifying invoking  compilation phase
   */
  public void verify(String where) {
    verify(where, true);
  }

  /**
   * Verify that the IR is well-formed.
   * NB: this is expensive -- be sure to guard invocations with 
   * debugging flags. 
   * 
   * The following invariants are checked:
   * <ul>
   * <li> All links in the code order of basic blocks are well formed.
   * <li> All links in the intra-block instruction lists are well formed.
   * <li> All links in the control flow graph are well formed.
   * <li> All blocks in the control flow graph are also in the code order.
   * <li> All blocks in the code order are also in the control flow graph.
   * <li> All non-parameter symbolic registers that appear as a use in 
   *      some instruction also are defined by some instruction.
   * <li> No symbolic register is used to hold more than 1 of the four
   *      kinds of values: int (includes references), long, float, and double.
   * </ul>
   * 
   * @param where    phrase identifying invoking  compilation phase
   * @param checkCFG should the CFG invariants be checked
   *                 (they can become invalid in "late" MIR).
   */
  public void verify(String where, boolean checkCFG) {
    // First, verify that the basic blocks are properly chained together
    // and that each basic block's instruction list is properly constructed.
    {
      OPT_BasicBlock cur = cfg.firstInCodeOrder();
      OPT_BasicBlock prev = null;
      while (cur != null) {
        if (cur.getPrev() != prev) {
          verror(where, 
                 "Prev link of "+cur+" does not point to "+prev);
        }

        // Verify cur's start and end instructions
        OPT_Instruction s = cur.start;
        OPT_Instruction e = cur.end;
        if (s == null) {
          verror(where, 
                 "Bblock "+cur+" has null start instruction");
        }
        if (e == null) {
          verror(where, 
                 "Bblock "+cur+" has null end instruction");
        }
        
        // cur has start and end instructions, 
        // make sure that they are locally ok.
        if (!s.isBbFirst()) {
          verror(where, 
                 "Instr "+s+" is first instr of "+cur+" but is not BB_FIRST");
        }
        if (s.getBasicBlock() != cur) {
          verror(where, 
                 "Instr "+s+" is first instr of "+cur+
                 " but points to BBlock "+s.getBasicBlock());
        }
        if (!e.isBbLast()) {
          verror(where, 
                 "Instr "+e+" is last instr of "+cur+" but is not BB_LAST");
        }
        if (e.getBasicBlock() != cur) {
          verror(where, 
                 "Instr "+e+" is last instr of "+cur+
                 " but points to BBlock "+e.getBasicBlock());
        }
        
        // Now check the integrity of the block's instruction list
        if (s.getPrev() != null) {
          verror(where, "Instr "+s+
                 " is the first instr of "+cur+" but has a predecessor "
                 +s.getPrev());
        }
        if (e.getNext() != null) {
          verror(where, "Instr "+s+
                 " is the last instr of "+cur+" but has a successor "
                 +e.getNext());
        }
        OPT_Instruction pp = s;
        OPT_Instruction p = s.getNext();
        boolean foundBranch = false;
        while (p != e) {
          if (p == null) {
            verror(where, "Fell off the instruction list in "
                   +cur+" before finding "+e);
          }
          if (p.getPrev() != pp) {
            verror(where, "Instr "+pp+" has next "+p
                   +" but "+p+" has prev "+p.getPrev());
          }
          if (!p.isBbInside()) {
            verror(where, "Instr "+p+
                   " should be inside "+cur+" but is not BBInside");
          }
          if (foundBranch && !p.isBranch()) {
            printInstructions();
            verror(where, "Non branch "+p+" after branch "+pp+" in "+cur);
          }
          if (p.isBranch() && p.operator() != LOWTABLESWITCH) {
            foundBranch = true;
            if (p.isUnconditionalBranch() && p.getNext() != e) {
              printInstructions();
              verror(where, "Unconditional branch "+p+
                     " does not end its basic block "+cur);
            }
          }
          pp = p;
          p = p.getNext();
        }       
        if (p.getPrev() != pp) {
          verror(where, "Instr "+pp+" has next "
                 +p+" but "+p+" has prev "+p.getPrev());
        }

        // initialize the mark bit for the bblist test below
        cur.scratch = 0;

        prev = cur;
        cur = (OPT_BasicBlock)cur.getNext();
      }
    }

    // Check that the CFG links are well formed
    final int inBBListMarker = 999;  // actual number is insignificant
    if (checkCFG) {
      final boolean VERIFY_CFG_EDGES = false;
      java.util.HashSet origOutSet = null;
      if (VERIFY_CFG_EDGES) origOutSet = new java.util.HashSet();

      for (OPT_BasicBlock cur = cfg.firstInCodeOrder();
           cur != null;
           cur = (OPT_BasicBlock)cur.getNext()) {

        // Check incoming edges
        for (OPT_BasicBlockEnumeration e = cur.getIn(); 
             e.hasMoreElements(); ) {
          OPT_BasicBlock pred = e.next();
          if (!pred.pointsOut(cur)) {
            verror(where, pred+
                   " is an inEdge of "+cur+" but "+cur+
                   " is not an outEdge of "+pred);
          }
        }

        // Check outgoing edges
        for (OPT_BasicBlockEnumeration e =cur.getOut();
             e.hasMoreElements(); ) {
          OPT_BasicBlock succ = e.next();
          if (!succ.pointsIn(cur)) {
            verror(where, succ+
                   " is an outEdge of "+cur+" but "+cur+
                   " is not an inEdge of "+succ);
          }
          // Remember the original out edges for CFG edge verification
          if (VERIFY_CFG_EDGES && IRStage<=LIR) origOutSet.add(succ);
        }


        if (VERIFY_CFG_EDGES && IRStage <= LIR) {
          // Next, check that the CFG links are semantically correct
          // (ie that the CFG links and branch instructions agree)
          // This done by calling recomputeNormalOut() and confirming
          // that nothing changes.
          cur.recomputeNormalOut(this);
          
          // Confirm outgoing edges didn't change
          for (OPT_BasicBlockEnumeration e =cur.getOut();
               e.hasMoreElements(); ) {
            OPT_BasicBlock succ = e.next();
            if (!origOutSet.contains(succ) &&
                !succ.isExit() // Sometimes recomput is conservative in adding edge to exit
                               // because it relies soley on the mayThrowUncaughtException
                               // flag.
                ) {
              cur.printExtended();
              verror(where, "An edge in the cfg was incorrect.  " + 
                     succ+ " was not originally an out edge of " + cur + 
                     " but it was after calling recomputeNormalOut()" );
            }
            origOutSet.remove(succ); // we saw it, so remove it
          }
          // See if there were any edges that we didn't see the second
          // time around
          if (!origOutSet.isEmpty()) {
            OPT_BasicBlock missing = 
              (OPT_BasicBlock) origOutSet.iterator().next();

            cur.printExtended();
            verror(where, "An edge in the cfg was incorrect.  " +
                   missing + " was originally an out edge of " + cur + 
                   " but not after calling recomputeNormalOut()");
          }
        }

        // mark this block because it is the bblist
        cur.scratch = inBBListMarker;
      }
      
      // Check to make sure that all blocks connected 
      // (via a CFG edge) to a block
      // that is in the bblist are also in the bblist
      for (OPT_BasicBlock cur = cfg.firstInCodeOrder();
           cur != null;
           cur = (OPT_BasicBlock)cur.getNext()) {
        for (OPT_BasicBlockEnumeration e = cur.getIn(); 
             e.hasMoreElements(); ) {
          OPT_BasicBlock pred = e.next();
          if (pred.scratch != inBBListMarker) {
            verror(where, "In Method "+ method.getName() +", "
                   + pred+" is an inEdge of "+cur+
                   " but it is not in the CFG!");
          }
        }
        for (OPT_BasicBlockEnumeration e = cur.getOut(); 
             e.hasMoreElements();) {
          OPT_BasicBlock succ = e.next();
          if (succ.scratch != inBBListMarker) {
            // the EXIT block is never in the BB list
            if (succ != cfg.exit()) { 
              verror(where, "In Method "+ method.getName() +", "+ succ+
                     " is an outEdge of "+cur+" but it is not in the CFG!");
            }
          }
        }
      }
    } // end CFG sanity check

    if (IRStage<MIR) {
      verifyRegisterDefs(where);
      verifyRegisterTypes(where);
    }
  }

  /**
   * Verify that every non-physical, non-parameter symbolic register
   * that has a use also has at least one def
   * 
   * @param where    phrase identifying invoking  compilation phase
   */
  private void verifyRegisterDefs(String where) {
    OPT_DefUse.computeDU(this);
    //TODO: (SJF)I hate the register list interface.  Re-do it.
    for (OPT_Register r = regpool.getFirstSymbolicRegister(); 
         r != null; 
         r = r.getNext()) {
      if (r.isPhysical()) continue;
      if (r.useList != null) {
        if (r.defList == null) {
          printInstructions();
          verror(where, "verifyRegisterDefs: " + r + " has use but no defs");
        }
      }
    }
  }

  /**
   * Verify that no register is used as a long type and an int type
   * PRECONDITION: register lists computed
   * 
   * @param where    phrase identifying invoking  compilation phase
   */
  private void verifyRegisterTypes(String where) {
    for (OPT_Register r = regpool.getFirstSymbolicRegister(); 
         r != null;
         r = (OPT_Register)r.getNext()) {
      // don't worry about physical registers
      if (r.isPhysical()) continue;

      int types = 0;
      if (r.isLong()) types++;
      if (r.isDouble()) types++;
      if (r.isInteger()) types++;
      if (r.isAddress()) types++;
      if (r.isFloat()) types++;
      if (types > 1) {
        verror(where, "Register " + r + " has incompatible types.");
      }
    }
  }

  private void verror(String where, String msg) throws NoInlinePragma {
    throw new OPT_OptimizingCompilerException("VERIFY: "+where, msg);
  }
}
