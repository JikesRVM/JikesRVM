/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM;

/**
 * Encoding of try ranges in the final machinecode and the
 * corresponding exception type and catch block start.
 *
 * @author Dave Grove
 * @author Mauricio Serrano
 * @author Janice Shepherd
 */
final class VM_BaselineExceptionTable extends VM_ExceptionTable {

  /**
   * Encode an exception table
   * @param emap the exception table to encode
   * @param bytecodeMap mapping from bytecode to machinecode offsets
   * @return the encoded exception table
   */
  static int[] encode(VM_ExceptionHandlerMap emap, int[] bytecodeMap) {
    int[] startPCs = emap.startPCs;
    int[] endPCs = emap.endPCs;
    int[] handlerPCs = emap.handlerPCs;
    VM_Type[] exceptionTypes = emap.exceptionTypes;
    int tableSize = startPCs.length;
    int[] eTable = new int[tableSize*4];
    
    for (int i=0; i<tableSize; i++) {
      eTable[i*4 + TRY_START] = bytecodeMap[startPCs[i]] << VM.LG_INSTRUCTION_WIDTH;
      eTable[i*4 + TRY_END] = bytecodeMap[endPCs[i]] << VM.LG_INSTRUCTION_WIDTH;
      eTable[i*4 + CATCH_START] = bytecodeMap[handlerPCs[i]] << VM.LG_INSTRUCTION_WIDTH;
      eTable[i*4 + EX_TYPE] = exceptionTypes[i].getDictionaryId();
    }
    return eTable;
  }
}
