/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.baseline;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.ExceptionHandlerMap;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.common.ExceptionTable;

/**
 * Encoding of try ranges in the final machinecode and the
 * corresponding exception type and catch block start.
 */
final class BaselineExceptionTable extends ExceptionTable {

  /**
   * Encode an exception table
   * @param emap the exception table to encode
   * @param bytecodeMap mapping from bytecode to machinecode offsets
   * @return the encoded exception table
   */
  static int[] encode(ExceptionHandlerMap emap, int[] bytecodeMap) {
    int[] startPCs = emap.getStartPC();
    int[] endPCs = emap.getEndPC();
    int[] handlerPCs = emap.getHandlerPC();
    TypeReference[] exceptionTypes = emap.getExceptionTypes();
    int tableSize = startPCs.length;
    int[] eTable = new int[tableSize * 4];

    for (int i = 0; i < tableSize; i++) {
      eTable[i * 4 + TRY_START] =
          bytecodeMap[startPCs[i]] << ArchitectureSpecific.RegisterConstants.LG_INSTRUCTION_WIDTH;
      eTable[i * 4 + TRY_END] =
          bytecodeMap[endPCs[i]] << ArchitectureSpecific.RegisterConstants.LG_INSTRUCTION_WIDTH;
      eTable[i * 4 + CATCH_START] =
          bytecodeMap[handlerPCs[i]] << ArchitectureSpecific.RegisterConstants.LG_INSTRUCTION_WIDTH;
      try {
        eTable[i * 4 + EX_TYPE] = exceptionTypes[i].resolve().getId();
      } catch (NoClassDefFoundError except) {
        // Yuck.  If this happens beatup Dave and make him do the right thing.
        // For now, we are forcing early loading of exception types to
        // avoid a bunch of ugly issues in resolving the type when delivering
        // the exception.  The problem is that we currently can't allow a GC
        // while in the midst of delivering an exception and resolving the
        // type reference might entail calling arbitrary classloader code.
        VM.sysWriteln("Trouble resolving a caught exception at compile time:");
        except.printStackTrace(); // sysFail won't print the stack trace that
        // lead to the NoClassDefFoundError.
        VM.sysFail("Unable to resolve caught exception type at compile time");
      }
    }
    return eTable;
  }
}
