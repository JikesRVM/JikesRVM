/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Dynamic linking via indirection tables. <p>
 *
 * The main idea for dynamic linking is that we maintain
 * arrays of field and method offsets indexed by fid and mid.  
 * The generated code at a dynamically linked site will 
 * load the appropriate value from the field/method offset table and 
 * check to see if the value is valid. If it is, then no dynamic linking 
 * is required.  If the value is invalid, then resolveDynamicLink
 * is invoked to perfrom dynamic class loading.  During the
 * process of class loading, the required value will be stored in the 
 * appropriate offset table.
 * Thus when resolveYYY method returns, execution can be restarted 
 * by reloading/indexing the offset table. <p>
 *
 * <p> NOTE: We believe that only use of invokespecial that could possibly 
 * require dynamic linking is that of invoking an object initializer.
 * As part of creating the uninitialized instance of an object, the 
 * runtime system must have invoked the class loader to load the class 
 * and therefore by the time the call to the init code is executed, 
 * the method offset table will contain a valid value.
 *
 * @author Jong-Deok Choi
 * @author Dave Grove
 */
public class VM_TableBasedDynamicLinker implements VM_Constants {

  /*
   * Offsets of fields and methods of java classes that have been encountered so far.
   * Entries in these arrays are co-indexed with corresponding entries in
   * VM_FieldDictionary and VM_MethodDictionary. Offsets are with respect to
   * jtoc, object instance, or tib, as appropriate. 
   * A value of NEEDS_DYNAMIC_LINK means the field or method hasn't been resolved yet 
   * or class initializer hasn't been run. <p>
   * NOTE: these fields are private, but are accessed directly from compiled code!
   */ 
  private static int[] methodOffsets = VM_RuntimeStructures.newContiguousIntArray(4096);
  private static int[] fieldOffsets  = VM_RuntimeStructures.newContiguousIntArray(4096);

  /*
   * Methods invoked from compiled code to cause 
   * dynamic linking and/or classloading to happen at runtime.
   * 
   */

  /**
   * Cause dynamic linking of the VM_Method whose dictionary id is given.
   * Invoked directly from (baseline) compiled code.
   * @param methodId the dictionaryId of the method to link.
   */
  public static void resolveMethod(int methodId) throws VM_ResolutionException {
    VM_Method target = VM_MethodDictionary.getValue(methodId);
    resolve(target);
  }
      
  /**
   * Cause dynamic linking of the VM_Field whose dictionary id is given.
   * Invoked directly from (baseline) compiled code.
   * @param fieldId the dictionaryId of the method to link.
   */
  public static void resolveField(int fieldId) throws VM_ResolutionException {
    VM_Field target = VM_FieldDictionary.getValue(fieldId);
    resolve(target);
  }

  /**
   * Perform the dynamic linking required to access the
   * argument VM_Method. Will raise linking errors as necessary.
   * The indirection tables are updated as a side-effect of calling 
   * initializeClassForDynamicLink.
   * 
   * @param target the VM_Method to link.
   */
  public static void resolve(VM_Method target) throws VM_ResolutionException {
    VM_Class declaringClass = target.getDeclaringClass();
    VM_Runtime.initializeClassForDynamicLink(declaringClass);

    // Check for a ghost reference and patch the extra table entry if necessary.
    // The call to resolve is also responsible for raising linking errors
    // such as NoSuchField/MethodError.
    VM_Method rt = target.resolve();
    if (rt != target) {
      setMethodOffset(target, rt.getOffset());
    }
  }

  /**
   * Perform the dynamic linking required to access the
   * argument VM_Field. Will raise linking errors as necessary.
   * The indirection tables are updated as a side-effect of calling 
   * initializeClassForDynamicLink.
   * 
   * @param target the VM_Field to link.
   */
  public static void resolve(VM_Field target) throws VM_ResolutionException {
    VM_Class declaringClass = target.getDeclaringClass();
    VM_Runtime.initializeClassForDynamicLink(declaringClass);

    // Check for a ghost reference and patch the extra table entry if necessary.
    // The call to resolve is also responsible for raising linking errors
    // such as NoSuchField/MethodError.
    VM_Field rt = target.resolve();
    if (rt != target) {
      setFieldOffset(target, rt.getOffset());
    }
  }



  /*
   * Methods invoked from VM_ClassLoader to maintain and update 
   * offset tables
   * 
   */

  /**
   * Ensure that method offset table has entry for argument method id.
   * Must be invoked each time a new methodId is created.
   * @param mid method dictionary id
   */
  static void ensureMethodCapacity(int methodId) {
    if (methodId >= methodOffsets.length)
      // grow array by 2x in anticipation of more entries being added
      methodOffsets = growArray(methodOffsets, methodId << 1); 
  }

  /**
   * Ensure that field offset table has entry for argument field id.
   * Must be invoked each time a new fieldId is created.
   * @param mid field dictionary id
   */
  static void ensureFieldCapacity(int fieldId) {
    if (fieldId >= fieldOffsets.length)
      // grow array by 2x in anticipation of more entries being added
      fieldOffsets = growArray(fieldOffsets, fieldId << 1); 
  }

  /**
   * Expand an offset array.
   */ 
  private static int[] growArray(int[] array, int newLength) {
    // assertion: no special array initialization needed (default 0 is ok)
    if (VM.VerifyAssertions) VM._assert(NEEDS_DYNAMIC_LINK == 0); 
    int[] newarray = VM_RuntimeStructures.newContiguousIntArray(newLength);
    System.arraycopy(array, 0, newarray, 0, array.length);
    VM_Magic.sync(); // be sure array initialization is visible before we publish the reference!
    return newarray;
  }


  /**
   * Get offset of method within jtoc or tib.
   * @param methodId method dictionary id
   * @return method offset (a value of NEEDS_DYNAMIC_LINK means method 
   * hasn't been resolved yet or class initializer hasn't been run)
   * @see VM_MethodDictionary
   * @see VM_Method#getOffset
   */
  static int getMethodOffset(int methodId) {
    return methodOffsets[methodId];
  }

  /**
   * NOTE: A method's offset must not be set to valid until we are positive that
   * a compiled method will be available at that offset before the offset
   * could be read/used by any thread.
   */
  static void setMethodOffset(VM_Method method, int offset) {
    if (VM.VerifyAssertions) VM._assert(offset != NEEDS_DYNAMIC_LINK);
    methodOffsets[method.getDictionaryId()] = offset;
  }

  /**
   * Get offset of field within jtoc or instance object.
   * @param fieldId field dictionary id
   * @return field offset (a value of NEEDS_DYNAMIC_LINK means field hasn't 
   * been resolved yet or class initializer hasn't been run)
   * @see VM_FieldDictionary 
   * @see VM_Field#getOffset
   */ 
  static int getFieldOffset(int fieldId) {
    return fieldOffsets[fieldId];
  }

  /**
   * NOTE: A field's offset should not be set to valid until references to
   * that field no longer require any class loading action to be performed.
   */ 
  static void setFieldOffset(VM_Field field, int offset) {
    if (VM.VerifyAssertions) VM._assert(offset != NEEDS_DYNAMIC_LINK);
    fieldOffsets[field.getDictionaryId()] = offset;
  }
}




