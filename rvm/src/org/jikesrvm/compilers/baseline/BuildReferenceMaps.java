/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.baseline;

import org.jikesrvm.ArchitectureSpecific.BaselineCompilerImpl;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.BytecodeConstants;
import org.jikesrvm.classloader.BytecodeStream;
import org.jikesrvm.classloader.ClassLoaderConstants;
import org.jikesrvm.classloader.ExceptionHandlerMap;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.TypeReference;

/**
 * This class builds the reference and non-reference maps for a given method.
 * The maps are recorded with ReferenceMaps. This class works with the baseline
 * compiler, calculating the maps for local variables (including parameters),
 * and the java operand stack. Given the basic blocks mapped out by BuildBB
 * determine for each GC point (call sites and new's, etc) what the stack and
 * variable maps are. Note that this class deals with reference maps (the term
 * "stack maps" was not used as it is too ambiguous - does "stack" refer to the
 * java operand stack or a C-like stack?; when processing java bytecodes it
 * seemed best to use "stack" for java operand stack.)
 */
final class BuildReferenceMaps implements BytecodeConstants, ClassLoaderConstants, BBConstants {

  static final byte NON_REFERENCE = 0;
  static final byte REFERENCE = 1;
  static final byte RETURN_ADDRESS = 2;
  static final byte NOT_SET = 0;
  static final byte SET_TO_REFERENCE = 1;
  static final byte SET_TO_NONREFERENCE = 3;

  private static enum PrimitiveSize {
    ONEWORD, DOUBLEWORD
  };

  // These two variables are used and updated by more than one method in this class,
  // therefore they need to be instance variables;
  int workStkTop;
  int JSRSubNext;

