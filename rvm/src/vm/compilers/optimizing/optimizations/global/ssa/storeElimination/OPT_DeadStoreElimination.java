/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import  java.util.*;
import instructionFormats.*;

/**
 * This class implements the dead store scalar replacement algorithm by
 * Fink, Knobe & Sarkar, SAS 2000.  See paper for details.
 *
 * <p> NOTE: This prototype implementation is not terribly efficient.
 * 
 * @author Stephen Fink
 *
 */
final class OPT_DeadStoreElimination extends 
    OPT_OptimizationPlanCompositeElement {

  /**
   * Construct this phase as a composite of several primitive pases.
   */
  OPT_DeadStoreElimination () {
    super("Dead StoreElimination", new OPT_OptimizationPlanElement[] {
      // 1. Set up IR state to control SSA translation as needed
      new OPT_OptimizationPlanAtomicElement(new StoreEliminationPreparation()), 
          // 2. Get the desired SSA form
      new OPT_OptimizationPlanAtomicElement(new OPT_EnterSSA()), 
      // 3. Perform global value numbering
      new OPT_OptimizationPlanAtomicElement(new OPT_GlobalValueNumber()), 
          // 4. Perform load elimination
      new OPT_OptimizationPlanAtomicElement(new StoreElimination()), 
    });
  }

  /**
   * Redefine shouldPerform so that none of the subphases will occur
   * unless we pass through this test.
   * 
   * @param options controlling compiler options
   * @return whether we should perform this phase.
   */
  boolean shouldPerform (OPT_Options options) {
    return  options.STORE_ELIMINATION;
  }

  /**
   * The compiler phase that actually performs the optimization.
   */
  private static final class StoreElimination extends OPT_CompilerPhase
      implements OPT_Operators {
    static final boolean DEBUG = false;

    /**
     * Should we perform this phase?
     * @param options controlling compiler options
     * @return whether to perform the phase
     */
    final boolean shouldPerform (OPT_Options options) {
      return  options.STORE_ELIMINATION;
    }

    /**
     * Return a String representation for this phase
     * @return a String representation for this phase
     */
    final String getName () {
      return  "Array SSA Dead Store Elimination";
    }

    /**
     * Should the IR be printed either before or after performing this phase?
     *
     * @param options controlling compiler options
     * @param before true iff querying before the phase
     * @return true or false
     */
    final boolean printingEnabled (OPT_Options options, boolean before) {
      return  false;
    }

    /** 
     * Main driver for dead store elimination.
     *
     * <P> Preconditions: Array SSA form and Global Value Numbers computed
     *
     * @param ir the governing IR
     */
    final public void perform (OPT_IR ir) {
      try {
        OPT_DeadStoreSystem system = new OPT_DeadStoreSystem(ir);
        system.solve();
        OPT_DF_Solution solution = system.getSolution();
        eliminateDeadStores(ir, solution);
      } catch (OPT_TooManyEquationsException e) {
      // give up
      }
      // Store elimination preserves SSA!  Note this.
      ir.actualSSAOptions.setScalarValid(true);
      ir.actualSSAOptions.setHeapValid(true);
    }

    /**
     * Using the dataflow solution, eliminate dead store instructions
     *
     * @param ir the governing IR
     * @param solution the dataflow solution from the analysis
     */
    final private void eliminateDeadStores (OPT_IR ir, 
        OPT_DF_Solution solution) {
      OPT_SSADictionary ssa = ir.HIRInfo.SSADictionary;
      OPT_GlobalValueNumberState valueNumbers = ir.HIRInfo.valueNumbers;
      // walk through each instruction
      // eliminate it if the dataflow solution says it's dead
      // can't use enumerator because of remove()
      for (OPT_Instruction s = ir.firstInstructionInCodeOrder(), nextS = null; 
          s != null; s = nextS) {
        nextS = s.nextInstructionInCodeOrder();
        if (!PutField.conforms(s) && 
            !PutStatic.conforms(s) && !AStore.conforms(s))
          continue;
        if (!ssa.defsHeapVariable(s))
          continue;
        if (ssa.getNumberOfHeapDefs(s) != 1)
          continue;
        // don't attempt to eliminate stores that can throw exceptions
        if (s.isPEI())
          continue;
        if (PutField.conforms(s) || PutStatic.conforms(s)) {
          OPT_HeapOperand A[] = ssa.getHeapDefs(s);
          Object address = OPT_DeadStoreSystem.getStoreAddress(s, A[0]);
          int v = valueNumbers.getValueNumber(address);
          OPT_ObjectPair key = new OPT_ObjectPair(A[0].getHeapVariable(), 
              OPT_DeadStoreSystem.DEAD_DEF);
          OPT_ValueNumberSetCell cell = (OPT_ValueNumberSetCell)solution.lookup
              (key);
          if (cell.contains(v)) {
            if (DEBUG)
              System.out.println("ELIMINATING STORE: " + s);
            s.remove();
          }
        } 
        else {                  // AStore.conforms(s)
          OPT_HeapOperand A[] = ssa.getHeapDefs(s);
          Object array = AStore.getArray(s);
          Object index = AStore.getIndex(s);
          int v1 = valueNumbers.getValueNumber(array);
          int v2 = valueNumbers.getValueNumber(index);
          OPT_ObjectPair key = new OPT_ObjectPair(A[0].getHeapVariable(), 
              OPT_DeadStoreSystem.DEAD_DEF);
          OPT_ValueNumberPairSetCell cell = (OPT_ValueNumberPairSetCell)
              solution.lookup(key);
          if (cell.contains(v1, v2)) {
            if (DEBUG)
              System.out.println("ELIMINATING STORE: " + s);
            s.remove();
          }
        }
      }
    }
  }

  /**
   * compiler phase to set up the IR to prepare SSA form for
   * Dead Store Elimination
   */
  private static class StoreEliminationPreparation extends OPT_CompilerPhase {

    /**
     * Should we perform this phase?
     * @param options controlling compiler options
     * @return whether to perform the phase
     */
    final boolean shouldPerform (OPT_Options options) {
      return  options.STORE_ELIMINATION;
    }

    /**
     * Return a String representation for this phase
     * @return a String representation for this phase
     */
    final String getName () {
      return  "Store Elimination Preparation";
    }

    /**
     * Should the IR be printed either before or after performing this phase?
     *
     * @param options controlling compiler options
     * @param before true iff querying before the phase
     * @return true or false
     */
    final boolean printingEnabled (OPT_Options options, boolean before) {
      return  false;
    }

    /**
     * Register the SSA properties desired for dead store elimination.
     *
     * @param ir the governing IR
     */
    final public void perform (OPT_IR ir) {
      ir.desiredSSAOptions = new OPT_SSAOptions();
      ir.desiredSSAOptions.setScalarsOnly(false);
      ir.desiredSSAOptions.setBackwards(true);
      ir.desiredSSAOptions.setInsertUsePhis(false);
      ir.desiredSSAOptions.setHeapTypes(null);
    }
  }
}


