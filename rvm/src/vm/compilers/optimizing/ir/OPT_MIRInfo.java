/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Wrapper class around IR info that is valid on the MIR
 *
 * @author Dave Grove
 * @author Mauricio Serrano
 */
final class OPT_MIRInfo {
  
  /**
   * The VM_OptCompilerInfo to be associated with the compiled code 
   * generated for this IR.
   */
  public VM_OptCompilerInfo info;

  /**
   * The generated machinecodes produced by this compilation of 'method'
   */
   public INSTRUCTION[] machinecode;

  /**
   * The IRMap for the method (symbolic GCMapping info)
   */
  public OPT_GCIRMap  gcIRMap;

  /**
   * The frame size of the current method
   */
  int FrameSize;

  /**
   * The number of floating point stack slots allocated.
   * (Only used on IA32)
   */
  int fpStackHeight;

  /**
   * A basic block holding the call to VM_Thread.threadSwitch for a
   * prologue.
   */
  OPT_BasicBlock prologueYieldpointBlock = null;

  /**
   * A basic block holding the call to VM_Thread.threadSwitch for an
   * epilogue.
   */
  OPT_BasicBlock epilogueYieldpointBlock = null;

  /**
   * A basic block holding the call to VM_Thread.threadSwitch for a
   * backedge.
   */
  OPT_BasicBlock backedgeYieldpointBlock = null;

  /**
   * Information needed for linear scan. 
   */
  OPT_LinearScan.LinearScanState linearScanState = null;

  OPT_Instruction instAfterPrologue;
  
  OPT_MIRInfo(OPT_IR ir) {
    info = new VM_OptCompilerInfo(ir.method);
    info.setSaveVolatile(ir.method.getDeclaringClass().isSaveVolatile());
    info.setOptLevel(ir.options.getOptLevel());
  }

}
