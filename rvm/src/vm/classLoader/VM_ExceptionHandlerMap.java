/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import java.io.DataInputStream;
import java.io.IOException;

/** 
 * A java method's try/catch/finally information.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public class VM_ExceptionHandlerMap {
  //-----------//
  // Interface //
  //-----------//

  public final int[] getStartPC() { return startPCs;   }
  public final int[] getEndPC()   { return endPCs;     }
  public final int[] getHandlerPC() { return handlerPCs; }
  public final VM_TypeReference[] getExceptionTypes() { return exceptionTypes; }
  public final VM_TypeReference getExceptionType(int i) { return exceptionTypes[i]; }
   
  //-#if RVM_WITH_OSR
  /* we need to adjust the exception handler map for pseudo bytecode
   */
  public final void setStartPC(int[] newPCs) { startPCs = newPCs; }
  public final void setEndPC(int[] newPCs) { endPCs = newPCs; }
  public final void setHandlerPC(int[] newPCs) { handlerPCs = newPCs; }
  //-#endif

  //----------------//
  // Implementation //
  //----------------//

  /**
   * bytecode offset at which i-th try block begins
   * 0-indexed from start of method's bytecodes[]
   */
  int[] startPCs;

  /**
   * bytecode offset at which i-th try block ends (exclusive)
   * 0-indexed from start of method's bytecodes[]
   */
  int[] endPCs;

  /**
   * bytecode offset at which exception handler for i-th try block begins
   * 0-indexed from start of method's bytecodes[]
   */
  int[] handlerPCs; 

  /**
   * exception type for which i-th handler is to be invoked
   * - something like "java/lang/IOException".
   * NOTE: When constructing the VM_ExceptionHandlerMap we replace
   * 'null' entries (means a finally block that catches everything)
   * with VM_Type.JavaLangThrowableType so we don't have to do anything
   * special anywhere else in the VM.
   */
  VM_TypeReference[] exceptionTypes; 

  VM_ExceptionHandlerMap(DataInputStream input, 
                         VM_Class declaringClass, 
                         int n) throws IOException {
    startPCs       = new int[n];
    endPCs         = new int[n];
    handlerPCs     = new int[n];
    exceptionTypes = new VM_TypeReference[n];
    for (int i = 0; i < n; ++i) {
      startPCs[i]       = input.readUnsignedShort();
      endPCs[i]         = input.readUnsignedShort();
      handlerPCs[i]     = input.readUnsignedShort();
      VM_TypeReference et = declaringClass.getTypeRef(input.readUnsignedShort()); // possibly null
      if (et == null) {
        // A finally block...set to java.lang.Throwable to avoid
        // needing to think about this case anywhere else in the VM.
        exceptionTypes[i] = VM_TypeReference.JavaLangThrowable;
      } else {
        exceptionTypes[i] = et;
      }
    }
  }

  //-#if RVM_WITH_OSR
  VM_ExceptionHandlerMap() {}
 
  VM_ExceptionHandlerMap deepCopy() {
    VM_ExceptionHandlerMap other =
      new VM_ExceptionHandlerMap();
 
    int n = startPCs.length;
    other.startPCs = new int[n];
    other.endPCs   = new int[n];
    other.handlerPCs = new int[n];
    other.exceptionTypes = new VM_TypeReference[n];
 
    System.arraycopy(this.startPCs, 0, other.startPCs, 0, n);
    System.arraycopy(this.endPCs, 0, other.endPCs, 0, n);
    System.arraycopy(this.handlerPCs, 0, other.handlerPCs, 0, n);
    System.arraycopy(this.exceptionTypes, 0, other.exceptionTypes, 0, n);
 
    return other;
  }
  //-#endif
}
