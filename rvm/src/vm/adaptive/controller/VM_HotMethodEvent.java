/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Abstract parent class for events from organizers to the controller 
 * used to communicate that a method should be considered as a candidate
 * for recompilation.
 *
 * @author Dave Grove 
 */
abstract class VM_HotMethodEvent {

  /**
   * The compiled method id and associated querries.
   */
  private int cmid;
  public final int getCMID() { return cmid; }
  public final VM_CompiledMethod getCompiledMethod() {
    return VM_ClassLoader.getCompiledMethod(cmid);
  }
  public final VM_Method getMethod() {
    return getCompiledMethod().getMethod();
  }
  public final boolean isOptCompiled() {
    int compType = getCompiledMethod().getCompilerInfo().getCompilerType();
    return compType == VM_CompilerInfo.OPT;
  }
  public final int getOptCompiledLevel() {
    if (!isOptCompiled()) return -1;
    return ((VM_OptCompilerInfo)getCompiledMethod().getCompilerInfo()).getOptLevel();
  }


  /**
   * Number of samples attributed to this method.
   */
  private double numSamples;
  public final double getNumSamples() { return numSamples; }

  /**
   * @param _cmid the compiled method id
   * @param _numSamples the number of samples attributed to the method
   */
  VM_HotMethodEvent(int _cmid, double _numSamples) {
    if (VM.VerifyAssertions) {
      VM.assert(_cmid != 0, "Don't create me for zero-value cmid!");
      VM.assert(_numSamples >= 0.0, "Invalid numSamples value");
    }
    cmid = _cmid;
    numSamples = _numSamples;
  }

  /**
   * @param _cmid the compiled method id
   * @param _numSamples the number of samples attributed to the method
   */
  VM_HotMethodEvent(int _cmid, int _numSamples) {
    this(_cmid, (double)_numSamples);
  }

  public String toString() {
    return getMethod()+ " = " +getNumSamples();
  }
}
