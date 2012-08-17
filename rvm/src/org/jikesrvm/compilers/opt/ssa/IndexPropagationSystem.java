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
package org.jikesrvm.compilers.opt.ssa;

import static org.jikesrvm.compilers.opt.ir.Operators.FENCE;
import static org.jikesrvm.compilers.opt.ir.Operators.READ_CEILING;
import static org.jikesrvm.compilers.opt.ir.Operators.WRITE_FLOOR;

import java.util.Enumeration;

import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.dfsolver.DF_Equation;
import org.jikesrvm.compilers.opt.dfsolver.DF_LatticeCell;
import org.jikesrvm.compilers.opt.dfsolver.DF_Operator;
import org.jikesrvm.compilers.opt.dfsolver.DF_System;
import org.jikesrvm.compilers.opt.ir.ALoad;
import org.jikesrvm.compilers.opt.ir.AStore;
import org.jikesrvm.compilers.opt.ir.Attempt;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.CacheOp;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.GetField;
import org.jikesrvm.compilers.opt.ir.GetStatic;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.MonitorOp;
import org.jikesrvm.compilers.opt.ir.New;
import org.jikesrvm.compilers.opt.ir.NewArray;
import org.jikesrvm.compilers.opt.ir.Phi;
import org.jikesrvm.compilers.opt.ir.Prepare;
import org.jikesrvm.compilers.opt.ir.PutField;
import org.jikesrvm.compilers.opt.ir.PutStatic;
import org.jikesrvm.compilers.opt.ir.operand.HeapOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ssa.IndexPropagation.ArrayCell;
import org.jikesrvm.compilers.opt.ssa.IndexPropagation.ObjectCell;

/**
 * Represents a set of dataflow equations used to solve the
 * index propagation problem.
 */
class IndexPropagationSystem extends DF_System {

  /**
   * The governing IR.
   */
  private final IR ir;
  /**
   * Heap Array SSA lookaside information for the IR.
   */
  private final SSADictionary ssa;
  /**
   * Results of global value numbering
   */
  private final GlobalValueNumberState valueNumbers;
  /**
   * object representing the MEET operator
   */
  private static final MeetOperator MEET = new MeetOperator();

  /**
   * Set up the system of dataflow equations.
   * @param _ir the IR
   */
  public IndexPropagationSystem(IR _ir) {
    ir = _ir;
    ssa = ir.HIRInfo.dictionary;
    valueNumbers = ir.HIRInfo.valueNumbers;
    setupEquations();
  }

  /**
   * Create an DF_LatticeCell corresponding to an HeapVariable
   * @param o the heap variable
   * @return a new lattice cell corresponding to this heap variable
   */
  @Override
  protected DF_LatticeCell makeCell(Object o) {
    if (!(o instanceof HeapVariable)) {
      throw new OptimizingCompilerException("IndexPropagation:makeCell");
    }
    DF_LatticeCell result = null;
    Object heapType = ((HeapVariable<?>) o).getHeapType();
    if (heapType instanceof TypeReference) {
      result = new ArrayCell((HeapVariable<?>) o);
    } else {
      result = new ObjectCell((HeapVariable<?>) o);
    }
    return result;
  }

  /**
   * Initialize the lattice variables.
   */
  @Override
  protected void initializeLatticeCells() {
    // initially all lattice cells are set to TOP
    // set the lattice cells that are exposed on entry to BOTTOM
    for (DF_LatticeCell c : cells.values()) {
      if (c instanceof ObjectCell) {
        ObjectCell c1 = (ObjectCell) c;
        HeapVariable<?> key = c1.getKey();
        if (key.isExposedOnEntry()) {
          c1.setBOTTOM();
        }
      } else {
        ArrayCell c1 = (ArrayCell) c;
        HeapVariable<?> key = c1.getKey();
        if (key.isExposedOnEntry()) {
          c1.setBOTTOM();
        }
      }
    }
  }