  /**
   * After the analysis of the blocks of a method, examine the byte codes again, to
   * determine the reference maps for the gc points. Record the maps with
   * referenceMaps.
   */
  public void buildReferenceMaps(NormalMethod method, int[] stackHeights, byte[] localTypes,
                                 ReferenceMaps referenceMaps, BuildBB buildBB) {
    //****************************************************************//
    // These were calculated by BuildBB.determineTheBasicBlocks    //
    //****************************************************************//
    int gcPointCount = buildBB.gcPointCount;
    short[] byteToBlockMap = buildBB.byteToBlockMap;
    BasicBlock[] basicBlocks = buildBB.basicBlocks;
    int jsrCount = buildBB.numJsrs;

    byte[][] bbMaps;            // The starting map for each block, a block is not
    // processed until it has a starting map.
    int[] blockStkTop;           // For each block, track where its current stack top is.

    int currBBNum;             // Block number of block currently being processed
    byte[] currBBMap;           // The current map, used during processing thru a block
    int currBBStkTop;          // Stack top for the current map

    int currBBStkEmpty;        // Level when stack is empty - value depends on number of locals
    int paramCount;            // Number of parameters to the method being processed

    // Variables for processing JSR instructions, RET instructions and JSR subroutines
    PendingRETInfo[] bbPendingRETs = null;
    PendingRETInfo currPendingRET;
    JSRSubroutineInfo[] JSRSubs = null;

    // Blocks that need to be processed are put on the workStk
    short[] workStk;

    // Track whether a block has already been seen once. Any recording of maps done
    // within such a block will be processed as a "rerecording" instead of a new map.
    //
    boolean[] blockSeen;

    // blocks that represent "catch" blocks need special processing. Catch blocks
    // also referred to as handlers
    //
    ExceptionHandlerMap exceptions;                // exception table class for method being processed
    int[] tryStartPC;              // array of try start indicesinto byte code table
    int[] tryEndPC;                // array of try end indices into byte code table
    int[] tryHandlerPC;            // array of try handlers start indices into bytecode
    int tryHandlerLength;          // length of try handlers array
    int[] reachableHandlerBBNums;  // array of reachable handlers from a given try block
    int reachableHandlersCount;    // Number of reachable handlers
    boolean[] handlerProcessed;        // Handler blocks are processed after the normal flow. As
    // they may be nested, they need to be handled
    // individually. This array is used to track which
    // have been processed.
    boolean handlersAllDone;

    // Other local variables
    //
    BytecodeStream bcodes;                // byte codes for the method
    short brBBNum;               // For processing branches, need block number of target

    final boolean debug = false;

    // Note that the mapping done here is "double mapping" of parameters.
    // Double mapping is when the parameters for a method are included in the map of
    // the method as well as in the map of the caller of the method. The original
    // intent was that with double mapping call sites that are tricks
    // (eg Magic.callFunctionReturnVoid ) would at least be correctly mapped on one
    // of the two sides. However with more recent changes to the runtime stack frame
    // layout, the parameters specified on the caller side occupy different
    // locations than the parameters on the callee side for the baseline compiler.
    // Thus both need to be described.

    //
    //  Initialization
    //

    // Determine what stack empty looks like
    paramCount = method.getParameterWords();
    if (!method.isStatic()) paramCount++;

    currBBStkEmpty = method.getLocalWords() - 1;   // -1 to locate the last "local" index

    if (debug) VM.sysWrite("getLocalWords() : " + method.getLocalWords() + "\n");

    // Get information from the method being processed
    bcodes = method.getBytecodes();

    // Set up the array of maps per block; block 0 is not used
    int numBB = buildBB.bbf.getNumberofBlocks();
    bbMaps = new byte[numBB + 1][];
    blockStkTop = new int[bbMaps.length];
    blockSeen = new boolean[bbMaps.length];

    // Try Handler processing initialization

    exceptions = method.getExceptionHandlerMap();
    if (exceptions != null) {
      tryStartPC = exceptions.getStartPC();
      tryEndPC = exceptions.getEndPC();
      tryHandlerPC = exceptions.getHandlerPC();
      tryHandlerLength = tryHandlerPC.length;

      reachableHandlerBBNums = new int[tryStartPC.length];
      handlerProcessed = new boolean[tryStartPC.length];
      if (jsrCount > 0) {
        JSRSubs = new JSRSubroutineInfo[jsrCount];
        JSRSubNext = 0;
        bbPendingRETs = new PendingRETInfo[bbMaps.length];
      }
      handlersAllDone = (tryHandlerLength == 0);

      // write poison values to help distinguish different errors
      for (int ii = 0; ii < reachableHandlerBBNums.length; ii++) {
        reachableHandlerBBNums[ii] = -1;
      }
    } else {
      tryHandlerLength = 0;
      handlersAllDone = true;
      tryStartPC = null;
      tryEndPC = null;
      tryHandlerPC = null;
      reachableHandlerBBNums = null;
      handlerProcessed = null;
    }
    reachableHandlersCount = 0;

    // Start a new set of maps with the reference Map class.
    // 3rd argument is parameter count included with the maps
    referenceMaps.startNewMaps(gcPointCount, jsrCount, paramCount);

    // Set up the Work stack
    workStk = new short[10 + tryHandlerLength];

    // Start by putting the first block on the work stack
    workStkTop = 0;
    workStk[workStkTop] = byteToBlockMap[0];
    currBBMap = new byte[method.getOperandWords() + currBBStkEmpty + 1];

    //
    // Need to include the parameters of this method in the map
    //
    TypeReference[] parameterTypes = method.getParameterTypes();
    int paramStart;
    if (!method.isStatic()) {
      currBBMap[0] = REFERENCE; // implicit "this" object
      localTypes[0] = ADDRESS_TYPE;
      paramStart = 1;
    } else {
      paramStart = 0;
    }

    for (int i = 0; i < parameterTypes.length; i++, paramStart++) {
      TypeReference parameterType = parameterTypes[i];
      if (parameterType.isReferenceType()) {
        localTypes[paramStart] = ADDRESS_TYPE;
        currBBMap[paramStart] = REFERENCE;
      } else {
        currBBMap[paramStart] = NON_REFERENCE;

        if (parameterType.getStackWords() == 2) {
          if (parameterType.isLongType()) {
            localTypes[paramStart] = LONG_TYPE;
          } else {
            localTypes[paramStart] = DOUBLE_TYPE;
          }
          paramStart++;
        } else if (parameterType.isFloatType()) {
          localTypes[paramStart] = FLOAT_TYPE;
        } else if (parameterType.isIntLikeType()) {
          localTypes[paramStart] = INT_TYPE;
        } else {
          localTypes[paramStart] = ADDRESS_TYPE;
        }
      }
    }

    // The map for the start of the first block, is stack empty, with none
    // of the locals set yet
    //
    currBBStkTop = currBBStkEmpty;
    bbMaps[byteToBlockMap[0]] = currBBMap;
    blockStkTop[byteToBlockMap[0]] = currBBStkTop;

    // For all methods, record a map at the start of the method for the corresponding
    // conditional call to "yield".

    referenceMaps.recordStkMap(0, currBBMap, currBBStkTop, false);

    currBBMap = new byte[currBBMap.length];

    //----------------------------------------------------------
    //
    //  Keep looping until the Work Stack is empty
    //
    //----------------------------------------------------------
    while (workStkTop > -1) {

      // Get the next item off the work stack
      currBBNum = workStk[workStkTop];
      workStkTop--;

      boolean inJSRSub = false;
      if (bbMaps[currBBNum] != null) {
        currBBStkTop = blockStkTop[currBBNum];
        for (int k = 0; k <= currBBStkTop; k++) {
          currBBMap[k] = bbMaps[currBBNum][k];
        }

        if (jsrCount > 0 && basicBlocks[currBBNum].isInJSR()) {
          inJSRSub = true;
        }
      } else {
        VM.sysWrite("BuildReferenceMaps, error: found a block on work stack with");
        VM.sysWrite(" no starting map. The block number is ");
        VM.sysWrite(basicBlocks[currBBNum].getBlockNumber());
        VM.sysWrite("\n");
        VM.sysFail("BuildReferenceMaps work stack failure");
      }

      int start = basicBlocks[currBBNum].getStart();
      int end = basicBlocks[currBBNum].getEnd();

      if (jsrCount > 0 && inJSRSub) {
        currPendingRET = bbPendingRETs[currBBNum];
        if (basicBlocks[currBBNum].isTryStart()) {
          for (int k = 0; k < tryHandlerLength; k++) {
            if (tryStartPC[k] == start) {
              int handlerBBNum = byteToBlockMap[tryHandlerPC[k]];
              bbPendingRETs[handlerBBNum] = new PendingRETInfo(currPendingRET);
            }
          }
        }
      } else {
        currPendingRET = null;
      }

      boolean inTryBlock;
      if (basicBlocks[currBBNum].isTryBlock()) {
        inTryBlock = true;
        reachableHandlersCount = 0;
        for (int i = 0; i < tryHandlerLength; i++) {
          if (start <= tryEndPC[i] && end >= tryStartPC[i]) {
            reachableHandlerBBNums[reachableHandlersCount] = byteToBlockMap[tryHandlerPC[i]];
            reachableHandlersCount++;
            int handlerBBNum = byteToBlockMap[tryHandlerPC[i]];
            if (bbMaps[handlerBBNum] == null) {
              bbMaps[handlerBBNum] = new byte[currBBMap.length];
              for (int k = 0; k <= currBBStkEmpty; k++) {
                bbMaps[handlerBBNum][k] = currBBMap[k];
              }
              bbMaps[handlerBBNum][currBBStkEmpty + 1] = REFERENCE;
              blockStkTop[handlerBBNum] = currBBStkEmpty + 1;
            } else  {
              if (inJSRSub && basicBlocks[handlerBBNum].isInJSR()) {
                // In JSR and handler within the same JSR.
                // Ensure SET_TO_NONREFERENCE is carried across
                for (int k = 0; k <= currBBStkEmpty; k++) {
                  if (currBBMap[k] == SET_TO_NONREFERENCE && bbMaps[handlerBBNum][k] != SET_TO_NONREFERENCE) {
                    handlerProcessed[i] = false;
                    bbMaps[handlerBBNum][k] = SET_TO_NONREFERENCE;
                  }
                }
              } else if (inJSRSub) {
                // In JSR but handler is shared by JSR and non JSR
                // realise JSR and SET_TO_NONREFERENCE becomes NON_REFERENCE
                for (int k = 0; k <= currBBStkEmpty; k++) {
                  if (currBBMap[k] == SET_TO_NONREFERENCE && bbMaps[handlerBBNum][k] != NON_REFERENCE) {
                    handlerProcessed[i] = false;
                    bbMaps[handlerBBNum][k] = NON_REFERENCE;
                  }
                }
              } else {
                // No JSRs involved, simply ensure NON_REFERENCE is carried over
                for (int k = 0; k <= currBBStkEmpty; k++) {
                  if (currBBMap[k] == NON_REFERENCE && bbMaps[handlerBBNum][k] != NON_REFERENCE) {
                    handlerProcessed[i] = false;
                    bbMaps[handlerBBNum][k] = NON_REFERENCE;
                  }
                }
              }
            }
          }
        }
      } else {
        inTryBlock = false;
      }

      boolean processNextBlock = true;

      bcodes.reset(start);
      while (bcodes.index() <= end) {
        int biStart = bcodes.index();
        int opcode = bcodes.nextInstruction();
        if (stackHeights != null) {
          stackHeights[biStart] = currBBStkTop;
        }

        if (debug) {
          VM.sysWrite("opcode : " + opcode + "\n");
          VM.sysWrite("current map: ");
          for (int j = 0; j <= currBBStkTop; j++) {
            VM.sysWrite(currBBMap[j]);
          }
          VM.sysWrite("\n");
        }

        switch (opcode) {
          case JBC_nop: {
            break;
          }
          case JBC_aconst_null: {
            currBBStkTop++;
            currBBMap[currBBStkTop] = REFERENCE;
            break;
          }
          case JBC_aload_0: {
            int localNumber = 0;
            currBBStkTop++;
            currBBMap[currBBStkTop] = currBBMap[localNumber];
            break;
          }
          case JBC_aload_1: {
            int localNumber = 1;
            currBBStkTop++;
            currBBMap[currBBStkTop] = currBBMap[localNumber];
            break;
          }
          case JBC_aload_2: {
            int localNumber = 2;
            currBBStkTop++;
            currBBMap[currBBStkTop] = currBBMap[localNumber];
            break;
          }
          case JBC_aload_3: {
            int localNumber = 3;
            currBBStkTop++;
            currBBMap[currBBStkTop] = currBBMap[localNumber];
            break;
          }
          case JBC_aload: {
            int localNumber = bcodes.getLocalNumber();
            currBBStkTop++;
            currBBMap[currBBStkTop] = currBBMap[localNumber];
            break;
          }

          case JBC_iconst_m1:
          case JBC_iconst_0:
          case JBC_iconst_1:
          case JBC_iconst_2:
          case JBC_iconst_3:
          case JBC_iconst_4:
          case JBC_iconst_5:
          case JBC_fconst_0:
          case JBC_fconst_1:
          case JBC_fconst_2:
          case JBC_iload_0:
          case JBC_iload_1:
          case JBC_iload_2:
          case JBC_iload_3:
          case JBC_fload_0:
          case JBC_fload_1:
          case JBC_fload_2:
          case JBC_fload_3:
          case JBC_bipush:
          case JBC_iload:
          case JBC_fload:
          case JBC_sipush:
          case JBC_i2l:
          case JBC_i2d:
          case JBC_f2l:
          case JBC_f2d: {
            currBBStkTop++;
            currBBMap[currBBStkTop] = NON_REFERENCE;
            bcodes.skipInstruction(); // contains mix of 1,2,3 byte bytecodes
            break;
          }

          case JBC_lconst_0:
          case JBC_lconst_1:
          case JBC_dconst_0:
          case JBC_dconst_1:
          case JBC_lload_0:
          case JBC_lload_1:
          case JBC_lload_2:
          case JBC_lload_3:
          case JBC_dload_0:
          case JBC_dload_1:
          case JBC_dload_2:
          case JBC_dload_3:
          case JBC_ldc2_w:
          case JBC_lload:
          case JBC_dload: {
            currBBStkTop++;
            currBBMap[currBBStkTop] = NON_REFERENCE;
            currBBStkTop++;
            currBBMap[currBBStkTop] = NON_REFERENCE;
            bcodes.skipInstruction(); // mix of 1, 2, and 3 byte bytecodes
            break;
          }

          case JBC_ldc: {
            currBBStkTop++;
            int cpi = bcodes.getConstantIndex();
            int type = bcodes.getConstantType(cpi);
            if (type == CP_STRING || type == CP_CLASS) {
              currBBMap[currBBStkTop] = REFERENCE;
            } else {
              currBBMap[currBBStkTop] = NON_REFERENCE;
            }
            break;
          }
          case JBC_ldc_w: {
            currBBStkTop++;
            int cpi = bcodes.getWideConstantIndex();
            int type = bcodes.getConstantType(cpi);
            if (type == CP_STRING || type == CP_CLASS) {
              currBBMap[currBBStkTop] = REFERENCE;
            } else {
              currBBMap[currBBStkTop] = NON_REFERENCE;
            }
            break;
          }

          case JBC_istore: {
            int index = bcodes.getLocalNumber();
            if (!inJSRSub) {
              currBBMap[index] = NON_REFERENCE;
            } else {
              currBBMap[index] = SET_TO_NONREFERENCE;
            }
            if (inTryBlock) {
              setHandlersMapsNonRef(index, PrimitiveSize.ONEWORD, reachableHandlerBBNums, reachableHandlersCount, inJSRSub, bbMaps);
            }
            currBBStkTop--;
            localTypes[index] |= INT_TYPE;
            break;
          }

          case JBC_fstore: {
            int index = bcodes.getLocalNumber();
            if (!inJSRSub) {
              currBBMap[index] = NON_REFERENCE;
            } else {
              currBBMap[index] = SET_TO_NONREFERENCE;
            }
            if (inTryBlock) {
              setHandlersMapsNonRef(index, PrimitiveSize.ONEWORD, reachableHandlerBBNums, reachableHandlersCount, inJSRSub, bbMaps);
            }
            currBBStkTop--;
            localTypes[index] |= FLOAT_TYPE;
            break;
          }

          case JBC_lstore: {
            int index = bcodes.getLocalNumber();
            if (!inJSRSub) {
              currBBMap[index] = NON_REFERENCE;
              currBBMap[index + 1] = NON_REFERENCE;
            } else {
              currBBMap[index] = SET_TO_NONREFERENCE;
              currBBMap[index + 1] = SET_TO_NONREFERENCE;
            }

            if (inTryBlock) {
              setHandlersMapsNonRef(index, PrimitiveSize.DOUBLEWORD,
                reachableHandlerBBNums, reachableHandlersCount, inJSRSub,
                bbMaps);
            }
            currBBStkTop = currBBStkTop - 2;
            localTypes[index] |= LONG_TYPE;
            break;
          }

          case JBC_dstore: {
            int index = bcodes.getLocalNumber();
            if (!inJSRSub) {
              currBBMap[index] = NON_REFERENCE;
              currBBMap[index + 1] = NON_REFERENCE;
            } else {
              currBBMap[index] = SET_TO_NONREFERENCE;
              currBBMap[index + 1] = SET_TO_NONREFERENCE;
            }

            if (inTryBlock) {
              setHandlersMapsNonRef(index, PrimitiveSize.DOUBLEWORD,
                reachableHandlerBBNums, reachableHandlersCount, inJSRSub,
                bbMaps);
            }
            currBBStkTop = currBBStkTop - 2;
            localTypes[index] |= DOUBLE_TYPE;
            break;
          }

          case JBC_astore: {
            int index = bcodes.getLocalNumber();
            currBBMap[index] = currBBMap[currBBStkTop];// may be a reference or a return address
            if (inJSRSub) {
              if (currBBMap[index] == RETURN_ADDRESS) {
                currPendingRET.updateReturnAddressLocation(index);
              }
              if (inTryBlock) {
                if (currBBMap[index] == REFERENCE) {
                  setHandlersMapsRef(index, reachableHandlerBBNums, reachableHandlersCount, bbMaps);
                } else {
                  setHandlersMapsReturnAddress(index, reachableHandlerBBNums, reachableHandlersCount, bbMaps);
                }
              }
            }
            currBBStkTop--;
            localTypes[index] |= ADDRESS_TYPE;
            break;
          }

          case JBC_istore_0: {
            if (!inJSRSub) {
              currBBMap[0] = NON_REFERENCE;
            } else {
              currBBMap[0] = SET_TO_NONREFERENCE;
            }
            if (inTryBlock) {
              setHandlersMapsNonRef(0, PrimitiveSize.ONEWORD, reachableHandlerBBNums, reachableHandlersCount, inJSRSub, bbMaps);
            }
            currBBStkTop--;
            localTypes[0] |= INT_TYPE;
            break;
          }

          case JBC_fstore_0: {
            if (!inJSRSub) {
              currBBMap[0] = NON_REFERENCE;
            } else {
              currBBMap[0] = SET_TO_NONREFERENCE;
            }
            if (inTryBlock) {
              setHandlersMapsNonRef(0, PrimitiveSize.ONEWORD, reachableHandlerBBNums, reachableHandlersCount, inJSRSub, bbMaps);
            }
            currBBStkTop--;
            localTypes[0] |= FLOAT_TYPE;
            break;
          }

          case JBC_istore_1: {
            if (!inJSRSub) {
              currBBMap[1] = NON_REFERENCE;
            } else {
              currBBMap[1] = SET_TO_NONREFERENCE;
            }
            if (inTryBlock) {
              setHandlersMapsNonRef(1, PrimitiveSize.ONEWORD, reachableHandlerBBNums, reachableHandlersCount, inJSRSub, bbMaps);
            }
            currBBStkTop--;
            localTypes[1] |= INT_TYPE;
            break;
          }

          case JBC_fstore_1: {
            if (!inJSRSub) {
              currBBMap[1] = NON_REFERENCE;
            } else {
              currBBMap[1] = SET_TO_NONREFERENCE;
            }
            if (inTryBlock) {
              setHandlersMapsNonRef(1, PrimitiveSize.ONEWORD, reachableHandlerBBNums, reachableHandlersCount, inJSRSub, bbMaps);
            }
            currBBStkTop--;
            localTypes[1] |= FLOAT_TYPE;
            break;
          }

          case JBC_istore_2: {
            if (!inJSRSub) {
              currBBMap[2] = NON_REFERENCE;
            } else {
              currBBMap[2] = SET_TO_NONREFERENCE;
            }
            if (inTryBlock) {
              setHandlersMapsNonRef(2, PrimitiveSize.ONEWORD, reachableHandlerBBNums, reachableHandlersCount, inJSRSub, bbMaps);
            }
            currBBStkTop--;
            localTypes[2] |= INT_TYPE;
            break;
          }

          case JBC_fstore_2: {
            if (!inJSRSub) {
              currBBMap[2] = NON_REFERENCE;
            } else {
              currBBMap[2] = SET_TO_NONREFERENCE;
            }
            if (inTryBlock) {
              setHandlersMapsNonRef(2, PrimitiveSize.ONEWORD, reachableHandlerBBNums, reachableHandlersCount, inJSRSub, bbMaps);
            }
            currBBStkTop--;
            localTypes[2] |= FLOAT_TYPE;
            break;
          }

          case JBC_istore_3: {
            if (!inJSRSub) {
              currBBMap[3] = NON_REFERENCE;
            } else {
              currBBMap[3] = SET_TO_NONREFERENCE;
            }
            if (inTryBlock) {
              setHandlersMapsNonRef(3, PrimitiveSize.ONEWORD, reachableHandlerBBNums, reachableHandlersCount, inJSRSub, bbMaps);
            }
            currBBStkTop--;
            localTypes[3] |= INT_TYPE;
            break;
          }

          case JBC_fstore_3: {
            if (!inJSRSub) {
              currBBMap[3] = NON_REFERENCE;
            } else {
              currBBMap[3] = SET_TO_NONREFERENCE;
            }
            if (inTryBlock) {
              setHandlersMapsNonRef(3, PrimitiveSize.ONEWORD, reachableHandlerBBNums, reachableHandlersCount, inJSRSub, bbMaps);
            }
            currBBStkTop--;
            localTypes[3] |= FLOAT_TYPE;
            break;
          }

          case JBC_lstore_0: {
            if (inJSRSub) {
              currBBMap[0] = NON_REFERENCE;
              currBBMap[1] = NON_REFERENCE;
            } else {
              currBBMap[0] = SET_TO_NONREFERENCE;
              currBBMap[1] = SET_TO_NONREFERENCE;
            }
            if (inTryBlock) {
              setHandlersMapsNonRef(0, PrimitiveSize.DOUBLEWORD, reachableHandlerBBNums, reachableHandlersCount, inJSRSub, bbMaps);
            }
            currBBStkTop = currBBStkTop - 2;
            localTypes[0] |= LONG_TYPE;
            break;
          }
          case JBC_dstore_0: {
            if (inJSRSub) {
              currBBMap[0] = NON_REFERENCE;
              currBBMap[1] = NON_REFERENCE;
            } else {
              currBBMap[0] = SET_TO_NONREFERENCE;
              currBBMap[1] = SET_TO_NONREFERENCE;
            }
            if (inTryBlock) {
              setHandlersMapsNonRef(0, PrimitiveSize.DOUBLEWORD, reachableHandlerBBNums, reachableHandlersCount, inJSRSub, bbMaps);
            }
            currBBStkTop = currBBStkTop - 2;
            localTypes[0] |= DOUBLE_TYPE;
            break;
          }

          case JBC_lstore_1: {
            if (!inJSRSub) {
              currBBMap[1] = NON_REFERENCE;
              currBBMap[2] = NON_REFERENCE;
            } else {
              currBBMap[1] = SET_TO_NONREFERENCE;
              currBBMap[2] = SET_TO_NONREFERENCE;
            }
            if (inTryBlock) {
              setHandlersMapsNonRef(1, PrimitiveSize.DOUBLEWORD, reachableHandlerBBNums, reachableHandlersCount, inJSRSub, bbMaps);
            }
            currBBStkTop = currBBStkTop - 2;
            localTypes[1] |= LONG_TYPE;
            break;
          }

          case JBC_dstore_1: {
            if (!inJSRSub) {
              currBBMap[1] = NON_REFERENCE;
              currBBMap[2] = NON_REFERENCE;
            } else {
              currBBMap[1] = SET_TO_NONREFERENCE;
              currBBMap[2] = SET_TO_NONREFERENCE;
            }
            if (inTryBlock) {
              setHandlersMapsNonRef(1, PrimitiveSize.DOUBLEWORD, reachableHandlerBBNums, reachableHandlersCount, inJSRSub, bbMaps);
            }
            currBBStkTop = currBBStkTop - 2;
            localTypes[1] |= DOUBLE_TYPE;
            break;
          }

          case JBC_lstore_2: {
            if (!inJSRSub) {
              currBBMap[2] = NON_REFERENCE;
              currBBMap[3] = NON_REFERENCE;
            } else {
              currBBMap[2] = SET_TO_NONREFERENCE;
              currBBMap[3] = SET_TO_NONREFERENCE;
            }
            if (inTryBlock) {
              setHandlersMapsNonRef(2, PrimitiveSize.DOUBLEWORD, reachableHandlerBBNums, reachableHandlersCount, inJSRSub, bbMaps);
            }
            currBBStkTop = currBBStkTop - 2;
            localTypes[2] |= LONG_TYPE;
            break;
          }

          case JBC_dstore_2: {
            if (!inJSRSub) {
              currBBMap[2] = NON_REFERENCE;
              currBBMap[3] = NON_REFERENCE;
            } else {
              currBBMap[2] = SET_TO_NONREFERENCE;
              currBBMap[3] = SET_TO_NONREFERENCE;
            }
            if (inTryBlock) {
              setHandlersMapsNonRef(2, PrimitiveSize.DOUBLEWORD, reachableHandlerBBNums, reachableHandlersCount, inJSRSub, bbMaps);
            }
            currBBStkTop = currBBStkTop - 2;
            localTypes[2] |= DOUBLE_TYPE;
            break;
          }

          case JBC_lstore_3: {
            if (!inJSRSub) {
              currBBMap[3] = NON_REFERENCE;
              currBBMap[4] = NON_REFERENCE;
            } else {
              currBBMap[3] = SET_TO_NONREFERENCE;
              currBBMap[4] = SET_TO_NONREFERENCE;
            }
            if (inTryBlock) {
              setHandlersMapsNonRef(3, PrimitiveSize.DOUBLEWORD, reachableHandlerBBNums, reachableHandlersCount, inJSRSub, bbMaps);
            }
            currBBStkTop = currBBStkTop - 2;
            localTypes[3] |= LONG_TYPE;
            break;
          }

          case JBC_dstore_3: {
            if (!inJSRSub) {
              currBBMap[3] = NON_REFERENCE;
              currBBMap[4] = NON_REFERENCE;
            } else {
              currBBMap[3] = SET_TO_NONREFERENCE;
              currBBMap[4] = SET_TO_NONREFERENCE;
            }
            if (inTryBlock) {
              setHandlersMapsNonRef(3, PrimitiveSize.DOUBLEWORD, reachableHandlerBBNums, reachableHandlersCount, inJSRSub, bbMaps);
            }
            currBBStkTop = currBBStkTop - 2;
            localTypes[3] |= DOUBLE_TYPE;
            break;
          }

          case JBC_astore_0: {
            currBBMap[0] = currBBMap[currBBStkTop];
            if (inJSRSub) {
              if (currBBMap[0] == RETURN_ADDRESS) {
                currPendingRET.updateReturnAddressLocation(0);
              }
              if (inTryBlock) {
                if (currBBMap[0] == REFERENCE) {
                  setHandlersMapsRef(0, reachableHandlerBBNums, reachableHandlersCount, bbMaps);
                } else {
                  setHandlersMapsReturnAddress(0, reachableHandlerBBNums, reachableHandlersCount, bbMaps);
                }
              }
            }
            currBBStkTop--;
            localTypes[0] |= ADDRESS_TYPE;
            break;
          }

          case JBC_astore_1: {
            currBBMap[1] = currBBMap[currBBStkTop];
            if (inJSRSub) {
              if (currBBMap[1] == RETURN_ADDRESS) {
                currPendingRET.updateReturnAddressLocation(1);
              }
              if (inTryBlock) {
                if (currBBMap[1] == REFERENCE) {
                  setHandlersMapsRef(1, reachableHandlerBBNums, reachableHandlersCount, bbMaps);
                } else {
                  setHandlersMapsReturnAddress(1, reachableHandlerBBNums, reachableHandlersCount, bbMaps);
                }
              }
            }
            currBBStkTop--;
            localTypes[1] |= ADDRESS_TYPE;
            break;
          }

          case JBC_astore_2: {
            currBBMap[2] = currBBMap[currBBStkTop];
            if (inJSRSub) {
              if (currBBMap[2] == RETURN_ADDRESS) {
                currPendingRET.updateReturnAddressLocation(2);
              }
              if (inTryBlock) {
                if (currBBMap[2] == REFERENCE) {
                  setHandlersMapsRef(2, reachableHandlerBBNums, reachableHandlersCount, bbMaps);
                } else {
                  setHandlersMapsReturnAddress(2, reachableHandlerBBNums, reachableHandlersCount, bbMaps);
                }
              }
            }
            currBBStkTop--;
            localTypes[2] |= ADDRESS_TYPE;
            break;
          }
          case JBC_astore_3: {
            currBBMap[3] = currBBMap[currBBStkTop];
            if (inJSRSub) {
              if (currBBMap[3] == RETURN_ADDRESS) {
                currPendingRET.updateReturnAddressLocation(3);
              }
              if (inTryBlock) {
                if (currBBMap[3] == REFERENCE) {
                  setHandlersMapsRef(3, reachableHandlerBBNums, reachableHandlersCount, bbMaps);
                } else {
                  setHandlersMapsReturnAddress(3, reachableHandlerBBNums, reachableHandlersCount, bbMaps);
                }
              }
            }
            currBBStkTop--;
            localTypes[3] |= ADDRESS_TYPE;
            break;
          }

          case JBC_dup: {
            currBBMap[currBBStkTop + 1] = currBBMap[currBBStkTop];
            currBBStkTop++;
            break;
          }
          case JBC_dup2: {
            currBBMap[currBBStkTop + 1] = currBBMap[currBBStkTop - 1];
            currBBMap[currBBStkTop + 2] = currBBMap[currBBStkTop];
            currBBStkTop = currBBStkTop + 2;
            break;
          }
          case JBC_dup_x1: {
            currBBMap[currBBStkTop + 1] = currBBMap[currBBStkTop];
            currBBMap[currBBStkTop] = currBBMap[currBBStkTop - 1];
            currBBMap[currBBStkTop - 1] = currBBMap[currBBStkTop + 1];
            currBBStkTop++;
            break;
          }
          case JBC_dup2_x1: {
            currBBMap[currBBStkTop + 2] = currBBMap[currBBStkTop];
            currBBMap[currBBStkTop + 1] = currBBMap[currBBStkTop - 1];
            currBBMap[currBBStkTop] = currBBMap[currBBStkTop - 2];
            currBBMap[currBBStkTop - 1] = currBBMap[currBBStkTop + 2];
            currBBMap[currBBStkTop - 2] = currBBMap[currBBStkTop + 1];
            currBBStkTop = currBBStkTop + 2;
            break;
          }
          case JBC_dup_x2: {
            currBBMap[currBBStkTop + 1] = currBBMap[currBBStkTop];
            currBBMap[currBBStkTop] = currBBMap[currBBStkTop - 1];
            currBBMap[currBBStkTop - 1] = currBBMap[currBBStkTop - 2];
            currBBMap[currBBStkTop - 2] = currBBMap[currBBStkTop + 1];
            currBBStkTop++;
            break;
          }
          case JBC_dup2_x2: {
            currBBMap[currBBStkTop + 2] = currBBMap[currBBStkTop];
            currBBMap[currBBStkTop + 1] = currBBMap[currBBStkTop - 1];
            currBBMap[currBBStkTop] = currBBMap[currBBStkTop - 2];
            currBBMap[currBBStkTop - 1] = currBBMap[currBBStkTop - 3];
            currBBMap[currBBStkTop - 2] = currBBMap[currBBStkTop + 2];
            currBBMap[currBBStkTop - 3] = currBBMap[currBBStkTop + 1];
            currBBStkTop = currBBStkTop + 2;
            break;
          }
          case JBC_swap: {
            byte temp;
            temp = currBBMap[currBBStkTop];
            currBBMap[currBBStkTop] = currBBMap[currBBStkTop - 1];
            currBBMap[currBBStkTop - 1] = temp;
            break;
          }
          case JBC_pop:
          case JBC_iadd:
          case JBC_fadd:
          case JBC_isub:
          case JBC_fsub:
          case JBC_imul:
          case JBC_fmul:
          case JBC_fdiv:
          case JBC_frem:
          case JBC_ishl:
          case JBC_ishr:
          case JBC_iushr:
          case JBC_lshl:      // long shifts that int shift value
          case JBC_lshr:
          case JBC_lushr:
          case JBC_iand:
          case JBC_ior:
          case JBC_ixor:
          case JBC_l2i:
          case JBC_l2f:
          case JBC_d2i:
          case JBC_d2f:
          case JBC_fcmpl:
          case JBC_fcmpg: {
            currBBStkTop--;
            bcodes.skipInstruction();
            break;
          }

          case JBC_irem:
          case JBC_idiv: {
            currBBStkTop = currBBStkTop - 2; // record map after 2 integers popped off stack
            if (!inJSRSub) {
              referenceMaps.recordStkMap(biStart, currBBMap, currBBStkTop, blockSeen[currBBNum]);
            } else {
              referenceMaps.recordJSRSubroutineMap(biStart,
                                                   currBBMap,
                                                   currBBStkTop,
                                                   currPendingRET.returnAddressLocation,
                                                   blockSeen[currBBNum]);
            }
            currBBStkTop++;
            break;
          }
          case JBC_ladd:
          case JBC_dadd:
          case JBC_lsub:
          case JBC_dsub:
          case JBC_lmul:
          case JBC_dmul:
          case JBC_ddiv:
          case JBC_drem:
          case JBC_land:
          case JBC_lor:
          case JBC_lxor:
          case JBC_pop2: {
            currBBStkTop = currBBStkTop - 2;
            break;
          }
          case JBC_lrem:
          case JBC_ldiv: {
            currBBStkTop = currBBStkTop - 4; // record map after 2 longs popped off stack
            if (!inJSRSub) {
              referenceMaps.recordStkMap(biStart, currBBMap, currBBStkTop, blockSeen[currBBNum]);
            } else {
              referenceMaps.recordJSRSubroutineMap(biStart,
                                                   currBBMap,
                                                   currBBStkTop,
                                                   currPendingRET.returnAddressLocation,
                                                   blockSeen[currBBNum]);
            }
            currBBStkTop = currBBStkTop + 2;
            break;
          }
          case JBC_ineg:
          case JBC_lneg:
          case JBC_fneg:
          case JBC_dneg:
          case JBC_iinc:
          case JBC_i2f:
          case JBC_l2d:
          case JBC_f2i:
          case JBC_d2l:
          case JBC_int2byte:
          case JBC_int2char:
          case JBC_int2short: {
            bcodes.skipInstruction();
            break;
          }

          case JBC_lcmp:
          case JBC_dcmpl:
          case JBC_dcmpg: {
            currBBStkTop = currBBStkTop - 3;
            break;
          }

          case JBC_ifeq:
          case JBC_ifne:
          case JBC_iflt:
          case JBC_ifge:
          case JBC_ifgt:
          case JBC_ifle: {
            int offset = bcodes.getBranchOffset();
            if (offset <= 0) {
              // potential backward branch-generate reference map
              // Register the reference map

              if (!inJSRSub) {
                referenceMaps.recordStkMap(biStart, currBBMap, currBBStkTop, blockSeen[currBBNum]);
              } else {
                // in a jsr subroutine
                referenceMaps.recordJSRSubroutineMap(biStart,
                                                     currBBMap,
                                                     currBBStkTop,
                                                     currPendingRET.returnAddressLocation,
                                                     blockSeen[currBBNum]);
              }
            }

            // process the basic block logic
            currBBStkTop--;
            if (offset <= 0) {
              short fallThruBBNum = byteToBlockMap[biStart + 3];
              workStk =
                  processBranchBB(fallThruBBNum,
                                  currBBStkTop,
                                  currBBMap,
                                  currBBStkEmpty,
                                  inJSRSub,
                                  bbMaps,
                                  blockStkTop,
                                  currPendingRET,
                                  bbPendingRETs,
                                  workStk);
              processNextBlock = false;
            }
            brBBNum = byteToBlockMap[biStart + offset];
            workStk =
                processBranchBB(brBBNum,
                                currBBStkTop,
                                currBBMap,
                                currBBStkEmpty,
                                inJSRSub,
                                bbMaps,
                                blockStkTop,
                                currPendingRET,
                                bbPendingRETs,
                                workStk);
            break;
          }

          case JBC_if_icmpeq:
          case JBC_if_icmpne:
          case JBC_if_icmplt:
          case JBC_if_icmpge:
          case JBC_if_icmpgt:
          case JBC_if_icmple:
          case JBC_if_acmpeq:
          case JBC_if_acmpne: {
            int offset = bcodes.getBranchOffset();
            if (offset <= 0) {
              // possible backward branch-generate reference map
              // Register the reference map

              if (!inJSRSub) {
                referenceMaps.recordStkMap(biStart, currBBMap, currBBStkTop, blockSeen[currBBNum]);
              } else {
                // in a jsr subroutine
                referenceMaps.recordJSRSubroutineMap(biStart,
                                                     currBBMap,
                                                     currBBStkTop,
                                                     currPendingRET.returnAddressLocation,
                                                     blockSeen[currBBNum]);
              }
            }

            //process the basic blocks
            currBBStkTop = currBBStkTop - 2;
            if (offset <= 0) {
              short fallThruBBNum = byteToBlockMap[biStart + 3];
              workStk =
                  processBranchBB(fallThruBBNum,
                                  currBBStkTop,
                                  currBBMap,
                                  currBBStkEmpty,
                                  inJSRSub,
                                  bbMaps,
                                  blockStkTop,
                                  currPendingRET,
                                  bbPendingRETs,
                                  workStk);
              processNextBlock = false;
            }
            brBBNum = byteToBlockMap[biStart + offset];
            workStk =
                processBranchBB(brBBNum,
                                currBBStkTop,
                                currBBMap,
                                currBBStkEmpty,
                                inJSRSub,
                                bbMaps,
                                blockStkTop,
                                currPendingRET,
                                bbPendingRETs,
                                workStk);
            break;
          }

          case JBC_ifnull:
          case JBC_ifnonnull: {
            int offset = bcodes.getBranchOffset();
            if (offset <= 0) {
              // possible backward branch-generate reference map
              // Register the reference map

              if (!inJSRSub) {
                referenceMaps.recordStkMap(biStart, currBBMap, currBBStkTop, blockSeen[currBBNum]);
              } else {
                // in a jsr subroutine
                referenceMaps.recordJSRSubroutineMap(biStart,
                                                     currBBMap,
                                                     currBBStkTop,
                                                     currPendingRET.returnAddressLocation,
                                                     blockSeen[currBBNum]);
              }
            }

            //process the basic block logic
            currBBStkTop--;
            if (offset <= 0) {
              short fallThruBBNum = byteToBlockMap[biStart + 3];
              workStk =
                  processBranchBB(fallThruBBNum,
                                  currBBStkTop,
                                  currBBMap,
                                  currBBStkEmpty,
                                  inJSRSub,
                                  bbMaps,
                                  blockStkTop,
                                  currPendingRET,
                                  bbPendingRETs,
                                  workStk);
              processNextBlock = false;
            }
            brBBNum = byteToBlockMap[biStart + offset];
            workStk =
                processBranchBB(brBBNum,
                                currBBStkTop,
                                currBBMap,
                                currBBStkEmpty,
                                inJSRSub,
                                bbMaps,
                                blockStkTop,
                                currPendingRET,
                                bbPendingRETs,
                                workStk);
            break;
          }

          case JBC_goto: {
            int offset = bcodes.getBranchOffset();
            if (offset <= 0) {
              // backward branch-generate reference map
              // Register the reference map
              if (!inJSRSub) {
                referenceMaps.recordStkMap(biStart, currBBMap, currBBStkTop, blockSeen[currBBNum]);
              } else {
                // in a jsr subroutine
                referenceMaps.recordJSRSubroutineMap(biStart,
                                                     currBBMap,
                                                     currBBStkTop,
                                                     currPendingRET.returnAddressLocation,
                                                     blockSeen[currBBNum]);
              }
            }

            //  process the basic block logic
            brBBNum = byteToBlockMap[biStart + offset];
            workStk =
                processBranchBB(brBBNum,
                                currBBStkTop,
                                currBBMap,
                                currBBStkEmpty,
                                inJSRSub,
                                bbMaps,
                                blockStkTop,
                                currPendingRET,
                                bbPendingRETs,
                                workStk);
            processNextBlock = false;
            break;
          }
          case JBC_goto_w: {
            int offset = bcodes.getWideBranchOffset();
            if (offset <= 0) {
              // backward branch-generate reference map
              // Register the reference map

              if (!inJSRSub) {
                referenceMaps.recordStkMap(biStart, currBBMap, currBBStkTop, blockSeen[currBBNum]);
              } else {
                // in a jsr subroutine
                referenceMaps.recordJSRSubroutineMap(biStart,
                                                     currBBMap,
                                                     currBBStkTop,
                                                     currPendingRET.returnAddressLocation,
                                                     blockSeen[currBBNum]);
              }
            }

            //process basic block structures
            brBBNum = byteToBlockMap[biStart + offset];
            workStk =
                processBranchBB(brBBNum,
                                currBBStkTop,
                                currBBMap,
                                currBBStkEmpty,
                                inJSRSub,
                                bbMaps,
                                blockStkTop,
                                currPendingRET,
                                bbPendingRETs,
                                workStk);
            processNextBlock = false;
            break;
          }
          case JBC_tableswitch: {
            currBBStkTop--;
            bcodes.alignSwitch();
            // get default offset and process branch to default branch point
            int def = bcodes.getDefaultSwitchOffset();
            workStk =
                processBranchBB(byteToBlockMap[biStart + def],
                                currBBStkTop,
                                currBBMap,
                                currBBStkEmpty,
                                inJSRSub,
                                bbMaps,
                                blockStkTop,
                                currPendingRET,
                                bbPendingRETs,
                                workStk);

            int low = bcodes.getLowSwitchValue();
            int high = bcodes.getHighSwitchValue();
            int n = high - low + 1;
            // generate labels for offsets
            for (int k = 0; k < n; k++) {
              int offset = bcodes.getTableSwitchOffset(k);
              workStk =
                  processBranchBB(byteToBlockMap[biStart + offset],
                                  currBBStkTop,
                                  currBBMap,
                                  currBBStkEmpty,
                                  inJSRSub,
                                  bbMaps,
                                  blockStkTop,
                                  currPendingRET,
                                  bbPendingRETs,
                                  workStk);
            }
            bcodes.skipTableSwitchOffsets(n);
            processNextBlock = false;
            break;
          }
          case JBC_lookupswitch: {
            currBBStkTop--;
            bcodes.alignSwitch();
            // get default offset and process branch to default branch point
            int def = bcodes.getDefaultSwitchOffset();
            workStk =
                processBranchBB(byteToBlockMap[biStart + def],
                                currBBStkTop,
                                currBBMap,
                                currBBStkEmpty,
                                inJSRSub,
                                bbMaps,
                                blockStkTop,
                                currPendingRET,
                                bbPendingRETs,
                                workStk);

            int npairs = bcodes.getSwitchLength();

            // generate label for each offset in table
            for (int k = 0; k < npairs; k++) {
              int offset = bcodes.getLookupSwitchOffset(k);
              workStk =
                  processBranchBB(byteToBlockMap[biStart + offset],
                                  currBBStkTop,
                                  currBBMap,
                                  currBBStkEmpty,
                                  inJSRSub,
                                  bbMaps,
                                  blockStkTop,
                                  currPendingRET,
                                  bbPendingRETs,
                                  workStk);
            }
            bcodes.skipLookupSwitchPairs(npairs);
            processNextBlock = false;
            break;
          }

          case JBC_jsr: {
            processNextBlock = false;
            int offset = bcodes.getBranchOffset();
            if (!inJSRSub) {
              referenceMaps.recordStkMap(biStart, currBBMap, currBBStkEmpty, blockSeen[currBBNum]);
            } else {
              referenceMaps.recordJSRSubroutineMap(biStart,
                                                   currBBMap,
                                                   currBBStkEmpty,
                                                   currPendingRET.returnAddressLocation,
                                                   blockSeen[currBBNum]);
            }
            currBBStkTop++;
            currBBMap[currBBStkTop] = RETURN_ADDRESS;
            workStk =
                processJSR(byteToBlockMap[biStart],
                           biStart + offset,
                           byteToBlockMap[biStart + offset],
                           byteToBlockMap[biStart + 3],
                           bbMaps,
                           currBBStkTop,
                           currBBMap,
                           currBBStkEmpty,
                           blockStkTop,
                           bbPendingRETs,
                           currPendingRET,
                           JSRSubs,
                           workStk);
            break;
          }
          case JBC_jsr_w: {
            processNextBlock = false;
            int offset = bcodes.getWideBranchOffset();
            if (!inJSRSub) {
              referenceMaps.recordStkMap(biStart, currBBMap, currBBStkEmpty, blockSeen[currBBNum]);
            } else {
              referenceMaps.recordJSRSubroutineMap(biStart,
                                                   currBBMap,
                                                   currBBStkEmpty,
                                                   currPendingRET.returnAddressLocation,
                                                   blockSeen[currBBNum]);
            }
            currBBStkTop++;
            currBBMap[currBBStkTop] = RETURN_ADDRESS;
            workStk =
                processJSR(byteToBlockMap[biStart],
                           biStart + offset,
                           byteToBlockMap[biStart + offset],
                           byteToBlockMap[biStart + 5],
                           bbMaps,
                           currBBStkTop,
                           currBBMap,
                           currBBStkEmpty,
                           blockStkTop,
                           bbPendingRETs,
                           currPendingRET,
                           JSRSubs,
                           workStk);
            break;
          }
          case JBC_ret: {
            int index = bcodes.getLocalNumber();

            // Can not be used again as a return addr.
            //
            currBBMap[index] = SET_TO_NONREFERENCE;
            processNextBlock = false;
            int subStart = currPendingRET.JSRSubStartByteIndex;
            int k;
            for (k = 0; k < JSRSubNext; k++) {
              if (JSRSubs[k].subroutineByteCodeStart == subStart) {
                JSRSubs[k].newEndMaps(currBBMap, currBBStkTop);
                break;
              }
            }

            boolean JSRisinJSRSub = bbPendingRETs[currPendingRET.JSRBBNum] != null;
            workStk =
                computeJSRNextMaps(currPendingRET.JSRNextBBNum,
                                   currBBMap.length,
                                   k,
                                   JSRisinJSRSub,
                                   bbMaps,
                                   blockStkTop,
                                   JSRSubs,
                                   currBBStkEmpty,
                                   workStk);
            if (JSRisinJSRSub && bbPendingRETs[currPendingRET.JSRNextBBNum] == null) {
              bbPendingRETs[currPendingRET.JSRNextBBNum] =
                  new PendingRETInfo(bbPendingRETs[currPendingRET.JSRBBNum]);
            }
            break;
          }
          case JBC_invokevirtual:
          case JBC_invokespecial: {
            MethodReference target = bcodes.getMethodReference();
            currBBStkTop =
                processInvoke(target,
                              biStart,
                              currBBStkTop,
                              currBBMap,
                              false,
                              inJSRSub,
                              referenceMaps,
                              currPendingRET,
                              blockSeen[currBBNum],
                              currBBStkEmpty);
            break;
          }
          case JBC_invokeinterface: {
            MethodReference target = bcodes.getMethodReference();
            bcodes.alignInvokeInterface();
            currBBStkTop =
                processInvoke(target,
                              biStart,
                              currBBStkTop,
                              currBBMap,
                              false,
                              inJSRSub,
                              referenceMaps,
                              currPendingRET,
                              blockSeen[currBBNum],
                              currBBStkEmpty);
            break;
          }
          case JBC_invokestatic: {
            MethodReference target = bcodes.getMethodReference();
            currBBStkTop =
                processInvoke(target,
                              biStart,
                              currBBStkTop,
                              currBBMap,
                              true,
                              inJSRSub,
                              referenceMaps,
                              currPendingRET,
                              blockSeen[currBBNum],
                              currBBStkEmpty);
            break;
          }

          case JBC_ireturn:
          case JBC_lreturn:
          case JBC_freturn:
          case JBC_dreturn:
          case JBC_areturn:
          case JBC_return: {
            if (VM.UseEpilogueYieldPoints || method.isSynchronized()) {
              referenceMaps.recordStkMap(biStart, currBBMap, currBBStkTop, blockSeen[currBBNum]);
            }
            processNextBlock = false;
            break;
          }

          case JBC_getstatic: {
            // Register the reference map (could cause dynamic linking)
            if (!inJSRSub) {
              referenceMaps.recordStkMap(biStart, currBBMap, currBBStkTop, blockSeen[currBBNum]);
            } else {
              referenceMaps.recordJSRSubroutineMap(biStart,
                                                   currBBMap,
                                                   currBBStkTop,
                                                   currPendingRET.returnAddressLocation,
                                                   blockSeen[currBBNum]);
            }

            TypeReference fieldType = bcodes.getFieldReference().getFieldContentsType();
            currBBMap[++currBBStkTop] = fieldType.isPrimitiveType() ? NON_REFERENCE : REFERENCE;
            if (fieldType.getStackWords() == 2) {
              currBBMap[++currBBStkTop] = NON_REFERENCE;
            }
            break;
          }
          case JBC_putstatic: {
            // Register the reference map (could cause dynamic linking)
            if (!inJSRSub) {
              referenceMaps.recordStkMap(biStart, currBBMap, currBBStkTop, blockSeen[currBBNum]);
            } else {
              referenceMaps.recordJSRSubroutineMap(biStart,
                                                   currBBMap,
                                                   currBBStkTop,
                                                   currPendingRET.returnAddressLocation,
                                                   blockSeen[currBBNum]);
            }
            TypeReference fieldType = bcodes.getFieldReference().getFieldContentsType();
            currBBStkTop--;
            if (fieldType.getStackWords() == 2) {
              currBBStkTop--;
            }
            break;
          }
          case JBC_getfield: {
            TypeReference fieldType = bcodes.getFieldReference().getFieldContentsType();
            // Register the reference map (could cause dynamic linking..if so there will be a NPE, but the linking happens first.)
            if (!inJSRSub) {
              referenceMaps.recordStkMap(biStart, currBBMap, currBBStkTop, blockSeen[currBBNum]);
            } else {
              referenceMaps.recordJSRSubroutineMap(biStart,
                                                   currBBMap,
                                                   currBBStkTop,
                                                   currPendingRET.returnAddressLocation,
                                                   blockSeen[currBBNum]);
            }
            currBBStkTop--;    // pop object pointer
            currBBMap[++currBBStkTop] = fieldType.isPrimitiveType() ? NON_REFERENCE : REFERENCE;
            if (fieldType.getStackWords() == 2) {
              currBBMap[++currBBStkTop] = NON_REFERENCE;
            }
            break;
          }
          case JBC_putfield: {
            TypeReference fieldType = bcodes.getFieldReference().getFieldContentsType();
            // Register the reference map with the values still on the stack
            //  note: putfield could result in a call to the classloader
            if (!inJSRSub) {
              referenceMaps.recordStkMap(biStart, currBBMap, currBBStkTop, blockSeen[currBBNum]);
            } else {
              referenceMaps.recordJSRSubroutineMap(biStart,
                                                   currBBMap,
                                                   currBBStkTop,
                                                   currPendingRET.returnAddressLocation,
                                                   blockSeen[currBBNum]);
            }
            currBBStkTop -= 2;  // remove objectref and one value
            if (fieldType.getStackWords() == 2) {
              currBBStkTop--;
            }
            break;
          }
          case JBC_checkcast: {
            if (!inJSRSub) {
              referenceMaps.recordStkMap(biStart, currBBMap, currBBStkTop, blockSeen[currBBNum]);
            } else {
              referenceMaps.recordJSRSubroutineMap(biStart,
                                                   currBBMap,
                                                   currBBStkTop,
                                                   currPendingRET.returnAddressLocation,
                                                   blockSeen[currBBNum]);
            }
            bcodes.skipInstruction();
            break;
          }
          case JBC_instanceof: {
            if (!inJSRSub) {
              referenceMaps.recordStkMap(biStart, currBBMap, currBBStkTop, blockSeen[currBBNum]);
            } else {
              referenceMaps.recordJSRSubroutineMap(biStart,
                                                   currBBMap,
                                                   currBBStkTop,
                                                   currPendingRET.returnAddressLocation,
                                                   blockSeen[currBBNum]);
            }
            currBBMap[currBBStkTop] = NON_REFERENCE;
            bcodes.skipInstruction();
            break;
          }
          case JBC_new: {
            if (!inJSRSub) {
              referenceMaps.recordStkMap(biStart, currBBMap, currBBStkTop, blockSeen[currBBNum]);
            } else {
              referenceMaps.recordJSRSubroutineMap(biStart,
                                                   currBBMap,
                                                   currBBStkTop,
                                                   currPendingRET.returnAddressLocation,
                                                   blockSeen[currBBNum]);
            }
            currBBStkTop++;
            currBBMap[currBBStkTop] = REFERENCE;
            bcodes.skipInstruction();
            break;
          }

          // For the <x>aload instructions the map is needed in case gc occurs
          // while the array index check is taking place. Stack has not been
          // altered yet.
          case JBC_iaload:
          case JBC_faload:
          case JBC_baload:
          case JBC_caload:
          case JBC_saload: {
            if (!inJSRSub) {
              referenceMaps.recordStkMap(biStart, currBBMap, currBBStkTop, blockSeen[currBBNum]);
            } else {
              referenceMaps.recordJSRSubroutineMap(biStart,
                                                   currBBMap,
                                                   currBBStkTop,
                                                   currPendingRET.returnAddressLocation,
                                                   blockSeen[currBBNum]);
            }
            currBBStkTop--;
            currBBMap[currBBStkTop] = NON_REFERENCE;
            break;
          }
          case JBC_laload:
          case JBC_daload: {
            if (!inJSRSub) {
              referenceMaps.recordStkMap(biStart, currBBMap, currBBStkTop, blockSeen[currBBNum]);
            } else {
              referenceMaps.recordJSRSubroutineMap(biStart,
                                                   currBBMap,
                                                   currBBStkTop,
                                                   currPendingRET.returnAddressLocation,
                                                   blockSeen[currBBNum]);
            }
            currBBMap[currBBStkTop - 1] = NON_REFERENCE;
            break;
          }

          case JBC_aaload: {
            if (!inJSRSub) {
              referenceMaps.recordStkMap(biStart, currBBMap, currBBStkTop, blockSeen[currBBNum]);
            } else {
              referenceMaps.recordJSRSubroutineMap(biStart,
                                                   currBBMap,
                                                   currBBStkTop,
                                                   currPendingRET.returnAddressLocation,
                                                   blockSeen[currBBNum]);
            }
            currBBStkTop--;
            break;
          }

          // For the <x>astore instructions the map recorded is in case gc occurs
          // during the array index bounds check or the arraystore check (for aastore).
          // Stack has not been modified at this point.
          case JBC_iastore:
          case JBC_fastore:
          case JBC_aastore:
          case JBC_bastore:
          case JBC_castore:
          case JBC_sastore: {
            if (!inJSRSub) {
              referenceMaps.recordStkMap(biStart, currBBMap, currBBStkTop, blockSeen[currBBNum]);
            } else {
              referenceMaps.recordJSRSubroutineMap(biStart,
                                                   currBBMap,
                                                   currBBStkTop,
                                                   currPendingRET.returnAddressLocation,
                                                   blockSeen[currBBNum]);
            }
            currBBStkTop = currBBStkTop - 3;
            break;
          }
          case JBC_lastore:
          case JBC_dastore: {
            if (!inJSRSub) {
              referenceMaps.recordStkMap(biStart, currBBMap, currBBStkTop, blockSeen[currBBNum]);
            } else {
              referenceMaps.recordJSRSubroutineMap(biStart,
                                                   currBBMap,
                                                   currBBStkTop,
                                                   currPendingRET.returnAddressLocation,
                                                   blockSeen[currBBNum]);
            }
            currBBStkTop = currBBStkTop - 4;
            break;
          }

          case JBC_newarray:
          case JBC_anewarray: {
            if (!inJSRSub) {
              referenceMaps.recordStkMap(biStart, currBBMap, currBBStkTop, blockSeen[currBBNum]);
            } else {
              referenceMaps.recordJSRSubroutineMap(biStart,
                                                   currBBMap,
                                                   currBBStkTop,
                                                   currPendingRET.returnAddressLocation,
                                                   blockSeen[currBBNum]);
            }
            currBBMap[currBBStkTop] = REFERENCE;
            bcodes.skipInstruction();
            break;
          }

          case JBC_multianewarray: {
            bcodes.getTypeReference();
            int dim = bcodes.getArrayDimension();
            if (!inJSRSub) {
              referenceMaps.recordStkMap(biStart, currBBMap, currBBStkTop, blockSeen[currBBNum]);
            } else {
              referenceMaps.recordJSRSubroutineMap(biStart,
                                                   currBBMap,
                                                   currBBStkTop,
                                                   currPendingRET.returnAddressLocation,
                                                   blockSeen[currBBNum]);
            }
            currBBStkTop = currBBStkTop - dim + 1;
            currBBMap[currBBStkTop] = REFERENCE;
            break;
          }
          case JBC_arraylength: {
            currBBMap[currBBStkTop] = NON_REFERENCE;
            break;
          }
          case JBC_athrow: {
            if (!inJSRSub) {
              referenceMaps.recordStkMap(biStart, currBBMap, currBBStkTop, blockSeen[currBBNum]);
            } else {
              referenceMaps.recordJSRSubroutineMap(biStart,
                                                   currBBMap,
                                                   currBBStkTop,
                                                   currPendingRET.returnAddressLocation,
                                                   blockSeen[currBBNum]);
            }
            currBBStkTop = currBBStkEmpty + 1;
            currBBMap[currBBStkTop] = REFERENCE;
            processNextBlock = false;
            break;
          }
          case JBC_monitorenter:
          case JBC_monitorexit: {
            currBBStkTop--;
            if (!inJSRSub) {
              referenceMaps.recordStkMap(biStart, currBBMap, currBBStkTop, blockSeen[currBBNum]);
            } else {
              referenceMaps.recordJSRSubroutineMap(biStart,
                                                   currBBMap,
                                                   currBBStkTop,
                                                   currPendingRET.returnAddressLocation,
                                                   blockSeen[currBBNum]);
            }
            break;
          }

          case JBC_wide: {
            int widecode = bcodes.getWideOpcode();
            int index = bcodes.getWideLocalNumber();
            switch (widecode) {
              case JBC_iload:
              case JBC_fload: {
                currBBStkTop++;
                currBBMap[currBBStkTop] = NON_REFERENCE;
                break;
              }

              case JBC_lload:
              case JBC_dload: {
                currBBStkTop++;
                currBBMap[currBBStkTop] = NON_REFERENCE;
                currBBStkTop++;
                currBBMap[currBBStkTop] = NON_REFERENCE;
                break;
              }

              case JBC_aload: {
                currBBStkTop++;
                currBBMap[currBBStkTop] = currBBMap[index];
                break;
              }

              case JBC_istore: {
                if (!inJSRSub) {
                  currBBMap[index] = NON_REFERENCE;
                } else {
                  currBBMap[index] = SET_TO_NONREFERENCE;
                }
                currBBStkTop--;
                localTypes[index] |= INT_TYPE;
                break;
              }

              case JBC_fstore: {
                if (!inJSRSub) {
                  currBBMap[index] = NON_REFERENCE;
                } else {
                  currBBMap[index] = SET_TO_NONREFERENCE;
                }
                currBBStkTop--;
                localTypes[index] |= FLOAT_TYPE;
                break;
              }

              case JBC_lstore: {
                if (!inJSRSub) {
                  currBBMap[index] = NON_REFERENCE;
                  currBBMap[index + 1] = NON_REFERENCE;
                } else {
                  currBBMap[index] = SET_TO_NONREFERENCE;
                  currBBMap[index + 1] = SET_TO_NONREFERENCE;
                }
                currBBStkTop = currBBStkTop - 2;
                localTypes[index] |= LONG_TYPE;
                break;
              }

              case JBC_dstore: {
                if (!inJSRSub) {
                  currBBMap[index] = NON_REFERENCE;
                  currBBMap[index + 1] = NON_REFERENCE;
                } else {
                  currBBMap[index] = SET_TO_NONREFERENCE;
                  currBBMap[index + 1] = SET_TO_NONREFERENCE;
                }
                currBBStkTop = currBBStkTop - 2;
                localTypes[index] |= DOUBLE_TYPE;
                break;
              }

              case JBC_astore: {
                currBBMap[index] = currBBMap[currBBStkTop];
                currBBStkTop--;
                localTypes[index] |= ADDRESS_TYPE;
                break;
              }

              case JBC_iinc: {
                bcodes.getWideIncrement();
                break;
              }
              case JBC_ret: {
                // Can not be used again as a return addr.
                //
                currBBMap[index] = SET_TO_NONREFERENCE;
                processNextBlock = false;
                int subStart = currPendingRET.JSRSubStartByteIndex;
                int k;
                for (k = 0; k < JSRSubNext; k++) {
                  if (JSRSubs[k].subroutineByteCodeStart == subStart) {
                    JSRSubs[k].newEndMaps(currBBMap, currBBStkTop);
                    break;
                  }
                }

                boolean JSRisinJSRSub = bbPendingRETs[currPendingRET.JSRBBNum] != null;
                workStk =
                    computeJSRNextMaps(currPendingRET.JSRNextBBNum,
                                       currBBMap.length,
                                       k,
                                       JSRisinJSRSub,
                                       bbMaps,
                                       blockStkTop,
                                       JSRSubs,
                                       currBBStkEmpty,
                                       workStk);
                if (JSRisinJSRSub && bbPendingRETs[currPendingRET.JSRNextBBNum] == null) {
                  bbPendingRETs[currPendingRET.JSRNextBBNum] =
                      new PendingRETInfo(bbPendingRETs[currPendingRET.JSRBBNum]);
                }
                break;
              }
              default: // switch on widecode
                if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
            }
            break;
          }  // case JBC_wide:

          default: {
            VM.sysFail("Unknown opcode:" + opcode);
          }

        }  // end switch (opcode)

      }  // for start to end

      blockSeen[currBBNum] = true;

      if (processNextBlock) {
        short fallThruBBNum = byteToBlockMap[bcodes.index()];
        workStk =
            processBranchBB(fallThruBBNum,
                            currBBStkTop,
                            currBBMap,
                            currBBStkEmpty,
                            inJSRSub,
                            bbMaps,
                            blockStkTop,
                            currPendingRET,
                            bbPendingRETs,
                            workStk);

      }

      // if the work stack is empty, we must have processed the whole program
      // we can now process the try handlers if there are any.
      // If a handler doesn't have a starting map already, then the associated try
      // has not been processed yet. The try and the handler must be in another
      // handler, so that handler must be processed first.
      // If one handler is in the try block associated with a second handler, then
      // the second handler must not be processed until the first handler has been.
      //
      if ((workStkTop == -1) && !handlersAllDone) {
        int i;
        for (i = 0; i < tryHandlerLength; i++) {
          // already processed this handler, or, haven't seen the
          // associated try block yet so no starting map is available,
          // the try block must be in one of the other handlers
          if (!handlerProcessed[i] && bbMaps[byteToBlockMap[tryHandlerPC[i]]] != null) break;
        }
        if (i == tryHandlerLength) {
          handlersAllDone = true;
        } else {
          int considerIndex = i;

          while (i != tryHandlerLength) {
            int tryStart = tryStartPC[considerIndex];
            int tryEnd = tryEndPC[considerIndex];

            for (i = 0; i < tryHandlerLength; i++) {
              // If the handler handles itself, then make the wild assumption
              // that the local variables will be the same......is this reasonable??
              // This is a patch to deal with defect 3046.
              // I'm not entirely convinced this is right, but don't know what else we can do. --dave
              if (i == considerIndex) continue;

              // For every handler that has not yet been processed,
              // but already has a known starting map,
              // make sure it is not in the try block part of the handler
              // we are considering working on.
              if (!handlerProcessed[i] &&
                  tryStart <= tryHandlerPC[i] &&
                  tryHandlerPC[i] < tryEnd &&
                  bbMaps[byteToBlockMap[tryHandlerPC[i]]] != null) {
                break;
              }
            }

            if (i != tryHandlerLength) {
              considerIndex = i;
            }
          }

          short blockNum = byteToBlockMap[tryHandlerPC[considerIndex]];
          handlerProcessed[considerIndex] = true;
          workStk = addToWorkStk(blockNum, workStk);
        }
      }

    }  // while workStk not empty

    // Indicate that any temporaries can be freed
    referenceMaps.recordingComplete();

  }

