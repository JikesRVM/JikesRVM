/*
 * (C) Copyright IBM Corp. 2001, 2004
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;
import com.ibm.JikesRVM.opt.OPT_IndexPropagation.*;
import java.util.*;

/**
 * Represents a set of dataflow equations used to solve the
 * index propagation problem.
 *
 * @author Stephen Fink
 */
class OPT_IndexPropagationSystem extends OPT_DF_System
implements OPT_Operators {

  /** 
   * Set up the system of dataflow equations.
   * @param _ir the IR
   */
  public OPT_IndexPropagationSystem(OPT_IR _ir) {
    ir = _ir;
    ssa = ir.HIRInfo.SSADictionary;
    valueNumbers = ir.HIRInfo.valueNumbers;
    setupEquations();
  }

  /**
   * The governing IR.
   */
  OPT_IR ir;
  /**
   * Heap Array SSA lookaside information for the IR.
   */
  OPT_SSADictionary ssa;
  /**
   * Results of global value numbering
   */
  OPT_GlobalValueNumberState valueNumbers;
  /**
   * object representing the MEET operator
   */
  MeetOperator MEET = new MeetOperator();

  /**
   * Create an OPT_DF_LatticeCell corresponding to an OPT_HeapVariable
   * @param o the heap variable
   * @return a new lattice cell corresponding to this heap variable
   */
  protected OPT_DF_LatticeCell makeCell(Object o) {
    if (!(o instanceof OPT_HeapVariable))
      throw  new OPT_OptimizingCompilerException(
                                                 "OPT_IndexPropagation:makeCell");
    OPT_DF_LatticeCell result = null;
    Object heapType = ((OPT_HeapVariable)o).getHeapType();
    if (heapType instanceof VM_TypeReference) {
      result = new ArrayCell((OPT_HeapVariable)o);
    } else {
      result = new ObjectCell((OPT_HeapVariable)o);
    }
    return  result;
  }

  /** 
   * Initialize the lattice variables. 
   */
  protected void initializeLatticeCells() {
    // initially all lattice cells are set to TOP
    // set the lattice cells that are exposed on entry to BOTTOM
    for (java.util.Iterator e = cells.values().iterator(); e.hasNext();) {
      OPT_DF_LatticeCell c = (OPT_DF_LatticeCell)e.next();
      if (c instanceof ObjectCell) {
        ObjectCell c1 = (ObjectCell)c;
        OPT_HeapVariable key = c1.key;
        if (key.isExposedOnEntry()) {
          c1.setBOTTOM();
        }
      } 
      else {
        ArrayCell c1 = (ArrayCell)c;
        OPT_HeapVariable key = c1.key;
        if (key.isExposedOnEntry()) {
          c1.setBOTTOM();
        }
      }
    }
  }

  /** 
   * Initialize the work list for the dataflow equation system.
   */
  protected void initializeWorkList() {
    // add all equations to the work list that contain a non-TOP
    // variable
    for (Enumeration e = getEquations(); e.hasMoreElements();) {
      OPT_DF_Equation eq = (OPT_DF_Equation)e.nextElement();
      OPT_DF_LatticeCell[] operands = eq.getOperands();
      for (int i = 0; i < operands.length; i++) {
        if (operands[i] instanceof ObjectCell) {
          if (!((ObjectCell)operands[i]).isTOP()) {
            addToWorkList(eq);
            break;
          }
        } 
        else {
          if (!((ArrayCell)operands[i]).isTOP()) {
            addToWorkList(eq);
            break;
          }
        }
      }
    }
  }

  /** 
   * Walk through the IR and add dataflow equations for each instruction
   * that affects the values of Array SSA variables.
   */
  void setupEquations() {
    for (Enumeration e = ir.getBasicBlocks(); e.hasMoreElements();) {
      OPT_BasicBlock bb = (OPT_BasicBlock)e.nextElement();
      for (Enumeration e2 = ssa.getAllInstructions(bb); e2.hasMoreElements();) {
        OPT_Instruction s = (OPT_Instruction)e2.nextElement();
        // only consider instructions which use/def Array SSA variables
        if (!ssa.usesHeapVariable(s) && !ssa.defsHeapVariable(s))
          continue;
        if (s.isDynamicLinkingPoint())
          processCall(s); 
        else if (GetStatic.conforms(s))
          processLoad(s); 
        else if (GetField.conforms(s))
          processLoad(s); 
        else if (PutStatic.conforms(s))
          processStore(s); 
        else if (PutField.conforms(s))
          processStore(s); 
        else if (New.conforms(s))
          processNew(s); 
        else if (NewArray.conforms(s))
          processNew(s); 
        else if (ALoad.conforms(s))
          processALoad(s); 
        else if (AStore.conforms(s))
          processAStore(s); 
        else if (Call.conforms(s))
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
        else if (s.operator == READ_CEILING)
          processCall(s); 
        else if (s.operator == WRITE_FLOOR)
          processCall(s);
      }
    }
  }

  /**
   * Update the set of dataflow equations to account for the actions
   * of a Load instruction
   *
   * <p> The load is of the form x = A[k].  let A_1 be the array SSA
   * variable before the load, and A_2 the array SSA variable after
   * the store.  Then we add the dataflow equation
   * L(A_2) = updateUse(L(A_1), VALNUM(k))
   *
   * <p> Intuitively, this equation represents the fact that A[k] is available
   * after the store
   *
   * @param s the Load instruction
   */
  void processLoad(OPT_Instruction s) {
    OPT_HeapOperand[] A1 = ssa.getHeapUses(s);
    OPT_HeapOperand[] A2 = ssa.getHeapDefs(s);
    if ((A1.length != 1) || (A2.length != 1))
      throw  new OPT_OptimizingCompilerException("OPT_IndexPropagation.processLoad: load instruction defs or uses multiple heap variables?");
    int valueNumber = -1;
    if (GetField.conforms(s)) {
      Object address = GetField.getRef(s);
      valueNumber = valueNumbers.getValueNumber(address);
    } 
    else {      // GetStatic.conforms(s)
      valueNumber = 0;
    }
    if (OPT_IRTools.mayBeVolatileFieldLoad(s) ||
        ir.options.READS_KILL) {
      // to obey JMM strictly, we must treat every load as a 
      // DEF
      addUpdateObjectDefEquation(A2[0].getHeapVariable(), 
                                 A1[0].getHeapVariable(), 
                                 valueNumber);
    } 
    else {
      // otherwise, don't have to treat every load as a DEF
      addUpdateObjectUseEquation(A2[0].getHeapVariable(), 
                                 A1[0].getHeapVariable(), 
                                 valueNumber);
    }
  }

  /**
   * Update the set of dataflow equations to account for the actions
   * of a Store instruction.
   *
   * <p> The store is of the form A[k] = val.  let A_1 be the array SSA
   * variable before the store, and A_2 the array SSA variable after
   * the store.  Then we add the dataflow equation
   * L(A_2) = updateDef(L(A_1), VALNUM(k))
   *
   * <p> Intuitively, this equation represents the fact that A[k] is available
   * after the store
   *
   * @param s the Store instruction
   */
  void processStore(OPT_Instruction s) {
    OPT_HeapOperand[] A1 = ssa.getHeapUses(s);
    OPT_HeapOperand[] A2 = ssa.getHeapDefs(s);
    if ((A1.length != 1) || (A2.length != 1))
      throw  new OPT_OptimizingCompilerException("OPT_IndexPropagation.processStore: store instruction defs or uses multiple heap variables?");
    int valueNumber = -1;
    if (PutField.conforms(s)) {
      Object address = PutField.getRef(s);
      valueNumber = valueNumbers.getValueNumber(address);
    } 
    else {      // PutStatic.conforms(s)
      valueNumber = 0;
    }
    addUpdateObjectDefEquation(A2[0].getHeapVariable(), 
                               A1[0].getHeapVariable(), 
                               valueNumber);
  }

  /**
   * Update the set of dataflow equations to account for the actions
   * of ALoad instruction s
   *
   * <p> The load is of the form x = A[k].  let A_1 be the array SSA
   * variable before the load, and A_2 the array SSA variable after
   * the load.  Then we add the dataflow equation
   * L(A_2) = updateUse(L(A_1), VALNUM(k))
   *
   * <p> Intuitively, this equation represents the fact that A[k] is available
   * after the store
   *
   * @param s the Aload instruction
   */
  void processALoad(OPT_Instruction s) {
    OPT_HeapOperand A1[] = ssa.getHeapUses(s);
    OPT_HeapOperand A2[] = ssa.getHeapDefs(s);
    if ((A1.length != 1) || (A2.length != 1))
      throw  new OPT_OptimizingCompilerException("OPT_IndexPropagation.processALoad: aload instruction defs or uses multiple heap variables?");
    OPT_Operand array = ALoad.getArray(s);
    OPT_Operand index = ALoad.getIndex(s);
    if (OPT_IRTools.mayBeVolatileFieldLoad(s) ||
        ir.options.READS_KILL) {
      // to obey JMM strictly, we must treat every load as a 
      // DEF
      addUpdateArrayDefEquation(A2[0].getHeapVariable(), 
                                A1[0].getHeapVariable(), 
                                array, index);
    } 
    else {
      // otherwise, don't have to treat every load as a DEF
      addUpdateArrayUseEquation(A2[0].getHeapVariable(), 
                                A1[0].getHeapVariable(), 
                                array, index);
    }
  }

  /**
   * Update the set of dataflow equations to account for the actions
   * of AStore instruction s
   *
   * <p> The store is of the form A[k] = val.  let A_1 be the array SSA
   * variable before the store, and A_2 the array SSA variable after
   * the store.  Then we add the dataflow equation
   * L(A_2) = update(L(A_1), VALNUM(k))
   *
   * <p> Intuitively, this equation represents the fact that A[k] is available
   * after the store
   *
   * @param s the Astore instruction
   */
  void processAStore(OPT_Instruction s) {
    OPT_HeapOperand A1[] = ssa.getHeapUses(s);
    OPT_HeapOperand A2[] = ssa.getHeapDefs(s);
    if ((A1.length != 1) || (A2.length != 1))
      throw  new OPT_OptimizingCompilerException("OPT_IndexPropagation.processAStore: astore instruction defs or uses multiple heap variables?");
    OPT_Operand array = AStore.getArray(s);
    OPT_Operand index = AStore.getIndex(s);
    addUpdateArrayDefEquation(A2[0].getHeapVariable(), A1[0].getHeapVariable(), 
                              array, index);
  }

  /**
   * Update the set of dataflow equations to account for the actions
   * of allocation instruction s
   * 
   * @param s the New instruction
   */
  void processNew(OPT_Instruction s) {
    /** Assume nothing is a available after a new. So, set
     * each lattice cell defined by the NEW as BOTTOM.
     * TODO: add logic that understands that after a
     * NEW, all fields have their default values.
     */
    OPT_HeapOperand A[] = ssa.getHeapDefs(s);
    for (int i = 0; i < A.length; i++) {
      OPT_DF_LatticeCell c = findOrCreateCell(A[i].getHeapVariable());
      if (c instanceof ObjectCell) {
        ((ObjectCell)c).setBOTTOM();
      } 
      else {
        ((ArrayCell)c).setBOTTOM();
      }
    }
  }

  /**
   * Update the set of dataflow equations to account for the actions
   * of CALL instruction.
   *
   * @param s the Call instruction
   */
  void processCall(OPT_Instruction s) {

    /** set all lattice cells def'ed by the call to bottom */
    OPT_HeapOperand A[] = ssa.getHeapDefs(s);
    for (int i = 0; i < A.length; i++) {
      OPT_DF_LatticeCell c = findOrCreateCell(A[i].getHeapVariable());
      if (c instanceof ObjectCell) {
        ((ObjectCell)c).setBOTTOM();
      } 
      else {
        ((ArrayCell)c).setBOTTOM();
      }
    }
  }

  /**
   * Update the set of dataflow equations to account for the actions
   * of Phi instruction.
   *
   * <p> The instruction has the form A1 = PHI (A2, A3, A4);
   * We add the dataflow equation
   * L(A1) = MEET(L(A2), L(A3), L(A4))
   *
   * @param s the Phi instruction
   */
  void processPhi(OPT_Instruction s) {
    OPT_Operand result = Phi.getResult(s);
    if (!(result instanceof OPT_HeapOperand))
      return;
    OPT_HeapVariable lhs = ((OPT_HeapOperand)result).value;
    OPT_DF_LatticeCell A1 = findOrCreateCell(lhs);
    OPT_DF_LatticeCell rhs[] = new OPT_DF_LatticeCell[Phi.getNumberOfValues(s)];
    for (int i = 0; i < rhs.length; i++) {
      OPT_HeapOperand op = (OPT_HeapOperand)Phi.getValue(s, i);
      rhs[i] = findOrCreateCell(op.value);
    }
    newEquation(A1, MEET, rhs);
  }

  /**
   * Add an equation to the system of the form
   * L(A1) = updateDef(L(A2), VALNUM(address))
   * 
   * @param A1 variable in the equation
   * @param A2 variable in the equation
   * @param valueNumber value number of the address
   */
  void addUpdateObjectDefEquation(OPT_HeapVariable A1, OPT_HeapVariable A2, 
                                  int valueNumber) {
    OPT_DF_LatticeCell cell1 = findOrCreateCell(A1);
    OPT_DF_LatticeCell cell2 = findOrCreateCell(A2);
    UpdateDefObjectOperator op = new UpdateDefObjectOperator(valueNumber);
    newEquation(cell1, op, cell2);
  }

  /**
   * Add an equation to the system of the form
   * <pre>
   * L(A1) = updateUse(L(A2), VALNUM(address))
   * </pre>
   * 
   * @param A1 variable in the equation
   * @param A2 variable in the equation
   * @param valueNumber value number of address
   */
  void addUpdateObjectUseEquation (OPT_HeapVariable A1, OPT_HeapVariable A2, 
                                   int valueNumber) {
    OPT_DF_LatticeCell cell1 = findOrCreateCell(A1);
    OPT_DF_LatticeCell cell2 = findOrCreateCell(A2);
    UpdateUseObjectOperator op = new UpdateUseObjectOperator(valueNumber);
    newEquation(cell1, op, cell2);
  }

  /**
   * Add an equation to the system of the form
   * <pre>
   * L(A1) = updateDef(L(A2), <VALNUM(array),VALNUM(index)>)
   * </pre>
   * 
   * @param A1 variable in the equation
   * @param A2 variable in the equation
   * @param array variable in the equation
   * @param index variable in the equation
   */
  void addUpdateArrayDefEquation(OPT_HeapVariable A1, OPT_HeapVariable A2, 
                                 Object array, Object index) {
    OPT_DF_LatticeCell cell1 = findOrCreateCell(A1);
    OPT_DF_LatticeCell cell2 = findOrCreateCell(A2);
    int arrayNumber = valueNumbers.getValueNumber(array);
    int indexNumber = valueNumbers.getValueNumber(index);
    UpdateDefArrayOperator op = new UpdateDefArrayOperator(arrayNumber, 
                                                           indexNumber);
    newEquation(cell1, op, cell2);
  }

  /**
   * Add an equation to the system of the form
   * <pre>
   * L(A1) = updateUse(L(A2), <VALNUM(array),VALNUM(index)>)
   * </pre>
   * 
   * @param A1 variable in the equation
   * @param A2 variable in the equation
   * @param array variable in the equation
   * @param index variable in the equation
   */
  void addUpdateArrayUseEquation(OPT_HeapVariable A1, OPT_HeapVariable A2, 
                                 Object array, Object index) {
    OPT_DF_LatticeCell cell1 = findOrCreateCell(A1);
    OPT_DF_LatticeCell cell2 = findOrCreateCell(A2);
    int arrayNumber = valueNumbers.getValueNumber(array);
    int indexNumber = valueNumbers.getValueNumber(index);
    UpdateUseArrayOperator op = new UpdateUseArrayOperator(arrayNumber, 
                                                           indexNumber);
    newEquation(cell1, op, cell2);
  }

  /**
   * Represents a MEET function (intersection) over Cells.
   */
  class MeetOperator extends OPT_DF_Operator {

    /**
     * @return "MEET"
     */
    public String toString() { return "MEET"; }

    /**
     * Evaluate a dataflow equation with the MEET operator
     * @param operands the operands of the dataflow equation
     * @return true iff the value of the lhs changes
     */
    boolean evaluate(OPT_DF_LatticeCell[] operands) {
      OPT_DF_LatticeCell lhs = operands[0];
      if (lhs instanceof ObjectCell) {
        return evaluateObjectMeet(operands);
      } 
      else {
        return evaluateArrayMeet(operands);
      }
    }

    /**
     * Evaluate a dataflow equation with the MEET operator
     * for lattice cells representing field heap variables.
     * @param operands the operands of the dataflow equation
     * @return true iff the value of the lhs changes
     */
    boolean evaluateObjectMeet(OPT_DF_LatticeCell[] operands) {
      ObjectCell lhs = (ObjectCell)operands[0];

      // short-circuit if lhs is already bottom
      if (lhs.isBOTTOM()) {
        return false;
      }

      // short-circuit if any rhs is bottom
      for (int j = 1; j < operands.length; j++) {
        ObjectCell r = (ObjectCell)operands[j];
        if (r.isBOTTOM()) {
          // from the previous short-circuit, we know lhs was not already bottom, so ...
          lhs.setBOTTOM(); 
          return true;
        }
      }

      boolean lhsWasTOP = lhs.isTOP();
      int[] oldNumbers = null;

      if (!lhsWasTOP) oldNumbers = lhs.copyValueNumbers();

      lhs.clear();
      // perform the intersections
      if (operands.length > 1) {
        int firstNonTopRHS = -1;
        // find the first RHS lattice cell that is not TOP
        for (int j = 1; j < operands.length; j++) {
          ObjectCell r = (ObjectCell)operands[j];
          if (!r.isTOP()) {
            firstNonTopRHS = j;
            break;
          }
        }
        // if we did not find ANY non-top cell, then simply set
        // lhs to top and return 
        if (firstNonTopRHS == -1) {
          lhs.setTOP(true);
          return  false;
        }
        // if we get here, we found a non-top cell. Start merging
        // here
        int[] rhsNumbers = ((ObjectCell)operands[firstNonTopRHS]).copyValueNumbers();

        if (rhsNumbers != null) {
          for (int i = 0; i < rhsNumbers.length; i++) {
            int v = rhsNumbers[i];
            lhs.add(v);
            for (int j = firstNonTopRHS + 1; j < operands.length; j++) {
              ObjectCell r = (ObjectCell)operands[j];
              if (!r.contains(v)) {
                lhs.remove(v);
                break;
              }
            }
          }
        }
      }
      // check if anything has changed
      if (lhsWasTOP) return true;
      int[] newNumbers = lhs.copyValueNumbers();

      boolean changed = ObjectCell.setsDiffer(oldNumbers, newNumbers);
      return  changed;
    }

    /**
     * Evaluate a dataflow equation with the MEET operator
     * for lattice cells representing array heap variables.
     * @param operands the operands of the dataflow equation
     * @return true iff the value of the lhs changes
     */
    boolean evaluateArrayMeet(OPT_DF_LatticeCell[] operands) {
      ArrayCell lhs = (ArrayCell)operands[0];

      // short-circuit if lhs is already bottom
      if (lhs.isBOTTOM()) {
        return false;
      }

      // short-circuit if any rhs is bottom
      for (int j = 1; j < operands.length; j++) {
        ArrayCell r = (ArrayCell)operands[j];
        if (r.isBOTTOM()) {
          // from the previous short-circuit, we know lhs was not already bottom, so ...
          lhs.setBOTTOM(); 
          return true;
        }
      }

      boolean lhsWasTOP = lhs.isTOP();
      OPT_ValueNumberPair[] oldNumbers = null;
      if (!lhsWasTOP) oldNumbers = lhs.copyValueNumbers();

      lhs.clear();
      // perform the intersections
      if (operands.length > 1) {
        int firstNonTopRHS = -1;
        // find the first RHS lattice cell that is not TOP
        for (int j = 1; j < operands.length; j++) {
          ArrayCell r = (ArrayCell)operands[j];
          if (!r.isTOP()) {
            firstNonTopRHS = j;
            break;
          }
        }
        // if we did not find ANY non-top cell, then simply set
        // lhs to top and return 
        if (firstNonTopRHS == -1) {
          lhs.setTOP(true);
          return  false;
        }
        // if we get here, we found a non-top cell. Start merging
        // here
        OPT_ValueNumberPair[] rhsNumbers = ((ArrayCell)operands[firstNonTopRHS]).copyValueNumbers();
        if (rhsNumbers != null) {
          for (int i = 0; i < rhsNumbers.length; i++) {
            int v1 = rhsNumbers[i].v1;
            int v2 = rhsNumbers[i].v2;
            lhs.add(v1, v2);
            for (int j = firstNonTopRHS + 1; j < operands.length; j++) {
              ArrayCell r = (ArrayCell)operands[j];
              if (!r.contains(v1, v2)) {
                lhs.remove(v1, v2);
                break;
              }
            }
          }
        }
      }
      // check if anything has changed
      if (lhsWasTOP) return  true;
      OPT_ValueNumberPair[] newNumbers = lhs.copyValueNumbers();

      boolean changed = ArrayCell.setsDiffer(oldNumbers, newNumbers);
      return  changed;
    }
  }

  /**
   * Represents an UPDATE_DEF function over two ObjectCells.
   * <p> Given a value number v, this function updates a heap variable
   * lattice cell to indicate that element at address v is
   * available, but kills any available indices that are not DD from v
   */
  class UpdateDefObjectOperator extends OPT_DF_Operator {
    /**
     * The value number used in the dataflow equation.
     */
    int valueNumber;

    /**
     * @return a String representation
     */
    public String toString() { return "UPDATE-DEF<" + valueNumber + ">"; }

    /**
     * Create an operator with a given value number
     * @param     valueNumber
     */
    UpdateDefObjectOperator(int valueNumber) {
      this.valueNumber = valueNumber;
    }

    /**
     * Evaluate the dataflow equation with this operator.
     * @param operands operands in the dataflow equation
     * @return true iff the lhs changes from this evaluation
     */
    boolean evaluate(OPT_DF_LatticeCell[] operands) {
      ObjectCell lhs = (ObjectCell)operands[0];

      if (lhs.isBOTTOM()) {
        return false;
      }

      ObjectCell rhs = (ObjectCell)operands[1];
      boolean lhsWasTOP = lhs.isTOP();
      int[] oldNumbers = null;
      if (!lhsWasTOP) oldNumbers = lhs.copyValueNumbers();
      lhs.clear();
      if (rhs.isTOP()) {
        throw  new OPT_OptimizingCompilerException(
                                                   "Unexpected lattice operation");
      }
      int[] numbers = rhs.copyValueNumbers();
      // add all rhs numbers that are DD from valueNumber
      if (numbers != null) {
        for (int i = 0; i < numbers.length; i++) {
          if (valueNumbers.DD(numbers[i], valueNumber)) {
            lhs.add(numbers[i]);
          }
        }
      }
      // add value number generated by this update 
      lhs.add(valueNumber);
      // check if anything has changed
      if (lhsWasTOP) return  true;
      int[] newNumbers = lhs.copyValueNumbers();

      boolean changed = ObjectCell.setsDiffer(oldNumbers, newNumbers);
      return  changed;
    }
  }

  /**
   * Represents an UPDATE_USE function over two ObjectCells.
   *
   * <p> Given a value number v, this function updates a heap variable
   * lattice cell to indicate that element at address v is
   * available, and doesn't kill any available indices
   */
  class UpdateUseObjectOperator extends OPT_DF_Operator {
    /**
     * The value number used in the dataflow equation.
     */
    int valueNumber;

    /**
     * @return "UPDATE-USE"
     */
    public String toString() { return "UPDATE-USE<" + valueNumber + ">"; }

    /**
     * Create an operator with a given value number
     * @param     valueNumber
     */
    UpdateUseObjectOperator(int valueNumber) {
      this.valueNumber = valueNumber;
    }

    /**
     * Evaluate the dataflow equation with this operator.
     * @param operands operands in the dataflow equation
     * @return true iff the lhs changes from this evaluation
     */
    boolean evaluate(OPT_DF_LatticeCell[] operands) {
      ObjectCell lhs = (ObjectCell)operands[0];

      if (lhs.isBOTTOM()) {
        return false;
      }

      ObjectCell rhs = (ObjectCell)operands[1];
      int[] oldNumbers = null;
      boolean lhsWasTOP = lhs.isTOP();
      if (!lhsWasTOP) oldNumbers = lhs.copyValueNumbers();
      lhs.clear();
      if (rhs.isTOP()) {
        throw  new OPT_OptimizingCompilerException("Unexpected lattice operation");
      }
      int[] numbers = rhs.copyValueNumbers();
      // add all rhs numbers 
      if (numbers != null) {
        for (int i = 0; i < numbers.length; i++) {
          lhs.add(numbers[i]);
        }
      }
      // add value number generated by this update 
      lhs.add(valueNumber);
      // check if anything has changed
      if (lhsWasTOP) return  true;
      int[] newNumbers = lhs.copyValueNumbers();

      boolean changed = ObjectCell.setsDiffer(oldNumbers, newNumbers);
      return  changed;
    }
  }

  /**
   * Represents an UPDATE_DEF function over two ArrayCells.
   * Given two value numbers v1, v2, this function updates a heap variable
   * lattice cell to indicate that element for array v1 at address v2 is
   * available, but kills any available indices that are not DD from <v1,v2> 
   */
  class UpdateDefArrayOperator extends OPT_DF_Operator {
    /**
     * The value number pair used in the dataflow equation.
     */
    OPT_ValueNumberPair v = new OPT_ValueNumberPair();

    /**
     * @return "UPDATE-DEF"
     */
    public String toString() { return "UPDATE-DEF<" + v + ">"; }


    /**
     * Create an operator with a given value number pair
     * @param     v1 first value number in the pari
     * @param     v2 first value number in the pari
     */
    UpdateDefArrayOperator(int v1, int v2) {
      v.v1 = v1;
      v.v2 = v2;
    }

    /**
     * Evaluate the dataflow equation with this operator.
     * @param operands operands in the dataflow equation
     * @return true iff the lhs changes from this evaluation
     */
    boolean evaluate(OPT_DF_LatticeCell[] operands) {
      ArrayCell lhs = (ArrayCell)operands[0];

      if (lhs.isBOTTOM()) {
        return false;
      }
      ArrayCell rhs = (ArrayCell)operands[1];
      OPT_ValueNumberPair[] oldNumbers = null;
      boolean lhsWasTOP = lhs.isTOP();
      if (!lhsWasTOP) oldNumbers = lhs.copyValueNumbers();
      lhs.clear();
      if (rhs.isTOP()) {
        throw  new OPT_OptimizingCompilerException("Unexpected lattice operation");
      }
      OPT_ValueNumberPair[] numbers = rhs.copyValueNumbers();
      // add all rhs pairs that are DD from either v.v1 or v.v2
      if (numbers != null) {
        for (int i = 0; i < numbers.length; i++) {
          if (valueNumbers.DD(numbers[i].v1, v.v1)) {
            lhs.add(numbers[i].v1, numbers[i].v2);
          } 
          else if (valueNumbers.DD(numbers[i].v2, v.v2)) {
            lhs.add(numbers[i].v1, numbers[i].v2);
          }
        }
      }
      // add the value number pair generated by this update 
      lhs.add(v.v1, v.v2);
      // check if anything has changed
      if (lhsWasTOP)
        return  true;
      OPT_ValueNumberPair[] newNumbers = lhs.copyValueNumbers();

      boolean changed = ArrayCell.setsDiffer(oldNumbers, newNumbers);
      return  changed;
    }
  }

  /**
   * Represents an UPDATE_USE function over two ArrayCells.
   *
   * <p> Given two value numbers v1, v2, this function updates a heap variable
   * lattice cell to indicate that element at array v1 index v2 is
   * available, and doesn't kill any available indices
   */
  class UpdateUseArrayOperator extends OPT_DF_Operator {
    /**
     * The value number pair used in the dataflow equation.
     */
    OPT_ValueNumberPair v = new OPT_ValueNumberPair();

    /**
     * @return "UPDATE-USE"
     */
    public String toString() { return "UPDATE-USE<" + v + ">"; }

    /**
     * Create an operator with a given value number pair
     * @param     v1 first value number in the pair
     * @param     v2 second value number in the pair
     */
    UpdateUseArrayOperator(int v1, int v2) {
      v.v1 = v1;
      v.v2 = v2;
    }

    /**
     * Evaluate the dataflow equation with this operator.
     * @param operands operands in the dataflow equation
     * @return true iff the lhs changes from this evaluation
     */
    boolean evaluate(OPT_DF_LatticeCell[] operands) {
      ArrayCell lhs = (ArrayCell)operands[0];

      if (lhs.isBOTTOM()) {
        return false;
      }

      ArrayCell rhs = (ArrayCell)operands[1];
      OPT_ValueNumberPair[] oldNumbers = null;
      boolean lhsWasTOP = lhs.isTOP();
      if (!lhsWasTOP) oldNumbers = lhs.copyValueNumbers();
      lhs.clear();
      if (rhs.isTOP()) {
        throw  new OPT_OptimizingCompilerException("Unexpected lattice operation");
      }
      OPT_ValueNumberPair[] numbers = rhs.copyValueNumbers();
      // add all rhs numbers 
      if (numbers != null) {
        for (int i = 0; i < numbers.length; i++) {
          lhs.add(numbers[i].v1, numbers[i].v2);
        }
      } 
      // add value number generated by this update 
      lhs.add(v.v1, v.v2);
      // check if anything has changed
      if (lhsWasTOP)
        return  true;
      OPT_ValueNumberPair[] newNumbers = lhs.copyValueNumbers();

      boolean changed = ArrayCell.setsDiffer(oldNumbers, newNumbers);
      return  changed;
    }
  }
}
