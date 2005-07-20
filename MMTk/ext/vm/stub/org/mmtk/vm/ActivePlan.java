package org.mmtk.vm;

import org.mmtk.plan.Plan;
import org.mmtk.plan.PlanLocal;
import org.mmtk.plan.PlanConstraints;

/**
 * Stub to give access to plan local, constraint and global instances
 *
 * $Id$
 *
 * @author Daniel Frampton
 * @author Robin Garner
 */
public class ActivePlan {
  
  /**
   * @return The active Plan instance.
   */
  public static final Plan global() {
    return null;
  }
	
  /**
   * @return The active PlanLocal instance.
   */
  public static final PlanLocal local() {
    return null;
  }
  
  /**
   * Return the PlanLocal instance given it's unique identifier.
   * 
   * @param id The identifier of the PlanLocal to return
   * @return The specified PlanLocal
   */
  public static final PlanLocal local(int id) {
    return null;
  }
  
  /**
   * @return The active PlanConstraints instance.
   */
  public static final PlanConstraints constraints() {
    return null;
  }
  
  /**
   * @return The number of registered PlanLocal instances.
   */
  public static final int localCount() {
    return 0;
  }
  
  /**
   * Register a new PlanLocal instance.
   * 
   * @param local The PlanLocal to register.
   * @return The PlanLocal's unique identifier
   */
  public static final int registerLocal(PlanLocal local) {
    return 0;
  }
}