  // -------------------- Private Instance Methods --------------------

  private short[] addToWorkStk(short blockNum, short[] workStk) {
    workStkTop++;
    if (workStkTop >= workStk.length) {
      short[] biggerQ = new short[workStk.length + 20];
      for (int i = 0; i < workStk.length; i++) {
        biggerQ[i] = workStk[i];
      }
      workStk = biggerQ;
      biggerQ = null;
    }
    workStk[workStkTop] = blockNum;
    return workStk;
  }

  private short[] addUniqueToWorkStk(short blockNum, short[] workStk) {
    if ((workStkTop + 1) >= workStk.length) {
      short[] biggerQ = new short[workStk.length + 20];
      boolean matchFound = false;
      for (int i = 0; i < workStk.length; i++) {
        biggerQ[i] = workStk[i];
        matchFound = (workStk[i] == blockNum);
      }
      workStk = biggerQ;
      biggerQ = null;
      if (matchFound) return workStk;
    } else {
      for (int i = 0; i <= workStkTop; i++) {
        if (workStk[i] == blockNum) {
          return workStk;
        }
      }
    }
    workStkTop++;
    workStk[workStkTop] = blockNum;
    return workStk;
  }

  private short[] processBranchBB(short brBBNum, int currBBStkTop, byte[] currBBMap, int currBBStkEmpty,
                                  boolean inJSRSub, byte[][] bbMaps, int[] blockStkTop,
                                  PendingRETInfo currPendingRET, PendingRETInfo[] bbPendingRETs,
                                  short[] workStk) {

    short[] newworkStk = workStk;

    // If the destination block doesn't already have a map, then use this
    // map as its map and add it to the work stack

    if (bbMaps[brBBNum] == null) {
      bbMaps[brBBNum] = new byte[currBBMap.length];
      for (int i = 0; i <= currBBStkTop; i++) {
        bbMaps[brBBNum][i] = currBBMap[i];
      }
      blockStkTop[brBBNum] = currBBStkTop;
      newworkStk = addToWorkStk(brBBNum, workStk);
      if (inJSRSub) {
        bbPendingRETs[brBBNum] = new PendingRETInfo(currPendingRET);
      }

    } else {
      // If the destination block already has a map, then check if there are any
      // new NONReference values. Note that a new Reference value, will not change an
      // existing NONReference value to Reference, as there must be a valid path where
      // the variable is not a reference (and not "null" - which is treated as a
      // reference) so the variable is unusable.
      //
      int mismatchAt = -1;
      byte[] blockMap = bbMaps[brBBNum];
      for (int i = 0; i <= currBBStkEmpty; i++) {
        if ((currBBMap[i] != blockMap[i]) && (blockMap[i] != NON_REFERENCE)) {
          mismatchAt = i;
          break;
        }
      }
      if (mismatchAt == -1) {
        return newworkStk;  // no further work to be done
      } else {
        newworkStk = addUniqueToWorkStk(brBBNum, workStk);
        for (int i = mismatchAt; i <= currBBStkEmpty; i++) {
          if (!inJSRSub) {
            blockMap[i] = (blockMap[i] == currBBMap[i]) ? blockMap[i] : NON_REFERENCE;
          } else {
            blockMap[i] = (blockMap[i] == currBBMap[i]) ? blockMap[i] : SET_TO_NONREFERENCE;
          }
        }
      }
    }
    return newworkStk;
  }

