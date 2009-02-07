/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.classloader;

import org.jikesrvm.VM;
import org.jikesrvm.Constants;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;

/**
 * A field or method of a java class.
 */
public abstract class RVMMember extends AnnotatedElement implements Constants, ClassLoaderConstants {

  /** Initial value for a field offset - indicates field not laid out. */
  private static final int NO_OFFSET = Short.MIN_VALUE + 1;

  /**
   * The class that declared this member, available by calling
   * getDeclaringClass once the class is loaded.
   */
  private final TypeReference declaringClass;

  /**
   * The canonical MemberReference for this member
   */
  protected final MemberReference memRef;

  /**
   * The modifiers associated with this member.
   */
  protected final short modifiers;

  /**
   * The signature is a string representing the generic type for this
   * field or method declaration, may be null
   */
  private final Atom signature;

  /**
   * The member's jtoc/obj/tib offset in bytes.
   * Set by {@link RVMClass#resolve()}
   */
  protected int offset;

  /**
   * NOTE: Only {@link RVMClass} is allowed to create an instance of a RVMMember.
   *
   * @param declaringClass the TypeReference object of the class that declared this member
   * @param memRef the canonical memberReference for this member.
   * @param modifiers modifiers associated with this member.
   * @param signature generic type of this member
   * @param annotations array of runtime visible annotations
   */
  protected RVMMember(TypeReference declaringClass, MemberReference memRef, short modifiers, Atom signature,
                      RVMAnnotation[] annotations) {
    super(annotations);
    this.declaringClass = declaringClass;
    this.memRef = memRef;
    this.modifiers = modifiers;
    this.signature = signature;
    this.offset = NO_OFFSET; // invalid value. Set to valid value during RVMClass.resolve()
  }

  //--------------------------------------------------------------------//
  //                         Section 1.                                 //
  // The following are available after class loading.                   //
  //--------------------------------------------------------------------//

  /**
   * Class that declared this field or method. Not available before
   * the class is loaded.
   */
  @Uninterruptible
  public final RVMClass getDeclaringClass() {
    return declaringClass.peekType().asClass();
  }

  /**
   * Canonical member reference for this member.
   */
  @Uninterruptible
  public final MemberReference getMemberRef() {
    return memRef;
  }

  /**
   * Name of this member.
   */
  @Uninterruptible
  public final Atom getName() {
    return memRef.getName();
  }

  /**
   * Descriptor for this member.
   * something like "I" for a field or "(I)V" for a method.
   */
  @Uninterruptible
  public final Atom getDescriptor() {
    return memRef.getDescriptor();
  }

  /**
   * Generic type for member
   */
  public final Atom getSignature() {
    return signature;
  }

  /**
   * Get a unique id for this member.
   * The id is the id of the canonical MemberReference for this member
   * and thus may be used to find the member by first finding the member reference.
   */
  @Uninterruptible
  public final int getId() {
    return memRef.getId();
  }

  /*
   * Define hashcode in terms of Atom.hashCode to enable
   * consistent hash codes during bootImage writing and run-time.
   */
  @Override
  public int hashCode() {
    return memRef.hashCode();
  }

  @Override
  public final String toString() {
    return declaringClass + "." + getName() + " " + getDescriptor();
  }

  /**
   * Usable from classes outside its package?
   */
  public final boolean isPublic() {
    return (modifiers & ACC_PUBLIC) != 0;
  }

  /**
   * Usable only from this class?
   */
  public final boolean isPrivate() {
    return (modifiers & ACC_PRIVATE) != 0;
  }

  /**
   * Usable from subclasses?
   */
  public final boolean isProtected() {
    return (modifiers & ACC_PROTECTED) != 0;
  }

  /**
   * Get the member's modifiers.
   */
  public final int getModifiers() {
    return modifiers;
  }

  /**
   * Has the field been laid out in the object yet ?
   *
   * @return true if the field has been assigned an offset, false if not.
   */
  public final boolean hasOffset() {
    return !(offset == NO_OFFSET);
  }

  //------------------------------------------------------------------//
  //                       Section 2.                                 //
  // The following are available after the declaring class has been   //
  // "resolved".                                                      //
  //------------------------------------------------------------------//

  /**
   * Offset of this field or method, in bytes.
   * <ul>
   * <li> For a static field:      offset of field from start of jtoc
   * <li> For a static method:     offset of code object reference from start of jtoc
   * <li> For a non-static field:  offset of field from start of object
   * <li> For a non-static method: offset of code object reference from start of tib
   * </ul>
   */
  @Uninterruptible
  public final Offset getOffset() {
    if (VM.VerifyAssertions) VM._assert(declaringClass!=null);
    if (VM.VerifyAssertions) VM._assert(declaringClass.isLoaded());
    if (VM.VerifyAssertions) VM._assert(offset != NO_OFFSET);
    return Offset.fromIntSignExtend(offset);
  }

  /**
   * Only meant to be used by ObjectModel.layoutInstanceFields.
   * TODO: refactor system so this functionality is in the classloader package
   * and this method doesn't have to be final.
   */
  public final void setOffset(Offset off) {
    offset = off.toInt();
  }
}

