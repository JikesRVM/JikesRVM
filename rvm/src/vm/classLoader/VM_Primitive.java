/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.memoryManagers.vmInterface.MM_Interface;

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
 * @see VM_Type
 * @see VM_Class
 * @see VM_Array
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public final class VM_Primitive extends VM_Type implements VM_Constants, 
                                                           VM_ClassLoaderConstants,
                                                           VM_SynchronizedObject {

  /**
   * The pretty (external) name for this primitive.
   * For example, for a long the name is 'long' 
   * and the descriptor is 'J'
   */
  private final VM_Atom name;

  /**
   * How many slots in the Java Expression Stack does it take
   * to hold a value of this primitive type?
   */
  private final int stackWords;
   
  /**
   * Name - something like "int".
   */ 
  public final String toString() { 
    return name.toString();
  }

  /**
   * Stack space requirement.
   */ 
  public final int getStackWords() throws VM_PragmaUninterruptible {
    return stackWords;
  }
      
  /*
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

  public final boolean hasFinalizer() throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    return false;
  }
      
  public final Object[] getTypeInformationBlock() throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    return null;
  }

  /**
   * Create an instance of a VM_Primitive
   * @param typeRef the canonical type reference for this primitive
   */
  VM_Primitive(VM_TypeReference tr) {
    super(tr);
    depth = 0;
    acyclic = true;     // All primitives are inherently acyclic

    switch (getDescriptor().parseForTypeCode()) {
    case VoidTypeCode:
      stackWords = 0;
      name = VM_Atom.findOrCreateAsciiAtom("void");
      break;
    case BooleanTypeCode:
      stackWords = 1;
      name = VM_Atom.findOrCreateAsciiAtom("boolean");
      break;
    case ByteTypeCode:
      stackWords = 1;
      name = VM_Atom.findOrCreateAsciiAtom("byte");
      break;
    case CharTypeCode:
      stackWords = 1;
      name = VM_Atom.findOrCreateAsciiAtom("char");
      break;
    case ShortTypeCode:
      stackWords = 1;
      name = VM_Atom.findOrCreateAsciiAtom("short");
      break;
    case IntTypeCode:
      stackWords = 1;
      name = VM_Atom.findOrCreateAsciiAtom("int");
      break;
    case LongTypeCode:
      stackWords = 2;
      name = VM_Atom.findOrCreateAsciiAtom("long");
      break;
    case FloatTypeCode:
      stackWords = 1;
      name = VM_Atom.findOrCreateAsciiAtom("float");
      break;
    case DoubleTypeCode:
      stackWords = 2;
      name = VM_Atom.findOrCreateAsciiAtom("double");
      break;
    default:
      if (tr == VM_TypeReference.Address ||
          tr == VM_TypeReference.Word ||
          tr == VM_TypeReference.Offset ||
          tr == VM_TypeReference.Extent ||
          tr == VM_TypeReference.Code) {
        stackWords = 1; //Kris Venstermans: dependant of Magic or not ?
        name = tr.getName();
      } else {
        if (VM.VerifyAssertions) VM._assert(false);
        stackWords = -1;
        name = null;
      }
    }

    state = CLASS_INITIALIZED; // primitives have no-op "resolve, instantiate, initialize" phases
  }

  public final void resolve() {}
  public final void instantiate() {}
  public final void initialize() {}
}
