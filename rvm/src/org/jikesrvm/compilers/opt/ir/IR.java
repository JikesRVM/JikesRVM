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
package org.jikesrvm.compilers.opt.ir;

import static org.jikesrvm.compilers.opt.ir.IRDumpTools.dumpIR;
import static org.jikesrvm.compilers.opt.ir.Operators.ATHROW_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.BBEND_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_IFCMP_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_IFCMP_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.GOTO_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_IFCMP2_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_IFCMP_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IR_PROLOGUE;
import static org.jikesrvm.compilers.opt.ir.Operators.LABEL;
import static org.jikesrvm.compilers.opt.ir.Operators.LABEL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_IFCMP_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LOOKUPSWITCH_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LOWTABLESWITCH;
import static org.jikesrvm.compilers.opt.ir.Operators.PHI_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_IFCMP_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.RETURN_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.TABLESWITCH_opcode;
import static org.jikesrvm.runtime.UnboxedSizeConstants.LOG_BYTES_IN_ADDRESS;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Stack;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.opt.DefUse;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.bc2ir.GenerationContext;
import org.jikesrvm.compilers.opt.controlflow.Dominators;
import org.jikesrvm.compilers.opt.controlflow.LTDominators;
import org.jikesrvm.compilers.opt.driver.CompilationPlan;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.driver.InstrumentationPlan;
import org.jikesrvm.compilers.opt.inlining.InlineOracle;
import org.jikesrvm.compilers.opt.ir.operand.BranchProfileOperand;
import org.jikesrvm.compilers.opt.ir.operand.ConditionOperand;
import org.jikesrvm.compilers.opt.ir.operand.HeapOperand;
import org.jikesrvm.compilers.opt.ir.operand.InlinedOsrTypeInfoOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.TrapCodeOperand;
import org.jikesrvm.compilers.opt.ir.operand.ia32.BURSManagedFPROperand;
import org.jikesrvm.compilers.opt.ir.operand.ia32.IA32ConditionOperand;
import org.jikesrvm.compilers.opt.ir.operand.ppc.PowerPCConditionOperand;
import org.jikesrvm.compilers.opt.ir.operand.ppc.PowerPCTrapOperand;
import org.jikesrvm.compilers.opt.liveness.LiveInterval;
import org.jikesrvm.compilers.opt.regalloc.GenericStackManager;
import org.jikesrvm.compilers.opt.runtimesupport.OptCompiledMethod;
import org.jikesrvm.compilers.opt.ssa.HeapVariable;
import org.jikesrvm.compilers.opt.ssa.SSAOptions;
import org.jikesrvm.util.BitVector;
import org.vmmagic.pragma.NoInline;

/**
 * An <code>IR</code> object (IR is short for Intermediate Representation)
 * contains all the per-compilation information associated with
 * a method that is being compiled.
 * <p>
 * <code>IR</code> objects are intended to be transitory.
 * They are created to compile a particular method under a
 * given {@link CompilationPlan compilation plan}
 * and are discarded once the compilation plan has been completed.
 * <p>
 * The primary component of the IR is the
 * {@link ControlFlowGraph <em>FCFG</em>} (factored control flow graph)
 * The FCFG contains
 * {@link Instruction intermediate language instructions}
 * grouped into {@link BasicBlock factored basic blocks}.
 * In addition to the FCFG, an <code>IR</code> object also
 * contains a variety of other supporting and derived data structures.
 * <p>
 * The class {@link IRDumpTools} provides methods to dump the IR.
 *
 * @see ControlFlowGraph
 * @see BasicBlock
 * @see Instruction
 * @see Operator
 * @see Operand
 */
