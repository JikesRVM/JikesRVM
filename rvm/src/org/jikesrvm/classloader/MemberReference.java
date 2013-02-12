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

import java.util.StringTokenizer;
import org.jikesrvm.VM;
import org.jikesrvm.util.ImmutableEntryHashSetRVM;
import org.vmmagic.pragma.Uninterruptible;

/**
 * A class to represent the reference in a class file to some
 * member (field or method).
 * A member reference is uniquely defined by
 * <ul>
 * <li> a type reference
 * <li> a method name
 * <li> a descriptor
 * </ul>
 * Resolving a MemberReference to a RVMMember can
 * be an expensive operation.  Therefore we canonicalize
 * MemberReference instances and cache the result of resolution.
 */
public abstract class MemberReference {

  /**
   * Used to canonicalize memberReferences
   */
  private static final ImmutableEntryHashSetRVM<MemberReference> dictionary =
    new ImmutableEntryHashSetRVM<MemberReference>();

  /**
   * 2^LOG_ROW_SIZE is the number of elements per row
   */
  private static final int LOG_ROW_SIZE = 10;
  /**
   * Mask to ascertain row from id number
   */
  private static final int ROW_MASK = (1 << LOG_ROW_SIZE)-1;
  /**
   * Dictionary of all MemberReference instances.
   */
  private static MemberReference[][] members = new MemberReference[16][1 << LOG_ROW_SIZE];

  /**
   * Used to assign ids.  Id 0 is not used to support usage of member reference id's in JNI.
   */
  private static int nextId = 1;

  /**
   * Unique id for the member reference (ignored in .equals comparison)
   */
  protected final int id;

  /**
   * The type reference
   */
  protected final TypeReference type;

  /**
   * The member name
   */
  protected final Atom name;

  /**
   * The descriptor
   */
  protected final Atom descriptor;

  /**
   * Find or create the canonical MemberReference instance for
   * the given tuple.
   * @param tRef the type reference
   * @param mn the name of the member
   * @param md the descriptor of the member
   */
  public static synchronized MemberReference findOrCreate(TypeReference tRef, Atom mn, Atom md) {
    MemberReference key;
    if (md.isMethodDescriptor()) {
      if (tRef.isArrayType() && !tRef.isUnboxedArrayType()) {
        tRef = RVMType.JavaLangObjectType.getTypeRef();
      }
      key = new MethodReference(tRef, mn, md, nextId + 1);
    } else {
      key = new FieldReference(tRef, mn, md, nextId + 1);
    }
    MemberReference val = dictionary.get(key);
    if (val != null) return val;
    nextId++;
    TableBasedDynamicLinker.ensureCapacity(key.id);
    int column = key.id >> LOG_ROW_SIZE;
    if (column == members.length) {
      MemberReference[][] tmp = new MemberReference[column+1][];
      for (int i=0; i < column; i++) {
        tmp[i] = members[i];
      }
      members = tmp;
      members[column] = new MemberReference[1 << LOG_ROW_SIZE];
    }
    members[column][key.id & ROW_MASK] = key;
    dictionary.add(key);
    return key;
  }

  /**
   * Given a StringTokenizer currently pointing to the start of a {@link
   * MemberReference} (created by doing a toString() on a
   * MemberReference), parse it and find/create the appropriate
   * MemberReference. Consumes all of the tokens corresponding to the
   * member reference.
   */
  public static MemberReference parse(StringTokenizer parser) {
    return parse(parser, false);
  }

  public static MemberReference parse(StringTokenizer parser, boolean boot) {
    String clName = null;
    try {
      parser.nextToken(); // discard <
      clName = parser.nextToken();
      if ((!clName.equals(BootstrapClassLoader.myName)) && (boot)) {
        return null;
      }
      Atom dc = Atom.findOrCreateUnicodeAtom(parser.nextToken());
      Atom mn = Atom.findOrCreateUnicodeAtom(parser.nextToken());
      Atom md = Atom.findOrCreateUnicodeAtom(parser.nextToken());
      parser.nextToken(); // discard '>'
      ClassLoader cl = null;
      if (clName.equals(BootstrapClassLoader.myName)) {
        cl = BootstrapClassLoader.getBootstrapClassLoader();
      } else if (clName.equals(ApplicationClassLoader.myName)) {
        cl = RVMClassLoader.getApplicationClassLoader();
      } else {
        cl = RVMClassLoader.findWorkableClassloader(dc);
      }
      TypeReference tref = TypeReference.findOrCreate(cl, dc);
      return findOrCreate(tref, mn, md);
    } catch (Exception e) {
      VM.sysWriteln("Warning: error parsing for class "+clName+": "+e);
      return null;
    }
  }

