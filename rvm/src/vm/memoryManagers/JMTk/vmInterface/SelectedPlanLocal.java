/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
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

//-#if RVM_WITH_JMTK_INLINE_PLAN
public class SelectedPlanLocal extends
//-#else
public final class SelectedPlanLocal extends
//-#endif

//-#value RVM_WITH_JMTK_PLANLOCAL

  implements Uninterruptible {

  /**
   * Gets the plan instance associated with the current processor.
   *
   * @return the plan instance for the current processor
   */
  public static final SelectedPlanLocal get() throws InlinePragma {
    //-#if RVM_WITH_JMTK_INLINE_PLAN
    return VM_Processor.getCurrentProcessor();
    //-#else
    return VM_Processor.getCurrentProcessor().mmPlan;
    //-#endif
  }
}
