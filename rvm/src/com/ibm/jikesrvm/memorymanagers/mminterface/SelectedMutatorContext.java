/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
//$Id$
package com.ibm.jikesrvm.memorymanagers.mminterface;

import com.ibm.jikesrvm.VM_Processor;

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
