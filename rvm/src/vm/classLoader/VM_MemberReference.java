/*
 * (C) Copyright IBM Corp. 2001, 2005
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.VM;
import org.vmmagic.pragma.*;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

/**
 * A class to represent the reference in a class file to some 
 * member (field or method). 
 * A member reference is uniquely defined by
 * <ul>
 * <li> a type reference
 * <li> a method name
 * <li> a descriptor
 * </ul>
 * Resolving a VM_MemberReference to a VM_Member can
 * be an expensive operation.  Therefore we canonicalize
 * VM_MemberReference instances and cache the result of resolution.
 * 
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public abstract class VM_MemberReference {

  /**
   * Used to canonicalize memberReferences
   */
  private static HashMap dictionary = new HashMap();

  /**
   * Dictionary of all VM_MemberReference instances.
   */
  private static VM_MemberReference[] members = new VM_MemberReference[16000];

  /**
   * Used to assign ids.  Id 0 is not used to support usage of member reference id's in JNI.
   */
  private static int nextId = 1; 
  
  /**
   * The type reference
   */
  protected final VM_TypeReference type;

  /**
   * The member name
   */
  protected final VM_Atom name;

  /**
   * The descriptor
   */
  protected final VM_Atom descriptor;

  /**
   * Unique id for the member reference
   */
  protected int id;

  /**
   * Find or create the canonical VM_MemberReference instance for
   * the given tuple.
   * @param tRef the type reference
   * @param mn the name of the member
   * @param md the descriptor of the member
   */
  public static synchronized VM_MemberReference findOrCreate(VM_TypeReference tRef, VM_Atom mn, VM_Atom md) {
    VM_MemberReference key;
    if (md.isMethodDescriptor()) {
      if (tRef.isArrayType() && !(tRef.isWordArrayType() || tRef.isCodeArrayType())) {
        tRef = VM_Type.JavaLangObjectType.getTypeRef();
      }
      key = new VM_MethodReference(tRef, mn, md);
    } else {
      key = new VM_FieldReference(tRef, mn, md);
    }
    VM_MemberReference val = (VM_MemberReference)dictionary.get(key);
    if (val != null)  return val;
    key.id = nextId++;
    VM_TableBasedDynamicLinker.ensureCapacity(key.id);
    if (key.id == members.length) {
      VM_MemberReference[] tmp = new VM_MemberReference[members.length*2];
      System.arraycopy(members, 0, tmp, 0, members.length);
      members = tmp;
    }
    members[key.id] = key;
    dictionary.put(key, key);
    return key;
  }


  /**
   * Given a StringTokenizer currently pointing to the start of a {@link
   * VM_MemberReference} (created by doing a toString() on a
   * VM_MemberReference), parse it and find/create the appropriate
   * VM_MemberReference. Consumes all of the tokens corresponding to the
   * member reference.
   */
  public static VM_MemberReference parse(StringTokenizer parser) {
    try {
      parser.nextToken(); // discard <
      String clName = parser.nextToken();
      VM_Atom dc = VM_Atom.findOrCreateUnicodeAtom(parser.nextToken());
      VM_Atom mn = VM_Atom.findOrCreateUnicodeAtom(parser.nextToken());
      VM_Atom md = VM_Atom.findOrCreateUnicodeAtom(parser.nextToken());
      parser.nextToken(); // discard '>'
      ClassLoader cl;
      if (clName.equals(VM_BootstrapClassLoader.myName)) {
        cl = VM_BootstrapClassLoader.getBootstrapClassLoader();
      } else if (clName.equals( VM_ClassLoader.USE_OLD_APP_CLASSLOADER
                               ? ApplicationClassLoader.myName
                               : VM_ApplicationClassLoader2.myName)) 
      {
        cl = VM_ClassLoader.getApplicationClassLoader();
      } else {
        return null;
      }
      VM_TypeReference tref = VM_TypeReference.findOrCreate(cl, dc);
      return findOrCreate(tref, mn, md);
    } catch (NoSuchElementException e) {
      return null;
    }
  }

  //BEGIN HRM
  public static final int getNextId() {
    return nextId;
  }
  //END HRM

  public static VM_MemberReference getMemberRef(int id) throws UninterruptiblePragma {
    return members[id];
  }

  /**
   * @param tRef the type reference
   * @param mn the field or method name
   * @param d the field or method descriptor
   */
  protected VM_MemberReference(VM_TypeReference tRef, VM_Atom mn, VM_Atom d) {
    type = tRef;
    name = mn;
    descriptor = d;
  }

  /**
   * @return the type reference component of this member reference
   */
  public final VM_TypeReference getType() throws UninterruptiblePragma {
    return type;
  }

  /**
   * @return the member name component of this member reference
   */
  public final VM_Atom getName() throws UninterruptiblePragma {
    return name;
  }

  /**
   * @return the descriptor component of this member reference
   */
  public final VM_Atom getDescriptor() throws UninterruptiblePragma {
    return descriptor;
  }

  /**
   * @return the dynamic linking id to use for this member.
   */
  public final int getId() throws UninterruptiblePragma {
    return id;
  }

  /**
   * Is this member reference to a field?
   */
  public final boolean isFieldReference() throws UninterruptiblePragma {
    return this instanceof VM_FieldReference;
  }

  /**
   * Is this member reference to a method?
   */
  public final boolean isMethodReference() throws UninterruptiblePragma {
    return this instanceof VM_MethodReference;
  }

  /**
   * @return this cast to a VM_FieldReference
   */
  public final VM_FieldReference asFieldReference() throws UninterruptiblePragma {
    return (VM_FieldReference)this;
  }

  /**
   * @return this cast to a VM_MethodReference
   */
  public final VM_MethodReference asMethodReference() throws UninterruptiblePragma {
    return (VM_MethodReference)this;
  }

  /**
   * Is dynamic linking code required to access "this" member when 
   * referenced from "that" method?
   */ 
  public final boolean needsDynamicLink(VM_Method that) {
    if (type == that.getMemberRef().getType()) {
      // Intra-class references don't need to be compiled with dynamic linking
      // because they execute *after* class has been loaded/resolved/compiled.
      return false;
    }

    VM_Class thisClass = (VM_Class)type.peekResolvedType();
    if (thisClass == null) return true; // thisClass isn't loaded yet.
    
    if (thisClass.isInitialized()) {
      // No dynamic linking code is required to access this member
      // because its size and offset are known and its class's static 
      // initializer has already run.
      return false;
    }
        
    if (isFieldReference() && thisClass.isResolved() && 
        thisClass.getClassInitializerMethod() == null) {
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
      if (!thisClass.isResolved()) VM.sysWrite("unresolved: \"" + this + "\" referenced from \"" + that + "\"\n");
      if (VM.VerifyAssertions) VM._assert(thisClass.isResolved());
      return false;
    }

    // This member needs size and offset to be computed, or its class's static
    // initializer needs to be run when the member is first "touched", so
    // dynamic linking code is required to access the member.
    return true;
  }

  public final int hashCode() {
    return type.hashCode() + name.hashCode() + descriptor.hashCode();
  }

  public final boolean equals(Object other) {
    if (other instanceof VM_MemberReference) {
      VM_MemberReference that = (VM_MemberReference)other;
      return type == that.type && name == that.name &&
        descriptor == that.descriptor;
    } else {
      return false;
    }
  }

  public final String toString() {
    return "< " + type.getClassLoader() + ", " + type.getName() + ", " + name + ", " + descriptor + " >";
  }
}
