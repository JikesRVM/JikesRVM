/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import  java.util.*;
import instructionFormats.*;

/**
 * This compiler phase constructs SSA form.  
 *
 * <p> This module constructs SSA according to the SSA properties defined
 * in </code> OPT_IR.desiredSSAOptions </code>.  See <code> OPT_SSAOptions
 * </code> for more details on supported options for SSA construction.
 *
 * <p>The SSA construction algorithm is the classic dominance frontier
 * based algorithm from Cytron et al.'s 1991 TOPLAS paper.
 *
 * <p> See our SAS 2000 paper
 * <a href="http://www.research.ibm.com/jalapeno/publication.html#sas00">
 *  Unified Analysis of Arrays and Object References in Strongly Typed
 *  Languages </a> for an overview of Array SSA form.  More implementation
 *  details are documented in {@link OPT_SSA <code> OPT_SSA.java</code>}.
 *
 * @see OPT_SSA
 * @see OPT_SSAOptions
 * @see OPT_LTDominators
 *
 * @author Stephen Fink
 * @author Julian Dolby
 * @author Martin Trapp
 */
class OPT_EnterSSA extends OPT_CompilerPhase
implements OPT_Operators, OPT_Constants {
  /**
   * flag to optionally print verbose debugging messages
   */
  static final boolean DEBUG = false;

  /**
   * The govering IR
   */
  private OPT_IR ir;

  /**
   * Cached results of liveness analysis
   */
  private OPT_LiveAnalysis live;

  /**
   * A set of registers determined to span basic blocks
   */
  private java.util.HashSet nonLocalRegisters;

  /**
   * Should this phase be performed under a guiding set of compiler
   * options?
   *
   * @param options the controlling compiler options
   * @return true iff SSA is enabled under the options
   */
  final boolean shouldPerform (OPT_Options options) {
    return options.SSA;
  }

  /**
   * Return a string identifying this compiler phase.
   * @return "Enter SSA"
   */
  final String getName () {
    return "Enter SSA";
  }

  /**
   * Should the IR be printed either before or after performing this phase?
   *
   * @param options controlling compiler options
   * @param before true iff querying before the phase
   * @return true or false
   */
  final boolean printingEnabled (OPT_Options options, boolean before) {
    return false;
  }

  /**
   * Construct SSA form to satisfy the desired options in ir.desiredSSAOptions.
   * This module is lazy; if the actual SSA options satisfy the desired options,
   * then do nothing.
   *
   * @param ir the governing IR
   */
  final public void perform (OPT_IR ir) {
    // Exit if we don't have to recompute SSA.
    if (ir.actualSSAOptions != null)
      if (ir.actualSSAOptions.satisfies(ir.desiredSSAOptions))
        return;
    this.ir = ir;
    boolean scalarsOnly = ir.desiredSSAOptions.getScalarsOnly();
    boolean backwards = ir.desiredSSAOptions.getBackwards();
    java.util.Set heapTypes = ir.desiredSSAOptions.getHeapTypes();
    boolean insertUsePhis = ir.desiredSSAOptions.getInsertUsePhis();
    boolean insertPEIDeps = ir.desiredSSAOptions.getInsertPEIDeps();
    boolean excludeGuards = ir.desiredSSAOptions.getExcludeGuards();

    // make sure the dominator computation completed successfully
    if (!ir.HIRInfo.dominatorsAreComputed)
      throw  new OPT_OptimizingCompilerException("Need dominators for SSA");
    // reset SSA dictionary information
    ir.HIRInfo.SSADictionary = new OPT_SSADictionary(null, true, false, ir);
    // initialize as needed for SSA options
    prepare();
    // work around problem with PEI-generated values and handlers
    if (true /* ir.options.UNFACTOR_FOR_SSA */)
      patchPEIgeneratedValues();
    if (ir.options.PRINT_SSA)
      OPT_SSA.printInstructions(ir);
    computeSSA(ir, scalarsOnly, backwards, heapTypes,
               insertUsePhis, insertPEIDeps, excludeGuards);
    // reset the SSAOptions
    ir.actualSSAOptions = new OPT_SSAOptions();
    ir.actualSSAOptions.setScalarsOnly(scalarsOnly);
    ir.actualSSAOptions.setBackwards(backwards);
    ir.actualSSAOptions.setHeapTypes(heapTypes);
    ir.actualSSAOptions.setInsertUsePhis(insertUsePhis);
    ir.actualSSAOptions.setInsertPEIDeps(insertPEIDeps);
    ir.actualSSAOptions.setExcludeGuards(excludeGuards);
  }

  /**
   * Perform some calculations to prepare for SSA construction.
   * <ul>
   * <li> If using pruned SSA, compute liveness.
   * <li> If using semi-pruned SSA, compute non-local registers
   * </ul>
   */
  private void prepare () {
    if (ir.options.PRUNED_SSA) {
      live = new OPT_LiveAnalysis(false, // don't create GC maps
                                  true,  // skip (final) local propagation step
                                  // of live analysis
                                  false, // don't store live at handlers
                                  ir.desiredSSAOptions.getExcludeGuards());
      // don't skip guards 
      live.perform(ir);
    } 
    else if (ir.options.SEMI_PRUNED_SSA) {
      computeNonLocals();
    }
    // minimal SSA needs no preparation
  }

  /**
   * Pass through the IR and calculate which registers are not
   * local to a basic block.  Store the result in the <code> nonLocalRegisters
   * </code> field.
   */
  private void computeNonLocals () {
    nonLocalRegisters = new java.util.HashSet(20);
    OPT_BasicBlockEnumeration blocks = ir.getBasicBlocks();
    while (blocks.hasMoreElements()) {
      java.util.HashSet killed = new java.util.HashSet(5);
      OPT_BasicBlock block = blocks.next();
      OPT_InstructionEnumeration instrs = block.forwardRealInstrEnumerator();
      while (instrs.hasMoreElements()) {
        OPT_Instruction instr = instrs.next();
        OPT_OperandEnumeration uses = instr.getUses();
        while (uses.hasMoreElements()) {
          OPT_Operand op = uses.next();
          if (op instanceof OPT_RegisterOperand)
            if (!killed.contains(op.asRegister().register))
              nonLocalRegisters.add(op.asRegister().register);
        }
        OPT_OperandEnumeration defs = instr.getDefs();
        while (defs.hasMoreElements()) {
          OPT_Operand op = defs.next();
          if (op instanceof OPT_RegisterOperand)
            killed.add(op.asRegister().register);
        }
      }
    }
  }

  /**
   * Work around some problems with PEI-generated values and 
   * handlers.  Namely, if a PEI has a return value, rename the
   * result register before and after the PEI in order to reflect the fact
   * that the PEI may not actually assign the result register.
   */
  private void patchPEIgeneratedValues () {
    // this only applies if there are exception handlers
    if (!ir.hasReachableExceptionHandlers()) return;

    java.util.HashSet needed = new java.util.HashSet(4);
    OPT_BasicBlockEnumeration blocks = ir.getBasicBlocks();
    while (blocks.hasMoreElements()) {
      OPT_BasicBlock block = blocks.next();
      if (block.getExceptionalOut().hasMoreElements()) {
        OPT_Instruction pei = block.lastRealInstruction();
        if (pei != null && pei.isPEI() && ResultCarrier.conforms(pei)) {
          boolean copyNeeded = false;
          OPT_RegisterOperand v = ResultCarrier.getResult(pei);
          // void calls and the like... :(
          if (v != null) {
            OPT_Register orig = v.register;
            if (ir.options.PRUNED_SSA) {
              OPT_BasicBlockEnumeration out = 
                block.getApplicableExceptionalOut(pei);
              while (out.hasMoreElements()) {
                OPT_BasicBlock exp = out.next();
                OPT_LiveSet explive = live.getLiveInfo(exp).in();
                if (explive.contains(orig)) {
                  copyNeeded = true;
                  break;
                }
              }
            } 
            else if (ir.options.SEMI_PRUNED_SSA) {
              if (nonLocalRegisters.contains(orig))
                copyNeeded = true;
            } 
            else                // MINIMAL_SSA
              copyNeeded = true;
            if (copyNeeded) {
              boolean copyRequested = false;
              OPT_BasicBlockEnumeration out = 
                block.getApplicableExceptionalOut(pei);
              while (out.hasMoreElements()) {
                OPT_BasicBlock exp = out.next();
                needed.add(new OPT_Pair(exp, v));
              }
            }
          }
        }
      }
    }
    // having determine where copies should be inserted, now insert them.
    java.util.Iterator copies = needed.iterator();
    while (copies.hasNext()) {
      OPT_Pair copy = (OPT_Pair)copies.next();
      OPT_BasicBlock inBlock = (OPT_BasicBlock)copy.first;
      OPT_RegisterOperand registerOp = (OPT_RegisterOperand)copy.second;
      VM_Type type = registerOp.type;
      OPT_Register register = registerOp.register;
      OPT_Register temp = ir.regpool.getReg(register);
      inBlock.prependInstruction(OPT_SSA.makeMoveInstruction(ir, register, 
                                                             temp, type));
      if (ir.options.PRUNED_SSA) {
        OPT_LiveAnalysis.BBLiveElement inl = live.getLiveInfo(inBlock);
        inl.gen().add(new OPT_RegisterOperand(temp, type));
        inl.in().add(new OPT_RegisterOperand(temp, type));
      } 
      else if (ir.options.SEMI_PRUNED_SSA) {
        nonLocalRegisters.add(temp);
      }
      OPT_BasicBlockEnumeration outBlocks = inBlock.getIn();
      while (outBlocks.hasMoreElements()) {
        OPT_BasicBlock outBlock = outBlocks.next();
        OPT_Instruction x = OPT_SSA.makeMoveInstruction(ir, temp, register, 
                                                        type);
        OPT_SSA.addAtEnd(ir, outBlock, x, true);
        if (ir.options.PRUNED_SSA) {
          OPT_LiveAnalysis.BBLiveElement ol = live.getLiveInfo(outBlock);
          ol.BBKillSet().add(new OPT_RegisterOperand(temp, type));
        }
      }
    }
  }

  /**
   * Calculate SSA form for an IR.  This routine holds the guts of the
   * transformation.
   *
   * @param ir the governing IR
   * @param scalarsOnly: should we compute SSA only for scalar variables?
   * @param backwards: If this is true, then every statement that
   * can leave the procedure is considered to <em> use </em> every heap 
   * variable.  This option is useful for backwards analyses such as dead
   * store elimination.
   * @param heapTypes: If this variable is non-null, then heap array SSA
   * form will restrict itself to this set of types. If this is null, build 
   * heap array SSA for all types.
   * @param insertUsePhis: Should we insert uphi functions for heap array
   * SSA? ie., should we create a new name for each heap array at every use 
   * of the heap array? This option is useful for some analyses, such as
   * our redundant load elimination algorithm.
   * @param insertPEIDeps: Should we model exceptions with an explicit
   * heap variable for exception state? This option is useful for global
   * code placement algorithms.
   * @param excludeGuards: Should we exclude guard registers from SSA?
   */
  private void computeSSA (OPT_IR ir, boolean scalarsOnly, boolean backwards, 
                           java.util.Set heapTypes, boolean insertUsePhis,
                           boolean insertPEIDeps, boolean excludeGuards) {
    // if reads Kill.  model this with uphis.
    if (ir.options.READS_KILL) insertUsePhis = true;

    if (OPT_SSA.containsUnsupportedOpcode(ir))
      throw  new OPT_OperationNotImplementedException(
                              "Warning: SSA skipped due to unsupported opcode");
    // reset Array SSA information
    if (!scalarsOnly)
      ir.HIRInfo.SSADictionary = new OPT_SSADictionary(heapTypes, 
                                                       insertUsePhis, 
                                                       insertPEIDeps, ir); 
    else 
      ir.HIRInfo.SSADictionary = new OPT_SSADictionary(null, 
                                                       insertUsePhis, 
                                                       insertPEIDeps, ir);
    if (DEBUG)
      System.out.println("Computing register lists...");
    // 1. re-compute the flow-insensitive isSSA flag for each register
    OPT_DefUse.computeDU(ir);
    OPT_DefUse.recomputeSSA(ir);
    // 2. set up a mapping from symbolic register number to the
    //	register.  !!TODO: factor this out and make it more
    //	useful.
    OPT_Register[] symbolicRegisters = getSymbolicRegisters(ir);
    // 3. walk through the IR, and set up BitVectors representing the defs
    //    for each symbolic register (more efficient than using register
    //	lists)
    if (DEBUG)
      System.out.println("Find defs for each register...");
    OPT_BitVector[] defSets = getDefSets(ir);
    // 4. Insert phi functions for scalars
    if (DEBUG)
      System.out.println("Insert phi functions...");
    insertPhiFunctions(ir, defSets, symbolicRegisters, excludeGuards);
    // 5. Insert heap variables into the Array SSA form
    if (!scalarsOnly) {
      insertHeapVariables(ir, backwards);
    }
    if (DEBUG)
      System.out.println("Before renaming...");
    if (DEBUG)
      OPT_SSA.printInstructions(ir);
    if (DEBUG)
      System.out.println("Renaming...");
    renameSymbolicRegisters(ir, symbolicRegisters);
    if (!scalarsOnly) {
      renameHeapVariables(ir);
    }
    if (DEBUG)
      System.out.println("SSA done.");
    if (ir.options.PRINT_SSA)
      OPT_SSA.printInstructions(ir);
  }

  /**
   * Insert heap variables needed for Array SSA form.
   *
   * @param ir the governing IR
   * @param backwards if this is true, every statement that can leave the
   *		       procedure <em> uses </em> every heap variable.
   *		       This option is useful for backwards analyses
   */
  private static void insertHeapVariables (OPT_IR ir, boolean backwards) {
    // insert dphi functions where needed
    registerHeapVariables(ir);

    // insert heap defs and uses for CALL instructions
    registerCalls(ir);

    // register heap uses for instructions that can leave the procedure
    if (backwards)
      registerExits(ir);

    // insert phi funcions where needed
    insertHeapPhiFunctions(ir);
  }

  /**
   * Register every instruction that can leave this method with the
   * implicit heap array SSA look aside structure.
   *
   * @param ir the governing IR
   */
  private static void registerExits (OPT_IR ir) {
    OPT_SSADictionary dictionary = ir.HIRInfo.SSADictionary;
    for (OPT_BasicBlockEnumeration bbe = ir.getBasicBlocks(); 
         bbe.hasMoreElements();) {
      OPT_BasicBlock b = bbe.next();
      for (OPT_InstructionEnumeration e = b.forwardInstrEnumerator(); 
           e.hasMoreElements();) {
        OPT_Instruction s = (OPT_Instruction)e.nextElement();
        // we already handled calls in a previous pass.
        if (Call.conforms(s))
          continue;
        if (Return.conforms(s) || Athrow.conforms(s) || s.isPEI()) {
          dictionary.registerExit(s, b);
        }
      }
    }
  }

  /**
   * Register every CALL instruction in this method with the
   * implicit heap array SSA look aside structure.
   * Namely, mark that this instruction defs and uses <em> every </em> 
   * type of heap variable in the IR's SSA dictionary.
   *
   * @param ir the governing IR
   */
  private static void registerCalls (OPT_IR ir) {
    OPT_SSADictionary dictionary = ir.HIRInfo.SSADictionary;
    for (OPT_BasicBlockEnumeration bbe = ir.getBasicBlocks(); 
         bbe.hasMoreElements();) {
      OPT_BasicBlock b = bbe.next();
      for (OPT_InstructionEnumeration e = b.forwardInstrEnumerator(); 
           e.hasMoreElements();) {
        OPT_Instruction s = e.next();
        //-#if RVM_FOR_IA32
        boolean isSynch = false;                // ignore this for now.
        //-#elif RVM_FOR_IA32
        boolean isSynch = false;                // ignore this for now.
        //-#elif RVM_FOR_POWERPC
        boolean isSynch = (s.operator() == SYNC) || (s.operator() == ISYNC);
        //-#endif
        if (isSynch || Call.conforms(s) || CallSpecial.conforms(s) || 
            MonitorOp.conforms(s) || Prepare.conforms(s) || Attempt.conforms(s)
            || CacheOp.conforms(s) 
            || s.isDynamicLinkingPoint()) {
          dictionary.registerUnknown(s, b);
        }
      }
    }
  }

  /**
   * Register every instruction in this method with the
   * implicit heap array SSA lookaside structure.
   *
   * @param ir the governing IR
   */
  private static void registerHeapVariables (OPT_IR ir) {
    OPT_SSADictionary dictionary = ir.HIRInfo.SSADictionary;
    for (OPT_BasicBlockEnumeration bbe = ir.getBasicBlocks(); 
         bbe.hasMoreElements();) {
      OPT_BasicBlock b = bbe.next();
      for (OPT_InstructionEnumeration e = b.forwardInstrEnumerator(); 
           e.hasMoreElements();) {
        OPT_Instruction s = e.next();
        if (s.isImplicitLoad() || s.isImplicitStore() || s.isAllocation()
            || Phi.conforms(s) || s.isPEI()
            || Label.conforms(s) || BBend.conforms(s)
            || s.operator.opcode == UNINT_BEGIN_opcode
            || s.operator.opcode == UNINT_END_opcode) {
          dictionary.registerInstruction(s, b);
        }
      }
    }
  }

  /**
   * Insert phi functions for heap array SSA heap variables.
   *
   * @param ir the governing IR
   */
  private static void insertHeapPhiFunctions (OPT_IR ir) {
    Enumeration e = ir.HIRInfo.SSADictionary.getHeapVariables();
    for (; e.hasMoreElements();) {
      OPT_HeapVariable H = (OPT_HeapVariable)e.nextElement();

      if (DEBUG) System.out.println("Inserting phis for Heap " + H);
      if (DEBUG) System.out.println("Start iterated frontier...");

      OPT_BitVector defH = H.getDefBlocks();
      if (DEBUG) System.out.println(H + " DEFINED IN " + defH);

      OPT_BitVector needsPhi = OPT_DominanceFrontier.
        getIteratedDominanceFrontier(ir, defH);
      if (DEBUG) System.out.println(H + " NEEDS PHI " + needsPhi);

      if (DEBUG) System.out.println("Done.");
      for (int b = 0; b < needsPhi.length(); b++) {
        if (needsPhi.get(b)) {
          OPT_BasicBlock bb = ir.getBasicBlock(b);
          ir.HIRInfo.SSADictionary.createHeapPhiInstruction(bb, H);
        }
      }
    }
  }

  /**
   * Calculate the set of blocks that contain defs for each
   * 	symbolic register in an IR.  <em> Note: </em> This routine skips
   * 	registers marked  already having a single static
   * 	definition, physical registers, and guard registeres.
   *
   * @param ir the governing IR
   * @returns an array of BitVectors, where element <em>i</em> represents the
   *	basic blocks that contain defs for symbolic register <em>i</em>
   */
  private static OPT_BitVector[] getDefSets (OPT_IR ir) {
    int nBlocks = ir.getMaxBasicBlockNumber();
    OPT_BitVector[] result = new OPT_BitVector[ir.getNumberOfSymbolicRegisters()];

    for (int i = 0; i < result.length; i++)
      result[i] = new OPT_BitVector(nBlocks+1);

    // loop over each basic block
    for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks(); 
         e.hasMoreElements();) {
      OPT_BasicBlock bb = e.next();
      int bbNumber = bb.getNumber();
      // visit each instruction in the basic block
      for (OPT_InstructionEnumeration ie = bb.forwardInstrEnumerator(); 
           ie.hasMoreElements();) {
        OPT_Instruction s = ie.next();
        // record each def in the instruction
        // skip SSA defs
        for (int j = 0; j < s.getNumberOfDefs(); j++) {
          OPT_Operand operand = s.getOperand(j);
          if (operand == null) continue;
          if (!operand.isRegister()) continue;
          if (operand.asRegister().register.isSSA()) continue;
          if (operand.asRegister().register.isPhysical()) continue;

          int reg = operand.asRegister().register.getNumber();
          result[reg].set(bbNumber);
        }
      }
    }
    return  result;
  }

  /**
   * Insert the necessary phi functions into an IR.
   * <p> Algorithm:
   * <p>For register r, let S be the set of all blocks that
   *    contain defs of r.  Let D be the iterated dominance frontier
   *    of S.  Each block in D needs a phi-function for r.
   *
   * <p> Special Java case: if node N dominates all defs of r, then N
   *			  does not need a phi-function for r
   *
   * @param ir the governing IR
   * @param defs defs[i] represents the basic blocks that define
   *	        symbolic register i.
   * @param symbolics symbolics[i] is symbolic register number i
   */
  private void insertPhiFunctions(OPT_IR ir, OPT_BitVector[] defs, 
                                  OPT_Register[] symbolics,
                                  boolean excludeGuards) {
    for (int r = 0; r < defs.length; r++) {
      if (symbolics[r] == null)
        continue;
      if (symbolics[r].isSSA())
        continue;
      if (symbolics[r].isPhysical())
        continue;
      if (excludeGuards && symbolics[r].isValidation()) continue;
      if (DEBUG)
        System.out.println("Inserting phis for register " + r);
      if (DEBUG)
        System.out.println("Start iterated frontier...");
      OPT_BitVector needsPhi = OPT_DominanceFrontier.
        getIteratedDominanceFrontier(ir, defs[r]);
      removePhisThatDominateAllDefs(needsPhi, ir, defs[r]);
      if (DEBUG)
        System.out.println("Done.");

      if (ir.options.PRUNED_SSA) {
        for (int b = 0; b < needsPhi.length(); b++) {
          if (needsPhi.get(b)) {
            OPT_BasicBlock bb = ir.getBasicBlock(b);
            if (live.getLiveInfo(bb).in().contains(symbolics[r]))
              insertPhi(bb, symbolics[r]);
          }
        }
      } else if (ir.options.SEMI_PRUNED_SSA) {
        if (nonLocalRegisters.contains(symbolics[r])) {
          for (int b = 0; b < needsPhi.length(); b++) {
            OPT_BasicBlock bb = ir.getBasicBlock(b);
            if (needsPhi.get(b))
              insertPhi(bb, symbolics[r]);
          }
        }
      } else { // ir.options.MINIMAL_SSA
        for (int b = 0; b < needsPhi.length(); b++) {
          OPT_BasicBlock bb = ir.getBasicBlock(b);
          if (needsPhi.get(b))
            insertPhi(bb, symbolics[r]);
        }
      }
    }
  }

  /**
   * If node N dominates all defs of a register r, then N does
   * not need a phi function for r; this function removes such
   * nodes N from a Bit Set.
   *
   * @param needsPhi representation of set of nodes that
   *		    need phi functions for a register r
   * @param ir the governing IR
   * @param defs set of nodes that define register r
   */
  private static void removePhisThatDominateAllDefs (OPT_BitVector needsPhi, 
                                                     OPT_IR ir, 
                                                     OPT_BitVector defs) {
    for (int i = 0; i < needsPhi.length(); i++) {
      if (!needsPhi.get(i))
        continue;
      if (ir.HIRInfo.dominatorTree.dominates(i, defs))
        needsPhi.clear(i);
    }
  }

  /**
   * Insert a phi function for a symbolic register at the head
   * of a basic block.
   *
   * @param bb the basic block
   * @param r the symbolic register that needs a phi function
   */
  private void insertPhi (OPT_BasicBlock bb, OPT_Register r) {
    OPT_Instruction s = makePhiInstruction(r, bb);
    bb.firstInstruction().insertAfter(s);
  }

  /**
   * Create a phi-function instruction
   *
   * @param r the symbolic register
   * @param bb the basic block holding the new phi function
   * @returns the instruction r = PHI r,r,..,r
   */
  private OPT_Instruction makePhiInstruction (OPT_Register r,
                                              OPT_BasicBlock bb) {
    int n = bb.getNumberOfIn();
    OPT_BasicBlockEnumeration in = bb.getIn();
    VM_Type type = null;
    OPT_Instruction s = Phi.create(PHI, 
                                   new OPT_RegisterOperand(r, type), 
                                   n);
    for (int i = 0; i < n; i++) {
      OPT_RegisterOperand op = new OPT_RegisterOperand(r, type);
      Phi.setValue(s, i, op);
      OPT_BasicBlock pred = in.next();
      Phi.setPred(s, i, new OPT_BasicBlockOperand(pred)); 
    }
    s.position = ir.gc.inlineSequence;
    s.bcIndex = SSA_SYNTH_BCI;
    return  s;
  }

  /**
   * Set up a mapping from symbolic register number to the register.
   * <p> TODO: put this functionality elsewhere.
   *
   * @param ir the governing IR.
   * @returns a mapping
   */
  private static OPT_Register[] getSymbolicRegisters (OPT_IR ir) {
    OPT_Register[] map = new OPT_Register[ir.getNumberOfSymbolicRegisters()];
    for (OPT_Register reg = ir.regpool.getFirstRegister(); reg != null; 
         reg = reg.getNext()) {
      int number = reg.getNumber();
      map[number] = reg;
    }
    return  map;
  }

  /**
   * Rename the symbolic registers so that each register has only one 
   * definition.
   *
   * <p><em> Note </em>: call this after phi functions have been inserted.
   * <p> <b> Algorithm:</b> from Cytron et. al 91
   * <pre>
   *  call search(entry)
   *
   *  search(X):
   *  for each statement A in X do
   *     if A is not-phi
   *	   for each r in RHS(A) do
   *		if !r.isSSA, replace r with TOP(S(r))
   *	   done
   *     fi
   *	for each r in LHS(A) do
   *		if !r.isSSA
   *		    r2 = new temp register
   *		    push r2 onto S(r)
   *	            replace r in A by r2
   *		fi
   * 	done
   *  done (end of first loop)
   *  for each Y in succ(X) do
   *      j <- whichPred(Y,X)
   *      for each phi-function F in Y do
   *	   replace the j-th operand (r) in RHS(F) with TOP(S(r))
   *	 done
   *  done (end of second loop)
   *  for each Y in Children(X) do
   * 	call search(Y)
   *  done (end of third loop)
   *  for each assignment A in X do
   *     for each r in LHS(A) do
   *	   pop(S(r))
   *	done
   *  done (end of fourth loop)
   *  end
   * <pre>
   *
   * @param ir the governing IR
   * @param symbolicRegisters mapping from integer to symbolic registers
   */
   private static void renameSymbolicRegisters (OPT_IR ir, 
                                                OPT_Register[] symbolicRegisters) {
      OPT_SSADictionary dictionary = ir.HIRInfo.SSADictionary;
      int n = ir.getNumberOfSymbolicRegisters();
      java.util.Stack[] S = new java.util.Stack[n + 1];
      for (int i = 0; i < S.length; i++) {
        S[i] = new java.util.Stack();
        // populate the Stacks with initial names for
        // each parameter, and push "null" for other symbolic registers
        if (i >= symbolicRegisters.length)
          continue;
        OPT_Register r = symbolicRegisters[i];
        // If a register's name is "null", that means the
        // register has not yet been defined.
        S[i].push(null);
      }
      OPT_BasicBlock entry = ir.cfg.entry();
      java.util.HashMap phiDefTypes = new java.util.HashMap(10);
      java.util.HashSet ambiguous = new java.util.HashSet(10);
      OPT_DefUse.clearDU(ir);
      search(entry, S, ir, phiDefTypes, ambiguous);
      OPT_DefUse.recomputeSSA(ir);
      setAmbiguousTypes(ambiguous, phiDefTypes);
    }

  /**
   * This routine is the guts of the SSA construction phase for scalars.  See
   * renameSymbolicRegisters for more details.
   *
   * @param X basic block to search dominator tree from
   * @param S stack of names for each register
   * @param ir governing IR
   * @param phiDefTypes, a mapping from register->type, for registers
   *			defined in phi instructions
   * @param ambiguous, a set of phi statements whose type is unknown
   */
  private static void search (OPT_BasicBlock X, java.util.Stack[] S, OPT_IR ir, 
                              java.util.HashMap phiDefTypes, 
                              java.util.HashSet ambiguous) {
    if (DEBUG)
      System.out.println("SEARCH " + X);
    OPT_SSADictionary dictionary = ir.HIRInfo.SSADictionary;
    for (OPT_InstructionEnumeration ie = 
         X.forwardInstrEnumerator(); ie.hasMoreElements();) {
      OPT_Instruction A = ie.next();
      if (A.operator() != PHI) {
        // replace each use
        for (int u = A.getNumberOfDefs(); u < A.getNumberOfOperands(); u++) {
          OPT_Operand op = A.getOperand(u);
          if (op instanceof OPT_RegisterOperand) {
            OPT_RegisterOperand rop = (OPT_RegisterOperand)op;
            OPT_Register r1 = rop.register;
            if (r1.isSSA())
              continue;
            if (r1.isPhysical())
              continue;
            OPT_RegisterOperand r2 = (OPT_RegisterOperand)
              S[r1.getNumber()].peek();
            if (DEBUG)
              System.out.println("REPLACE NORMAL USE " + r1 + " with "
                                 + r2);
            if (r2 != null) {
              rop.register = r2.register;
              OPT_DefUse.recordUse(rop);
              dictionary.setOriginalRegister(r2.register, r1);
            }
          }
        }
      }
      // replace each def
      for (int d = 0; d < A.getNumberOfDefs(); d++) {
        OPT_Operand op = A.getOperand(d);
        if (op instanceof OPT_RegisterOperand) {
          OPT_RegisterOperand rop = (OPT_RegisterOperand)op;
          OPT_Register r1 = rop.register;
          if (r1.isSSA())
            continue;
          if (r1.isPhysical())
            continue;
          OPT_Register r2 = ir.regpool.getReg(r1);
          if (DEBUG)
            System.out.println("PUSH " + r2 + " FOR " + r1 + "BECAUSE "
                               + A);
          S[r1.getNumber()].push(new OPT_RegisterOperand(r2, rop.type));
          // if we haven't yet determined the type of the def'ed
          // register, (this can happen for phi defs occasionally)
          // place this instruction in a set whose
          // types are yet to be determined.
          if (rop.type == null)
            ambiguous.add(A);
          rop.setRegister(r2);
          r2.scratchObject = r1;
          dictionary.setOriginalRegister(r2, r1);
        }
      }
    } // end of first loop

    if (DEBUG) System.out.println("SEARCH (second loop) " + X);
    for (OPT_BasicBlockEnumeration y = X.getOut(); y.hasMoreElements();) {
      OPT_BasicBlock Y = y.next();
      if (Y.isExit())
        continue;
      OPT_Instruction s = Y.firstRealInstruction();
      if (s == null)
        continue;
      // replace use USE in each PHI instruction
      while (s.operator() == PHI) {
        for (int j = 0; j< Phi.getNumberOfValues(s); j++) {
          if (Phi.getPred(s,j).block == X) {
            OPT_Operand valj = Phi.getValue(s, j);
            if (valj instanceof OPT_RegisterOperand) {
              // skip registers already marked SSA from a previous pass
              if (!valj.asRegister().register.isSSA()) {
                OPT_RegisterOperand rop = (OPT_RegisterOperand)valj;
                OPT_Register r1 = rop.register;
                OPT_RegisterOperand r2 = (OPT_RegisterOperand)
                  S[r1.getNumber()].peek();
                if (r2 == null) {
                  // in this case, the register is never defined along
                  // this particular control flow path into the basic
                  // block.  So, null out the operand to indicate that
                  // that case will never happen.
                  if (r1.isValidation()) {
                    Phi.setValue(s, j, new OPT_TrueGuardOperand());
                  } else {
                    if (ir.IRStage == ir.LIR) {
                      Phi.setValue(s, j, new OPT_IntConstantOperand(0));
                    } 
                    else {
                      Phi.setValue(s, j, new OPT_NullConstantOperand());
                    }
                  }
                } else {
                  rop.register = r2.register;
                  OPT_DefUse.recordUse(rop);
                  // if type is null, then this register was
                  // defined in a phi instruction.  get the type
                  // from the mapping phiDefTypes
                  VM_Type t = r2.type;
                  if (t == null) {
                    t = (VM_Type)phiDefTypes.get(r2.register);
                  }
                  // resolve the type for this phi instruction
                  setPhiType(s, t, phiDefTypes, j);
                  dictionary.setOriginalRegister(r2.register, r1);
                }
              }
            }
          }
        }
        s = s.nextInstructionInCodeOrder();
      }
    } // end of second loop

    if (DEBUG) System.out.println("SEARCH (third loop) " + X);
    for (Enumeration c = ir.HIRInfo.dominatorTree.getChildren(X); 
         c.hasMoreElements();) {
      OPT_DominatorTreeNode v = (OPT_DominatorTreeNode)c.nextElement();
      search(v.getBlock(), S, ir, phiDefTypes, ambiguous);
    } // end of third loop

    if (DEBUG) System.out.println("SEARCH (fourth loop) " + X);
    for (OPT_InstructionEnumeration a = X.forwardInstrEnumerator(); 
         a.hasMoreElements();) {
      OPT_Instruction A = a.next();
      // loop over each def
      for (int d = 0; d < A.getNumberOfDefs(); d++) {
        OPT_Operand newOp = A.getOperand(d);
        if (newOp == null)
          continue;
        if (!newOp.isRegister())
          continue;
        OPT_Register newReg = newOp.asRegister().register;
        if (newReg.isSSA())
          continue;
        if (newReg.isPhysical())
          continue;
        OPT_Register r1 = (OPT_Register)newReg.scratchObject;
        S[r1.getNumber()].pop();
        if (DEBUG)
          System.out.println("POP " + r1);
      }
    } // end of fourth loop
    if (DEBUG) System.out.println("FINISHED SEARCH " + X);
  }

  /**
   * Fix up types on phi function operands marked as having ambiguous
   * types.
   * 
   * <p> After renaming with <code> search </code> above, some phi 
   * functions * may still be marked with type null.  Fix up the types
   * for these instructions.
   *
   * @param ambig set of instructions with ambiguous types
   * @param phiDefTypes mapping from register to types
   */
  private static void setAmbiguousTypes (java.util.HashSet ambig, 
                                         java.util.HashMap phiDefTypes) {
    java.util.HashSet remove = new java.util.HashSet(10);
    // worklist algorithm to drain the ambig set.
    int fixedPoint = ambig.size() + 1;
    while (ambig.size() < fixedPoint) {
      remove.clear();
      // for each phi instruction with an ambiguous type  ..
      for (java.util.Iterator i = ambig.iterator(); i.hasNext();) {
        OPT_Instruction s = (OPT_Instruction)i.next();
        // loop across all rvals with type info
        OPT_Operand op = null;
        int j = 0;
        for (; j < Phi.getNumberOfValues(s); j++) {
          OPT_Operand op2 = Phi.getValue(s, j);
          if (op2 instanceof OPT_RegisterOperand) {
            OPT_RegisterOperand r = op2.asRegister();
            if (r.type != null) {
              op = op2;
              setPhiType(s, r.type, phiDefTypes, j);
              remove.add(s);
            } 
            // set the type of the phi instruction, and update
            // the phiDefTypes mapping
            else {
              VM_Type t = (VM_Type)phiDefTypes.get(r.register);
              if (t != null) {
                op = op2;
                setPhiType(s, t, phiDefTypes, j);
                remove.add(s);
              }
            }
          }
        }
        if (op == null) {
          // in this case, this phi instruction is really dead
          OPT_RegisterOperandEnumeration uses = OPT_DefUse.uses
            (s.getOperand(0).asRegister().register);
          while (uses.hasMoreElements()) {
            OPT_RegisterOperand use = uses.next();
            int ii = use.getIndexInInstruction();
            use.instruction.putOperand(ii, new OPT_NullConstantOperand());
          }
          s.remove();
          remove.add(s);
        }
      }
      // now remove the resolved instructions from the worklist
      for (java.util.Iterator i2 = remove.iterator(); i2.hasNext();) {
        OPT_Instruction s = (OPT_Instruction)i2.next();
        ambig.remove(s);
      }
      // set the new fixed point
      fixedPoint = ambig.size();
    }
    // we've reached the fixed point.  Remaining ambiguous
    // phis are truly dead. remove them
    for (java.util.Iterator i3 = ambig.iterator(); i3.hasNext();) {
      OPT_Instruction s = (OPT_Instruction)i3.next();
      OPT_RegisterOperandEnumeration uses = OPT_DefUse.uses
        (s.getOperand(0).asRegister().register);
      while (uses.hasMoreElements()) {
        OPT_RegisterOperand use = uses.next();
        int ii = use.getIndexInInstruction();
        use.instruction.putOperand(ii, new OPT_NullConstantOperand());
      }
      s.remove();
    }
  }

  /**
   * Rename the implicit heap variables in the SSA form so that
   * each heap variable has only one definition.
   *
   * <p> Algorithm: Cytron et. al 91  (see renameSymbolicRegisters)
   *
   * @param ir the governing IR
   */
  private static void renameHeapVariables (OPT_IR ir) {
    int n = ir.HIRInfo.SSADictionary.getNumberOfHeapVariables();
    if (n == 0)
      return;
    // we maintain a stack of names for each type of heap variable
    // stacks implements a mapping from type to Stack.
    // Example: to get the stack of names for HEAP<int> variables,
    // use stacks.get(VM_Type.IntType);
    java.util.HashMap stacks = new java.util.HashMap(n);
    // populate the stacks variable with the initial heap variable
    // names, currently stored in the SSADictionary
    for (Enumeration e = ir.HIRInfo.SSADictionary.getHeapVariables(); 
         e.hasMoreElements();) {
      OPT_HeapVariable H = (OPT_HeapVariable)e.nextElement();
      java.util.Stack S = new java.util.Stack();
      S.push(new OPT_HeapOperand(H));
      Object heapType = H.getHeapType();
      stacks.put(heapType, S);
    }
    OPT_BasicBlock entry = ir.cfg.entry();
    search2(entry, stacks, ir);
    registerRenamedHeapPhis(ir);
  }

  /**
   * This routine is the guts of the SSA construction phase for heap array
   * SSA.  The renaming algorithm is analagous to the algorithm for
   * scalars See <code> renameSymbolicRegisters </code> for more details.
   *
   * @param X the current basic block being traversed
   * @param stacks a structure holding the current names for each heap
   * variable
   * @param ir the governing IR
   * used and defined by each instruction.
   */
  private static void search2(OPT_BasicBlock X, java.util.HashMap stacks, 
                              OPT_IR ir ) {
    if (DEBUG) System.out.println("SEARCH2 " + X);
    OPT_SSADictionary dictionary = ir.HIRInfo.SSADictionary;
    for (Enumeration ie = dictionary.getAllInstructions(X); 
         ie.hasMoreElements();) {
      OPT_Instruction A = (OPT_Instruction)ie.nextElement();
      if (!dictionary.usesHeapVariable(A) && !dictionary.defsHeapVariable(A))
        continue;
      if (A.operator() != PHI) {
        // replace the Heap variables USED by this instruction
        OPT_HeapOperand[] uses = dictionary.getHeapUses(A);
        if (uses != null) {
          OPT_HeapOperand[] newUses = new OPT_HeapOperand[uses.length];
          for (int i = 0; i < uses.length; i++) {
            java.util.Stack S = (java.util.Stack)stacks.get
              (uses[i].getHeapType());
            newUses[i] = (OPT_HeapOperand)((OPT_HeapOperand)S.peek()).copy();
            if (DEBUG)
              System.out.println("NORMAL USE PEEK " + newUses[i]);
          }
          dictionary.replaceUses(A, newUses);
        }
      }
      // replace any Heap variable DEF
      OPT_HeapOperand[] defs = dictionary.getHeapDefs(A);
      if (defs != null) {
        OPT_HeapOperand r[] = dictionary.replaceDefs(A, X);
        for (int i = 0; i < r.length; i++) {
          java.util.Stack S = (java.util.Stack)stacks.get(r[i].getHeapType());
          S.push(r[i]);
          if (DEBUG)
            System.out.println("PUSH " + r[i] + " FOR " + r[i].getHeapType());
        }
      }
    } // end of first loop

    for (OPT_BasicBlockEnumeration y = X.getOut(); y.hasMoreElements();) {
      OPT_BasicBlock Y = y.next();
      if (Y.isExit())
        continue;
      // replace each USE in each HEAP-PHI function for Y
      for (Enumeration hp = dictionary.getHeapPhiInstructions(Y); 
           hp.hasMoreElements();) {
        OPT_Instruction s = (OPT_Instruction)hp.nextElement();
        for (int j=0; j<Phi.getNumberOfValues(s); j++) {
          if (Phi.getPred(s,j).block == X) {
            OPT_HeapOperand H1 = (OPT_HeapOperand)Phi.getValue(s,j);
            java.util.Stack S = (java.util.Stack)stacks.get(H1.getHeapType());
            OPT_HeapOperand H2 = (OPT_HeapOperand)S.peek();
            Phi.setValue(s, j, new OPT_HeapOperand(H2.getHeapVariable()));
          }
        }
      }
    } // end of second loop

    for (Enumeration c = ir.HIRInfo.dominatorTree.getChildren(X); 
         c.hasMoreElements();) {
      OPT_DominatorTreeNode v = (OPT_DominatorTreeNode)c.nextElement();
      search2(v.getBlock(), stacks, ir);
    } // end of third loop

    for (Enumeration a = dictionary.getAllInstructions(X); 
         a.hasMoreElements();) {
      OPT_Instruction A = (OPT_Instruction)a.nextElement();
      // retrieve the Heap Variables defined by A
      OPT_HeapOperand[] defs = dictionary.getHeapDefs(A);
      if (defs != null) {
        for (int i = 0; i < defs.length; i++) {
          java.util.Stack S = (java.util.Stack)stacks.get(
                                                          defs[i].getHeapType());
          S.pop();
          if (DEBUG) System.out.println("POP " + defs[i].getHeapType());
        }
      }
    } // end of fourth loop
    if (DEBUG) System.out.println("END SEARCH2 " + X);
  }

  /**
   * After performing renaming on heap phi functions, this
   * routines notifies the SSA dictionary of the new names.
   * 
   * @param ir the governing IR
   */
  private static void registerRenamedHeapPhis (OPT_IR ir) {
    OPT_SSADictionary ssa = ir.HIRInfo.SSADictionary;
    for (Enumeration e1 = ir.getBasicBlocks(); e1.hasMoreElements();) {
      OPT_BasicBlock bb = (OPT_BasicBlock)e1.nextElement();
      for (Enumeration e2 = ssa.getAllInstructions(bb); e2.hasMoreElements();) {
        OPT_Instruction s = (OPT_Instruction)e2.nextElement();
        if (Phi.conforms(s)) {
          if (ssa.defsHeapVariable(s)) {
            int n = Phi.getNumberOfValues(s);
            OPT_HeapOperand[] uses = new OPT_HeapOperand[n];
            for (int i = 0; i < n; i++) {
              uses[i] = (OPT_HeapOperand)Phi.getValue(s, i);
            }
            ssa.replaceUses(s, uses);
          }
        }
      }
    }
  }

  /**
   * Store a copy of the Heap variables each instruction defs.
   * 
   * @param IR governing IR
   * @param store place to store copies
   */
  private static void copyHeapDefs (OPT_IR ir, java.util.HashMap store) {
    OPT_SSADictionary dictionary = ir.HIRInfo.SSADictionary;
    for (OPT_BasicBlockEnumeration be = ir.forwardBlockEnumerator(); 
         be.hasMoreElements();) {
      OPT_BasicBlock bb = be.next();
      for (Enumeration e = dictionary.getAllInstructions(bb); 
           e.hasMoreElements();) {
        OPT_Instruction s = (OPT_Instruction)e.nextElement();
        store.put(s, ir.HIRInfo.SSADictionary.getHeapDefs(s));
      }
    }
  }

  /**
   * Set the type of every operand in a phi instruction.
   *
   * @param s phi instruction
   * @param t type
   * @param phiDefTypes this holds a mapping from register-> type for
   *	    registers that are defined by phi instructions
   */
  private static void setPhiType (OPT_Instruction s, VM_Type t, 
                                  java.util.HashMap phiDefTypes, 
                                  int operandIndex) {
    // if t is null, do nothing
    if (t == null) return;

    OPT_Operand op = Phi.getValue(s, operandIndex);
    if (op.isRegister()) {
      op.asRegister().type = t;
    }
    OPT_Operand result = Phi.getResult(s);
    if (result.isRegister()) {
      if (result.asRegister().type == null)
        result.asRegister().type = t; 
      else {
        VM_Type meet = OPT_ClassLoaderProxy.proxy.
          findCommonSuperclass(t, result.asRegister().type);
        if (meet == null) {
          if ((   (  result.asRegister().type == VM_Type.IntType)
                  && t.isReferenceType())
              || ((  result.asRegister().type.isReferenceType())
                  && t == VM_Type.IntType)) {
            meet = VM_Type.IntType;
          }
        }
        t = meet;
        result.asRegister().type = t;
      }
      if (VM.VerifyAssertions) VM.assert (t != null);
      phiDefTypes.put(result.asRegister().register, t);
      OPT_RegisterOperandEnumeration uses = OPT_DefUse.uses
        (result.asRegister().register);
      while (uses.hasMoreElements()) {
        OPT_RegisterOperand use = uses.next();
        if (use.type == null)
          use.type = result.asRegister().type;
      }
    }
  }

  /**
   * Find a parameter type.
   *
   * <p> Given a register that holds a parameter, look at the register's
   * use chain to find the type of the parameter
   */
  private static VM_Type findParameterType (OPT_Register p) {
    OPT_RegisterOperand firstUse = p.useList;
    if (firstUse == null) {
      return  null;             // parameter has no uses
    }
    return  firstUse.type;
  }
}