public final class IR {

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
   * {@link #verify(String,boolean)} is invoked as each
   * compilation phase is
   * {@link CompilerPhase#performPhase(IR) performed}.
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
   * The {@link NormalMethod} object corresponding to the
   * method being compiled. Other methods may have been inlined into
   * the IR during compilation, so method really only represents the
   * primary or outermost method being compiled.
   */
  public final NormalMethod method;

  /**
   * The specialized parameters to be used in place of those defined
   * in the NormalMethod.
   */
  public final TypeReference[] params;

  /**
   * @return The {@link NormalMethod} object corresponding to the
   * method being compiled. Other methods may have been inlined into
   * the IR during compilation, so method really only represents the
   * primary or outermost method being compiled.
   */
  public NormalMethod getMethod() {
    return method;
  }

  /**
   * The compiled method created to hold the result of this compilation.
   */
  public final OptCompiledMethod compiledMethod;

  /**
   * The compiler {@link OptOptions options} that apply
   * to the current compilation.
   */
  public final OptOptions options;

  /**
   * {@link SSAOptions Options} that define the SSA properties
   * desired the next time we enter SSA form.
   */
  public SSAOptions desiredSSAOptions;

  /**
   * {@link SSAOptions Options} that define the SSA properties
   * currently carried by the IR.  Compiler phases that are invoked
   * on SSA form should update this object to reflect transformations
   * on SSA form.
   */
  public SSAOptions actualSSAOptions;

  public boolean inSSAForm() {
    return (actualSSAOptions != null) && actualSSAOptions.getScalarValid();
  }

  public boolean inSSAFormAwaitingReEntry() {
    return (actualSSAOptions != null) && !actualSSAOptions.getScalarValid();
  }

  /**
   * The root {@link GenerationContext generation context}
   * for the current compilation.
   */
  private GenerationContext gc;

  /**
   * The {@link InlineOracle inlining oracle} to be used for the
   * current compilation.
   * TODO: It would make more sense to have the inlining oracle be
   * a component of the generation context, but as things currently
   * stand the IR is created before the generation context.  We might be
   * able to restructure things such that the generation context is
   * created in the IR constructor and then eliminate this field,
   * replacing all uses with gc.inlinePlan instead.
   */
  public final InlineOracle inlinePlan;

  /**
   * Information specifying what instrumentation should be performed
   * during compilation of this method.
   */
  public final InstrumentationPlan instrumentationPlan;

  /**
   * The {@link ControlFlowGraph FCFG} (Factored Control Flow Graph)
   */
  public ControlFlowGraph cfg;

  /**
   * The {@link GenericRegisterPool register pool}
   */
  public GenericRegisterPool regpool;

  /**
   * The {@link GenericStackManager stack manager}.
   */
  public final GenericStackManager stackManager;

  /**
   * The IR is tagged to identify its level (stage).
   * As compilation continues, the level monotonically
   * increases from {@link #UNFORMED} to {@link #HIR}
   * to {@link #LIR} to {@link #MIR}.
   */
  private byte IRStage = UNFORMED;

  /**
   *  Was liveness for handlers computed?
   */
  private boolean handlerLivenessComputed = false;

  /**
   * Information about liveness, {@code null} if not yet computed.
   */
  private LiveInterval livenessInformation;

  /**
   * Information about dominators as used for global code placement
   * during SSA. This dominator information is not to be confused
   * with the dominator information that is used to leave SSA form.
   * The field will be {@code null} if the dominator information
   * was not computed yet.
   */
  private Dominators dominators;

  /**
   * Information about dominators as used for leaving SSA form.
   * This dominator information is not to be confused
   * with the dominator information that is used to do global code
   * placement in the SSA form.
   * The field will be {@code null} if the dominator information
   * was not computed yet.
   */
  private LTDominators ltDominators;

  /**
   * Pointer to the HIRInfo for this method.
   * Valid only if {@link #IRStage}&gt;=HIR
   */
  public HIRInfo HIRInfo;

  /**
   * Pointer to the LIRInfo for this method.
   * Valid only if {@link #IRStage}&gt;=LIR.
   */
  public LIRInfo LIRInfo;

  /**
   * Pointer to the MIRInfo for this method.
   * Valid only if {@link #IRStage}&gt;=MIR.
   */
  public MIRInfo MIRInfo;

  /**
   * Backing store for {@link #getBasicBlock(int)}.
   */
  private BasicBlock[] basicBlockMap;

  /**
   * Does this IR include a syscall?
   * Initialized during lir to mir conversion;
   */
  private boolean hasSysCall = false;

  public boolean hasSysCall() {
    return hasSysCall;
  }

  public void setHasSysCall(boolean b) {
    hasSysCall = b;
  }

  /** id of the current phase. Used for printout options */
  private int phaseId;

  public void setIdForNextPhase() {
    phaseId++;
  }

  public String getIdForCurrentPhase() {
    return String.format("%03d", phaseId);
  }

  {
    if (VM.BuildForIA32) {
      stackManager = new org.jikesrvm.compilers.opt.regalloc.ia32.StackManager();
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      stackManager = new org.jikesrvm.compilers.opt.regalloc.ppc.StackManager();
    }
  }

  /**
   * @param m    The method to compile
   * @param ip   The inlining oracle to use for the compilation
   * @param opts The options to use for the compilation
   */
  public IR(NormalMethod m, InlineOracle ip, OptOptions opts) {
    method = m;
    params = null;
    options = opts;
    inlinePlan = ip;
    instrumentationPlan = null;
    compiledMethod = (OptCompiledMethod) CompiledMethods.createCompiledMethod(method, CompiledMethod.OPT);
  }

  /**
   * @param m    The method to compile
   * @param cp   The compilation plan to execute
   */
  public IR(NormalMethod m, CompilationPlan cp) {
    method = m;
    params = cp.params;
    options = cp.options;
    inlinePlan = cp.inlinePlan;
    instrumentationPlan = cp.instrumentationPlan;
    compiledMethod = (OptCompiledMethod) CompiledMethods.createCompiledMethod(method, CompiledMethod.OPT);
  }

  /**
   * Transfers HIR and misc state from a generation context to
   * this IR.
   * @param gc the context to transfer from
   */
  public void initializeStateForHIR(GenerationContext gc) {
    this.gc = gc;
    this.cfg = gc.getCfg();
    this.regpool = gc.getTemps();
    if (gc.requiresStackFrame()) {
      this.stackManager.forceFrameAllocation();
    }
    this.IRStage = IR.HIR;
    this.HIRInfo = new HIRInfo(this);
  }

  public void initializeStateForLIR() {
    this.IRStage = IR.LIR;
    this.LIRInfo = new LIRInfo(this);
  }

  public void initializeStateForMIR() {
    this.IRStage = IR.MIR;
    this.MIRInfo = new MIRInfo(this);
  }

  /**
   * Print the instructions in this IR to System.out.
   */
  public void printInstructions() {
    printInstructionsToStream(System.out);
  }

  public void printInstructionsToStream(PrintStream out) {
    for (Enumeration<Instruction> e = forwardInstrEnumerator(); e.hasMoreElements();) {
      Instruction i = e.nextElement();
      out.print(i.getBytecodeIndex() + "\t" + i);

      // Print block frequency with the label instruction
      if (i.operator() == LABEL) {
        BasicBlock bb = i.getBasicBlock();
        out.print("   Frequency:  " + bb.getExecutionFrequency());
      }

      out.println();
    }
  }

  /**
   * @return {@code true} if the IR is a high-level intermediate
   *  representation, {@code false} otherwise.
   *
   *  @see #IRStage
   */
  public boolean isHIR() {
    return IRStage == IR.HIR;
  }

  /**
   * @return {@code true} if the IR is a low-level intermediate
   *  representation, {@code false} otherwise.
   *
   *  @see #IRStage
   */
  public boolean isLIR() {
    return IRStage == IR.LIR;
  }

  /**
   * @return {@code true} if the IR is in a stage lather than the
   *  the high-level intermediate representation, {@code false}
   *  otherwise
   *
   *  @see #IRStage
   */
  public boolean isNotHIR() {
    return IRStage != IR.HIR;
  }

  /**
   * @return {@code true} if the IR is in a stage earlier than the
   *  the machine-dependent intermediate representation, {@code false}
   *  otherwise
   *
   *  @see #IRStage
   */
  public boolean isNotMIR() {
    return IRStage < IR.MIR;
  }

  /**
   * Should {@code strictfp} be adhered to for the given instructions?
   * <p>
   * Note: we currently don't support {@code strictfp} at all, so this method
   * is unused.
   *
   * @param is a sequence of instruction
   * @return {@code true} if any of the instructions requires
   *  {@code strictfp}
   */
  public static boolean strictFP(Instruction... is) {
    for (Instruction i : is) {
      if (i.position().method.isStrictFP()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Return the first instruction with respect to
   * the current code linearization order.
   *
   * @return the first instruction in the code order
   */
  public Instruction firstInstructionInCodeOrder() {
    return firstBasicBlockInCodeOrder().firstInstruction();
  }

  /**
   * Return the last instruction with respect to
   * the current code linearization order.
   *
   * @return the last instruction in the code order
   */
  public Instruction lastInstructionInCodeOrder() {
    return lastBasicBlockInCodeOrder().lastInstruction();
  }

  /**
   * Return the first basic block with respect to
   * the current code linearization order.
   *
   * @return the first basic block in the code order
   */
  public BasicBlock firstBasicBlockInCodeOrder() {
    return cfg.firstInCodeOrder();
  }

  /**
   * Return the last basic block with respect to
   * the current code linearization order.
   *
   * @return the last basic block in the code order
   */
  public BasicBlock lastBasicBlockInCodeOrder() {
    return cfg.lastInCodeOrder();
  }

  /**
   * Forward (with respect to the current code linearization order)
   * iteration over all the instructions in this IR.
   * The IR must <em>not</em> be modified during the iteration.
   *
   * @return an enumeration that enumerates the
   *         instructions in forward code order.
   */
  public Enumeration<Instruction> forwardInstrEnumerator() {
    return IREnumeration.forwardGlobalIE(this);
  }

  /**
   * Reverse (with respect to the current code linearization order)
   * iteration over all the instructions in this IR.
   * The IR must <em>not</em> be modified during the iteration.
   *
   * @return an enumeration that enumerates the
   *         instructions in reverse code order.
   */
  public Enumeration<Instruction> reverseInstrEnumerator() {
    return IREnumeration.reverseGlobalIE(this);
  }

  /**
   * Enumerate the basic blocks in the IR in an arbitrary order.
   *
   * @return an enumeration of {@link BasicBlock}s that enumerates the
   *         basic blocks in an arbitrary order.
   */
  public Enumeration<BasicBlock> getBasicBlocks() {
    return IREnumeration.forwardBE(this);
  }

  /**
   * Forward (with respect to the current code linearization order)
   * iteration overal all the basic blocks in the IR.
   *
   * @return an enumeration of {@link BasicBlock}s that enumerates the
   *         basic blocks in forward code order.
   */
  public Enumeration<BasicBlock> forwardBlockEnumerator() {
    return IREnumeration.forwardBE(this);
  }

  /**
   * Reverse (with respect to the current code linearization order)
   * iteration overal all the basic blocks in the IR.
   *
   * @return an enumeration of {@link BasicBlock}s that enumerates the
   *         basic blocks in reverse code order.
   */
  public Enumeration<BasicBlock> reverseBlockEnumerator() {
    return IREnumeration.reverseBE(this);
  }

  /**
   * Return an enumeration of the parameters to the IR
   * Warning: Only valid before register allocation (see CallingConvention)
   *
   * @return the parameters of the IR.
   */
  public Enumeration<Operand> getParameters() {
    for (Instruction s = firstInstructionInCodeOrder(); true; s = s.nextInstructionInCodeOrder()) {
      if (s.operator() == IR_PROLOGUE) {
        return s.getDefs();
      }
    }
  }

  /**
   * Is the operand a parameter of the IR?
   * Warning: Only valid before register allocation (see CallingConvention)
   *
   * @param op the operand to check
   * @return {@code true} if the op is a parameter to the IR, {@code false} otherwise
   */
  public boolean isParameter(Operand op) {
    for (Enumeration<Operand> e = getParameters(); e.hasMoreElements();) {
      if (e.nextElement().similar(op)) return true;
    }
    return false;
  }

  /**
   * How many bytes of parameters does this method take?
   *
   * @return number of bytes that are necessary to hold the method's
   *  parameters, including space for the {@code this} parameter
   *  if applicable
   *
   */
  public int incomingParameterBytes() {
    int nWords = method.getParameterWords();
    // getParameterWords() does not include the implicit 'this' parameter.
    if (!method.isStatic()) nWords++;
    return nWords << LOG_BYTES_IN_ADDRESS;
  }

  /**
   * Recompute the basic block map, so can use getBasicBlock(int)
   * to index into the basic blocks quickly.
   * TODO: think about possibly keeping the basic block map up-to-date
   *       automatically (Use a hashtable, perhaps?).
   */
  public void resetBasicBlockMap() {
    basicBlockMap = new BasicBlock[getMaxBasicBlockNumber() + 1];
    for (Enumeration<BasicBlock> bbEnum = cfg.basicBlocks(); bbEnum.hasMoreElements();) {
      BasicBlock block = bbEnum.nextElement();
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
  public BasicBlock getBasicBlock(int number) {
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
  public Enumeration<BasicBlock> getBasicBlocks(BitVector bits) {
    return new BitSetBBEnum(this, bits);
  }

  // TODO: It would be easy to avoid creating the Stack if we switch to
  //       the "advance" pattern used in BasicBlock.BBEnum.
  // TODO: Make this an anonymous local class.
  private static final class BitSetBBEnum implements Enumeration<BasicBlock> {
    private final Stack<BasicBlock> stack;

    BitSetBBEnum(IR ir, BitVector bits) {
      stack = new Stack<BasicBlock>();
      int size = bits.length();
      Enumeration<BasicBlock> bbEnum = ir.forwardBlockEnumerator();
      while (bbEnum.hasMoreElements()) {
        BasicBlock block = bbEnum.nextElement();
        int number = block.getNumber();
        if (number < size && bits.get(number)) stack.push(block);
      }
    }

    @Override
    public boolean hasMoreElements() {
      return !stack.empty();
    }

    @Override
    public BasicBlock nextElement() {
      return stack.pop();
    }
  }

  /**
   * Counts all the instructions currently in this IR.
   *
   * @return the number of instructions
   */
  public int countInstructions() {
    int num = 0;
    for (Instruction instr = firstInstructionInCodeOrder(); instr != null; instr =
        instr.nextInstructionInCodeOrder(), num++) {
    }
    return num;
  }

  /**
   * Densely numbers all the instructions currently in this IR
   * from 0...numInstr-1.
   *
   * @return a map that maps each instruction to its number
   */
  public Map<Instruction, Integer> numberInstructionsViaMap() {
    HashMap<Instruction, Integer> instructionNumbers = new HashMap<Instruction, Integer>();

    int num = 0;
    for (Instruction instr = firstInstructionInCodeOrder(); instr != null; instr =
        instr.nextInstructionInCodeOrder(), num++) {
      instructionNumbers.put(instr, Integer.valueOf(num));
    }
    return instructionNumbers;
  }

  /**
   * Returns the number of symbolic registers for this IR.
   *
   * @return number of symbolic registers that were allocated
   *  for this IR object
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
    if (cfg == null) {
      return -1;
    } else {
      return cfg.numberOfNodes();
    }
  }

  /**
   * Prune the exceptional out edges for each basic block in the IR.
   */
  public void pruneExceptionalOut() {
    if (hasReachableExceptionHandlers()) {
      for (Enumeration<BasicBlock> e = getBasicBlocks(); e.hasMoreElements();) {
        BasicBlock bb = e.nextElement();
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
    return gc.generatedExceptionHandlers();
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
   * For more information {@link BasicBlock see}.
   */
  public void unfactor() {
    Enumeration<BasicBlock> e = getBasicBlocks();
    while (e.hasMoreElements()) {
      BasicBlock b = e.nextElement();
      b.unfactor(this);
    }
  }

  public boolean getHandlerLivenessComputed() {
    return handlerLivenessComputed;
  }

  public void setHandlerLivenessComputed(boolean value) {
    handlerLivenessComputed = value;
  }

  public LiveInterval getLivenessInformation() {
    return livenessInformation;
  }

  public void setLivenessInformation(LiveInterval liveInterval) {
    this.livenessInformation = liveInterval;
  }

  public Dominators getDominators() {
    return dominators;
  }

  public void setDominators(Dominators dominators) {
    this.dominators = dominators;
  }

  public LTDominators getLtDominators() {
    return ltDominators;
  }

  public void setLtDominators(LTDominators ltDominators) {
    this.ltDominators = ltDominators;
  }

  /**
   * Verify that the IR is well-formed.<p>
   * NB: this is expensive -- be sure to guard invocations with
   * debugging flags.
   *
   * @param where phrase identifying invoking  compilation phase
   */
  public void verify(String where) {
    verify(where, true);
  }

  /**
   * Verify that the IR is well-formed.<p>
   * NB: this is expensive -- be sure to guard invocations with
   * debugging flags.
   *
   * @param where    phrase identifying invoking  compilation phase
   * @param checkCFG should the CFG invariants be checked
   *                 (they can become invalid in "late" MIR).
   */
  public void verify(String where, boolean checkCFG) {
    // Check basic block and the containing instruction construction
    verifyBBConstruction(where);

    if (checkCFG) {
      // Check CFG invariants
      verifyCFG(where);
    }

    if (IRStage < MIR) {
      // In HIR or LIR:
      // Simple def-use tests
      if (VM.BuildForPowerPC) {
        // only on PPC as def use doesn't consider def-use
        verifyRegisterDefs(where);
      }

      // Verify registers aren't in use for 2 different types
      verifyRegisterTypes(where);
    }

    if (PARANOID) {
      // Follow CFG checking use follows def (ultra-expensive)
      verifyUseFollowsDef(where);

      // Simple sanity checks on instructions
      verifyInstructions(where);
    }

    // Make sure CFG is in fit state for dominators
    // TODO: Enable this check; currently finds some broken IR
    //       that we need to fix.
    // verifyAllBlocksAreReachable(where);
  }

  /**
   * Verify basic block construction from the basic block and
   * instruction information.
   *
   * @param where    phrase identifying invoking  compilation phase
   */
  private void verifyBBConstruction(String where) {
    // First, verify that the basic blocks are properly chained together
    // and that each basic block's instruction list is properly constructed.
    BasicBlock cur = cfg.firstInCodeOrder();
    BasicBlock prev = null;
    while (cur != null) {
      if (cur.getPrev() != prev) {
        verror(where, "Prev link of " + cur + " does not point to " + prev);
      }

      // Verify cur's start and end instructions
      Instruction s = cur.start;
      Instruction e = cur.end;
      if (s == null) {
        verror(where, "Bblock " + cur + " has null start instruction");
      }
      if (e == null) {
        verror(where, "Bblock " + cur + " has null end instruction");
      }

      // cur has start and end instructions,
      // make sure that they are locally ok.
      if (!s.isBbFirst()) {
        verror(where, "Instr " + s + " is first instr of " + cur + " but is not BB_FIRST");
      }
      if (s.getBasicBlock() != cur) {
        verror(where, "Instr " + s + " is first instr of " + cur + " but points to BBlock " + s.getBasicBlock());
      }
      if (!e.isBbLast()) {
        verror(where, "Instr " + e + " is last instr of " + cur + " but is not BB_LAST");
      }
      if (e.getBasicBlock() != cur) {
        verror(where, "Instr " + e + " is last instr of " + cur + " but points to BBlock " + e.getBasicBlock());
      }

      // Now check the integrity of the block's instruction list
      if (s.getPrev() != null) {
        verror(where, "Instr " + s + " is the first instr of " + cur + " but has a predecessor " + s.getPrev());
      }
      if (e.getNext() != null) {
        verror(where, "Instr " + s + " is the last instr of " + cur + " but has a successor " + e.getNext());
      }
      Instruction pp = s;
      Instruction p = s.getNext();
      boolean foundBranch = false;
      while (p != e) {
        if (p == null) {
          verror(where, "Fell off the instruction list in " + cur + " before finding " + e);
        }
        if (p.getPrev() != pp) {
          verror(where, "Instr " + pp + " has next " + p + " but " + p + " has prev " + p.getPrev());
        }
        if (!p.isBbInside()) {
          verror(where, "Instr " + p + " should be inside " + cur + " but is not BBInside");
        }
        if (foundBranch && !p.isBranch()) {
          printInstructions();
          verror(where, "Non branch " + p + " after branch " + pp + " in " + cur);
        }
        if (p.isBranch() && p.operator() != LOWTABLESWITCH) {
          foundBranch = true;
          if (p.isUnconditionalBranch() && p.getNext() != e) {
            printInstructions();
            verror(where, "Unconditional branch " + p + " does not end its basic block " + cur);
          }
        }
        pp = p;
        p = p.getNext();
      }
      if (p.getPrev() != pp) {
        verror(where, "Instr " + pp + " has next " + p + " but " + p + " has prev " + p.getPrev());
      }

      prev = cur;
      cur = (BasicBlock) cur.getNext();
    }
  }

  /**
   * Verify control flow graph construction
   *
   * @param where    phrase identifying invoking  compilation phase
   */
  private void verifyCFG(String where) {
    // Check that the CFG links are well formed
    final boolean VERIFY_CFG_EDGES = false;
    int blockCountEstimate = getMaxBasicBlockNumber();
    HashSet<BasicBlock> seenBlocks = new HashSet<BasicBlock>(blockCountEstimate);
    HashSet<BasicBlock> origOutSet = null;
    if (VERIFY_CFG_EDGES) origOutSet = new HashSet<BasicBlock>();

    for (BasicBlock cur = cfg.firstInCodeOrder(); cur != null; cur = (BasicBlock) cur.getNext()) {

      // Check incoming edges
      for (Enumeration<BasicBlock> e = cur.getIn(); e.hasMoreElements();) {
        BasicBlock pred = e.nextElement();
        if (!pred.pointsOut(cur)) {
          verror(where, pred + " is an inEdge of " + cur + " but " + cur + " is not an outEdge of " + pred);
        }
      }

      // Check outgoing edges
      for (Enumeration<BasicBlock> e = cur.getOut(); e.hasMoreElements();) {
        BasicBlock succ = e.nextElement();
        if (!succ.pointsIn(cur)) {
          verror(where, succ + " is an outEdge of " + cur + " but " + cur + " is not an inEdge of " + succ);
        }
        // Remember the original out edges for CFG edge verification
        if (VERIFY_CFG_EDGES && IRStage <= LIR) origOutSet.add(succ);
      }

      if (VERIFY_CFG_EDGES && IRStage <= LIR) {
        // Next, check that the CFG links are semantically correct
        // (ie that the CFG links and branch instructions agree)
        // This done by calling recomputeNormalOut() and confirming
        // that nothing changes.
        cur.recomputeNormalOut(this);

        // Confirm outgoing edges didn't change
        for (Enumeration<BasicBlock> e = cur.getOut(); e.hasMoreElements();) {
          BasicBlock succ = e.nextElement();
          if (!origOutSet.contains(succ) && !succ.isExit() // Sometimes recomput is conservative in adding edge to exit
            // because it relies soley on the mayThrowUncaughtException
            // flag.
              ) {
            cur.printExtended();
            verror(where,
                   "An edge in the cfg was incorrect.  " +
                   succ +
                   " was not originally an out edge of " +
                   cur +
                   " but it was after calling recomputeNormalOut()");
          }
          origOutSet.remove(succ); // we saw it, so remove it
        }
        // See if there were any edges that we didn't see the second
        // time around
        if (!origOutSet.isEmpty()) {
          BasicBlock missing = origOutSet.iterator().next();

          cur.printExtended();
          verror(where,
                 "An edge in the cfg was incorrect.  " +
                 missing +
                 " was originally an out edge of " +
                 cur +
                 " but not after calling recomputeNormalOut()");
        }
      }

      // remember this block because it is the bblist
      seenBlocks.add(cur);
    }

    // Check to make sure that all blocks connected
    // (via a CFG edge) to a block
    // that is in the bblist are also in the bblist
    for (BasicBlock cur = cfg.firstInCodeOrder(); cur != null; cur = (BasicBlock) cur.getNext()) {
      for (Enumeration<BasicBlock> e = cur.getIn(); e.hasMoreElements();) {
        BasicBlock pred = e.nextElement();
        if (!seenBlocks.contains(pred)) {
          verror(where,
                 "In Method " +
                 method.getName() +
                 ", " +
                 pred +
                 " is an inEdge of " +
                 cur +
                 " but it is not in the CFG!");
        }
      }
      for (Enumeration<BasicBlock> e = cur.getOut(); e.hasMoreElements();) {
        BasicBlock succ = e.nextElement();
        if (!seenBlocks.contains(succ)) {
          // the EXIT block is never in the BB list
          if (succ != cfg.exit()) {
            verror(where,
                   "In Method " +
                   method.getName() +
                   ", " +
                   succ +
                   " is an outEdge of " +
                   cur +
                   " but it is not in the CFG!");
          }
        }
      }
    }
  }

  /**
   * Verify that every instruction:
   * <ul>
   * <li>1) has operands that back reference it</li>
   * <li>2) is valid for its position in the basic block</li>
   * <li>3) if we are MIR, has no guard operands</li>
   * <li>4) test instruction is canonical</li>
   * </ul>
   *
   * @param where phrase identifying invoking  compilation phase
   */
  private void verifyInstructions(String where) {
    Enumeration<BasicBlock> bbEnum = cfg.basicBlocks();
    while (bbEnum.hasMoreElements()) {
      BasicBlock block = bbEnum.nextElement();
      IREnumeration.AllInstructionsEnum instructions = new IREnumeration.AllInstructionsEnum(this, block);
      boolean startingInstructionsPassed = false;
      while (instructions.hasMoreElements()) {
        Instruction instruction = instructions.nextElement();
        // Perform (1) and (3)
        IREnumeration.AllUsesEnum useOperands = new IREnumeration.AllUsesEnum(this, instruction);
        while (useOperands.hasMoreElements()) {
          Operand use = useOperands.nextElement();
          if (use.instruction != instruction) {
            verror(where,
                   "In block " +
                   block +
                   " for instruction " +
                   instruction +
                   " the back link in the use of operand " +
                   use +
                   " is invalid and references " +
                   use
                       .instruction);
          }
          if ((IRStage >= MIR) && (use.isRegister()) && (use.asRegister().getRegister().isValidation())) {
            verror(where,
                   "In block " +
                   block +
                   " for instruction " +
                   instruction +
                   " the use operand " +
                   use +
                   " is invalid as it is a validation register and this IR is in MIR form");
          }
        }
        IREnumeration.AllDefsEnum defOperands = new IREnumeration.AllDefsEnum(this, instruction);
        while (defOperands.hasMoreElements()) {
          Operand def = defOperands.nextElement();
          if (def.instruction != instruction) {
            verror(where,
                   "In block " +
                   block +
                   " for instruction " +
                   instruction +
                   " the back link in the def of operand " +
                   def +
                   " is invalid and references " +
                   def
                       .instruction);
          }
          if ((IRStage >= MIR) && (def.isRegister()) && (def.asRegister().getRegister().isValidation())) {
            verror(where,
                   "In block " +
                   block +
                   " for instruction " +
                   instruction +
                   " the def operand " +
                   def +
                   " is invalid as it is a validation register and this IR is in MIR form");
          }
        }
        // Perform (4)
        if (Binary.conforms(instruction) && instruction.operator().isCommutative()) {
          Operand val1 = Binary.getVal1(instruction);
          if (val1.isConstant() && !val1.isMovableObjectConstant()) {
            verror(where, "Non-canonical commutative operation " + instruction);
          }
        }
        // Perform (2)
        // test for starting instructions
        if (!startingInstructionsPassed) {
          if (Label.conforms(instruction)) {
            continue;
          }
          if (Phi.conforms(instruction)) {
            if ((!inSSAForm()) && (!inSSAFormAwaitingReEntry())) {
              verror(where, "Phi node encountered but SSA not computed");
            }
            continue;
          }
          startingInstructionsPassed = true;
        }
        // main instruction location test
        switch (instruction.getOpcode()) {
          // Label and phi nodes must be at the start of a BB
          case PHI_opcode:
          case LABEL_opcode:
            verror(where, "Unexpected instruction in the middle of a basic block " + instruction);
            // BBend, Goto, IfCmp, TableSwitch, Return, Trap and Athrow
            // must all appear at the end of a basic block
          case INT_IFCMP_opcode:
          case INT_IFCMP2_opcode:
          case LONG_IFCMP_opcode:
          case FLOAT_IFCMP_opcode:
          case DOUBLE_IFCMP_opcode:
          case REF_IFCMP_opcode:
            instruction = instructions.nextElement();
            if (!Goto.conforms(instruction) && !BBend.conforms(instruction)) {
              if ((VM.BuildForIA32 && !org.jikesrvm.compilers.opt.ir.ia32.MIR_Branch.conforms(instruction)) ||
                  (VM.BuildForPowerPC && !org.jikesrvm.compilers.opt.ir.ppc.MIR_Branch.conforms(instruction))) {
                verror(where, "Unexpected instruction after IFCMP " + instruction);
              }
            }
            if (Goto.conforms(instruction) ||
                ((VM.BuildForIA32 && org.jikesrvm.compilers.opt.ir.ia32.MIR_Branch.conforms(instruction)) ||
                    (VM.BuildForPowerPC && org.jikesrvm.compilers.opt.ir.ppc.MIR_Branch.conforms(instruction)))) {
              instruction = instructions.nextElement();
              if (!BBend.conforms(instruction)) {
                verror(where, "Unexpected instruction after GOTO/MIR_BRANCH " + instruction);
              }
            }
            if (instructions.hasMoreElements()) {
              verror(where, "Unexpected instructions after BBEND " + instructions.nextElement());
            }
            break;
          case TABLESWITCH_opcode:
          case LOOKUPSWITCH_opcode:
          case ATHROW_opcode:
          case RETURN_opcode:
            // TODO: Traps should be at the end of basic blocks but
            // Simplify reduces instructions to traps not respecting
            // this. Uses of Simplify should eliminate unreachable
            // instructions when an instruction not at the end of a
            // basic block is reduced into a trap. When this happens
            // please uncomment the next line:
            //case TRAP_opcode:
          case GOTO_opcode:
            Instruction next = instructions.nextElement();
            if (!BBend.conforms(next)) {
              verror(where, "Unexpected instruction after " + instruction + "\n" + next);
            }
            if (instructions.hasMoreElements()) {
              verror(where, "Unexpected instructions after BBEND " + instructions.nextElement());
            }
            break;
          case BBEND_opcode:
            if (instructions.hasMoreElements()) {
              verror(where, "Unexpected instructions after BBEND " + instructions.nextElement());
            }
            break;
          default:
        }
      }
    }
  }

  /**
   * Verify that every block in the CFG is reachable as failing to do
   * so will cause EnterSSA.insertPhiFunctions to possibly access
   * elements in DominanceFrontier.getIteratedDominanceFrontier
   * and then DominanceFrontier.getDominanceFrontier that aren't
   * defined. Also verify that blocks reached over an exception out
   * edge are not also reachable on normal out edges as this will
   * confuse liveness analysis.
   *
   * @param where    phrase identifying invoking  compilation phase
   */
  @SuppressWarnings("unused")
  // used when needed for debugging
  private void verifyAllBlocksAreReachable(String where) {
    BitVector reachableNormalBlocks = new BitVector(cfg.numberOfNodes());
    BitVector reachableExceptionBlocks = new BitVector(cfg.numberOfNodes());
    resetBasicBlockMap();
    verifyAllBlocksAreReachable(where, cfg.entry(), reachableNormalBlocks, reachableExceptionBlocks, false);
    boolean hasUnreachableBlocks = false;
    StringBuilder unreachablesString = new StringBuilder();
    for (int j = 0; j < cfg.numberOfNodes(); j++) {
      if (!reachableNormalBlocks.get(j) && !reachableExceptionBlocks.get(j)) {
        hasUnreachableBlocks = true;
        if (basicBlockMap[j] != null) {
          basicBlockMap[j].printExtended();
        }
        unreachablesString.append(" BB").append(j);
      }
    }
    if (hasUnreachableBlocks) {
      verror(where, "Unreachable blocks in the CFG which will confuse dominators:" + unreachablesString);
    }
  }

  /**
   * Verify that every block in the CFG is reachable as failing to do
   * so will cause EnterSSA.insertPhiFunctions to possibly access
   * elements in DominanceFrontier.getIteratedDominanceFrontier
   * and then DominanceFrontier.getDominanceFrontier that aren't
   * defined. Also verify that blocks reached over an exception out
   * edge are not also reachable on normal out edges as this will
   * confuse liveness analysis.
   *
   * @param where location of verify in compilation
   * @param curBB the current BB to work on
   * @param visitedNormalBBs the blocks already visited (to avoid cycles) on normal out edges
   * @param visitedExceptionalBBs the blocks already visited (to avoid cycles) on exceptional out edges
   * @param fromExceptionEdge should paths from exceptions be validated?
   */
  private void verifyAllBlocksAreReachable(String where, BasicBlock curBB, BitVector visitedNormalBBs,
                                           BitVector visitedExceptionalBBs, boolean fromExceptionEdge) {
    // Set visited information
    if (fromExceptionEdge) {
      visitedExceptionalBBs.set(curBB.getNumber());
    } else {
      visitedNormalBBs.set(curBB.getNumber());
    }

    // Recurse to next BBs
    Enumeration<BasicBlock> outBlocks = curBB.getNormalOut();
    while (outBlocks.hasMoreElements()) {
      BasicBlock out = outBlocks.nextElement();
      if (!visitedNormalBBs.get(out.getNumber())) {
        verifyAllBlocksAreReachable(where, out, visitedNormalBBs, visitedExceptionalBBs, false);
      }
    }
    outBlocks = curBB.getExceptionalOut();
    while (outBlocks.hasMoreElements()) {
      BasicBlock out = outBlocks.nextElement();
      if (!visitedExceptionalBBs.get(out.getNumber())) {
        verifyAllBlocksAreReachable(where, out, visitedNormalBBs, visitedExceptionalBBs, true);
      }
      if (visitedNormalBBs.get(out.getNumber())) {
        curBB.printExtended();
        out.printExtended();
        verror(where,
               "Basic block " +
               curBB +
               " reaches " +
               out +
               " by normal and exceptional out edges thereby breaking a liveness analysis assumption.");
      }
    }
    if (curBB.mayThrowUncaughtException()) {
      visitedExceptionalBBs.set(cfg.exit().getNumber());
      if (!cfg.exit().isExit()) {
        cfg.exit().printExtended();
        verror(where, "The exit block is reachable by an exception edge and contains instructions.");
      }
    }
  }

  /**
   * Verify that every non-physical, non-parameter symbolic register
   * that has a use also has at least one def
   *
   * @param where    phrase identifying invoking  compilation phase
   */
  private void verifyRegisterDefs(String where) {
    DefUse.computeDU(this);
    //TODO: (SJF)I hate the register list interface.  Re-do it.
    for (Register r = regpool.getFirstSymbolicRegister(); r != null; r = r.getNext()) {
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
    for (Register r = regpool.getFirstSymbolicRegister(); r != null; r = r.getNext()) {
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

  /**
   * Checks whether uses follow definitions and that in SSA form
   * variables aren't multiply defined
   *
   * @param where phrase identifying invoking compilation phase
   */
  private void verifyUseFollowsDef(String where) {
    // Create set of defined variables and add registers that will be
    // defined before entry to the IR
    HashSet<Object> definedVariables = new HashSet<Object>();
    // NB the last two args determine how thorough we're going to test
    // things
    verifyUseFollowsDef(where,
                        definedVariables,
                        cfg.entry(),
                        new BitVector(cfg.numberOfNodes()),
                        new ArrayList<BasicBlock>(),
                        5,
                        // <-- maximum number of basic blocks followed
                        true
                        // <-- follow exception as well as normal out edges?
    );
  }

  /**
   * Check whether uses follow definitions and in SSA form that
   * variables aren't multiply defined
   *
   * @param where location of verify in compilation
   * @param definedVariables variables already defined on this path
   * @param curBB the current BB to work on
   * @param visitedBBs the blocks already visited (to avoid cycles)
   * @param path a record of the path taken to reach this basic block
   * @param maxPathLength the maximum number of basic blocks that will be followed
   * @param traceExceptionEdges    should paths from exceptions be validated?
   */
  private void verifyUseFollowsDef(String where, HashSet<Object> definedVariables, BasicBlock curBB,
                                   BitVector visitedBBs, ArrayList<BasicBlock> path, int maxPathLength,
                                   boolean traceExceptionEdges) {
    if (path.size() > maxPathLength) {
      return;
    }
    path.add(curBB);
    // Process instructions in block
    IREnumeration.AllInstructionsEnum instructions = new IREnumeration.AllInstructionsEnum(this, curBB);
    while (instructions.hasMoreElements()) {
      Instruction instruction = instructions.nextElement();
      // Special phi handling case
      if (Phi.conforms(instruction)) {
        if ((!inSSAForm()) && (!inSSAFormAwaitingReEntry())) {
          verror(where, "Phi node encountered but SSA not computed");
        }
        // Find predecessors that we have already visited
        for (int i = 0; i < Phi.getNumberOfPreds(instruction); i++) {
          BasicBlock phi_pred = Phi.getPred(instruction, i).block;
          if (phi_pred.getNumber() > basicBlockMap.length) {
            verror(where, "Phi predecessor not a valid basic block " + phi_pred);
          }
          if ((curBB != phi_pred) && path.contains(phi_pred)) {
            // This predecessor has been visited on this path so the
            // variable should be defined
            Object variable = getVariableUse(where, Phi.getValue(instruction, i));
            if ((variable != null) && (!definedVariables.contains(variable))) {
              StringBuilder pathString = new StringBuilder();
              for (int j = 0; j < path.size(); j++) {
                pathString.append(path.get(j).getNumber());
                if (j < (path.size() - 1)) {
                  pathString.append("->");
                }
              }
              verror(where, "Use of " + variable + " before definition: " + instruction + "\npath: " + pathString);
            }
          }
        }
      } else {
        // General use follows def test
        IREnumeration.AllUsesEnum useOperands = new IREnumeration.AllUsesEnum(this, instruction);
        while (useOperands.hasMoreElements()) {
          Object variable = getVariableUse(where, useOperands.nextElement());
          if ((variable != null) && (!definedVariables.contains(variable))) {
            if (instruction.operator().toString().indexOf("xor") != -1)
              continue;
            StringBuilder pathString = new StringBuilder();
            for (int i = 0; i < path.size(); i++) {
              pathString.append(path.get(i).getNumber());
              if (i < (path.size() - 1)) {
                pathString.append("->");
              }
            }
            verror(where, "Use of " + variable + " before definition: " + instruction + "\npath: " + pathString);
          }
        }
      }
      // Add definitions to defined variables
      IREnumeration.AllDefsEnum defOperands = new IREnumeration.AllDefsEnum(this, instruction);
      while (defOperands.hasMoreElements()) {
        Object variable = getVariableDef(where, defOperands.nextElement());
        // Check that a variable isn't defined twice when we believe we're in SSA form
        if (variable != null) {
          if ((inSSAForm()) && (!inSSAFormAwaitingReEntry())) {
            if (definedVariables.contains(variable)) {
              verror(where, "Single assignment broken - multiple definitions of " + variable);
            }
          }
          definedVariables.add(variable);
        }
      }
    }
    // Recurse to next BBs
    visitedBBs.set(curBB.getNumber());
    Enumeration<BasicBlock> outBlocks;
    if (traceExceptionEdges) {
      outBlocks = curBB.getOut(); // <-- very slow
    } else {
      outBlocks = curBB.getNormalOut();
    }
    while (outBlocks.hasMoreElements()) {
      BasicBlock out = outBlocks.nextElement();
      if (!visitedBBs.get(out.getNumber())) {
        verifyUseFollowsDef(where,
                            new HashSet<Object>(definedVariables),
                            out,
                            new BitVector(visitedBBs),
                            new ArrayList<BasicBlock>(path),
                            maxPathLength,
                            traceExceptionEdges);
        visitedBBs.set(out.getNumber());
      }
    }
  }

  /**
   * Get the variable used by this operand
   *
   * @param where the verification location
   * @param operand the operand to pull a variable from
   * @return {@code null} if the variable should be ignored otherwise the variable
   */
  private Object getVariableUse(String where, Operand operand) {
    if (operand.isConstant() ||
        (operand instanceof ConditionOperand) ||
        operand.isStringConstant() ||
        operand.isType() ||
        operand.isMethod() ||
        operand.isBranch() ||
        (operand instanceof BranchProfileOperand) ||
        operand.isLocation() ||
        operand.isStackLocation() ||
        operand.isMemory() ||
        (operand instanceof TrapCodeOperand) ||
        (operand instanceof InlinedOsrTypeInfoOperand) ||
        (VM.BuildForIA32 && operand instanceof IA32ConditionOperand) ||
        (VM.BuildForPowerPC && operand instanceof PowerPCConditionOperand) ||
        (VM.BuildForIA32 && operand instanceof BURSManagedFPROperand) ||
        (VM.BuildForPowerPC && operand instanceof PowerPCTrapOperand)) {
      return null;
    } else if (operand.isRegister()) {
      Register register = operand.asRegister().getRegister();
      // ignore physical registers
      return (register.isPhysical()) ? null : register;
    } else if (operand.isBlock()) {
      Enumeration<BasicBlock> blocks = cfg.basicBlocks();
      while (blocks.hasMoreElements()) {
        if (operand.asBlock().block == blocks.nextElement()) {
          return null;
        }
      }
      verror(where, "Basic block not found in CFG for BasicBlockOperand: " + operand);
      return null;
    } else if (operand instanceof HeapOperand) {
      if (!actualSSAOptions.getHeapValid()) {
        return null;
      }
      HeapVariable<?> variable = ((HeapOperand<?>) operand).getHeapVariable();
      if (variable.getNumber() > 0) {
        return variable;
      } else {
        // definition 0 comes in from outside the IR
        return null;
      }
    } else {
      verror(where, "Use: Unknown variable of " + operand.getClass() + " with operand: " + operand);
      return null;
    }
  }

  /**
   * Get the variable defined by this operand
   *
   * @param where the verification location
   * @param operand the operand to pull a variable from
   * @return {@code null} if the variable should be ignored otherwise the variable
   */
  private Object getVariableDef(String where, Operand operand) {
    if (operand.isRegister()) {
      Register register = operand.asRegister().getRegister();
      // ignore physical registers
      return (register.isPhysical()) ? null : register;
    } else if (operand instanceof HeapOperand) {
      if (!actualSSAOptions.getHeapValid()) {
        return null;
      }
      return ((HeapOperand<?>) operand).getHeapVariable();
    } else if (VM.BuildForIA32 && operand instanceof BURSManagedFPROperand) {
      return ((BURSManagedFPROperand) operand).regNum;
    } else if (operand.isStackLocation() || operand.isMemory()) {
      // it would be nice to handle these but they have multiple
      // constituent parts :-(
      return null;
    } else {
      verror(where, "Def: Unknown variable of " + operand.getClass() + " with operand: " + operand);
      return null;
    }
  }

  /**
   * Generate error
   *
   * @param where    phrase identifying invoking  compilation phase
   * @param msg      error message
   */
  @NoInline
  private void verror(String where, String msg) {
    dumpIR(this, "Verify: " + where + ": " + method, true);
    VM.sysWriteln("VERIFY: " + where + " " + msg);
    throw new OptimizingCompilerException("VERIFY: " + where, msg);
  }

  public GenerationContext getGc() {
    return gc;
  }

}
