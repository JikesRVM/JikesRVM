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
import org.vmmagic.pragma.Uninterruptible;

/**
 * A class to represent the reference in a class file to a field.
 */
public final class FieldReference extends MemberReference implements SizeConstants {

  /**
   * The field's type
   */
  private final TypeReference fieldContentsType;

  /**
   * The RVMField that this field reference resolved to ({@code null} if not yet resolved).
   */
  private RVMField resolvedMember;

  /**
   * @param tr a type reference
   * @param mn the field or method name
   * @param d the field or method descriptor
   * @param id the new ID of the member were a new member required
   */
  FieldReference(TypeReference tr, Atom mn, Atom d, int id) {
    super(tr, mn, d, id);
    fieldContentsType = TypeReference.findOrCreate(tr.getClassLoader(), d);
  }

  /**
   * @return the type of the field's value
   */
  @Uninterruptible
  public TypeReference getFieldContentsType() {
    return fieldContentsType;
  }

  /**
   * How many stackslots do value of this type take?
   */
  public int getNumberOfStackSlots() {
    return getFieldContentsType().getStackWords();
  }

  /**
   * Get size of the field's value, in bytes.
   */
  @Uninterruptible
  public int getSize() {
    return fieldContentsType.getMemoryBytes();
  }

  /**
   * Do this and that definitely refer to the different fields?
   */
  public boolean definitelyDifferent(FieldReference that) {
    if (this == that) return false;
    if (getName() != that.getName() || getDescriptor() != that.getDescriptor()) {
      return true;
    }
    RVMField mine = peekResolvedField();
    RVMField theirs = that.peekResolvedField();
    if (mine == null || theirs == null) return false;
    return mine != theirs;
  }

  /**
   * Do this and that definitely refer to the same field?
   */
  public boolean definitelySame(FieldReference that) {
    if (this == that) return true;
    if (getName() != that.getName() || getDescriptor() != that.getDescriptor()) {
      return false;
    }
    RVMField mine = peekResolvedField();
    RVMField theirs = that.peekResolvedField();
    if (mine == null || theirs == null) return false;
    return mine == theirs;
  }

  /**
   * Has the field reference already been resolved into a target method?
   */
  public boolean isResolved() {
    return resolvedMember != null;
  }

  /**
   * For use by RVMField constructor
   */
  void setResolvedMember(RVMField it) {
    if (VM.VerifyAssertions) VM._assert(resolvedMember == null || resolvedMember == it);
    resolvedMember = it;
  }

  /**
   * Find the RVMField that this field reference refers to using
   * the search order specified in JVM spec 5.4.3.2.
   * @return the RVMField that this method ref resolved to or null if it cannot be resolved.
   */
  public RVMField peekResolvedField() {
    if (resolvedMember != null) return resolvedMember;

    // Hasn't been resolved yet. Try to do it now without triggering class loading.
    RVMClass declaringClass = (RVMClass) type.peekType();
    if (declaringClass == null) return null;
    return resolveInternal(declaringClass);
  }

  /**
   * Find the RVMField that this field reference refers to using
   * the search order specified in JVM spec 5.4.3.2.
   * @return the RVMField that this method ref resolved to.
   */
  public synchronized RVMField resolve() {
    if (resolvedMember != null) return resolvedMember;

    // Hasn't been resolved yet. Do it now triggering class loading if necessary.
    return resolveInternal((RVMClass) type.resolve());
  }

  private RVMField resolveInternal(RVMClass declaringClass) {
    if (!declaringClass.isResolved()) {
      declaringClass.resolve();
    }
    for (RVMClass c = declaringClass; c != null; c = c.getSuperClass()) {
      // Look in this class
      RVMField it = c.findDeclaredField(name, descriptor);
      if (it != null) {
        resolvedMember = it;
        return resolvedMember;
      }
      // Look at all interfaces directly and indirectly implemented by this class.
      for (RVMClass i : c.getDeclaredInterfaces()) {
        it = searchInterfaceFields(i);
        if (it != null) {
          resolvedMember = it;
          return resolvedMember;
        }
      }
    }
    throw new NoSuchFieldError(this.toString());
  }

  private RVMField searchInterfaceFields(RVMClass c) {
    RVMField it = c.findDeclaredField(name, descriptor);
    if (it != null) return it;
    for (RVMClass i : c.getDeclaredInterfaces()) {
      it = searchInterfaceFields(i);
      if (it != null) return it;
    }
    return null;
  }
}
