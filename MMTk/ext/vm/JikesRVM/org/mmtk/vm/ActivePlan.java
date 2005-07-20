/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.vm;

import org.mmtk.plan.Plan;
import org.mmtk.plan.PlanLocal;
import org.mmtk.plan.PlanConstraints;

import com.ibm.JikesRVM.memoryManagers.mmInterface.SelectedPlan;
import com.ibm.JikesRVM.memoryManagers.mmInterface.SelectedPlanLocal;
import com.ibm.JikesRVM.memoryManagers.mmInterface.SelectedPlanConstraints;

import org.vmmagic.pragma.*;

/**
 * This class contains interfaces to access the current plan, plan local and
 * plan constraints instances.
 *
 * $Id$
 *
 * @author Daniel Frampton 
 * @author Robin Garner 
 * @version $Revision$
 * @date $Date$
 */
public final class ActivePlan implements Uninterruptible {

  /* PlanLocal Management */
  private static final int MAX_LOCALS = 100;
  private static SelectedPlanLocal[] locals = new SelectedPlanLocal[MAX_LOCALS];
  private static int localCount = 0; // Number of local instances 

  /**
   * Register a plan local instance.
   *
   * FIXME: Possible race in allocation of ids. Should be synchronized.
   *
   * @param planLocal The PlanLocal to register
   * @return The unique Plan id.
   */
  public static final int registerLocal(PlanLocal local) throws InterruptiblePragma {
    locals[localCount] = (SelectedPlanLocal)local;
    return localCount++;
  }

  public static final PlanLocal local() throws InlinePragma {
    return SelectedPlanLocal.get();
  }
   
  public static final PlanLocal local(int id) throws InlinePragma {
    return locals[id];
  }

  public static final int localCount() throws InlinePragma {
    return localCount;
  }
 
  public static final PlanConstraints constraints() throws InlinePragma {
    return SelectedPlanConstraints.get();
  } 

  public static final Plan global() throws InlinePragma {
    return SelectedPlan.get();
  } 
}
