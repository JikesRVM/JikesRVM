/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Description of a java "primitive" type (int, float, etc.)
 * 
 * This description is not read from a ".class" file, but rather
 * is manufactured by the vm before execution begins.
 * 
 * Note that instances of primitives are not objects:
 *  - they are never heap allocated in the virtual machine
 * - they have no virtual methods
 * - they appear only in the virtual machine's stack, in its registers,
 *   or in fields/elements of class/array instances.
 *
 * @see VM_Class
 * @see VM_Array
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public class VM_Primitive extends VM_Type
  implements VM_Constants, VM_ClassLoaderConstants {
  //-----------//
  // Interface //
  //-----------//
   
  // Name - something like "int".
  //
  public final String getName() { 
    return name.toString();
  }

  // Stack space requirement.
  //
  public final int getStackWords() {
    return stackWords;
  }
      
  public final void load() {}
  public final void resolve() {}
  public final void instantiate() {}
  public final void initialize() {}
      
  // Primitives are not first class objects - the following methods never get called.
  //
  public final boolean hasFinalizer() {
    if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
    return false;
  }
      
  public final VM_Field[] getStaticFields() { 
    if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
    return null; 
  }
      
  public final VM_Field[] getInstanceFields() { 
    if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
    return null; 
  }
   
  public final VM_Method[] getStaticMethods() { 
    if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
    return null; 
  }
   
  public final VM_Method[] getVirtualMethods() { 
    if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
    return null; 
  }

  public final Object[] getTypeInformationBlock() {
    if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
    return null;
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
      
    // install type information block (no method dispatch table) for use in type checking.
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
      default:              if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
      }

    state = CLASS_INITIALIZED; // primitives have no "load, resolve, instantiate, initialize" phases
  }
}
