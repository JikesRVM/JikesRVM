/*
 * (C) Copyright IBM Corp. 2001
 */

/**
 * An exception to indicate that a method in the vm failed to return
 * a value due to a class-loading, class-initialization,
 * or name-resolution problem.
 */
public class VM_ResolutionException extends Exception {
  //-----------//
  // interface //
  //-----------//
   
   // Get (descriptor for) type that was trying to be resolved.
   //
  public final VM_Atom getTargetTypeDescriptor() {
    return targetTypeDescriptor;
  }
   
  // Get exception that prevented resolution.
  //
  public final Throwable getException() {
    return exception;
  }
   
  //----------------//
  // implementation //
  //----------------//
   
  VM_Atom   targetTypeDescriptor; // descriptor for type whose resolution caused the problem
  Throwable exception;            // exception that prevented resolution
   
  VM_ResolutionException(VM_Atom targetTypeDescriptor, Throwable exception) {
    this.targetTypeDescriptor = targetTypeDescriptor;
    this.exception = exception;
  }

  public final String toString() {
    return "VM_ResolutionException: " + exception;
  }
}