  private int processInvoke(MethodReference target, int byteindex, int currBBStkTop, byte[] currBBMap,
                            boolean isStatic, boolean inJSRSub, ReferenceMaps referenceMaps,
                            PendingRETInfo currPendingRET, boolean blockSeen, int currBBStkEmpty) {
    boolean skipRecordingReferenceMap = false;
    boolean popParams = true;

    if (target.getType().isMagicType()) {
      boolean producesCall = BaselineCompilerImpl.checkForActualCall(target);
      if (producesCall) {
        // register a map, but do NOT include any of the parameters to the call.
        // Chances are what appear to be parameters are not parameters to
        // the routine that is actually called.
        // In any case, the callee routine will map its parameters
        // and we don't have to double map because we are positive that this can't be
        // a dynamically linked call site.
        for (TypeReference parameterType : target.getParameterTypes()) {
          currBBStkTop -= parameterType.getStackWords();
        }
        if (!isStatic) currBBStkTop--; // pop implicit "this" object reference
        popParams = false;
      } else {
        skipRecordingReferenceMap = true;
      }
    }

    if (!skipRecordingReferenceMap) {
      // Register the reference map, including the arguments on the stack for this call
      // (unless it is a magic call whose params we have popped above).
      if (!inJSRSub) {
        referenceMaps.recordStkMap(byteindex, currBBMap, currBBStkTop, blockSeen);
      } else {
        referenceMaps.recordJSRSubroutineMap(byteindex,
                                             currBBMap,
                                             currBBStkTop,
                                             currPendingRET.returnAddressLocation,
                                             blockSeen);
      }
    }

    if (popParams) {
      TypeReference[] parameterTypes = target.getParameterTypes();
      int pTypesLength = parameterTypes.length;

      // Pop the arguments for this call off the stack;
      for (int i = 0; i < pTypesLength; i++) {
        currBBStkTop -= parameterTypes[i].getStackWords();
      }

      if (!isStatic) {
        currBBStkTop--; // pop implicit "this" object reference
      }
    }

    // Add the return value to the stack
    TypeReference returnType = target.getReturnType();
    if (!returnType.isVoidType()) {
      // a non-void return value
      currBBMap[++currBBStkTop] = returnType.isReferenceType() ? REFERENCE : NON_REFERENCE;
      if (returnType.getStackWords() == 2) {
        currBBMap[++currBBStkTop] = NON_REFERENCE;
      }
    }

    // Return updated stack top
    return currBBStkTop;
  }

