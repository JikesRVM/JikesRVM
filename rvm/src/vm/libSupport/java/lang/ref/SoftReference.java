/*
 * (C) Copyright IBM Corp 2003
 */
//$Id$
package java.lang.ref;

import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;

/**
 * Implementation of java.lang.ref.SoftReference for JikesRVM.
 * @author Chris Hoffmann
 */
public class SoftReference extends Reference {

  public SoftReference(Object referent) {
    super(referent);
    MM_Interface.addSoftReference(this);
  }

  public SoftReference(Object referent, ReferenceQueue q) {
    super(referent, q);
    MM_Interface.addSoftReference(this);
  }
  
}
