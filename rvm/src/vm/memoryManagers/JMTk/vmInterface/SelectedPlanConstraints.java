/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
//$Id$
package com.ibm.JikesRVM.memoryManagers.mmInterface;

import org.vmmagic.pragma.*;

/**
 * This class extends the selected MMTk constraints class. 
 *
 * @author Daniel Frampton 
 * @author Robin Garner 
 *
 * @version $Revision$
 * @date $Date$
 */
public final class SelectedPlanConstraints extends
//-#value RVM_WITH_JMTK_PLANCONSTRAINTS
  implements Uninterruptible {

  public static final SelectedPlanConstraints singleton = new SelectedPlanConstraints();
  
  /**
   * Return the instance of the SelectedPlan
   */
  public static final SelectedPlanConstraints get() throws InlinePragma {
    return singleton;
  }
}
