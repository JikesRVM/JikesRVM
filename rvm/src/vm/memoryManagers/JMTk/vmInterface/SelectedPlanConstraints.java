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
package com.ibm.jikesrvm.memorymanagers.mmInterface;

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
//-#value RVM_WITH_MMTK_PLANCONSTRAINTS
  implements Uninterruptible {

  public static final SelectedPlanConstraints singleton = new SelectedPlanConstraints();
  
  /**
   * Return the instance of the SelectedPlan
   */
  public static final SelectedPlanConstraints get() throws InlinePragma {
    return singleton;
  }
}
