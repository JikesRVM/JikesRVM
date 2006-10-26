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
package com.ibm.jikesrvm.memoryManagers.mmInterface;

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
//-#value RVM_WITH_MMTK_PLAN
  implements Uninterruptible {

  public static final SelectedPlan singleton = new SelectedPlan();
  
  /**
   * Return the instance of the SelectedPlan
   */
  public static final SelectedPlan get() throws InlinePragma {
    return singleton;
  }
}
