/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright ANU, 2004
 */
//$Id$
package org.vmmagic.unboxed;

import org.vmmagic.pragma.*;
import com.ibm.jikesrvm.VM_SizeConstants;

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
@Uninterruptible public final class ObjectReference extends ArchitecturalWord implements VM_SizeConstants {
  ObjectReference(int value) {
    super(value, true);
  }
  ObjectReference(long value) {
    super(value);
  }
  
  /**
   * Convert from an object to a reference.
   * @param obj The object 
   * @return The corresponding reference
   */
  public static ObjectReference fromObject(Object obj) {
    return null;
  }

  /**
   * Return a null reference
   */
  public static final ObjectReference nullReference() throws InlinePragma {
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