  private short[] processJSR(int JSRBBNum, int JSRSubStartIndex, short brBBNum, short nextBBNum, byte[][] bbMaps,
                             int currBBStkTop, byte[] currBBMap, int currBBStkEmpty, int[] blockStkTop,
                             PendingRETInfo[] bbPendingRETs, PendingRETInfo currPendingRET,
                             JSRSubroutineInfo[] JSRSubs, short[] workStk) {
    short[] newworkStk = workStk;

    // If the destination block doesn't already have a map, then use this map to build
    // the stack portion of the reference map and add the block to the work stack.
    // The locals maps should be started with all zeros.

    if (bbMaps[brBBNum] == null) {
      bbMaps[brBBNum] = new byte[currBBMap.length];
      for (int i = currBBStkEmpty + 1; i <= currBBStkTop; i++) {
        bbMaps[brBBNum][i] = currBBMap[i];
      }
      blockStkTop[brBBNum] = currBBStkTop;
      newworkStk = addToWorkStk(brBBNum, workStk);

      bbPendingRETs[brBBNum] = new PendingRETInfo(JSRSubStartIndex, JSRBBNum, currBBStkTop, nextBBNum);
      JSRSubs[JSRSubNext++] = new JSRSubroutineInfo(JSRSubStartIndex, currBBMap, currBBStkEmpty);
    } else {
      // If the JSR subroutine block already has a map, then locate the ending map
      // and use that to determine the map of the next block. No need to reprocess
      // the JSR subroutine
      int matchingJSRStart;
      for (matchingJSRStart = 0; matchingJSRStart < JSRSubNext; matchingJSRStart++) {
        if (JSRSubs[matchingJSRStart].subroutineByteCodeStart == JSRSubStartIndex) {
          JSRSubs[matchingJSRStart].newStartMaps(currBBMap);
          break;
        }
      }

      boolean JSRisinJSRSub = (currPendingRET != null);
      newworkStk =
          computeJSRNextMaps(nextBBNum,
                             currBBMap.length,
                             matchingJSRStart,
                             JSRisinJSRSub,
                             bbMaps,
                             blockStkTop,
                             JSRSubs,
                             currBBStkEmpty,
                             workStk);
      if (JSRisinJSRSub && bbPendingRETs[nextBBNum] == null) {
        bbPendingRETs[nextBBNum] = new PendingRETInfo(currPendingRET);
      }
    }

    return newworkStk;
  }

