/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Dynamic linking via indirection tables.
 *
 * <p> The main idea for dynamic linking is that VM_Classloader maintains 
 * an array of field and method offsets indexed by fid and mid.  
 * The generated code at a dynamically linked site will 
 * load the appropriate value from the field/method offset table and 
 * check to see if the value is valid. If it is, then no dynamic linking 
 * is required.  If the value is invalid, then resolveDynamicLink
 * is invoked to perfrom dynamic class loading.  During the
 * process of class loading, the required value will be stored in the 
 * appropriate offset table.
 * Thus when resolveDynamicLink returns, execution can be restarted 
 * by reloading/indexing the offset table.
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
public class VM_TableBasedDynamicLinker {

  /**
   * Cause dynamic linking of the VM_Method whose dictionary id is given.
   * @param methodId the dictionaryId of the method to link.
   */
  public static void resolveMethod(int methodId) throws VM_ResolutionException {
    VM_Method target = VM_MethodDictionary.getValue(methodId);
    resolve(target);
  }
      
  /**
   * Cause dynamic linking of the VM_Field whose dictionary id is given.
   * @param fieldId the dictionaryId of the method to link.
   */
  public static void resolveField(int fieldId) throws VM_ResolutionException {
    VM_Field target = VM_FieldDictionary.getValue(fieldId);
    resolve(target);
  }

  /**
   * Perform the dynamic linking required to access the
   * argument VM_Method. Will raise linking errors as necessary.
   * The indirection tables maintained by the VM_ClassLoader are 
   * initialized as a side-effect of calling initializeClassForDynamicLink.
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
      VM_ClassLoader.setMethodOffset(target, rt.getOffset());
    }
  }

  /**
   * Perform the dynamic linking required to access the
   * argument VM_Field. Will raise linking errors as necessary.
   * The indirection tables maintained by the VM_ClassLoader are 
   * initialized as a side-effect of calling initializeClassForDynamicLink.
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
      VM_ClassLoader.setFieldOffset(target, rt.getOffset());
    }
  }
}



