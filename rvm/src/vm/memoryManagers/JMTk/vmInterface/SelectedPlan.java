/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package com.ibm.JikesRVM.memoryManagers.mmInterface;

import org.vmmagic.pragma.*;

/**
 * This class extends the selected global MMTk plan class. 
 *
 * @author Daniel Frampton 
 * @author Robin Garner 
 *
 * @version $Revision$
 * @date $Date$
 */
public final class SelectedPlan extends
//-#value RVM_WITH_JMTK_PLAN
  implements Uninterruptible {

  public static final SelectedPlan singleton = new SelectedPlan();
  
  /**
   * Return the instance of the SelectedPlan
   */
  public static final SelectedPlan get() throws InlinePragma {
    return singleton;
  }
}