  private short[] computeJSRNextMaps(short nextBBNum, int maplength, int JSRSubIndex, boolean JSRisinJSRSub,
                                     byte[][] bbMaps, int[] blockStkTop, JSRSubroutineInfo[] JSRSubs,
                                     int currBBStkEmpty, short[] workStk) {
    short[] newworkStk = workStk;

    // Calculate the new map for the block starting at the instruction after the
    // JSR instruction (this is the block nextBBNum)
    byte[] refMap;

    refMap = JSRSubs[JSRSubIndex].computeResultingMaps(maplength);

    // If no map is computed, then the JSR Subroutine must have ended in a return
    // Do NOT add the next block to the work Q.
    if (refMap == null) {
      return newworkStk;
    }

    // If the block after the JSR instruction does not have a map, then the newly
    // computed map should be used and the block should be added to the workStk.
    if (bbMaps[nextBBNum] == null) {
      bbMaps[nextBBNum] = refMap;
      blockStkTop[nextBBNum] = JSRSubs[JSRSubIndex].endReferenceTop;
      newworkStk = addToWorkStk(nextBBNum, workStk);
    } else {
      // The block after the JSR instruction already has a reference map.
      // Check if the computed map matches the previous map. If so, no further
      // work needed.
      int mismatchAt = -1;
      byte[] blockMap = bbMaps[nextBBNum];
      for (int i = 0; i <= currBBStkEmpty; i++) {
        if ((refMap[i] != blockMap[i]) && (blockMap[i] != NON_REFERENCE)) {
          mismatchAt = i;
          break;
        }
      }
      if (mismatchAt == -1) {
        return newworkStk;  // no further work to be done
      } else {
        newworkStk = addUniqueToWorkStk(nextBBNum, workStk);
        for (int i = mismatchAt; i <= currBBStkEmpty; i++) {
          if (!JSRisinJSRSub) {
            blockMap[i] = (blockMap[i] == refMap[i]) ? blockMap[i] : NON_REFERENCE;
          } else {
            blockMap[i] = (blockMap[i] == refMap[i]) ? blockMap[i] : SET_TO_NONREFERENCE;
          }
        }
      }
    }
    return newworkStk;
  }

