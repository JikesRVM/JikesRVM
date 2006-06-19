/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
//$Id$
package com.ibm.JikesRVM.memoryManagers.mmInterface;

import com.ibm.JikesRVM.VM_Processor;

import org.vmmagic.pragma.*;

/**
 * This class selects the appropriate MMTk plan local type. 
 *
 * @author Daniel Frampton 
 * @author Robin Garner
 *
 * @version $Revision$
 * @date $Date$
 */

//-#if RVM_WITH_MMTK_INLINE_PLAN
public class SelectedMutatorContext extends
//-#else
public final class SelectedMutatorContext extends
//-#endif
//-#value RVM_WITH_MMTK_MUTATORCONTEXT
  implements Uninterruptible {

  //-#if !RVM_WITH_MMTK_INLINE_PLAN
  private VM_Processor processor;
 /**
   * Constructor.  Create a back-link from this context to our parent
   * processor. 
   *
   * @param parent The <code>VM_Processor</code> containing this context.
   */
  public SelectedMutatorContext(VM_Processor parent) {
    super();
    this.processor = parent;
  }
  //-#endif

  /** @return The <code>VM_Processor</code> which contains this context */
  public final VM_Processor getProcessor() throws InlinePragma {
    //-#if RVM_WITH_MMTK_INLINE_PLAN
    return (VM_Processor) this;
    //-#else
    return processor;
    //-#endif
  }

  /**
   * Gets the plan instance associated with the current processor.
   *
   * @return the plan instance for the current processor
   */
  public static final SelectedMutatorContext get() throws InlinePragma {
    //-#if RVM_WITH_MMTK_INLINE_PLAN
    return VM_Processor.getCurrentProcessor();
    //-#else
    return VM_Processor.getCurrentProcessor().mutatorContext;
    //-#endif
  }
}
