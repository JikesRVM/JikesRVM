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

import org.jikesrvm.ArchitectureSpecific.CodeArray;
import org.jikesrvm.compilers.opt.regalloc.LinearScan;
import org.jikesrvm.osr.VariableMap;

/**
 * Wrapper class around IR info that is valid on the MIR
 */
public final class MIRInfo {

  /**
   * The generated machinecodes produced by this compilation of 'method'
   */
  public CodeArray machinecode;

  /**
   * Estimate produced by FinalMIRExpansion and used by
   * Assembler to create code array; only meaningful on PowerPC
   */
  public int mcSizeEstimate;

  /**
   * The IRMap for the method (symbolic GCMapping info)
   */
  public GCIRMap gcIRMap;

  public VariableMap osrVarMap;
  /**
   * The frame size of the current method
   */
  public int FrameSize;

  /**
   * The number of floating point stack slots allocated.
   * (Only used on IA32)
   */
  public int fpStackHeight;

  /**
   * A basic block holding the call to Thread.threadSwitch for a
   * prologue.
   */
  public BasicBlock prologueYieldpointBlock = null;

  /**
   * A basic block holding the call to Thread.threadSwitch for an
   * epilogue.
   */
  public BasicBlock epilogueYieldpointBlock = null;

  /**
   * A basic block holding the call to Thread.threadSwitch for a
   * backedge.
   */
  public BasicBlock backedgeYieldpointBlock = null;

  /**
   * A basic block holding the call to yieldpointFromOsrOpt for an
   * OSR invalidation.
   */
  public BasicBlock osrYieldpointBlock = null;

  /**
   * Information needed for linear scan.
   */
  public LinearScan.LinearScanState linearScanState = null;

  public MIRInfo(IR ir) {
    ir.compiledMethod.setSaveVolatile(ir.method.getDeclaringClass().hasSaveVolatileAnnotation());
    ir.compiledMethod.setOptLevel(ir.options.getOptLevel());
  }

}
