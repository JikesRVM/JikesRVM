/*
 * ObjectMap skeleton
 *
 * (C) Copyright Richard Jones, University of Kent at Canterbury 2001-3.
 * All rights reserved.
 */

package com.ibm.JikesRVM.memoryManagers.JMTk;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Uninterruptible;

public class ObjectMap implements VM_Uninterruptible {
  public ObjectMap() { }
  public final void boot() { }
  public final void release(VM_Address chunkAddr, int bytes) { }
  public final void release(VM_Address chunkAddr) { }
  public final void alloc(VM_Address addr) { }
  public final void dealloc(VM_Address addr) { }
  public void iterator(VM_Address start, VM_Address end) { }
  public boolean hasNext()  { return false; }
  public VM_Address next() { return null; }
  public boolean trapAllocation(boolean trap) { return false; }
}
