/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Pool of symbolic registers.
 * Each IR contains has exactly one register pool object associated with it.
 * 
 * @see OPT_Register
 * 
 * @author Dave Grove
 * @author Mauricio J. Serrano
 * @author John Whaley
 * @modified Vivek Sarkar
 * @modified Peter Sweeney
 */
class OPT_GenericRegisterPool extends OPT_AbstractRegisterPool {

  protected OPT_PhysicalRegisterSet physical = new OPT_PhysicalRegisterSet(); 

  OPT_PhysicalRegisterSet getPhysicalRegisterSet() {
    return physical;
  }

  /**
   * Initializes a new register pool for the method meth.
   * 
   * @param meth the VM_Method of the outermost method
   */
  OPT_GenericRegisterPool(VM_Method meth) {
    // currentNum is assigned an initial value to avoid overlap of
    // physical and symbolic registers.
    currentNum = OPT_PhysicalRegisterSet.getSize();
  }

  /**
   * Return the number of symbolic registers (doesn't count physical ones)
   * @return the number of synbloic registers allocated by the pool
   */
  public int getNumberOfSymbolicRegisters() {
    int start = OPT_PhysicalRegisterSet.getSize();
    return currentNum - start;
  }

  /**
   * Get the Framepointer (FP)
   * 
   * @return the FP register
   */ 
  public OPT_Register getFP() {
    return physical.getFP();
  }

  /**
   * Get a temporary that represents the FP register
   * 
   * @return the temp
   */ 
  public OPT_RegisterOperand makeFPOp() {
    return new OPT_RegisterOperand(getFP(),OPT_ClassLoaderProxy.AddressType);
  }

  /**
   * Get a temporary that represents the PR register
   * 
   * @return the temp
   */ 
  public OPT_RegisterOperand makePROp() {
    return new OPT_RegisterOperand(physical.getPR(),
				   OPT_ClassLoaderProxy.getVMProcessorType());
  }

}
