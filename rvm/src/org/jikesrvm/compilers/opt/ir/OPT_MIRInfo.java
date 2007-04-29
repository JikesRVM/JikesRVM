/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt.ir;

import org.jikesrvm.ArchitectureSpecific.VM_CodeArray;
import org.jikesrvm.compilers.opt.OPT_LinearScan;
import org.jikesrvm.osr.OSR_VariableMap;

/**
 * Wrapper class around IR info that is valid on the MIR
 *
 * @author Dave Grove
 * @author Mauricio Serrano
 */
public final class OPT_MIRInfo {
  
  /**
   * The generated machinecodes produced by this compilation of 'method'
   */
  public VM_CodeArray machinecode;

  /**
   * Estimate produced by OPT_FinalMIRExpansion and used by
   * OPT_Assembler to create code array; only meaningful on PowerPC
   */
  public int mcSizeEstimate;

  /**
   * The IRMap for the method (symbolic GCMapping info)
   */
  public OPT_GCIRMap  gcIRMap;

  public OSR_VariableMap osrVarMap;
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
   * A basic block holding the call to VM_Thread.threadSwitch for a
   * prologue.
   */
  public OPT_BasicBlock prologueYieldpointBlock = null;

  /**
   * A basic block holding the call to VM_Thread.threadSwitch for an
   * epilogue.
   */
  public OPT_BasicBlock epilogueYieldpointBlock = null;

  /**
   * A basic block holding the call to VM_Thread.threadSwitch for a
   * backedge.
   */
  public OPT_BasicBlock backedgeYieldpointBlock = null;

  /**
   * A basic block holding the call to yieldpointFromOsrOpt for an
   * OSR invalidation.
   */
  public OPT_BasicBlock osrYieldpointBlock = null;

  /**
   * Information needed for linear scan. 
   */
  public OPT_LinearScan.LinearScanState linearScanState = null;

  public OPT_MIRInfo(OPT_IR ir) {
    ir.compiledMethod.setSaveVolatile(ir.method.getDeclaringClass().hasSaveVolatileAnnotation());
    ir.compiledMethod.setOptLevel(ir.options.getOptLevel());
  }

}
