/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.io.DataInputStream;
import java.io.IOException;

/**
 * A field or method of a java class.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
abstract class VM_Member implements VM_Constants, VM_ClassLoaderConstants {
  //-----------//
  // Interface //
  //-----------//
   
  //--------------------------------------------------------------------//
  //                         Section 0.                                 //
  // The following are always available.                                //
  //--------------------------------------------------------------------//

  /**
   * Class from which this field or method was inherited.
   */ 
  public final VM_Class getDeclaringClass() throws VM_PragmaUninterruptible { 
    return declaringClass;
  }
      
  /**
   * Name of this field or method - something like "foo".
   */ 
  public final VM_Atom getName() throws VM_PragmaUninterruptible { 
    return name;
  }

  /**
   * Descriptor for this field or method - 
   * something like "I" for a field or "(I)V" for a method.
   */ 
  public final VM_Atom getDescriptor() throws VM_PragmaUninterruptible {
    return descriptor;
  }

  /**
   * Index of this field or method in the field or method dictionary
   */ 
  public final int getDictionaryId() throws VM_PragmaUninterruptible {
    return dictionaryId;
  }
 
  /**
   * Redefine hashCode(), to allow use of consistent hash codes during
   * bootImage writing and run-time
   */
  public int hashCode() { return dictionaryId; }


  //---------------------------------------------------------------------//
  //                           Section 1.                                //
  // The following are available after the declaring class has been      // 
  // "loaded".                                                           //
  //---------------------------------------------------------------------//

  //
  // Attributes.
  //
   
  /**
   * Loaded?
   */ 
  public final boolean isLoaded() throws VM_PragmaUninterruptible {
    return (modifiers & ACC_LOADED) != 0; 
  }

  /**
   * Usable from classes outside this package?
   */ 
  public final boolean isPublic() {
    if (VM.VerifyAssertions) VM.assert(declaringClass.isLoaded());
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return (modifiers & ACC_PUBLIC) != 0; 
  }

  /**
   * Usable only from this class?
   */ 
  public final boolean isPrivate() { 
    if (VM.VerifyAssertions) VM.assert(declaringClass.isLoaded());
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return (modifiers & ACC_PRIVATE) != 0; 
  }
   
  /**
   * Usable from subclasses?
   */ 
  public final boolean isProtected() { 
    if (VM.VerifyAssertions) VM.assert(declaringClass.isLoaded());
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return (modifiers & ACC_PROTECTED) != 0; 
  } 

  /**
   * Is dynamic linking code required to access "this" member when 
   * referenced from "that" method?
   */ 
  public final boolean needsDynamicLink(VM_Method that) {
    VM_Class thisClass = this.getDeclaringClass();
    
    if (thisClass.isInitialized()) {
      // No dynamic linking code is required to access this field or 
      // call this method
      // because its size and offset are known and its class's static 
      // initializer
      // has already run, thereby compiling this method or initializing 
      // this field.
      //
      return false;
    }
        
    if (this instanceof VM_Field && thisClass.isResolved() && 
	thisClass.getClassInitializerMethod() == null) {
      // No dynamic linking code is required to access this field
      // because its size and offset is known and its class has no static
      // initializer, therefore its value need not be specially initialized
      // (its default value of zero or null is sufficient).
      //
      return false;
    }
        
    if (VM.writingBootImage && thisClass.isInBootImage()) {
      // Loads, stores, and calls within boot image are compiled without dynamic
      // linking code because all boot image classes are explicitly 
      // loaded/resolved/compiled
      // and have had their static initializers run by the boot image writer.
      //
      if (!thisClass.isResolved()) VM.sysWrite("unresolved: \"" + this + "\" referenced from \"" + that + "\"\n");
      if (VM.VerifyAssertions) VM.assert(thisClass.isResolved());
      return false;
    }

    if (thisClass == that.getDeclaringClass()) {
      // Intra-class references don't need to be compiled with dynamic linking
      // because they execute *after* class has been loaded/resolved/compiled.
      //
      return false;
    }
  
    // This member needs size and offset to be computed, or its class's static
    // initializer needs to be run when the member is first "touched", so
    // dynamic linking code is required to access the member.
    //
    return true;
  }

  //------------------------------------------------------------------//
  //                       Section 2.                                 //
  // The following are available after the declaring class has been   // 
  // /"resolved".                                                     //
  //------------------------------------------------------------------//

  /**
   * Offset of this field or method, in bytes.
   * <ul>
   * <li> For a static field:      offset of field from start of jtoc
   * <li> For a static method:     offset of code object reference from 
   * start of jtoc
   * <li> For a non-static field:  offset of field from start of object
   * <li> For a non-static method: offset of code object reference from 
   * start of tib
   * </ul>
   * @see VM_Class#getLiteralOffset
   * to obtain offset of constant from start of jtoc
   */ 
  abstract int getOffset() throws VM_PragmaUninterruptible ;
   
  //----------------//
  // Implementation //
  //----------------//

  protected final static int UNINITIALIZED_OFFSET = -1;

  protected VM_Class declaringClass;
  protected VM_Atom  name;
  protected VM_Atom  descriptor;
  protected int      modifiers;
  protected int      dictionaryId;

  /**
   * To guarantee uniqueness, only the VM_ClassLoader class may construct 
   * VM_Member instances.
   * All VM_Member creation should be performed by calling 
   * "VM_ClassLoader.findOrCreate" methods.
   */ 
  protected VM_Member() { }

  protected VM_Member(VM_Class declaringClass, VM_Atom name, 
		      VM_Atom descriptor, int dictionaryId) {
    this.declaringClass = declaringClass;
    this.name           = name;
    this.descriptor     = descriptor;
    this.dictionaryId    = dictionaryId;
  }
   
  /**
   * Access the member's modifier flags.
   * @return 32 bits of modifier flags exactly like they come out of 
   * the class file.
   * @author John J. Barton
   * @date 5/98
   */
  public final int getModifiers() {
    return modifiers;
  }

  public final String toString() {
    return getDeclaringClass().getName() + "." + getName() + " " + 
      getDescriptor();
  }
}