  /**
   * For each of the reachable handlers (catch blocks) from the try block, track that
   * the local variable given by the index with 1 or 2 words, has been set to a
   * non reference value (eg int, float, etc)
   *
   * @param localVariable             Variable index in the map
   * @param wordCount                 2 for doubles and longs, 1 otherwise
   * @param reachableHandlerBBNums    The array with all the block numbers of
   *                                  reachable handlers
   * @param reachableHandlerCount     0 through <code>reachableHandlerCount
   *                                   - 1 </code> will all be valid
   *                                  array indices
   * @param inJSRSub                  TODO Document ME XXX
   * @param bbMaps                    TODO Document ME XXX
   */
  private void setHandlersMapsNonRef(int localVariable, PrimitiveSize wordCount, int[] reachableHandlerBBNums,
                                     int reachableHandlerCount, boolean inJSRSub, byte[][] bbMaps) {
    if (!inJSRSub) {
      for (int i = 0; i < reachableHandlerCount; i++) {
        bbMaps[reachableHandlerBBNums[i]][localVariable] = NON_REFERENCE;
        if (wordCount == PrimitiveSize.DOUBLEWORD) {
          bbMaps[reachableHandlerBBNums[i]][localVariable + 1] = NON_REFERENCE;
        }
      }
    } else {
      for (int i = 0; i < reachableHandlerCount; i++) {
        bbMaps[reachableHandlerBBNums[i]][localVariable] = SET_TO_NONREFERENCE;
        if (wordCount == PrimitiveSize.DOUBLEWORD) {
          bbMaps[reachableHandlerBBNums[i]][localVariable + 1] = SET_TO_NONREFERENCE;
        }
      }
    }

  }