/**
 * Implementation of the data-flow equation system for
 * dead store elimination
 */
class OPT_DeadStoreSystem extends OPT_DF_System
    implements OPT_Operators {

  /**
   *  Get the address field from a load instruction.
   *  <p> PRECONDITION: GetField.conforms(s) or GetStatic.conforms(s)
   *
   *  @param s the load instruction
   *  @param H the heap variabled def'ed by the load instruction
   *  @return an object representing the address read from in the heap
   */
  public static Object getLoadAddress (OPT_Instruction s, OPT_HeapOperand H) {
    Object address;
    if (GetField.conforms(s)) {
      address = GetField.getRef(s);
    } 
    else {      // GetStatic
      // use the static field itself as the address
      address = H.getHeapVariable().getHeapType();
    }
    return  address;
  }

  /**
   *  Get the address field from a store instruction.
   *  <p> PRECONDITION: PutStatic.conforms(s) || PutField.conforms(s)
   *
   *  @param s the load instruction
   *  @param H the heap variabled def'ed by the load instruction
   *  @return an object representing the address written to in the heap
   */
  public static Object getStoreAddress (OPT_Instruction s, OPT_HeapOperand H) {
    Object address;
    if (PutField.conforms(s)) {
      address = PutField.getRef(s);
    } 
    else {      // PutStatic 
      // use the static field itself as the address
      address = H.getHeapVariable().getHeapType();
    }
    return  address;
  }

  /** 
   * Construct the system for an IR
   *
   * @param ir the IR to solve for
   */
  public OPT_DeadStoreSystem (OPT_IR ir) throws OPT_TooManyEquationsException
  {
    this.ir = ir;
    ssa = ir.HIRInfo.SSADictionary;
    valueNumbers = ir.HIRInfo.valueNumbers;
    // recompute DU-chains for heap variables
    ssa.recomputeArrayDU();
    setupEquations();
  }

  /**
   * A mapping from OPT_HeapVariable to Object
   */
  private java.util.HashMap KILL = new java.util.HashMap();               
  /**
   * A mapping from OPT_HeapVariable to Object
   */
  private java.util.HashMap LIVE = new java.util.HashMap();             
  /**
   * The governing IR
   */
  private OPT_IR ir;            
  /**
   * Lookaside SSA information
   */
  private OPT_SSADictionary ssa;                
  /**
   * Value number analysis result
   */
  private OPT_GlobalValueNumberState valueNumbers;             
  /**
   * A component used to name lattice cells
   */
  public static final String DEAD_USE = "DEAD_USE";
  /**
   * A component used to name lattice cells
   */
  public static final String DEAD_DEF = "DEAD_DEF";
  /**
   * A component used to name lattice cells
   */
  public static final String KILL_KEY = "KILL";
  /**
   * A component used to name lattice cells
   */
  public static final String LIVE_KEY = "LIVE";

  /**
   * Walk through the IR, and add dataflow equations to the system
   * as needed.
   */
  private void setupEquations () throws OPT_TooManyEquationsException {
    // walk thorough each basic block in the IR
    for (Enumeration e = ir.getBasicBlocks(); e.hasMoreElements();) {
      OPT_BasicBlock bb = (OPT_BasicBlock)e.nextElement();
      for (Enumeration e2 = ssa.getAllInstructions(bb); e2.hasMoreElements();) {
        OPT_Instruction s = (OPT_Instruction)e2.nextElement();
        // only consider instructions which use/def Array SSA variables
        if (!ssa.usesHeapVariable(s) && !ssa.defsHeapVariable(s))
          continue;
        if (s.isDynamicLinkingPoint())
          processCall(s); 
        else if (GetField.conforms(s))
          processNonKiller(s); 
        else if (GetStatic.conforms(s))
          processNonKiller(s); 
        else if (New.conforms(s))
          processNonKiller(s); 
        else if (NewArray.conforms(s))
          processNonKiller(s); 
        else if (s.operator() == ARRAYLENGTH)
          processNonKiller(s); 
        else if (ALoad.conforms(s))
          processNonKiller(s); 
        else if (PutStatic.conforms(s))
          processStore(s); 
        else if (PutField.conforms(s))
          processStore(s); 
        else if (AStore.conforms(s))
          processAStore(s); 
        else if (Call.conforms(s))
          processCall(s); 
        else if (CallSpecial.conforms(s))
          processCall(s); 
        else if (MonitorOp.conforms(s))
          processCall(s); 
        else if (Prepare.conforms(s))
          processCall(s); 
        else if (Attempt.conforms(s))
          processCall(s); 
        else if (CacheOp.conforms(s))
          processCall(s); 
        else if (Phi.conforms(s))
          processPhi(s); 
        else if (Return.conforms(s))
          processExit(s); 
        else if (s.isPEI())
          processExit(s);
        //-#if RVM_FOR_POWERPC
        else if (s.operator() == ISYNC)
          processCall(s); 
        else if (s.operator() == SYNC)
          processCall(s);
        //-#endif
        else 
          throw  new OPT_OptimizingCompilerException(
              "DeadStoreElimination: unexpected operator "
              + s);
        if (getNumberOfEquations() > ir.options.MAX_DEADSTORE_SYSTEM_SIZE)
          throw  new OPT_TooManyEquationsException();
      }
    }
  }

  /**
   * Initialize each DEAD_DEF and DEAD_USE variable to be universal.
   */
  protected void initializeLatticeCells () {
    for (java.util.Iterator e = cells.values().iterator(); e.hasNext();) {
      Object cell = e.next();
      if (cell instanceof OPT_ValueNumberSetCell) {
        OPT_ValueNumberSetCell s = (OPT_ValueNumberSetCell)cell;
        setUniversal(s);
      } 
      else {
        OPT_ValueNumberPairSetCell s = (OPT_ValueNumberPairSetCell)cell;
        setUniversal(s);
      }
    }
  }

  /**
   * Initialize a set to hold all possible value numbers 
   * used to index into a Heap Object A
   *
   * @param cell the set of value numbers to initialize
   */
  private void setUniversal (OPT_ValueNumberSetCell cell) {
    OPT_ObjectPair key = (OPT_ObjectPair)cell.getKey();
    OPT_HeapVariable A;
    if (key.o1 instanceof OPT_HeapVariable) {
      A = (OPT_HeapVariable)key.o1;
    } 
    else {
      A = ((OPT_HeapOperand)key.o1).getHeapVariable();
    }
    for (java.util.Iterator i = ssa.iterateOriginalHeapUses(A); i.hasNext();) {
      OPT_HeapOperand H = (OPT_HeapOperand)i.next();
      OPT_Instruction s = H.instruction;
      if (GetField.conforms(s) || GetStatic.conforms(s)) {
        Object address = getLoadAddress(s, H);
        int v = valueNumbers.getValueNumber(address);
        cell.add(v);
      }
    }
    for (java.util.Iterator i = ssa.iterateOriginalHeapDefs(A); i.hasNext();) {
      OPT_HeapOperand H = (OPT_HeapOperand)i.next();
      OPT_Instruction s = H.instruction;
      if (PutStatic.conforms(s) || PutField.conforms(s)) {
        Object address = getStoreAddress(s, H);
        int v = valueNumbers.getValueNumber(address);
        cell.add(v);
      }
    }
  }

  /**
   * Initialize a set to hold all possible value number pairs
   * used to index into a Heap Object A
   *
   * @param cell the set of value number pairs to initialize
   */
  private void setUniversal (OPT_ValueNumberPairSetCell cell) {
    OPT_ObjectPair key = (OPT_ObjectPair)cell.getKey();
    OPT_HeapVariable A;
    if (key.o1 instanceof OPT_HeapVariable) {
      A = (OPT_HeapVariable)key.o1;
    } 
    else {
      A = ((OPT_HeapOperand)key.o1).getHeapVariable();
    }
    for (java.util.Iterator i = ssa.iterateOriginalHeapUses(A); i.hasNext();) {
      OPT_HeapOperand H = (OPT_HeapOperand)i.next();
      OPT_Instruction s = H.instruction;
      if (ALoad.conforms(s)) {
        OPT_Operand array = ALoad.getArray(s);
        OPT_Operand index = ALoad.getIndex(s);
        int v1 = valueNumbers.getValueNumber(array);
        int v2 = valueNumbers.getValueNumber(index);
        cell.add(v1, v2);
      }
    }
    for (java.util.Iterator i = ssa.iterateOriginalHeapDefs(A); i.hasNext();) {
      OPT_HeapOperand H = (OPT_HeapOperand)i.next();
      OPT_Instruction s = H.instruction;
      if (AStore.conforms(s)) {
        OPT_Operand array = AStore.getArray(s);
        OPT_Operand index = AStore.getIndex(s);
        int v1 = valueNumbers.getValueNumber(array);
        int v2 = valueNumbers.getValueNumber(index);
        cell.add(v1, v2);
      }
    }
  }

  /**
   * Initialize the work list for the dataflow equation system.
   */
  protected void initializeWorkList () {
    addAllEquationsToWorkList();
  }

  /**
   * Update the set of dataflow equations to account for the actions 
   * of an instruction s that does not kill any locations.
   *
   * <p> Instruction s must define at most one Array SSA Variable, A2, and
   * use at most one Array SSA Variable, A1.
   * <p> Add the following data flow equations to the system:
   * Let <A1,s> be the name for the use of A1 in instruction s.
   * <pre>
   *   KILL(A2) = {} // loads don't overwrite anything
   *   DEAD_USE(<A1,s>) = DEAD_DEF(A2) 
   *   DEAD_DEF(A2) = { intersection of DEAD_USE(u) for all uses of
   *			 A2 } - LIVE(A2)
   * </pre>
   * @param s the instruction that does not kill any locations
   */
  private void processNonKiller (OPT_Instruction s) {
    OPT_HeapOperand[] A1 = ssa.getHeapUses(s);
    // if this load is a PEI
    if (A1.length > 1) {
      // if we get here, this instruction must potentially exit the routine
      processExit(s);
      return;
    }
    OPT_HeapOperand[] A2 = ssa.getHeapDefs(s);
    if ((A2 != null) && (A2.length > 1)) {
      throw  new OPT_OptimizingCompilerException("OPT_DeadStoreSystem.processNonKiller: instruction defs multiple heap variables? "
          + s);
    }
    if (A2 != null) {
      // this instruction defs one heap variable
      setKill(A2[0], null);
      computeLive(A2[0]);
      addDeadUseEquation(A1[0], A2[0], A2[0]);
      addDeadDefEquation(A2[0]);
    } 
    else {
    // this instruction defs NO heap variables
    // does not affect data flow equations
    }
  }

  /**
   * Update the set of dataflow equations to account for the actions 
   * of a store instruction.
   *
   * <p> The store is of the form A[k] = x.  Call this instruction s.
   * It defs Array SSA Variable A2 and uses Array SSA Variable A1.
   * Let <A1,s> be the name for the use of A1 in instruction s.
   * Add the following data flow equations to the system:
   * <pre>
   *   KILL(A2) = { V(k) } // store overwrites value at index k.
   *   DEAD_USE(<A1,s>) = KILL(A_2) U DEAD_DEF(A2)
   *   DEAD_DEF(A2) = { intersection of DEAD_USE(u) for all uses of
   * 		 A2 } - LIVE(A2)
   * </pre>
   * @param s the store instruction
   */
  private void processStore (OPT_Instruction s) {
    OPT_HeapOperand[] A1 = ssa.getHeapUses(s);
    OPT_HeapOperand[] A2 = ssa.getHeapDefs(s);
    if (s.isPEI()) {
      processExit(s);
      return;
    }
    if (A2.length != 1) {
      throw  new OPT_OptimizingCompilerException("OPT_DeadStoreSystem.processStore: store instruction defs multiple heap variables?"
          + s);
    }
    Object address = getStoreAddress(s, A2[0]);
    setKill(A2[0], address);
    computeLive(A2[0]);
    addDeadUseEquation(A1[0], A2[0], A2[0]);
    addDeadDefEquation(A2[0]);
  }

  /**
   * Update the set of dataflow equations to account for the actions 
   * of an Astore instruction s.
   *
   * <p> The load is of the form y[k] = x.  Call this instruction s.
   * It defs Array SSA Variable A2 and uses Array SSA Variable A1.
   * Let <A1,s> be the name for the use of A1 in instruction s.
   * Add the following data flow equations to the system:
   * <pre>
   *   KILL(A2) = { (V(y),V(x)) } // store overwrites value at index x.
   *   DEAD_USE(<A1,s>) = KILL(A_2) U DEAD_DEF(A2)
   *   DEAD_DEF(A2) = { intersection of DEAD_USE(u) for all uses of
   * 		 A2 } - LIVE(A2)
   * </pre>
   * @param s the astore instruction
   */
  private void processAStore (OPT_Instruction s) {
    OPT_HeapOperand[] A1 = ssa.getHeapUses(s);
    OPT_HeapOperand[] A2 = ssa.getHeapDefs(s);
    if (s.isPEI()) {
      processExit(s);
      return;
    }
    if (A2.length != 1) {
      throw  new OPT_OptimizingCompilerException("OPT_DeadStoreSystem.processAStore: astore instruction defs multiple heap variables?"
          + s);
    }
    OPT_Operand array = AStore.getArray(s);
    OPT_Operand index = AStore.getIndex(s);
    setKill(A2[0], array, index);
    computeLive(A2[0]);
    addDeadUseEquation(A1[0], A2[0], A2[0]);
    addDeadDefEquation(A2[0]);
  }

  /**
   * Update the set of dataflow equations to account for the actions 
   * of a Call instruction.
   *
   * @param s the call instruction
   */
  private void processCall (OPT_Instruction s) {
    // treat a call like an exit
    processExit(s);
  }

  /**
   * Update the set of dataflow equations to account for the actions
   * of an instruction s that may exit the method
   *
   * Namely,
   * for each heap variable A that s uses, LIVE(A) = Universal
   * Also compute live for each heap variable s DEFS
   */
  private void processExit (OPT_Instruction s) {
    OPT_HeapOperand[] A = ssa.getHeapUses(s);
    for (int i = 0; i < A.length; i++) {
      setLiveUniversal(A[i].getHeapVariable());
    }
    OPT_HeapOperand[] B = ssa.getHeapDefs(s);
    if (B != null) {
      for (int j = 0; j < B.length; j++) {
        computeLive(B[j]);
      }
    }
  }

  /**
   * Update the set of dataflow equations to account for the actions 
   * of a Phi instruction
   *
   * <p> Let s: A = phi(B1, B2, ..)
   *
   * <p> For each B, DEAD_USE(< B,s >) = DEAD_DEF(A)
   * <p> Fake this using the UnionOperator
   * <p> also:
   * <pre>
   * DEAD_DEF(A) =  intersection of DEAD_USE(u) for all uses of A
   *		     - { v | there exists x in LIVE(A) st. not DD (v,x) }
   * </pre>
   */
  private void processPhi (OPT_Instruction s) {
    OPT_Operand result = Phi.getResult(s);
    if (!(result instanceof OPT_HeapOperand))
      return;
    OPT_HeapOperand A = (OPT_HeapOperand)result;
    for (int i = 0; i < Phi.getNumberOfValues(s); i++) {
      OPT_HeapOperand B = (OPT_HeapOperand)Phi.getValue(s, i);
      addDeadUseEquation(B, null, A);
    }
    addDeadDefEquation(A);
    computeLive(A);
  }

  /**
   * Register that the definition of Array SSA variable A kills 
   * locations with the same value number as key.
   *
   * @param A the heap variable
   * @param key a representative object whose value number identifies the
   * set of locations killed by the definition of A.
   */
  private void setKill (OPT_HeapOperand A, Object key) {
    if (key == null) {
      // register that A kills nothing
      KILL.put(A.getHeapVariable(), null);
      return;
    }
    int v = valueNumbers.getValueNumber(key);
    OPT_ObjectPair name = new OPT_ObjectPair(A.getHeapVariable(), KILL_KEY);
    OPT_ValueNumberSetCell s = new OPT_ValueNumberSetCell(name);
    s.add(v);
    KILL.put(A.getHeapVariable(), s);
  }

  /**
   * register that the definition of Array SSA variable A kills 
   * locations with the same value numbers as < key1, key2 >
   * <p> (used for arrays; key1 is the array pointer, key2 is the index
   *  into the array);
   * @param A the heap variable
   * @param key1 a representative object whose value number is the first
   * item in the tuple which represents the
   * set of locations killed by the definition of A.
   * @param key1 a representative object whose value number is the second
   * item in the tuple which represents the
   * set of locations killed by the definition of A.
   */
  private void setKill (OPT_HeapOperand A, Object key1, Object key2) {
    int v1 = valueNumbers.getValueNumber(key1);
    int v2 = valueNumbers.getValueNumber(key2);
    OPT_ObjectPair key = new OPT_ObjectPair(A.getHeapVariable(), KILL_KEY);
    OPT_ValueNumberPairSetCell s = new OPT_ValueNumberPairSetCell(key);
    s.add(v1, v2);
    KILL.put(A.getHeapVariable(), s);
  }

  /**
   * Compute the LIVE set for an Array SSA variable A.
   *
   * <p> LIVE(A) = { V(k) | there exists some load of A[k] }
   * <p> If A is exposed on method exit, LIVE(A) = Universal
   *
   * @param A a heap operand holding the aforementioned heap variable
   */
  private void computeLive (OPT_HeapOperand A) {
    if (ssa.isExposedOnExit(A.getHeapVariable())) {
      setLiveUniversal(A.getHeapVariable());
      return;
    }
    for (java.util.Iterator i = ssa.iterateHeapUses(A.getHeapVariable()); 
        i.hasNext();) {
      OPT_HeapOperand H = (OPT_HeapOperand)i.next();
      OPT_Instruction s = H.instruction;
      if (GetStatic.conforms(s) || GetField.conforms(s)) {
        Object address = getLoadAddress(s, H);
        int v = valueNumbers.getValueNumber(address);
        addToLiveSet(A.getHeapVariable(), v);
      }
      if (ALoad.conforms(s)) {
        OPT_Operand array = ALoad.getArray(s);
        OPT_Operand index = ALoad.getIndex(s);
        int v1 = valueNumbers.getValueNumber(array);
        int v2 = valueNumbers.getValueNumber(index);
        addToLiveSet(A.getHeapVariable(), v1, v2);
      }
    }
  }

  /**
   * Add the value number v to the LIVE set registered for heap 
   * variable A.
   * 
   * @param A the heap variable
   * @param v the value number
   */
  private void addToLiveSet (OPT_HeapVariable A, int v) {
    OPT_ValueNumberSetCell s = (OPT_ValueNumberSetCell)getLive(A);
    OPT_ObjectPair key = new OPT_ObjectPair(A, LIVE_KEY);
    if (s == null)
      s = new OPT_ValueNumberSetCell(key);
    s.add(v);
    setLive(A, s);
  }

  /**
   * Set the live set for a heap variable to be UNIVERSAL.
   *
   * @param A the heap variable in question
   */
  private void setLiveUniversal (OPT_HeapVariable A) {
    if (A.getHeapType() instanceof VM_Type) {
      // represents an array
      OPT_ValueNumberPairSetCell s = (OPT_ValueNumberPairSetCell)getLive(A);
      OPT_ObjectPair key = new OPT_ObjectPair(A, LIVE_KEY);
      if (s == null)
        s = new OPT_ValueNumberPairSetCell(key);
      setUniversal(s);
      setLive(A, s);
    } 
    else {
      // represents a field
      OPT_ValueNumberSetCell s = (OPT_ValueNumberSetCell)getLive(A);
      OPT_ObjectPair key = new OPT_ObjectPair(A, LIVE_KEY);
      if (s == null)
        s = new OPT_ValueNumberSetCell(key);
      setUniversal(s);
      setLive(A, s);
    }
  }

  /**
   * Add the value number pair <v1,v2> to the LIVE set registered for heap 
   * variable A
   *
   * @param A the heap variable
   * @param v1 first item in the value number pair
   * @param v2 second item in the value number pair
   */
  private void addToLiveSet (OPT_HeapVariable A, int v1, int v2) {
    OPT_ValueNumberPairSetCell s = (OPT_ValueNumberPairSetCell)getLive(A);
    OPT_ObjectPair key = new OPT_ObjectPair(A, LIVE_KEY);
    if (s == null)
      s = new OPT_ValueNumberPairSetCell(key);
    s.add(v1, v2);
    setLive(A, s);
  }

  /**
   * Add the following dataflow equation to the system
   * <pre>
   * DEAD_USE(A0) = KILL(A1) U DEAD_DEF(A2)
   * </pre>
   *
   * @param A0 heap variable in the dataflow equation
   * @param A1 heap variable in the dataflow equation
   * @param A2 heap variable in the dataflow equation
   */
  private void addDeadUseEquation (OPT_HeapOperand A0, OPT_HeapOperand A1, 
      OPT_HeapOperand A2) {
    OPT_DF_LatticeCell cell0 = findOrCreateDeadUseCell(A0);
    OPT_DF_LatticeCell cell2 = findOrCreateDeadDefCell(A2);
    UnionOperator U = null;
    if (A1 != null) {
      U = new UnionOperator(A1.getHeapVariable());
    } 
    else {
      U = new UnionOperator(null);
    }
    newEquation(cell0, U, cell2);
  }

  /** 
   * Return the object (either an OPT_ValueNumberSetCell or an
   *			 OPT_ValueNumberPairSetCell)
   * registered as the KILL set for a heap variable A.
   *
   * @param A the heap variable
   * @return the object representing the kill set for A
   */
  private Object getKill (OPT_HeapVariable A) {
    return  KILL.get(A);
  }

  /**
   * Find or create a Dead Use cell corresponding to a heap operand.
   *
   * @param A the heap operand
   * @return a lattice cell holding the dead use set for A
   */
  private OPT_DF_LatticeCell findOrCreateDeadUseCell (OPT_HeapOperand A) {
    OPT_ObjectPair key = new OPT_ObjectPair(A, A.getInstruction());
    return  findOrCreateCell(key);
  }

  /**
   * Find or create a Dead Def cell corresponging to a heap operand
   *
   * @param A the heap operand
   * @return a lattice cell holding the dead def set for A
   */
  private OPT_DF_LatticeCell findOrCreateDeadDefCell (OPT_HeapOperand A) {
    OPT_ObjectPair key = new OPT_ObjectPair(A.getHeapVariable(), DEAD_DEF);
    return  findOrCreateCell(key);
  }

  /**
   * Create a lattice cell corresponding to a given key
   *
   * @param key the key identifying the lattice cell
   */
  protected OPT_DF_LatticeCell makeCell (Object key) {
    OPT_ObjectPair p = (OPT_ObjectPair)key;
    OPT_HeapVariable H;
    if (p.o1 instanceof OPT_HeapOperand) {
      H = ((OPT_HeapOperand)p.o1).getHeapVariable();
    } 
    else {
      H = (OPT_HeapVariable)p.o1;
    }
    Object heapType = H.getHeapType();
    if (heapType instanceof VM_Type) {
      // H represents an array
      return  new OPT_ValueNumberPairSetCell(key);
    } 
    else {
      // H represents a field
      return  new OPT_ValueNumberSetCell(key);
    }
  }

  /**
   * Add the following dataflow equation to the system.
   * 
   * <pre>
   * DEAD_DEF(A) =  intersection of DEAD_USE(u) for all uses of A
   *		     - { v | there exists x in LIVE(A) st. not DD (v,x) }
   * <pre>
   */
  private void addDeadDefEquation (OPT_HeapOperand A) {
    int nUses = ssa.getNumberOfUses(A.getHeapVariable());
    OPT_DF_LatticeCell uses[] = new OPT_DF_LatticeCell[nUses];
    int j = 0;
    for (java.util.Iterator i = ssa.iterateHeapUses(A.getHeapVariable()); 
        i.hasNext();) {
      OPT_HeapOperand H = (OPT_HeapOperand)i.next();
      // if the instruction does not DEF any heap variables,
      // then DEAD_USE(H) does not appear in the equation
      if (ssa.defsHeapVariable(H.getInstruction())) {
        OPT_DF_LatticeCell cell = findOrCreateDeadUseCell(H);
        uses[j++] = cell;
      }
    }
    DeadDefOperator d = new DeadDefOperator(A.getHeapVariable());
    OPT_DF_LatticeCell lhs = findOrCreateDeadDefCell(A);
    // strip trailing nulls from uses
    OPT_DF_LatticeCell nonNullUses[] = new OPT_DF_LatticeCell[j];
    for (int i = 0; i < nonNullUses.length; i++) {
      nonNullUses[i] = uses[i];
    }
    newEquation(lhs, d, nonNullUses);
  }

  /**
   * return the object (either an OPT_ValueNumberSetCell or an
   *			 OPT_ValueNumberPairSetCell) registered
   * as the LIVE set for a heap variable A.
   *
   * @param A the heap variable in question
   */
  private Object getLive (OPT_HeapVariable A) {
    return  LIVE.get(A);
  }

  /**
   * register the object (either an OPT_ValueNumberSetCell or an
   *			 OPT_ValueNumberPairSetCell) 
   * as the LIVE set for a heap variable A
   * 
   * @param A the heap variable in question
   * @param o the new live set for A
   */
  private void setLive (OPT_HeapVariable A, Object o) {
    LIVE.put(A, o);
  }

  /**
   * This class serves as an operator to perform a set union
   * between the kill set for a variable H, and another 
   * OPT_ValueNumberPairSetCell.
   */
  class UnionOperator extends OPT_DF_Operator {
    /**
     * The heap variable H whose kill set this object will union with.
     */
    OPT_HeapVariable killH;

    /**
     * Create a set union operator for the kill set of a heap variable.
     *
     * @param H the heap variable
     */
    UnionOperator (OPT_HeapVariable H) {
      this.killH = H;
    }

    /**
     * Return a String representation of this operator
     * 
     * @return a String representation of this operator
     */
    public String toString () {
      return  "!UNION " + getKill(killH) + "!";
    }

    /**
     * Perform the set operation l = r U KILL(H)
     *
     * @param operands operands[0] is l and operands[1] is r.
     * @return true iff the result of this union (operands[0]) changes
     * from its previous value.
     */
    boolean evaluate (OPT_DF_LatticeCell[] operands) {
      OPT_DF_LatticeCell lhs = operands[0];
      OPT_DF_LatticeCell rhs = operands[1];
      if (lhs instanceof OPT_ValueNumberSetCell) {
        return  evaluateForFields(lhs, rhs);
      } 
      else {
        return  evaluateForArrays(lhs, rhs);
      }
    }

    /**
     * Perform the set operation l = r U KILL(H)
     *
     * @param l the left-hand side of the equation
     * @param r the first operand on the right-hand side of this equation
     * @return true iff the value of l changes as a result.
     */
    boolean evaluateForFields (OPT_DF_LatticeCell l, OPT_DF_LatticeCell r) {
      OPT_ValueNumberSetCell lhs = (OPT_ValueNumberSetCell)l;
      OPT_ValueNumberSetCell rhs = (OPT_ValueNumberSetCell)r;
      int[] oldNumbers = lhs.getValueNumbers();
      lhs.clear();
      int[] numbers = rhs.getValueNumbers();
      // add all rhs numbers to lhs
      for (int i = 0; i < numbers.length; i++) {
        lhs.add(numbers[i]);
      }
      // add all numbers from S to lhs
      // if S is null, add nothing
      OPT_ValueNumberSetCell S = (OPT_ValueNumberSetCell)getKill(killH);
      if (S != null) {
        numbers = S.getValueNumbers();
        for (int i = 0; i < numbers.length; i++) {
          lhs.add(numbers[i]);
        }
      }
      // check if anything has changed
      int[] newNumbers = lhs.getValueNumbers();
      boolean changed = OPT_ValueNumberSetCell.setsDiffer(oldNumbers, 
          newNumbers);
      return  changed;
    }

    /**
     * Perform the set operation l = r U KILL(H)
     *
     * @param l the left-hand side of the equation
     * @param r the first operand on the right-hand side of this equation
     * @return true iff the value of l changes as a result.
     * put your documentation comment here
     */
    boolean evaluateForArrays (OPT_DF_LatticeCell l, OPT_DF_LatticeCell r) {
      OPT_ValueNumberPairSetCell lhs = (OPT_ValueNumberPairSetCell)l;
      OPT_ValueNumberPairSetCell rhs = (OPT_ValueNumberPairSetCell)r;
      OPT_ValueNumberPair[] oldNumbers = lhs.getValueNumbers();
      lhs.clear();
      OPT_ValueNumberPair[] numbers;
      if (rhs != null) {
        numbers = rhs.getValueNumbers();
        // add all rhs numbers to lhs
        for (int i = 0; i < numbers.length; i++) {
          lhs.add(numbers[i]);
        }
      }
      // add all numbers from S to lhs
      // if S is null, add nothing
      OPT_ValueNumberPairSetCell S = (OPT_ValueNumberPairSetCell)getKill(killH);
      if (S != null) {
        numbers = S.getValueNumbers();
        for (int i = 0; i < numbers.length; i++) {
          lhs.add(numbers[i]);
        }
      }
      // check if anything has changed
      OPT_ValueNumberPair[] newNumbers = lhs.getValueNumbers();
      boolean changed = OPT_ValueNumberPairSetCell.setsDiffer(oldNumbers, 
          newNumbers);
      return  changed;
    }
  }

  /**
   * This class serves as an operator to evaluate a
   * dataflow equation of the form
   * <pre>
   * DEAD_DEF(A) = DeadDefOperator(A,B1, B2, .. BN) 
   * </pre>
   * where DeadDefOperator(A,B1,B2, ... , BN) is defined as
   * <pre>
   *  { intersection over all Bi} -
   *			{ v | there exists n in LIVE(A) and not DD(v,n)}
   * </pre>
   * 
   */
  class DeadDefOperator extends OPT_DF_Operator {
    /**
     * The heap variable H whose live set will be used to evaluate the
     * dataflow equation.
     */
    OPT_HeapVariable liveH;

    /**
     * default construction
     *
     * @param liveH The heap variable H whose live set will be 
     * used to evaluate the dataflow equation.
     */
    DeadDefOperator (OPT_HeapVariable liveH) {
      this.liveH = liveH;
    }

    /**
     * Return a string representation of the operator
     * @return a string representation of the operator
     */
    public String toString () {
      return  "!DEAD_DEF_OP " + getLive(liveH) + "!";
    }

    /**
     * Perform the set operation 
     * DEAD_DEF(A) = DeadDefOperator(A,B1, B2, .. BN) 
     *
     * @param operands operands[0] is DEAD_DEF(A). operands[1] ..
     * operands[n] hold B1 .. BN.
     * @return true iff the value of DEAD_DEF(A) changes as a result.
     */
    boolean evaluate (OPT_DF_LatticeCell[] operands) {
      OPT_DF_LatticeCell lhs = (OPT_DF_LatticeCell)operands[0];
      if (lhs instanceof OPT_ValueNumberSetCell) {
        return  evaluateForFields(operands);
      } 
      else {
        return  evaluateForArrays(operands);
      }
    }

    /**
     * Perform the set operation 
     * DEAD_DEF(A) = DeadDefOperator(A,B1, B2, .. BN) 
     *
     * @param operands operands[0] is DEAD_DEF(A). operands[1] ..
     * operands[n] hold B1 .. BN.
     * @return true iff the value of DEAD_DEF(A) changes as a result.
     */
    boolean evaluateForFields (OPT_DF_LatticeCell[] operands) {
      OPT_ValueNumberSetCell lhs = (OPT_ValueNumberSetCell)operands[0];
      OPT_ValueNumberSetCell rhs[] = new OPT_ValueNumberSetCell
          [operands.length - 1];
      for (int i = 1; i < operands.length; i++) {
        rhs[i - 1] = (OPT_ValueNumberSetCell)operands[i];
      }
      int[] oldNumbers = lhs.getValueNumbers();
      lhs.clear();
      // perform the intersections
      if (rhs.length > 0) {
        int[] rhsNumbers = rhs[0].getValueNumbers();
        for (int i = 0; i < rhsNumbers.length; i++) {
          int v = rhsNumbers[i];
          lhs.add(v);
          for (int j = 1; j < rhs.length; j++) {
            OPT_ValueNumberSetCell r = rhs[j];
            if (!r.contains(v)) {
              lhs.remove(v);
              break;
            }
          }
        }
      }
      // now update with the set subtraction:
      // subtract out any value numbers that are not DD from
      // everything in LIVE
      OPT_ValueNumberSetCell LIVE = (OPT_ValueNumberSetCell)getLive(liveH);
      if (LIVE != null) {
        int[] numbers = lhs.getValueNumbers();
        for (int i = 0; i < numbers.length; i++) {
          int[] live = ((OPT_ValueNumberSetCell)LIVE).getValueNumbers();
          for (int j = 0; j < live.length; j++) {
            if (!valueNumbers.DD(numbers[i], live[j])) {
              lhs.remove(numbers[i]);
              break;
            }
          }
        }
      }
      // check if anything has changed
      int[] newNumbers = lhs.getValueNumbers();
      boolean changed = OPT_ValueNumberSetCell.setsDiffer(oldNumbers, 
          newNumbers);
      return  changed;
    }

    /**
     * Perform the set operation 
     * DEAD_DEF(A) = DeadDefOperator(A,B1, B2, .. BN) 
     *
     * @param operands operands[0] is DEAD_DEF(A). operands[1] ..
     * operands[n] hold B1 .. BN.
     * @return true iff the value of DEAD_DEF(A) changes as a result.
     */
    boolean evaluateForArrays (OPT_DF_LatticeCell[] operands) {
      OPT_ValueNumberPairSetCell lhs = (OPT_ValueNumberPairSetCell)operands[0];
      OPT_ValueNumberPairSetCell rhs[] = new OPT_ValueNumberPairSetCell[
          operands.length - 1];
      for (int i = 1; i < operands.length; i++) {
        rhs[i - 1] = (OPT_ValueNumberPairSetCell)operands[i];
      }
      OPT_ValueNumberPair[] oldNumbers = lhs.getValueNumbers();
      lhs.clear();
      // perform the intersections
      if (rhs.length > 0) {
        OPT_ValueNumberPair[] rhsNumbers = rhs[0].getValueNumbers();
        for (int i = 0; i < rhsNumbers.length; i++) {
          OPT_ValueNumberPair v = rhsNumbers[i];
          lhs.add(v);
          for (int j = 1; j < rhs.length; j++) {
            OPT_ValueNumberPairSetCell r = rhs[j];
            if (!r.contains(v)) {
              lhs.remove(v);
              break;
            }
          }
        }
      }
      // now update with the set subtraction:
      // subtract out any value numbers that are not DD from
      // everything in LIVE
      OPT_ValueNumberPairSetCell LIVE = (OPT_ValueNumberPairSetCell)
          getLive(liveH);
      if (LIVE != null) {
        OPT_ValueNumberPair[] numbers = lhs.getValueNumbers();
        for (int i = 0; i < numbers.length; i++) {
          int n1 = numbers[i].v1;
          int n2 = numbers[i].v2;
          OPT_ValueNumberPair[] live = ((OPT_ValueNumberPairSetCell)LIVE).
              getValueNumbers();
          for (int j = 0; j < live.length; j++) {
            int v1 = live[j].v1;
            int v2 = live[j].v2;
            if (!valueNumbers.DD(n1, v1) && !valueNumbers.DD(n2, v2)) {
              lhs.remove(numbers[i]);
              break;
            }
          }
        }
      }
      // check if anything has changed
      OPT_ValueNumberPair[] newNumbers = lhs.getValueNumbers();
      boolean changed = OPT_ValueNumberPairSetCell.setsDiffer(oldNumbers, 
          newNumbers);
      return  changed;
    }
  }
}


/**
 * Utility class, for object pairs
 */
class OPT_ObjectPair {
  Object o1;
  Object o2;

  OPT_ObjectPair (Object o1, Object o2) {
    this.o1 = o1;
    this.o2 = o2;
  }

  public boolean equals (Object o) {
    if (!(o instanceof OPT_ObjectPair))
      return  false;
    OPT_ObjectPair p = (OPT_ObjectPair)o;
    return  (this.o1.equals(p.o1) && this.o2.equals(p.o2));
  }

  public int hashCode () {
    return  o1.hashCode() + o2.hashCode() << 8;
  }

  public String toString () {
    return  "[" + o1.toString() + "," + o2.toString() + "]";
  }
}


/**
 * Utility exception class
 */
class OPT_TooManyEquationsException extends OPT_OptimizingCompilerException {}
