/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * This class implements the redundant load elimination by
 * Fink, Knobe && Sarkar.  See SAS 2000 paper for details.
 *
 * @author Stephen Fink
 */


import  java.util.*;
import instructionFormats.*;


/**
 * put your documentation comment here
 */
final class OPT_LoadElimination extends OPT_OptimizationPlanCompositeElement
    implements OPT_Operators {

  /**
   * put your documentation comment here
   */
  OPT_LoadElimination () {
    super("Redundant Load Elimination", new OPT_OptimizationPlanElement[] {
      // 1. Set up IR state to control SSA translation as needed
      new OPT_OptimizationPlanAtomicElement(new LoadEliminationPreparation()), 
          // 2. Get the desired SSA form
      new OPT_OptimizationPlanAtomicElement(new OPT_EnterSSA()), 
      // 3. Perform global value numbering
      new OPT_OptimizationPlanAtomicElement(new OPT_GlobalValueNumber()), 
          // 4. Perform load elimination
      new OPT_OptimizationPlanAtomicElement(new LoadElimination())
    });
  }

  /**
   * Redefine shouldPerform so that none of the subphases will occur
   * unless we pass through this test.
   */
  boolean shouldPerform (OPT_Options options) {
    return  options.LOAD_ELIMINATION;
  }

  // define the compiler phase that actually performs the optimization.
  private static final class LoadElimination extends OPT_CompilerPhase
      implements OPT_Operators {
    static final boolean DEBUG = false;

    /**
     * put your documentation comment here
     * @param options
     * @return 
     */
    final boolean shouldPerform (OPT_Options options) {
      return  options.LOAD_ELIMINATION;
    }

    /**
     * put your documentation comment here
     * @return 
     */
    final String getName () {
      return  "Array SSA Load Elimination";
    }

    /**
     * put your documentation comment here
     * @param options
     * @param before
     * @return 
     */
    final boolean printingEnabled (OPT_Options options, boolean before) {
      return  false;
    }

    /** 
     * main driver for redundant load elimination 
     * Preconditions: Array SSA form and Global Value Numbers computed
     * @param ir the governing IR
     */
    final public void perform (OPT_IR ir) {
      // perform index propagation 
      OPT_IndexPropagation ip = new OPT_IndexPropagation();
      ip.perform(ir);
      OPT_DF_Solution indexPropSolution = ir.HIRInfo.indexPropagationSolution;
      eliminateLoads(ir, indexPropSolution);
      // Note that SSA is no longer valid!!!
      // This will force construction of SSA next time we call EnterSSA
      ir.actualSSAOptions.setScalarValid(false);
      ir.actualSSAOptions.setHeapValid(false);
    }

    /** 
     * Eliminate redundant loads with respect to prior defs and prior
     * uses.
     */
    final void eliminateLoads (OPT_IR ir, OPT_DF_Solution available) {
      // maintain a mapping from value number to temporary register
      java.util.HashMap registers = new java.util.HashMap();
      UseRecordSet UseRepSet = replaceLoads(ir, available, registers);
      replaceDefs(ir, UseRepSet, registers);
    }

    /**
     * Walk over each instruction.  If its a USE (load) of a heap
     * variable and the value is available, then replace the load
     * with a move from a register.
     *
     * POSTCONDITION: sets up the mapping 'registers' from value number
     *		     to temporary register
     * @param ir the IR
     * @param available information on which values are available
     * @param registers a place to store information about temp registers
     */
    final UseRecordSet replaceLoads (OPT_IR ir, OPT_DF_Solution available, 
        java.util.HashMap registers) {
      UseRecordSet result = new UseRecordSet();
      OPT_SSADictionary ssa = ir.HIRInfo.SSADictionary;
      OPT_GlobalValueNumberState valueNumbers = ir.HIRInfo.valueNumbers;
      for (Enumeration e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
        OPT_Instruction s = (OPT_Instruction)e.nextElement();
        if (!GetField.conforms(s) 
            && !GetStatic.conforms(s) 
            && !ALoad.conforms(s))
          continue;
        // do not attempt to optimize the load if it may case dynamic linking
        if (s.isDynamicLinkingPoint())  
          continue;
        // this instruction is a USE of heap variable H.
        // get the lattice cell that holds the available indices
        // for this heap variable
        OPT_HeapOperand[] H = ssa.getHeapUses(s);
        if (H == null) {
          // this case can happen due to certain magics that insert
          // INT_LOAD instructions in HIR
          // TODO: clean up HIR representation of these magics
          continue;
        }
        if (H.length != 1)
          throw  new OPT_OptimizingCompilerException(
              "OPT_LoadElimination: load with wrong number of heap uses");
        if (GetField.conforms(s) || GetStatic.conforms(s)) {
          int valueNumber = -1;
          if (GetField.conforms(s)) {
            Object address = GetField.getRef(s);
            valueNumber = valueNumbers.getValueNumber(address);
          } 
          else {
            // for getStatic, always use the value number 0
            valueNumber = 0;
          }
          OPT_IPObjectCell cell = (OPT_IPObjectCell)available.lookup(
              H[0].getHeapVariable());
          if (cell == null)
            continue;           // nothing available
          // .. if H{valueNumber} is available ...
          if (cell.contains(valueNumber)) {
            result.add(H[0].getHeapVariable(), valueNumber);
            VM_Type type = ResultCarrier.getResult(s).getType();
            OPT_Register r = findOrCreateRegister(H[0].getHeapType(), 
                valueNumber, registers, ir.regpool, type);
            if (DEBUG)
              System.out.println("ELIMINATING LOAD " + s);
            replaceLoadWithMove(r, s);
          }
        } 
        else {                  // ALoad.conforms(s)
          Object array = ALoad.getArray(s);
          Object index = ALoad.getIndex(s);
          OPT_IPArrayCell cell = (OPT_IPArrayCell)available.lookup(
              H[0].getHeapVariable());
          if (cell == null)
            continue;           // nothing available
          int v1 = valueNumbers.getValueNumber(array);
          int v2 = valueNumbers.getValueNumber(index);
          // .. if H{<v1,v2>} is available ...
          if (cell.contains(v1, v2)) {
            result.add(H[0].getHeapVariable(), v1, v2);
            VM_Type type = ALoad.getResult(s).getType();
            OPT_Register r = findOrCreateRegister(
                H[0].getHeapVariable().getHeapType(), 
                v1, v2, registers, ir.regpool, type);
            if (DEBUG)
              System.out.println("ELIMINATING LOAD " + s);
            replaceLoadWithMove(r, s);
          }
        }
      }
      return  result;
    }

    /**
     * Replace a Load instruction s with a load from a scalar register r
     * TODO: factor this functionality out elsewhere
     */
    void replaceLoadWithMove (OPT_Register r, OPT_Instruction load) {
      OPT_RegisterOperand dest = ResultCarrier.getResult(load);
      OPT_RegisterOperand rop = new OPT_RegisterOperand(r, dest.type);
      // TODO: note that the use of getMoveOp restricts us to HIR
      load.replace(Move.create(OPT_IRTools.getMoveOp(dest.type, false), 
          dest, rop));
    }

    /**
     * Perform scalar replacement actions for a Def of a heap variable.
     * NOTE: Even loads can def a heap variable.
     *
     * @param UseRepSet stores the uses(loads) that have been eliminated
     * @param registers mapping from valueNumber -> temporary register
     */
    final void replaceDefs (OPT_IR ir, UseRecordSet UseRepSet, 
        java.util.HashMap registers) {
      OPT_SSADictionary ssa = ir.HIRInfo.SSADictionary;
      for (Enumeration e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
        OPT_Instruction s = (OPT_Instruction)e.nextElement();
        if (!GetField.conforms(s) && !GetStatic.conforms(s) 
            && !PutField.conforms(s)
            && !PutStatic.conforms(s) && !ALoad.conforms(s) 
            && !AStore.conforms(s))
          continue;
        if (!ssa.defsHeapVariable(s))
          continue;
        if (s.isDynamicLinkingPoint())
          continue;
        // this instruction is a DEF of heap variable H.
        // Check if UseRepSet needs the scalar assigned by this def
        OPT_HeapOperand H[] = ssa.getHeapDefs(s);
        if (H.length != 1)
          throw  new OPT_OptimizingCompilerException(
          "OPT_LoadElimination: encountered a store with more than one def? "
              + s);
        int valueNumber = -1;
        Object index = null;
        if (AStore.conforms(s)) {
          Object address = AStore.getArray(s);
          index = AStore.getIndex(s);
          valueNumber = ir.HIRInfo.valueNumbers.getValueNumber(address);
        } 
        else if (GetField.conforms(s)) {
          Object address = GetField.getRef(s);
          valueNumber = ir.HIRInfo.valueNumbers.getValueNumber(address);
        } 
        else if (PutField.conforms(s)) {
          Object address = PutField.getRef(s);
          valueNumber = ir.HIRInfo.valueNumbers.getValueNumber(address);
        } 
        else if (GetStatic.conforms(s)) {
          valueNumber = 0;
        } 
        else if (PutStatic.conforms(s)) {
          valueNumber = 0;
        } 
        else if (ALoad.conforms(s)) {
          Object address = ALoad.getArray(s);
          valueNumber = ir.HIRInfo.valueNumbers.getValueNumber(address);
          index = ALoad.getIndex(s);
        }
        if (index == null) {
          // Load/Store
          if (UseRepSet.containsMatchingUse(
              H[0].getHeapVariable(), valueNumber)) {
            OPT_Operand value = null;
            if (PutField.conforms(s))
              value = PutField.getValue(s); 
            else if (PutStatic.conforms(s))
              value = PutStatic.getValue(s); 
            else if (GetField.conforms(s) || GetStatic.conforms(s))
              value = ResultCarrier.getResult(s);
            VM_Type type = value.getType();
            OPT_Register r = findOrCreateRegister(H[0].getHeapType(), 
                valueNumber, registers, ir.regpool, type);
            appendMove(r, value, s);
          }
        } 
        else {
          // ALoad / AStore
          int v1 = valueNumber;
          int v2 = ir.HIRInfo.valueNumbers.getValueNumber(index);
          if (UseRepSet.containsMatchingUse(H[0].getHeapVariable(), v1, 
              v2)) {
            OPT_Operand value = null;
            if (AStore.conforms(s))
              value = AStore.getValue(s); 
            else if (ALoad.conforms(s))
              value = ALoad.getResult(s);
            VM_Type type = value.getType();
            OPT_Register r = findOrCreateRegister(H[0].getHeapType(), 
                v1, v2, registers, ir.regpool, type);
            appendMove(r, value, s);
          }
        }
      }
    }

    /**
     * Append an instruction after a store instruction that caches
     * value in register r.
     */
    void appendMove (OPT_Register r, OPT_Operand src, OPT_Instruction store) {
      VM_Type type = src.getType();
      OPT_RegisterOperand rop = new OPT_RegisterOperand(r, type);
      // TODO: note that the use of getMoveOp restricts us to HIR
      store.insertAfter(Move.create(OPT_IRTools.getMoveOp(type, false), 
          rop, src));
    }

    /** 
     * Given a value number, return the temporary register allocated
     * for that value number.  Create one if necessary.
     *
     * @param heapType a VM_Type or VM_Field identifying the array SSA
     *			heap type
     * @param valueNumber
     * @param registers a mapping from value number to temporary register
     * @param pool register pool to allocate new temporaries from
     * @param type the type to store in the new register
     */
    OPT_Register findOrCreateRegister (Object heapType, int valueNumber, 
        java.util.HashMap registers, OPT_RegisterPool pool, VM_Type type) {
      UseRecord key = new UseRecord(heapType, valueNumber);
      OPT_Register result = (OPT_Register)registers.get(key);
      if (result == null) {
        // create a new temp and insert it in the mapping
        result = pool.makeTemp(type).register;
        registers.put(key, result);
      }
      return  result;
    }

    /** 
     * Given a pair of value numbers, return the temporary register 
     * allocated for that pair.  Create one if necessary.
     *
     * @param heapType a VM_Type identifying the array SSA
     *			heap type
     * @param v1, v2 valueNumbers
     * @param registers a mapping from value number to temporary register
     * @param pool register pool to allocate new temporaries from
     * @param type the type to store in the new register
     */
    OPT_Register findOrCreateRegister (Object heapType, int v1, int v2, 
        java.util.HashMap registers, OPT_RegisterPool pool, VM_Type type) {
      UseRecord key = new UseRecord(heapType, v1, v2);
      OPT_Register result = (OPT_Register)registers.get(key);
      if (result == null) {
        // create a new temp and insert it in the mapping
        result = pool.makeTemp(type).register;
        registers.put(key, result);
      }
      return  result;
    }

    // A UseRecord represents a load that will be eliminated
    static class UseRecord {
      Object type;              // may be either a VM_Type or a VM_Field
      int v1;                   // first value number (object pointer)
      int v2;                   // second value number (array index)
      final static int NONE = -2;

      /**
       * put your documentation comment here
       * @param       Object type
       * @param       int valueNumber
       */
      UseRecord (Object type, int valueNumber) {
        this.type = type;
        this.v1 = valueNumber;
        this.v2 = NONE;
      }

      /**
       * put your documentation comment here
       * @param       Object type
       * @param       int v1
       * @param       int v2
       */
      UseRecord (Object type, int v1, int v2) {
        this.type = type;
        this.v1 = v1;
        this.v2 = v2;
      }

      /**
       * put your documentation comment here
       * @param o
       * @return 
       */
      public boolean equals (Object o) {
        UseRecord u = (UseRecord)o;
        return  ((u.type == type) && (u.v1 == v1) && (u.v2 == v2));
      }

      /**
       * put your documentation comment here
       * @return 
       */
      public int hashCode () {
        return  type.hashCode() + v1 + v2;
      }
    }

    /**
     * put your documentation comment here
     */
    static class UseRecordSet {
      java.util.HashSet set = new java.util.HashSet(10);

      // Does this set contain a use that has the same type as H and
      // the given value number?
      boolean containsMatchingUse (OPT_HeapVariable H, int valueNumber) {
        Object type = H.getHeapType();
        UseRecord u = new UseRecord(type, valueNumber);
        return  (set.contains(u));
      }

      // Does this set contain a use that has the same type as H and
      // the given value number pair <v1,v2>?
      boolean containsMatchingUse (OPT_HeapVariable H, int v1, int v2) {
        Object type = H.getHeapType();
        UseRecord u = new UseRecord(type, v1, v2);
        return  (set.contains(u));
      }

      // add a USE to the set
      void add (OPT_HeapVariable H, int valueNumber) {
        UseRecord u = new UseRecord(H.getHeapType(), valueNumber);
        set.add(u);
      }

      /**
       * put your documentation comment here
       * @param H
       * @param v1
       * @param v2
       */
      void add (OPT_HeapVariable H, int v1, int v2) {
        UseRecord u = new UseRecord(H.getHeapType(), v1, v2);
        set.add(u);
      }
    }
  }

  /**
   * Do a quick pass over the IR, and return types that are candidates
   * for redundant load elimination.
   * Algorithm: return those types T where
   *	1) there's a load L of type T	
   *	2) there's another load or store M of type T, M!=L 
   */
  final public static java.util.Set getCandidates (OPT_IR ir) {
    java.util.HashSet seenLoad = new java.util.HashSet(10);
    java.util.HashSet seenStore = new java.util.HashSet(10);
    java.util.HashSet resultSet = new java.util.HashSet(10);
    // walk the instructions.  Store whether we've seen a load and/or a
    // store for each type. Then, when we see a second appearance,
    // if we've previously seen a load, then add this type to the
    // result set.
    for (OPT_InstructionEnumeration e = ir.forwardInstrEnumerator(); 
        e.hasMoreElements();) {
      OPT_Instruction s = e.next();
      switch (s.operator().opcode) {
        case GETFIELD_opcode:case GETFIELD_UNRESOLVED_opcode:
          OPT_LocationOperand loc = GetField.getLocation(s);
          VM_Field f = loc.field;
          if (seenLoad.contains(f))
            resultSet.add(f);
          if (seenStore.contains(f))
            resultSet.add(f);
          seenLoad.add(f);
          break;
        case PUTFIELD_opcode:case PUTFIELD_UNRESOLVED_opcode:
          loc = PutField.getLocation(s);
          f = loc.field;
          if (seenLoad.contains(f))
            resultSet.add(f);
          seenStore.add(f);
          break;
        case GETSTATIC_opcode:case GETSTATIC_UNRESOLVED_opcode:
          loc = GetStatic.getLocation(s);
          f = loc.field;
          if (seenLoad.contains(f))
            resultSet.add(f);
          if (seenStore.contains(f))
            resultSet.add(f);
          seenLoad.add(f);
          break;
        case PUTSTATIC_opcode:case PUTSTATIC_UNRESOLVED_opcode:
          loc = PutStatic.getLocation(s);
          f = loc.field;
          if (seenLoad.contains(f))
            resultSet.add(f);
          seenStore.add(f);
          break;
        case INT_ALOAD_opcode:case LONG_ALOAD_opcode:case FLOAT_ALOAD_opcode:
        case DOUBLE_ALOAD_opcode:case REF_ALOAD_opcode:case BYTE_ALOAD_opcode:
        case UBYTE_ALOAD_opcode:case USHORT_ALOAD_opcode:case SHORT_ALOAD_opcode:
          VM_Type type = ALoad.getArray(s).getType();
          if (!type.asArray().getElementType().isPrimitiveType())
            type = OPT_ClassLoaderProxy.JavaLangObjectArrayType;
          if (seenLoad.contains(type))
            resultSet.add(type);
          if (seenStore.contains(type))
            resultSet.add(type);
          seenLoad.add(type);
          break;
        case INT_ASTORE_opcode:case LONG_ASTORE_opcode:
        case FLOAT_ASTORE_opcode:case DOUBLE_ASTORE_opcode:
        case REF_ASTORE_opcode:case BYTE_ASTORE_opcode:
        case SHORT_ASTORE_opcode:
          type = AStore.getArray(s).getType();
          if (!type.asArray().getElementType().isPrimitiveType())
            type = OPT_ClassLoaderProxy.JavaLangObjectArrayType;
          if (seenLoad.contains(type))
            resultSet.add(type);
          seenStore.add(type);
        default:
          break;
      }
    }
    return  resultSet;
  }

  /**
   * This class sets up the IR state prior to entering SSA for load
   * elimination
   */
  private static class LoadEliminationPreparation extends OPT_CompilerPhase {

    /**
     * put your documentation comment here
     * @param options
     * @return 
     */
    final boolean shouldPerform (OPT_Options options) {
      return  options.LOAD_ELIMINATION;
    }

    /**
     * put your documentation comment here
     * @return 
     */
    final String getName () {
      return  "Load Elimination Preparation";
    }

    /**
     * put your documentation comment here
     * @param options
     * @param before
     * @return 
     */
    final boolean printingEnabled (OPT_Options options, boolean before) {
      return  false;
    }

    /**
     * put your documentation comment here
     * @param ir
     */
    final public void perform (OPT_IR ir) {
      // register in the IR the SSA properties we need for load
      // elimination
      ir.desiredSSAOptions = new OPT_SSAOptions();
      ir.desiredSSAOptions.setScalarsOnly(false);
      ir.desiredSSAOptions.setBackwards(false);
      ir.desiredSSAOptions.setInsertUsePhis(true);
      ir.desiredSSAOptions.setHeapTypes(OPT_LoadElimination.getCandidates(ir));
    }
  }
}



