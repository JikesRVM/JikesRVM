/*
 * (C) Copyright ANU, 2004
 */
//$Id$
package org.vmmagic.unboxed;

/**
 * The object reference type is used by the runtime system and collector to 
 * represent a type that holds a reference to a single object. 
 * We use a separate type instead of the Java Object type for coding clarity,
 * to make a clear distinction between objects the VM is written in, and 
 * objects that the VM is managing. No operations that can not be completed in
 * pure Java should be allowed on Object.
 * 
 * @author Daniel Frampton
 */
public final class ObjectReference {
  
  /**
   * Convert from an object to a reference.  Note: this is a JikesRVM
   * specific extension to vmmagic.
   * @param obj The object 
   * @return The corresponding reference
   */
  public static ObjectReference fromObject(Object obj) {
    return null;
  }

  /**
   * Return a null reference
   */
  public static final ObjectReference nullReference() {
    return null;
  }

  /**
   * Convert from an reference to an object. Note: this is a JikesRVM
   * specific extension to vmmagic.
   * @return The object 
   */
  public Object toObject() {
    return null;
  }

  /**
   * Get a heap address for the object. 
   */
  public Address toAddress() {
    return null;
  }

  /**
   * Is this a null reference?
   */
  public boolean isNull() {
    return false;
  }
}