  //BEGIN HRM
  public static int getNextId() {
    return nextId;
  }
  //END HRM

  @Uninterruptible
  public static MemberReference getMemberRef(int id) {
    return members[id >> LOG_ROW_SIZE][id & ROW_MASK];
  }

  /**
   * @param tRef the type reference
   * @param mn the field or method name
   * @param d the field or method descriptor
   * @param id the new ID of the member were a new member required (ignored in
   *            .equals test)
   */
  protected MemberReference(TypeReference tRef, Atom mn, Atom d, int id) {
    type = tRef;
    name = mn;
    descriptor = d;
    this.id = id;
  }

  /**
   * @return the type reference component of this member reference
   */
  @Uninterruptible
  public final TypeReference getType() {
    return type;
  }

  /**
   * @return the member name component of this member reference
   */
  @Uninterruptible
  public final Atom getName() {
    return name;
  }

  /**
   * @return the descriptor component of this member reference
   */
  @Uninterruptible
  public final Atom getDescriptor() {
    return descriptor;
  }

  /**
   * @return the dynamic linking id to use for this member.
   */
  @Uninterruptible
  public final int getId() {
    return id;
  }

  /**
   * Is this member reference to a field?
   */
  @Uninterruptible
  public final boolean isFieldReference() {
    return this instanceof FieldReference;
  }

  /**
   * Is this member reference to a method?
   */
  @Uninterruptible
  public final boolean isMethodReference() {
    return this instanceof MethodReference;
  }

  /**
   * @return this cast to a FieldReference
   */
  @Uninterruptible
  public final FieldReference asFieldReference() {
    return (FieldReference) this;
  }

  /**
   * @return this cast to a MethodReference
   */
  @Uninterruptible
  public final MethodReference asMethodReference() {
    return (MethodReference) this;
  }

  /**
   * @return the RVMMember this reference resolves to if it is already known
   * or {@code null} if it cannot be resolved without risking class loading.
   */
  public final RVMMember peekResolvedMember() {
    if (isFieldReference()) {
      return this.asFieldReference().peekResolvedField();
    } else {
      return this.asMethodReference().peekResolvedMethod();
    }
  }

  /**
   * Force resolution and return the resolved member.
   * Will cause classloading if necessary
   */
  public final RVMMember resolveMember() {
    if (isFieldReference()) {
      return this.asFieldReference().resolve();
    } else {
      return this.asMethodReference().resolve();
    }
  }

  /**
   * Is dynamic linking code required to access "this" member when
   * referenced from "that" method?
   */
  public final boolean needsDynamicLink(RVMMethod that) {
    RVMMember resolvedThis = this.peekResolvedMember();

    if (resolvedThis == null) {
      // can't tell because we haven't resolved the member reference
      // sufficiently to even know exactly where it is declared.
      return true;
    }
    RVMClass thisClass = resolvedThis.getDeclaringClass();

    if (thisClass == that.getDeclaringClass()) {
      // Intra-class references don't need to be compiled with dynamic linking
      // because they execute *after* class has been loaded/resolved/compiled.
      return false;
    }

    if (thisClass.isInitialized()) {
      // No dynamic linking code is required to access this member
      // because its size and offset are known and its class's static
      // initializer has already run.
      return false;
    }

    if (isFieldReference() && thisClass.isResolved() && thisClass.getClassInitializerMethod() == null) {
      // No dynamic linking code is required to access this field
      // because its size and offset is known and its class has no static
      // initializer, therefore its value need not be specially initialized
      // (its default value of zero or null is sufficient).
      return false;
    }

    if (VM.writingBootImage && thisClass.isInBootImage()) {
      // Loads, stores, and calls within boot image are compiled without dynamic
      // linking code because all boot image classes are explicitly
      // loaded/resolved/compiled and have had their static initializers
      // run by the boot image writer.
      if (VM.VerifyAssertions) VM._assert(thisClass.isResolved());
      return false;
    }

    // This member needs size and offset to be computed, or its class's static
    // initializer needs to be run when the member is first "touched", so
    // dynamic linking code is required to access the member.
    return true;
  }

  @Override
  public final int hashCode() {
    return type.hashCode() + name.hashCode() + descriptor.hashCode();
  }

  @Override
  public final boolean equals(Object other) {
    if (other instanceof MemberReference) {
      MemberReference that = (MemberReference) other;
      return type == that.type && name == that.name && descriptor == that.descriptor;
    } else {
      return false;
    }
  }

  @Override
  public final String toString() {
    return "< " + type.getClassLoader() + ", " + type.getName() + ", " + name + ", " + descriptor + " >";
  }
}
