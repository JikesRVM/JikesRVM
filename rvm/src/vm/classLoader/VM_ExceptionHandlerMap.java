/*
 * (C) Copyright IBM Corp. 2001
 */

/** 
 * A java method's try/catch/finally information.
 */
class VM_ExceptionHandlerMap {
  //-----------//
  // Interface //
  //-----------//

  final int[] getStartPC()   { return startPCs;   }
  final int[] getEndPC()     { return endPCs;     }
  final int[] getHandlerPC() { return handlerPCs; }
  final VM_Type getExceptionType(int i) { return exceptionTypes[i]; }
   
  //----------------//
  // Implementation //
  //----------------//

  int[] startPCs;           // bytecode offset at which i-th try block begins
  // 0-indexed from start of method's bytecodes[]

  int[] endPCs;             // bytecode offset at which i-th try block ends (exclusive)
  // 0-indexed from start of method's bytecodes[]

  int[] handlerPCs;         // bytecode offset at which exception handler for i-th try block begins
  // 0-indexed from start of method's bytecodes[]

  VM_Type[] exceptionTypes; // exception type for which i-th handler is to be invoked
  // - something like "java/lang/IOException".
  // a null indicates a "finally block" (handler accepts all
  // exceptions).

  VM_ExceptionHandlerMap(VM_BinaryData input, VM_Class declaringClass, int n){
    startPCs       = new int[n];
    endPCs         = new int[n];
    handlerPCs     = new int[n];
    exceptionTypes = new VM_Type[n];
    for (int i = 0; i < n; ++i) {
      startPCs[i]       = input.readUnsignedShort();
      endPCs[i]         = input.readUnsignedShort();
      handlerPCs[i]     = input.readUnsignedShort();
      exceptionTypes[i] = declaringClass.getTypeRef(input.readUnsignedShort()); // possibly null
    }
  }
}
