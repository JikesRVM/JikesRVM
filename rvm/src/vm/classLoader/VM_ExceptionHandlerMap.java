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
  public final VM_Type[] getExceptionTypes() { return exceptionTypes; }
  public final VM_Type getExceptionType(int i) { return exceptionTypes[i]; }
   
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
  VM_Type[] exceptionTypes; 

  VM_ExceptionHandlerMap(DataInputStream input, 
			 VM_Class declaringClass, 
			 int n) throws IOException {
    startPCs       = new int[n];
    endPCs         = new int[n];
    handlerPCs     = new int[n];
    exceptionTypes = new VM_Type[n];
    for (int i = 0; i < n; ++i) {
      startPCs[i]       = input.readUnsignedShort();
      endPCs[i]         = input.readUnsignedShort();
      handlerPCs[i]     = input.readUnsignedShort();
      VM_Type et = declaringClass.getTypeRef(input.readUnsignedShort()); // possibly null
      if (et == null) {
	// A finally block...set to java.lang.Throwable to avoid
	// needing to think about this case anywhere else in the VM.
	exceptionTypes[i] = VM_Type.JavaLangThrowableType;
      } else {
	exceptionTypes[i] = et;
      }
    }
  }
}
