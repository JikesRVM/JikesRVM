/*
 * (C) Copyright IBM Corp. 2001, 2004
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import java.util.HashMap;
import com.ibm.JikesRVM.*;
import org.vmmagic.unboxed.Offset;

/**
 *  An interface method signature is a pair of atoms: 
 *  interfaceMethodName + interfaceMethodDescriptor.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public final class VM_InterfaceMethodSignature implements VM_TIBLayoutConstants, VM_SizeConstants {

  /**
   * Used to canonicalize VM_InterfaceMethodSignatures
   */
  private static HashMap dictionary = new HashMap();

  /**
   * Used to assign ids. Don't use id 0 to allow clients to use id 0 as a 'null'.
   */
  private static int nextId = 1; 
  
  /**
   * Name of the interface method
   */
  private final VM_Atom name;

  /**
   * Descriptor of the interface method
   */
  private final VM_Atom descriptor;

  /**
   * Id of this interface method signature.
   */
  private int id;

  private VM_InterfaceMethodSignature(VM_Atom name, VM_Atom descriptor) {
    this.name = name;
    this.descriptor = descriptor;
  }

  /**
   * Find or create an interface method signature for the given method reference.
   *
   * @param ref     A reference to a supposed interface method
   * @return the interface method signature
   */
  public static synchronized VM_InterfaceMethodSignature findOrCreate(VM_MemberReference ref) {
    VM_InterfaceMethodSignature key = 
      new VM_InterfaceMethodSignature(ref.getName(), ref.getDescriptor());
    VM_InterfaceMethodSignature val = (VM_InterfaceMethodSignature)dictionary.get(key);
    if (val != null)  return val;
    key.id = nextId++;
    dictionary.put(key, key);
    return key;
  }    

  /**
   * @return name of the interface method
   */
  public final VM_Atom getName() {
    return name;
  }

  /**
   * @return descriptor of hte interface method
   */
  public final VM_Atom getDescriptor() {
    return descriptor;
  }

  /**
   * @return the id of thie interface method signature.
   */
  public final int getId() { return id; }

  public final String toString() {
    return "{" + name + " " + descriptor + "}";
  }

  public final int hashCode() {
    return name.hashCode() + descriptor.hashCode();
  }

  public final boolean equals(Object other) {
    if (other instanceof VM_InterfaceMethodSignature) {
      VM_InterfaceMethodSignature that = (VM_InterfaceMethodSignature)other;
      return name == that.name && descriptor == that.descriptor;
    }  else {
      return false;
    }
  }

  /**
   * If using embedded IMTs, Get offset of interface method slot in TIB.
   * If using indirect IMTs, Get offset of interface method slot in IMT.
   * Note that all methods with same name & descriptor map to the same slot.
   * <p>
   * TODO!! replace this stupid offset assignment algorithm with something more reasonable.
   * 
   * @return offset in TIB/IMT
   */ 
  public final Offset getIMTOffset() {
    if (VM.VerifyAssertions) VM._assert(VM.BuildForIMTInterfaceInvocation);
    int slot = id % IMT_METHOD_SLOTS;
    if (VM.BuildForEmbeddedIMT) {
      slot += TIB_FIRST_INTERFACE_METHOD_INDEX;
    }
    return Offset.fromIntZeroExtend(slot << LOG_BYTES_IN_ADDRESS);
  }
}
