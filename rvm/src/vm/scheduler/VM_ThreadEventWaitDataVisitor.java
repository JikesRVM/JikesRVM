/*
 * (C) Copyright IBM Corp. 2002
 */
// $Id$

package com.ibm.JikesRVM;

/**
 * Visitor class for <code>VM_ThreadEventWaitData</code> objects.
 * Subclasses can recover the actual type of an object from a
 * <code>VM_ThreadEventWaitData</code> reference.
 *
 * @author David Hovemeyer
 */
public abstract class VM_ThreadEventWaitDataVisitor {

  /**
   * Visit a VM_ThreadIOWaitData object.
   */
  public abstract void visitThreadIOWaitData(VM_ThreadIOWaitData waitData);

  /**
   * Visit a VM_ThreadProcessWaitData object.
   */
  public abstract void visitThreadProcessWaitData(VM_ThreadProcessWaitData waitData);

}
