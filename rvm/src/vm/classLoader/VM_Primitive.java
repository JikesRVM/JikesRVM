/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Description of a java "primitive" type (int, float, etc.)
 * 
 * <p> This description is not read from a ".class" file, but rather
 * is manufactured by the vm before execution begins.
 * 
 * <p> Note that instances of primitives are not objects:
 * <ul>
 * <li> they are never heap allocated in the virtual machine
 * <li> they have no virtual methods
 * <li> they appear only in the virtual machine's stack, in its registers,
 *   or in fields/elements of class/array instances.
 * </ul>
 *
 * @see VM_Class
 * @see VM_Array
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public final class VM_Primitive extends VM_Type
  implements VM_Constants, VM_ClassLoaderConstants, VM_SynchronizedObject {
  //-----------//
  // Interface //
  //-----------//
   
  /**
   * Name - something like "int".
   */ 
  public final String getName() { 
    return name.toString();
  }

  /**
   * Stack space requirement.
   */ 
  public final int getStackWords() throws VM_PragmaUninterruptible {
    return stackWords;
  }
      
  public final void load() {}
  public final void resolve() {}
  public final void instantiate() {}
  public final void initialize() {}
      
  /**
   * Primitives are not first class objects - 
   * but the implementation of reflection is cleaner if
   * we pretend that they are and provide dummy implementations of 
   * the following methods 
   */ 
  public final VM_Field[] getStaticFields() { 
    return new VM_Field[0];
  }
      
  public final VM_Field[] getInstanceFields() { 
    return new VM_Field[0];
  }
   
  public final VM_Method[] getStaticMethods() { 
    return new VM_Method[0];
  }
   
  public final VM_Method[] getVirtualMethods() { 
    return new VM_Method[0];
  }

  // these should never be called.
  public final boolean hasFinalizer() throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    return false;
  }
      
  public final Object[] getTypeInformationBlock() throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    return null;
  }

  public final ClassLoader getClassLoader() {
      return VM_SystemClassLoader.getVMClassLoader();
  }

  //----------------//
  // Implementation //
  //----------------//
   
  private VM_Atom name;
  private int     stackWords;
   
  VM_Primitive(VM_Atom name, VM_Atom descriptor, int dictionaryId) {
    this.name         = name;
    this.descriptor   = descriptor;
    this.dictionaryId = dictionaryId;
    this.tibSlot      = VM_Statics.allocateSlot(VM_Statics.TIB);
    this.dimension    = -1;
    this.depth        = 0;
    if (VM.BuildForConcurrentGC)
      this.acyclic  = true;	// All primitives are inherently acyclic
      
    // install type information block (no method dispatch table) 
    // for use in type checking.
    //
    Object[] tib = new Object[1];
    tib[0] = this;
    VM_Statics.setSlotContents(tibSlot, tib);

    switch (descriptor.parseForTypeCode())
      {
      case VoidTypeCode:    this.stackWords = 0; break;
      case BooleanTypeCode: this.stackWords = 1; break;
      case ByteTypeCode:    this.stackWords = 1; break;
      case ShortTypeCode:   this.stackWords = 1; break;
      case IntTypeCode:     this.stackWords = 1; break;
      case LongTypeCode:    this.stackWords = 2; break;
      case FloatTypeCode:   this.stackWords = 1; break;
      case DoubleTypeCode:  this.stackWords = 2; break;
      case CharTypeCode:    this.stackWords = 1; break;
      default:              if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
      }

    state = CLASS_INITIALIZED; // primitives have no "load, resolve, instantiate, initialize" phases
  }
}