  /**
   * For each of the reachable handlers (catch blocks) from the try block,
   * track that
   * the local variable given by the index,  has been set to a reference value.
   * Only call this method if the try block is in a JSR subroutine.
   * If a non-reference value becomes a reference value,
   * then it can not be used as
   * as reference value within the handler
   * (there is a path to the handler where the
   * value is not a reference) so mark the local variable as
   * a non-reference if we
   * are tracking the difference maps (for a JSR subroutine).
   *
   * @param localVariable             Variable index in the map
   * @param reachableHandlerBBNums    The array with all the block numbers of
   *                                  reachable handlers
   * @param reachableHandlerCount     0 through <code>reachableHandlerCount
   *                                   - 1 </code> will all be valid
   *                                  array indices
   * @param bbMaps                    TODO Document ME XXX
   */
  private void setHandlersMapsRef(int localVariable, int[] reachableHandlerBBNums, int reachableHandlerCount,
                                  byte[][] bbMaps) {
    for (int i = 0; i < reachableHandlerCount; i++) {
      if (bbMaps[reachableHandlerBBNums[i]][localVariable] != REFERENCE) {
        bbMaps[reachableHandlerBBNums[i]][localVariable] = SET_TO_NONREFERENCE;
      }
    }
  }

  /**
   * For each of the reachable handlers (catch blocks)
   * from the try block, track that
   * the local variable given by the index,
   * has been set to a return address value.
   * Only call this routine within a JSR subroutine.
   * If a non-reference, or reference value becomes a return address value,
   * then it
   * cannot be used as any of these values within the handler
   * (there is a path to
   * the handler where the value is not an internal reference,
   * and a path where it
   * is an internal reference) so mark the local variable as a
   * non-reference if we
   * are tracking the difference maps (for a JSR subroutine).
   *
   * @param localVariable             variable index in the map
   * @param reachableHandlerBBNums     the array with all the block numbers of
   *                                  reachable handlers
   * @param reachableHandlerCount     0 through <code>reachableHandlerCount
   *                                   - 1 </code> will all be valid
   *                                  array indices
   * @param bbMaps                    TODO Document ME XXX
   */
  private void setHandlersMapsReturnAddress(int localVariable, int[] reachableHandlerBBNums, int reachableHandlerCount,
                                            byte[][] bbMaps) {
    for (int i = 0; i < reachableHandlerCount; i++) {
      if (bbMaps[reachableHandlerBBNums[i]][localVariable] != RETURN_ADDRESS) {
        bbMaps[reachableHandlerBBNums[i]][localVariable] = SET_TO_NONREFERENCE;
      }
    }

  }
}
