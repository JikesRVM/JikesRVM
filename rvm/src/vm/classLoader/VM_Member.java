/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.*;
import java.io.DataInputStream;
import java.io.IOException;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Offset;

/**
 * A field or method of a java class.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public abstract class VM_Member implements VM_Constants, VM_ClassLoaderConstants {

  /**
   * The VM_Class that declared this member.
   */
  protected final VM_Class declaringClass;

  /**
   * The canonical VM_MemberReference for this member
   */
  protected final VM_MemberReference memRef;

  /**
   * The modifiers associated with this member.
   */
  protected final int modifiers;

  /**
   * The member's jtoc/obj/tib offset in bytes.
   * Set by {@link VM_Class#resolve()}
   */
  protected int offset;

  /**
   * NOTE: Only {@link VM_Class} is allowed to create an instance of a VM_Member.
   * 
   * @param declaringClass the VM_Class object of the class that declared this member
   * @param memRef the canonical memberReference for this member.
   * @param modifiers modifiers associated with this member.
   */
  protected VM_Member(VM_Class declaringClass, VM_MemberReference memRef,
                      int modifiers) {
    this.declaringClass = declaringClass;
    this.memRef = memRef;
    this.modifiers = modifiers;
    this.offset = -1; // invalid value. Set to valid value during VM_Class.resolve()
  }
   
  //--------------------------------------------------------------------//
  //                         Section 0.                                 //
  // The following are always available.                                //
  //--------------------------------------------------------------------//

  /**
   * Class that declared this field or method.
   */ 
  public final VM_Class getDeclaringClass() throws UninterruptiblePragma { 
    return declaringClass;
  }
      
  /**
   * Canonical member reference for this member.
   */ 
  public final VM_MemberReference getMemberRef() throws UninterruptiblePragma { 
    return memRef;
  }

  /**
   * Name of this member.
   */ 
  public final VM_Atom getName() throws UninterruptiblePragma { 
    return memRef.getName();
  }

  /**
   * Descriptor for this member.
   * something like "I" for a field or "(I)V" for a method.
   */ 
  public final VM_Atom getDescriptor() throws UninterruptiblePragma {
    return memRef.getDescriptor();
  }

  /**
   * Get a unique id for this member.
   * The id is the id of the canonical VM_MemberReference for this member
   * and thus may be used to find the member by first finding the member reference.
   */
  public final int getId() throws UninterruptiblePragma {
    return memRef.getId();
  }

  /*
   * Define hashcode in terms of VM_Atom.hashCode to enable
   * consistent hash codes during bootImage writing and run-time.
   */
  public int hashCode() { 
    return memRef.hashCode();
  }

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
  public final Offset getOffset() throws UninterruptiblePragma {
    if (VM.VerifyAssertions) VM._assert(declaringClass.isResolved());
    return Offset.fromIntSignExtend(offset);
  }

  /**
   * Offset of this field or method, in bytes.
   * <ul>
   * <li> For a static field:      offset of field from start of jtoc
   * <li> For a static method:     offset of code object reference from start of jtoc
   * <li> For a non-static field:  offset of field from start of object
   * <li> For a non-static method: offset of code object reference from start of tib
   * </ul>
   */ 
  public final int getOffsetAsInt() throws UninterruptiblePragma {
    if (VM.VerifyAssertions) VM._assert(declaringClass.isResolved());
    return offset;
  }

  /**
   * Only meant to be used by VM_ObjectModel.layoutInstanceFields.
   * TODO: refactor system so this functionality is in the classloader package
   * and this method doesn't have to be final.
   */
  public final void setOffset(int off) {
    offset = off;
  }
}
