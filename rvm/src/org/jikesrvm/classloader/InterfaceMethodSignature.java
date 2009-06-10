/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.classloader;

import org.jikesrvm.VM;
import org.jikesrvm.SizeConstants;
import org.jikesrvm.objectmodel.TIBLayoutConstants;
import org.jikesrvm.util.ImmutableEntryHashSetRVM;
import org.vmmagic.unboxed.Offset;

/**
 *  An interface method signature is a pair of atoms:
 *  interfaceMethodName + interfaceMethodDescriptor.
 */
public final class InterfaceMethodSignature implements TIBLayoutConstants, SizeConstants {

  /**
   * Used to canonicalize InterfaceMethodSignatures
   */
  private static final ImmutableEntryHashSetRVM<InterfaceMethodSignature> dictionary =
    new ImmutableEntryHashSetRVM<InterfaceMethodSignature>();

  /**
   * Used to assign ids. Don't use id 0 to allow clients to use id 0 as a 'null'.
   */
  private static int nextId = 1;

  /**
   * Name of the interface method
   */
  private final Atom name;

  /**
   * Descriptor of the interface method
   */
  private final Atom descriptor;

  /**
   * Id of this interface method signature (not used in hashCode or equals).
   */
  private final int id;

  private InterfaceMethodSignature(Atom name, Atom descriptor, int id) {
    this.name = name;
    this.descriptor = descriptor;
    this.id = id;
  }

  /**
   * Find or create an interface method signature for the given method reference.
   *
   * @param ref     A reference to a supposed interface method
   * @return the interface method signature
   */
  public static synchronized InterfaceMethodSignature findOrCreate(MemberReference ref) {
    InterfaceMethodSignature key = new InterfaceMethodSignature(ref.getName(), ref.getDescriptor(), nextId+1);
    InterfaceMethodSignature val = dictionary.get(key);
    if (val != null) return val;
    nextId++;
    dictionary.add(key);
    return key;
  }

  /**
   * @return name of the interface method
   */
  public Atom getName() {
    return name;
  }

  /**
   * @return descriptor of hte interface method
   */
  public Atom getDescriptor() {
    return descriptor;
  }

  /**
   * @return the id of thie interface method signature.
   */
  public int getId() { return id; }

  public String toString() {
    return "{" + name + " " + descriptor + "}";
  }

  public int hashCode() {
    return name.hashCode() + descriptor.hashCode();
  }

  public boolean equals(Object other) {
    if (other instanceof InterfaceMethodSignature) {
      InterfaceMethodSignature that = (InterfaceMethodSignature) other;
      return name == that.name && descriptor == that.descriptor;
    } else {
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
  public Offset getIMTOffset() {
    if (VM.VerifyAssertions) VM._assert(VM.BuildForIMTInterfaceInvocation);
    int slot = id % IMT_METHOD_SLOTS;
    return Offset.fromIntZeroExtend(slot << LOG_BYTES_IN_ADDRESS);
  }
}