  /**
   * Initialize the work list for the dataflow equation system.
   */
  @Override
  protected void initializeWorkList() {
    // add all equations to the work list that contain a non-TOP
    // variable
    for (Enumeration<DF_Equation> e = getEquations(); e.hasMoreElements();) {
      DF_Equation eq = e.nextElement();
      for (DF_LatticeCell operand : eq.getOperands()) {
        if (operand instanceof ObjectCell) {
          if (!((ObjectCell) operand).isTOP()) {
            addToWorkList(eq);
            break;
          }
        } else {
          if (!((ArrayCell) operand).isTOP()) {
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
    for (Enumeration<BasicBlock> e = ir.getBasicBlocks(); e.hasMoreElements();) {
      BasicBlock bb = e.nextElement();
      for (SSADictionary.AllInstructionEnumeration e2 = ssa.getAllInstructions(bb); e2.hasMoreElements();) {
        Instruction s = e2.nextElement();
        // only consider instructions which use/def Array SSA variables
        if (!ssa.usesHeapVariable(s) && !ssa.defsHeapVariable(s)) {
          continue;
        }
        if (s.isDynamicLinkingPoint()) {
          processCall(s);
        } else if (GetStatic.conforms(s)) {
          processLoad(s);
        } else if (GetField.conforms(s)) {
          processLoad(s);
        } else if (PutStatic.conforms(s)) {
          processStore(s);
        } else if (PutField.conforms(s)) {
          processStore(s);
        } else if (New.conforms(s)) {
          processNew(s);
        } else if (NewArray.conforms(s)) {
          processNew(s);
        } else if (ALoad.conforms(s)) {
          processALoad(s);
        } else if (AStore.conforms(s)) {
          processAStore(s);
        } else if (Call.conforms(s)) {
          processCall(s);
        } else if (MonitorOp.conforms(s)) {
          processCall(s);
        } else if (Prepare.conforms(s)) {
          processCall(s);
        } else if (Attempt.conforms(s)) {
          processCall(s);
        } else if (CacheOp.conforms(s)) {
          processCall(s);
        } else if (Phi.conforms(s)) {
          processPhi(s);
        } else if (s.operator == READ_CEILING) {
          processCall(s);
        } else if (s.operator == WRITE_FLOOR) {
          processCall(s);
        } else if (s.operator == FENCE) {
          processCall(s);
        }
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
  void processLoad(Instruction s) {
    HeapOperand<?>[] A1 = ssa.getHeapUses(s);
    HeapOperand<?>[] A2 = ssa.getHeapDefs(s);
    if ((A1.length != 1) || (A2.length != 1)) {
      throw new OptimizingCompilerException(
          "IndexPropagation.processLoad: load instruction defs or uses multiple heap variables?");
    }
    int valueNumber = -1;
    if (GetField.conforms(s)) {
      Object address = GetField.getRef(s);
      valueNumber = valueNumbers.getValueNumber(address);
    } else {      // GetStatic.conforms(s)
      valueNumber = 0;
    }
    if (IRTools.mayBeVolatileFieldLoad(s) || ir.options.READS_KILL) {
      // to obey JMM strictly, we must treat every load as a
      // DEF
      addUpdateObjectDefEquation(A2[0].getHeapVariable(), A1[0].getHeapVariable(), valueNumber);
    } else {
      // otherwise, don't have to treat every load as a DEF
      addUpdateObjectUseEquation(A2[0].getHeapVariable(), A1[0].getHeapVariable(), valueNumber);
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
  void processStore(Instruction s) {
    HeapOperand<?>[] A1 = ssa.getHeapUses(s);
    HeapOperand<?>[] A2 = ssa.getHeapDefs(s);
    if ((A1.length != 1) || (A2.length != 1)) {
      throw new OptimizingCompilerException(
          "IndexPropagation.processStore: store instruction defs or uses multiple heap variables?");
    }
    int valueNumber = -1;
    if (PutField.conforms(s)) {
      Object address = PutField.getRef(s);
      valueNumber = valueNumbers.getValueNumber(address);
    } else {      // PutStatic.conforms(s)
      valueNumber = 0;
    }
    addUpdateObjectDefEquation(A2[0].getHeapVariable(), A1[0].getHeapVariable(), valueNumber);
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
  void processALoad(Instruction s) {
    HeapOperand<?>[] A1 = ssa.getHeapUses(s);
    HeapOperand<?>[] A2 = ssa.getHeapDefs(s);
    if ((A1.length != 1) || (A2.length != 1)) {
      throw new OptimizingCompilerException(
          "IndexPropagation.processALoad: aload instruction defs or uses multiple heap variables?");
    }
    Operand array = ALoad.getArray(s);
    Operand index = ALoad.getIndex(s);
    if (IRTools.mayBeVolatileFieldLoad(s) || ir.options.READS_KILL) {
      // to obey JMM strictly, we must treat every load as a
      // DEF
      addUpdateArrayDefEquation(A2[0].getHeapVariable(), A1[0].getHeapVariable(), array, index);
    } else {
      // otherwise, don't have to treat every load as a DEF
      addUpdateArrayUseEquation(A2[0].getHeapVariable(), A1[0].getHeapVariable(), array, index);
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
  void processAStore(Instruction s) {
    HeapOperand<?>[] A1 = ssa.getHeapUses(s);
    HeapOperand<?>[] A2 = ssa.getHeapDefs(s);
    if ((A1.length != 1) || (A2.length != 1)) {
      throw new OptimizingCompilerException(
          "IndexPropagation.processAStore: astore instruction defs or uses multiple heap variables?");
    }
    Operand array = AStore.getArray(s);
    Operand index = AStore.getIndex(s);
    addUpdateArrayDefEquation(A2[0].getHeapVariable(), A1[0].getHeapVariable(), array, index);
  }

  /**
   * Update the set of dataflow equations to account for the actions
   * of allocation instruction s
   *
   * @param s the New instruction
   */
  void processNew(Instruction s) {
    /** Assume nothing is a available after a new. So, set
     * each lattice cell defined by the NEW as BOTTOM.
     * TODO: add logic that understands that after a
     * NEW, all fields have their default values.
     */
    for (HeapOperand<?> def : ssa.getHeapDefs(s)) {
      DF_LatticeCell c = findOrCreateCell(def.getHeapVariable());
      if (c instanceof ObjectCell) {
        ((ObjectCell) c).setBOTTOM();
      } else {
        ((ArrayCell) c).setBOTTOM();
      }
    }
  }

  /**
   * Update the set of dataflow equations to account for the actions
   * of CALL instruction.
   *
   * @param s the Call instruction
   */
  void processCall(Instruction s) {

    /** set all lattice cells def'ed by the call to bottom */
    for (HeapOperand<?> operand : ssa.getHeapDefs(s)) {
      DF_LatticeCell c = findOrCreateCell(operand.getHeapVariable());
      if (c instanceof ObjectCell) {
        ((ObjectCell) c).setBOTTOM();
      } else {
        ((ArrayCell) c).setBOTTOM();
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
  void processPhi(Instruction s) {
    Operand result = Phi.getResult(s);
    if (!(result instanceof HeapOperand)) {
      return;
    }
    HeapVariable<?> lhs = ((HeapOperand<?>) result).value;
    DF_LatticeCell A1 = findOrCreateCell(lhs);
    DF_LatticeCell[] rhs = new DF_LatticeCell[Phi.getNumberOfValues(s)];
    for (int i = 0; i < rhs.length; i++) {
      HeapOperand<?> op = (HeapOperand<?>) Phi.getValue(s, i);
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
  void addUpdateObjectDefEquation(HeapVariable<?> A1, HeapVariable<?> A2, int valueNumber) {
    DF_LatticeCell cell1 = findOrCreateCell(A1);
    DF_LatticeCell cell2 = findOrCreateCell(A2);
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
  void addUpdateObjectUseEquation(HeapVariable<?> A1, HeapVariable<?> A2, int valueNumber) {
    DF_LatticeCell cell1 = findOrCreateCell(A1);
    DF_LatticeCell cell2 = findOrCreateCell(A2);
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
  void addUpdateArrayDefEquation(HeapVariable<?> A1, HeapVariable<?> A2, Object array, Object index) {
    DF_LatticeCell cell1 = findOrCreateCell(A1);
    DF_LatticeCell cell2 = findOrCreateCell(A2);
    int arrayNumber = valueNumbers.getValueNumber(array);
    int indexNumber = valueNumbers.getValueNumber(index);
    UpdateDefArrayOperator op = new UpdateDefArrayOperator(arrayNumber, indexNumber);
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
  void addUpdateArrayUseEquation(HeapVariable<?> A1, HeapVariable<?> A2, Object array, Object index) {
    DF_LatticeCell cell1 = findOrCreateCell(A1);
    DF_LatticeCell cell2 = findOrCreateCell(A2);
    int arrayNumber = valueNumbers.getValueNumber(array);
    int indexNumber = valueNumbers.getValueNumber(index);
    UpdateUseArrayOperator op = new UpdateUseArrayOperator(arrayNumber, indexNumber);
    newEquation(cell1, op, cell2);
  }

  /**
   * Represents a MEET function (intersection) over Cells.
   */
  static class MeetOperator extends DF_Operator {

    /**
     * @return "MEET"
     */
    @Override
    public String toString() { return "MEET"; }

    /**
     * Evaluate a dataflow equation with the MEET operator
     * @param operands the operands of the dataflow equation
     * @return true iff the value of the lhs changes
     */
    @Override
    public boolean evaluate(DF_LatticeCell[] operands) {
      DF_LatticeCell lhs = operands[0];
      if (lhs instanceof ObjectCell) {
        return evaluateObjectMeet(operands);
      } else {
        return evaluateArrayMeet(operands);
      }
    }

    /**
     * Evaluate a dataflow equation with the MEET operator
     * for lattice cells representing field heap variables.
     * @param operands the operands of the dataflow equation
     * @return true iff the value of the lhs changes
     */
    boolean evaluateObjectMeet(DF_LatticeCell[] operands) {
      ObjectCell lhs = (ObjectCell) operands[0];

      // short-circuit if lhs is already bottom
      if (lhs.isBOTTOM()) {
        return false;
      }

      // short-circuit if any rhs is bottom
      for (int j = 1; j < operands.length; j++) {
        ObjectCell r = (ObjectCell) operands[j];
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
          ObjectCell r = (ObjectCell) operands[j];
          if (!r.isTOP()) {
            firstNonTopRHS = j;
            break;
          }
        }
        // if we did not find ANY non-top cell, then simply set
        // lhs to top and return
        if (firstNonTopRHS == -1) {
          lhs.setTOP(true);
          return false;
        }
        // if we get here, we found a non-top cell. Start merging
        // here
        int[] rhsNumbers = ((ObjectCell) operands[firstNonTopRHS]).copyValueNumbers();

        if (rhsNumbers != null) {
          for (int v : rhsNumbers) {
            lhs.add(v);
            for (int j = firstNonTopRHS + 1; j < operands.length; j++) {
              ObjectCell r = (ObjectCell) operands[j];
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

      return ObjectCell.setsDiffer(oldNumbers, newNumbers);
    }

    /**
     * Evaluate a dataflow equation with the MEET operator
     * for lattice cells representing array heap variables.
     * @param operands the operands of the dataflow equation
     * @return true iff the value of the lhs changes
     */
    boolean evaluateArrayMeet(DF_LatticeCell[] operands) {
      ArrayCell lhs = (ArrayCell) operands[0];

      // short-circuit if lhs is already bottom
      if (lhs.isBOTTOM()) {
        return false;
      }

      // short-circuit if any rhs is bottom
      for (int j = 1; j < operands.length; j++) {
        ArrayCell r = (ArrayCell) operands[j];
        if (r.isBOTTOM()) {
          // from the previous short-circuit, we know lhs was not already bottom, so ...
          lhs.setBOTTOM();
          return true;
        }
      }

      boolean lhsWasTOP = lhs.isTOP();
      ValueNumberPair[] oldNumbers = null;
      if (!lhsWasTOP) oldNumbers = lhs.copyValueNumbers();

      lhs.clear();
      // perform the intersections
      if (operands.length > 1) {
        int firstNonTopRHS = -1;
        // find the first RHS lattice cell that is not TOP
        for (int j = 1; j < operands.length; j++) {
          ArrayCell r = (ArrayCell) operands[j];
          if (!r.isTOP()) {
            firstNonTopRHS = j;
            break;
          }
        }
        // if we did not find ANY non-top cell, then simply set
        // lhs to top and return
        if (firstNonTopRHS == -1) {
          lhs.setTOP(true);
          return false;
        }
        // if we get here, we found a non-top cell. Start merging
        // here
        ValueNumberPair[] rhsNumbers = ((ArrayCell) operands[firstNonTopRHS]).copyValueNumbers();
        if (rhsNumbers != null) {
          for (ValueNumberPair pair : rhsNumbers) {
            int v1 = pair.v1;
            int v2 = pair.v2;
            lhs.add(v1, v2);
            for (int j = firstNonTopRHS + 1; j < operands.length; j++) {
              ArrayCell r = (ArrayCell) operands[j];
              if (!r.contains(v1, v2)) {
                lhs.remove(v1, v2);
                break;
              }
            }
          }
        }
      }
      // check if anything has changed
      if (lhsWasTOP) return true;
      ValueNumberPair[] newNumbers = lhs.copyValueNumbers();

      return ArrayCell.setsDiffer(oldNumbers, newNumbers);
    }
  }

  /**
   * Represents an UPDATE_DEF function over two ObjectCells.
   * <p> Given a value number v, this function updates a heap variable
   * lattice cell to indicate that element at address v is
   * available, but kills any available indices that are not DD from v
   */
  class UpdateDefObjectOperator extends DF_Operator {
    /**
     * The value number used in the dataflow equation.
     */
    private final int valueNumber;

    /**
     * @return a String representation
     */
    @Override
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
    @Override
    public boolean evaluate(DF_LatticeCell[] operands) {
      ObjectCell lhs = (ObjectCell) operands[0];

      if (lhs.isBOTTOM()) {
        return false;
      }

      ObjectCell rhs = (ObjectCell) operands[1];
      boolean lhsWasTOP = lhs.isTOP();
      int[] oldNumbers = null;
      if (!lhsWasTOP) oldNumbers = lhs.copyValueNumbers();
      lhs.clear();
      if (rhs.isTOP()) {
        throw new OptimizingCompilerException("Unexpected lattice operation");
      }
      int[] numbers = rhs.copyValueNumbers();
      // add all rhs numbers that are DD from valueNumber
      if (numbers != null) {
        for (int number : numbers) {
          if (valueNumbers.DD(number, valueNumber)) {
            lhs.add(number);
          }
        }
      }
      // add value number generated by this update
      lhs.add(valueNumber);
      // check if anything has changed
      if (lhsWasTOP) return true;
      int[] newNumbers = lhs.copyValueNumbers();

      return ObjectCell.setsDiffer(oldNumbers, newNumbers);
    }
  }

  /**
   * Represents an UPDATE_USE function over two ObjectCells.
   *
   * <p> Given a value number v, this function updates a heap variable
   * lattice cell to indicate that element at address v is
   * available, and doesn't kill any available indices
   */
  static class UpdateUseObjectOperator extends DF_Operator {
    /**
     * The value number used in the dataflow equation.
     */
    private final int valueNumber;

    /**
     * @return "UPDATE-USE"
     */
    @Override
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
    @Override
    public boolean evaluate(DF_LatticeCell[] operands) {
      ObjectCell lhs = (ObjectCell) operands[0];

      if (lhs.isBOTTOM()) {
        return false;
      }

      ObjectCell rhs = (ObjectCell) operands[1];
      int[] oldNumbers = null;
      boolean lhsWasTOP = lhs.isTOP();
      if (!lhsWasTOP) oldNumbers = lhs.copyValueNumbers();
      lhs.clear();
      if (rhs.isTOP()) {
        throw new OptimizingCompilerException("Unexpected lattice operation");
      }
      int[] numbers = rhs.copyValueNumbers();
      // add all rhs numbers
      if (numbers != null) {
        for (int number : numbers) {
          lhs.add(number);
        }
      }
      // add value number generated by this update
      lhs.add(valueNumber);
      // check if anything has changed
      if (lhsWasTOP) return true;
      int[] newNumbers = lhs.copyValueNumbers();

      return ObjectCell.setsDiffer(oldNumbers, newNumbers);
    }
  }

  /**
   * Represents an UPDATE_DEF function over two ArrayCells.
   * Given two value numbers v1, v2, this function updates a heap variable
   * lattice cell to indicate that element for array v1 at address v2 is
   * available, but kills any available indices that are not DD from <v1,v2>
   */
  class UpdateDefArrayOperator extends DF_Operator {
    /**
     * The value number pair used in the dataflow equation.
     */
    private final ValueNumberPair v;

    /**
     * @return "UPDATE-DEF"
     */
    @Override
    public String toString() { return "UPDATE-DEF<" + v + ">"; }

    /**
     * Create an operator with a given value number pair
     * @param     v1 first value number in the pari
     * @param     v2 first value number in the pari
     */
    UpdateDefArrayOperator(int v1, int v2) {
      v = new ValueNumberPair(v1, v2);
    }

    /**
     * Evaluate the dataflow equation with this operator.
     * @param operands operands in the dataflow equation
     * @return true iff the lhs changes from this evaluation
     */
    @Override
    public boolean evaluate(DF_LatticeCell[] operands) {
      ArrayCell lhs = (ArrayCell) operands[0];

      if (lhs.isBOTTOM()) {
        return false;
      }
      ArrayCell rhs = (ArrayCell) operands[1];
      ValueNumberPair[] oldNumbers = null;
      boolean lhsWasTOP = lhs.isTOP();
      if (!lhsWasTOP) oldNumbers = lhs.copyValueNumbers();
      lhs.clear();
      if (rhs.isTOP()) {
        throw new OptimizingCompilerException("Unexpected lattice operation");
      }
      ValueNumberPair[] numbers = rhs.copyValueNumbers();
      // add all rhs pairs that are DD from either v.v1 or v.v2
      if (numbers != null) {
        for (ValueNumberPair number : numbers) {
          if (valueNumbers.DD(number.v1, v.v1)) {
            lhs.add(number.v1, number.v2);
          } else if (valueNumbers.DD(number.v2, v.v2)) {
            lhs.add(number.v1, number.v2);
          }
        }
      }
      // add the value number pair generated by this update
      lhs.add(v.v1, v.v2);
      // check if anything has changed
      if (lhsWasTOP) {
        return true;
      }
      ValueNumberPair[] newNumbers = lhs.copyValueNumbers();

      return ArrayCell.setsDiffer(oldNumbers, newNumbers);
    }
  }

  /**
   * Represents an UPDATE_USE function over two ArrayCells.
   *
   * <p> Given two value numbers v1, v2, this function updates a heap variable
   * lattice cell to indicate that element at array v1 index v2 is
   * available, and doesn't kill any available indices
   */
  static class UpdateUseArrayOperator extends DF_Operator {
    /**
     * The value number pair used in the dataflow equation.
     */
    private final ValueNumberPair v;

    /**
     * @return "UPDATE-USE"
     */
    @Override
    public String toString() { return "UPDATE-USE<" + v + ">"; }

    /**
     * Create an operator with a given value number pair
     * @param     v1 first value number in the pair
     * @param     v2 second value number in the pair
     */
    UpdateUseArrayOperator(int v1, int v2) {
      v = new ValueNumberPair(v1, v2);
    }

    /**
     * Evaluate the dataflow equation with this operator.
     * @param operands operands in the dataflow equation
     * @return true iff the lhs changes from this evaluation
     */
    @Override
    public boolean evaluate(DF_LatticeCell[] operands) {
      ArrayCell lhs = (ArrayCell) operands[0];

      if (lhs.isBOTTOM()) {
        return false;
      }

      ArrayCell rhs = (ArrayCell) operands[1];
      ValueNumberPair[] oldNumbers = null;
      boolean lhsWasTOP = lhs.isTOP();
      if (!lhsWasTOP) oldNumbers = lhs.copyValueNumbers();
      lhs.clear();
      if (rhs.isTOP()) {
        throw new OptimizingCompilerException("Unexpected lattice operation");
      }
      ValueNumberPair[] numbers = rhs.copyValueNumbers();
      // add all rhs numbers
      if (numbers != null) {
        for (ValueNumberPair number : numbers) {
          lhs.add(number.v1, number.v2);
        }
      }
      // add value number generated by this update
      lhs.add(v.v1, v.v2);
      // check if anything has changed
      if (lhsWasTOP) {
        return true;
      }
      ValueNumberPair[] newNumbers = lhs.copyValueNumbers();

      return ArrayCell.setsDiffer(oldNumbers, newNumbers);
    }
  }
}
