/*
 * (C) Copyright IBM Corp 2003
 */
//$Id$
package java.lang.ref;

import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;

/**
 * Implementation of java.lang.ref.PhantomReference for JikesRVM.
 * @author Chris Hoffmann
 */
public class PhantomReference extends Reference {
  
  public PhantomReference(Object referent, ReferenceQueue q) {
    super(referent, q);
    MM_Interface.addPhantomReference(this);
  }
  
  /**
   * Returns the object this reference refers to. Phantom references
  always return <code>null</code>.
   * @return <code>null</code>
   */
  public Object get() {
    return null;
  }

}
