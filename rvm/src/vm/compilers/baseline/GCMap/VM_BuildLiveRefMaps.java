/*
 * (C) Copyright IBM Corp. 2001
 */
/** 
 * This class builds the LIVE reference and non-reference maps for a given method.
 * The maps are recorded with VM_ReferenceMaps. This class works with the 
 * baseline compiler, calculating the maps for local variables (including 
 * parameters), and the operand stack. Given the basic blocks mapped out by 
 * VM_BuildBB determine for each GC point (call sites and new's, etc) what the 
 * stack and variable maps are. For the variable maps calculate which must be kept
 * live because later code refers to them. Note that this class deals with 
 * reference maps (the term "stack maps" was not used as it is too ambiguous - does
 * "stack" refer to the java operand stack or a C-like stack; when processing
 * bytecodes it seemed best to use "stack" for java operand stack.)
 * @author Janice and Tony
 */

final class VM_BuildLiveRefMaps implements VM_BytecodeConstants {

  // ------------------ Static Class Data ------------------

   static final byte NON_REFERENCE = 0;
   static final byte REFERENCE = 1;
   static final byte RETURN_ADDRESS = 2;
   static final byte NOT_SET = 0;
   static final byte SET_TO_REFERENCE = 1;
   static final byte SET_TO_NONREFERENCE = 3;

  // -------------------- Instance Data ----------------------

  // These are updated in multiple methods in this class so they need to be global
  // to those methods (internal routines are not part of Java).

   private int             workStkTop;    
   private int             JSRSubNext;

  // -------------------- No Constructor -----------------------

  // -------------------- Instance Methods -----------------

 /**
   * After the analysis of the blocks of a method, examine the byte codes again, to
   * determine the reference maps for the gc points. Record the maps with 
   * referenceMaps.
   */

 public void
 buildReferenceMaps(VM_Method method, int[] byte2machine, 
		    VM_ReferenceMaps referenceMaps, VM_BuildBB buildBB) {

  //---------------------------------------------------------------//
  //                                                               //
  // These were calculated by VM_BuildBB.determineTheBasicBlocks   //
  //                                                               //
  //---------------------------------------------------------------//
  int             gcPointCount     = buildBB.gcPointCount;
  short           byteToBlockMap[] = buildBB.byteToBlockMap;
  VM_BasicBlock   basicBlocks[]    = buildBB.basicBlocks;
  int             jsrCount         = buildBB.numJsrs;

  int     blockCount;

  // These two map arrays are indexed by blockNum * localwords + localNum
  byte    blockReqMaps[];                 // The required map for each block
  byte    blockDefMaps[];                 // The defined map for each block
  // 

  byte    bbStartMaps[][];                // The starting map for each block

  int     blockStkTop[];                  // For each block, track where its 
                                          // current stack top is.
  boolean blockHasGCPoint[];              // For each block, track number of gc 
                                          // points within it.
  short   maxBlockGCPointCount;
  short   currBBGCPointCount;

  int     currBBNum;                      // Block number of block currently being
                                          // processed
  byte    currBBMap[];                    // The current map, used during 
                                          // processing thru a block
  int     currBBStkTop;                   // Stack top for the current map

  int     currBBStkEmpty;                 // Level when stack is empty - value 
                                          // depends on number of locals
  int     paramCount;                     // Number of parameters to the method 
                                          // being processed
  int     localCount;                     // Number of locals

  // Note that the mapping done here is "double mapping" of parameters.
  // Double mapping is when the parameters for a method are included in the map of
  // the method as well as in the map of the caller of the method. The original 
  // intent is that with double mapping call sites that are tricks
  // (eg VM_Magic.callFunctionReturnVoid ) will at least be correctly mapped on
  // one of the two sides. However with more recent changes to the runtime stack
  // frame layout, the parameters specified on the caller side occupy different
  // locations than the parameters on the callee side. Thus both need to be 
  // described.
  //

   
  // Variables for processing JSR instructions, RET instructions and JSR subroutines

  VM_PendingRETInfo        bbPendingRETs[] = null;
  VM_PendingRETInfo        currPendingRET;
  VM_JSRSubroutineInfo     JSRSubroutines[] = null;

  // Blocks that need to be processed are put on the workStk

  short                    workStk[];

  // Track whether a block has already been seen once. Any recording of maps done 
  // within such a block will be processed as a "rerecording" instead of a new map.

  boolean                 blockSeen[];

  // blocks that represent "catch" blocks need special processing. Catch blocks are
  // referred to as handlers within java .class files 
  //
  VM_ExceptionHandlerMap  exceptions;               // exception table class for 
                                                    // method being processed
  int                     tryStartPC[];             // array of try start indices 
                                                    // into byte code table
  int                     tryEndPC[];               // array of try end indices into 
                                                    // byte code table
  int                     tryHandlerPC[];           // array of try handlers start 
                                                    // indices into byte code
  int                     tryHandlerLength;         // length of try handlers array
  int                     reachableHandlerBBNums[]; // array of reachable handlers 
                                                    // from a given try block
  int                     reachableHandlersCount=0; // Number of reachable handlers 
  boolean                 handlerProcessed[];       // Handler blocks are processed 
                                                    // after the normal flow. As they
                                                    // may be nested, they need to 
                                                    // be handled individually. This
                                                    // array is used to track which 
                                                    // have been processed.
  boolean                 handlersAllDone;           

  // Other local variables
  //
  byte               bytecodes[];                 // byte codes for the method
  int                i;                           // index into bytecode array
  short              brBBNum;                     // For processing branches, need 
                                                  // block number of target
  VM_Class           declaringClass;              // The declaring class of the 
                                                  // method being processed

  //
  //  Initialization
  //

  // Determine what stack empty looks like

  paramCount = method.getParameterWords();
  if (!method.isStatic()) paramCount++;


  localCount     = method.getLocalWords();
  currBBStkEmpty = localCount-1;            // -1 to locate the last "local" index

  // Get information from the method being processed

  bytecodes      = method.getBytecodes();
  declaringClass = method.getDeclaringClass();

  // Set up the array of maps per block - block 0 not used
  //
  blockCount        = VM_BasicBlock.getNumberofBlocks()+1;
  bbStartMaps       = new byte[blockCount][];

  blockStkTop       = new int[blockCount];
  blockSeen         = new boolean[blockCount];
  blockReqMaps      = new byte[blockCount*localCount];
  blockDefMaps      = new byte[blockCount*localCount];

  blockHasGCPoint   = new boolean[blockCount];
  maxBlockGCPointCount = 0;

  // Try Handler processing initialization

  exceptions       = method.getExceptionHandlerMap();
  if (exceptions != null) {
    tryStartPC       = exceptions.getStartPC();
    tryEndPC         = exceptions.getEndPC();
    tryHandlerPC     = exceptions.getHandlerPC();
    tryHandlerLength = tryHandlerPC.length;

    reachableHandlerBBNums = new int[tryStartPC.length];
    handlerProcessed       = new boolean[tryStartPC.length];
    if (jsrCount > 0) {
      JSRSubroutines   = new VM_JSRSubroutineInfo[jsrCount];
      JSRSubNext       = 0;
      bbPendingRETs = new VM_PendingRETInfo[blockCount];
    }
    handlersAllDone        = (tryHandlerLength == 0);
  }
  else {
    handlersAllDone = true;
    tryHandlerLength = 0;
    tryStartPC = null;
    tryEndPC = null;
    tryHandlerPC = null;
    reachableHandlerBBNums = null;
    handlerProcessed = null;
  }


  // Start a new set of maps with the reference Map class.
  // 3rd argument is parameter count included with the maps
  //
  referenceMaps.startNewMaps(gcPointCount, jsrCount, paramCount);    
  if (VM.ReferenceMapsStatistics)
    referenceMaps.bytecount = referenceMaps.bytecount + bytecodes.length;

  // Set up the Work Stack
  workStk    = new short[10+tryHandlerLength];

  // Start by putting the first block on the work stack
  workStkTop = 0;
  workStk[workStkTop] = byteToBlockMap[0];
  currBBMap = new byte[method.getOperandWords() + currBBStkEmpty+1];    

  // The map for the start of the first block, is stack empty, with none
  // of the locals set yet
  //
  currBBStkTop = currBBStkEmpty;
  bbStartMaps[byteToBlockMap[0]] = currBBMap;
  blockStkTop[byteToBlockMap[0]] = currBBStkTop;
  currBBMap = new byte[currBBMap.length];

  //----------------------------------------------------------
  //
  //  First pass: compute - Required and Defined sets,
  //                      - Starting operand maps
  //                      - How many GC points each block has
  //  Keep looping until the work stack is empty
  //
  //----------------------------------------------------------

  while (workStkTop > -1) {

    // Get the next item off the work stack
    currBBNum = workStk[workStkTop];
    workStkTop--;

    currBBGCPointCount   = 0;

    boolean inJSRSubroutine = false;
    if (bbStartMaps[currBBNum] != null) {
      currBBStkTop = blockStkTop[currBBNum];
      for (int k = currBBStkEmpty+1; k <= currBBStkTop; k++) 
        currBBMap[k] = bbStartMaps[currBBNum][k];

      if (jsrCount > 0 && basicBlocks[currBBNum].isInJSR()) {
	inJSRSubroutine = true;
      }
    }
    else {      
      VM.sysWrite("VM_BuildLiveRefMaps, error: found a block on the work stack"); 
      VM.sysWrite(" with no starting map. Block number is ");
      VM.sysWrite(basicBlocks[currBBNum].blockNumber);
      VM.sysWrite("\n");
      VM.sysFail("VM_BuildLiveRefMap failure");
    }


    int start = basicBlocks[currBBNum].getStart();
    int end = basicBlocks[currBBNum].getEnd();

    if (jsrCount > 0 && inJSRSubroutine ) {
      currPendingRET = bbPendingRETs[currBBNum];
      if (basicBlocks[currBBNum].isTryStart()) {
	for (int k = 0; k < tryHandlerLength; k++) {
	  if (tryStartPC[k] == start) {
	    int handlerBBNum = byteToBlockMap[tryHandlerPC[k]];
            bbPendingRETs[handlerBBNum] = new VM_PendingRETInfo(currPendingRET);
	  }
	}
      }
    }
    else
      currPendingRET = null;

    boolean inTryBlock;
    if (basicBlocks[currBBNum].isTryBlock()) {
      inTryBlock = true;
      reachableHandlersCount = 0;
      for (i=0; i<tryHandlerLength; i++)
	if (tryStartPC[i] <= start &&
	    tryEndPC[i] >= end) {
	  reachableHandlerBBNums[reachableHandlersCount] = 
	                              byteToBlockMap[tryHandlerPC[i]];
          reachableHandlersCount++;
	  if (tryStartPC[i] == start) {
	    int handlerBBNum = byteToBlockMap[tryHandlerPC[i]];
	    if (bbStartMaps[handlerBBNum] == null) {
              bbStartMaps[handlerBBNum] = new byte[currBBMap.length];
	      for (int k=0; k<=currBBStkEmpty; k++)
	        bbStartMaps[handlerBBNum][k] = currBBMap[k];
	      bbStartMaps[handlerBBNum][currBBStkEmpty+1] = REFERENCE;
	      blockStkTop[handlerBBNum] = currBBStkEmpty+1;
	    }
	  }
	}
    }
    else
      inTryBlock = false;

    boolean processNextBlock = true;
    int mapIndex = (currBBNum-1) * localCount;

    for (i=start; i<= end; i++) {
      int opcode = ((int)bytecodes[i]) & 0x000000FF;
     
     switch (opcode) {

     case JBC_nop : {
        break;
     }
     case JBC_aconst_null : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = REFERENCE;
       break;
     }
     case JBC_iconst_m1 :
     case JBC_iconst_0 :
     case JBC_iconst_1 :
     case JBC_iconst_2 :
     case JBC_iconst_3 :
     case JBC_iconst_4 :
     case JBC_iconst_5 :
     case JBC_fconst_0 :
     case JBC_fconst_1 :
     case JBC_fconst_2 : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       break;
     }
     case JBC_lconst_0 :
     case JBC_lconst_1 :
     case JBC_dconst_0 :
     case JBC_dconst_1 : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       break;
     }
     case JBC_bipush : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       i = i + 1;
       break;
     }
     case JBC_sipush : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       i = i + 2;
       break;
     }
     case JBC_ldc : {
       // Get cp index - unsigned 8-bit.
       int index = ((int)bytecodes[i+1]) & 0xFF;
       currBBStkTop++;

       if (declaringClass.getLiteralDescription(index) == VM_Statics.STRING_LITERAL)
         currBBMap[currBBStkTop] = REFERENCE;
       else
	 currBBMap[currBBStkTop] = NON_REFERENCE;
       i = i + 1;
       break;
     }
     case JBC_ldc_w : {
       // Get cpindex - unsigned 16-bit.
       int index = (((int)bytecodes[i+1]) << 8 |
                    (((int)bytecodes[i+2]) & 0xFF)) & 0xFFFF;
       currBBStkTop++;
       if (declaringClass.getLiteralDescription(index) == VM_Statics.STRING_LITERAL)
         currBBMap[currBBStkTop] = REFERENCE;
       else
	 currBBMap[currBBStkTop] = NON_REFERENCE;
       i = i + 2;
       break;
     }
     case JBC_ldc2_w : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       i = i + 2;
       break;
     }
     case JBC_iload :
     case JBC_fload : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       i = i + 1;
       break;
     }
     case JBC_lload :
     case JBC_dload : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       i = i + 1;
       break;
     }
     case JBC_aload : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = REFERENCE;
       int index = ((int)bytecodes[i+1]) & 0xFF;  // Get local index - unsigned byte.
       if (blockDefMaps[mapIndex+index] != REFERENCE)
         blockReqMaps[mapIndex+index] = REFERENCE;
       i = i + 1;
       break;
     }
     case JBC_iload_0 :
     case JBC_iload_1 :
     case JBC_iload_2 :
     case JBC_iload_3 :
     case JBC_fload_0 :
     case JBC_fload_1 :
     case JBC_fload_2 :
     case JBC_fload_3 : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       break;
     }
     case JBC_lload_0 :
     case JBC_lload_1 :
     case JBC_lload_2 :
     case JBC_lload_3 :
     case JBC_dload_0 :
     case JBC_dload_1 :
     case JBC_dload_2 :
     case JBC_dload_3 : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       break;
     }

     case JBC_aload_0 : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = REFERENCE;
       if (blockDefMaps[mapIndex + 0] != REFERENCE)
         blockReqMaps[mapIndex + 0] = REFERENCE;
       break;
     }
     case JBC_aload_1 : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = REFERENCE;
       if (blockDefMaps[mapIndex + 1] != REFERENCE)
         blockReqMaps[mapIndex + 1] = REFERENCE;
       break;
     }
     case JBC_aload_2 : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = REFERENCE;
       if (blockDefMaps[mapIndex + 2] != REFERENCE)
         blockReqMaps[mapIndex + 2] = REFERENCE;
       break;
     }
     case JBC_aload_3 : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = REFERENCE;
       if (blockDefMaps[mapIndex + 3] != REFERENCE)
         blockReqMaps[mapIndex + 3] = REFERENCE;
       break;
     }

     case JBC_istore :
     case JBC_fstore : {
       int index = ((int)bytecodes[i+1]) & 0xFF;  // Get local index - unsigned byte.
       if (!inJSRSubroutine) 
	  blockDefMaps[mapIndex+index] = NON_REFERENCE;
       else
	  currBBMap[index]=SET_TO_NONREFERENCE;
       if (inTryBlock) 
	    setHandlersMapsNonRef(index, 1, reachableHandlerBBNums, 
				  reachableHandlersCount, inJSRSubroutine, 
				  bbStartMaps);
       currBBStkTop--;
       i = i + 1;
       break;
     }
     case JBC_lstore :
     case JBC_dstore : {
       int index = ((int)bytecodes[i+1]) & 0xFF;  // Get local index - unsigned byte.
       if (!inJSRSubroutine) {
	 blockDefMaps[mapIndex+index] = NON_REFERENCE;
	 blockDefMaps[mapIndex+index+1] = NON_REFERENCE;
       }
       else {
	  currBBMap[index]=SET_TO_NONREFERENCE;
	  currBBMap[index+1]=SET_TO_NONREFERENCE;
       }

       if (inTryBlock) 
	 setHandlersMapsNonRef(index, 2, reachableHandlerBBNums, 
			       reachableHandlersCount, inJSRSubroutine, bbStartMaps);
       currBBStkTop = currBBStkTop - 2;
       i = i + 1;
       break;
     }
     case JBC_astore : {
       // Get local index - unsigned byte.
       int index = ((int)bytecodes[i+1]) & 0xFF;
       blockDefMaps[mapIndex+index] = REFERENCE;

       // may be a reference or a return address
       //
       if (currBBMap[index] == RETURN_ADDRESS) 
	 currPendingRET.updateReturnAddressLocation(index);
       if (inTryBlock && inJSRSubroutine) {
         if (currBBMap[index] == REFERENCE)
             setHandlersMapsRef(index, reachableHandlerBBNums, 
				reachableHandlersCount, bbStartMaps);
	 else
             setHandlersMapsReturnAddress(index, reachableHandlerBBNums, 
					  reachableHandlersCount, bbStartMaps);
       }
       currBBStkTop--;
       i = i + 1;
       break;
     }
     case JBC_istore_0 :
     case JBC_fstore_0 : {
       if (!inJSRSubroutine)
           blockDefMaps[mapIndex+0] = NON_REFERENCE;
       else 
	   currBBMap[0]=SET_TO_NONREFERENCE;
       if (inTryBlock) 
             setHandlersMapsNonRef(0, 1, reachableHandlerBBNums, 
				   reachableHandlersCount, inJSRSubroutine, 
				   bbStartMaps);
       currBBStkTop--;
       break;
     }
     case JBC_istore_1 :
     case JBC_fstore_1 : {
      if (!inJSRSubroutine)
        blockDefMaps[mapIndex+1] = NON_REFERENCE;
      else 
         currBBMap[1]=SET_TO_NONREFERENCE;
      if (inTryBlock) 
           setHandlersMapsNonRef(1, 1, reachableHandlerBBNums, 
				 reachableHandlersCount, inJSRSubroutine, 
				 bbStartMaps);
       currBBStkTop--;
       break;
     }
     case JBC_istore_2 :
     case JBC_fstore_2 : {
       if (!inJSRSubroutine)
           blockDefMaps[mapIndex+2] = NON_REFERENCE;
       else
	   currBBMap[2]=SET_TO_NONREFERENCE;
       if (inTryBlock) 
           setHandlersMapsNonRef(2, 1, reachableHandlerBBNums, 
				 reachableHandlersCount, inJSRSubroutine, 
				 bbStartMaps);
       currBBStkTop--;
       break;
     }
     case JBC_istore_3 :
     case JBC_fstore_3 : {
       if (!inJSRSubroutine)
           blockDefMaps[mapIndex+3] = NON_REFERENCE;
       else
	   currBBMap[3]=SET_TO_NONREFERENCE;
       if (inTryBlock) 
           setHandlersMapsNonRef(3, 1, reachableHandlerBBNums, 
				 reachableHandlersCount, inJSRSubroutine, 
				 bbStartMaps);
       currBBStkTop--;
       break;
     }
     case JBC_lstore_0 :
     case JBC_dstore_0 : {
       if (inJSRSubroutine) {
	   blockDefMaps[mapIndex+0] = NON_REFERENCE;
	   blockDefMaps[mapIndex+1] = NON_REFERENCE;
       }
       else {
	    currBBMap[0]=SET_TO_NONREFERENCE;
	    currBBMap[1]=SET_TO_NONREFERENCE;
       }
       if (inTryBlock) 
           setHandlersMapsNonRef(0, 2, reachableHandlerBBNums, 
				 reachableHandlersCount, inJSRSubroutine, 
				 bbStartMaps);
       currBBStkTop = currBBStkTop - 2;
       break;
     }
     case JBC_lstore_1 :
     case JBC_dstore_1 : {
       if (!inJSRSubroutine) {
	   blockDefMaps[mapIndex+1] = NON_REFERENCE;
	   blockDefMaps[mapIndex+2] = NON_REFERENCE;
       }
       else {
           currBBMap[1]=SET_TO_NONREFERENCE;
           currBBMap[2]=SET_TO_NONREFERENCE;
       }
       if (inTryBlock) 
           setHandlersMapsNonRef(1, 2, reachableHandlerBBNums, 
				 reachableHandlersCount, inJSRSubroutine, 
				 bbStartMaps);
       currBBStkTop = currBBStkTop - 2;
       break;
     }
     case JBC_lstore_2 :
     case JBC_dstore_2 : {
       if (!inJSRSubroutine) {
	   blockDefMaps[mapIndex+2] = NON_REFERENCE;
	   blockDefMaps[mapIndex+3] = NON_REFERENCE;
       }
       else {
           currBBMap[2]=SET_TO_NONREFERENCE;
           currBBMap[3]=SET_TO_NONREFERENCE;
       }
       if (inTryBlock) 
           setHandlersMapsNonRef(2, 2, reachableHandlerBBNums, 
				 reachableHandlersCount, inJSRSubroutine, 
				 bbStartMaps);
       currBBStkTop = currBBStkTop - 2;
       break;
     }
     case JBC_lstore_3 :
     case JBC_dstore_3 : {
       if (!inJSRSubroutine) {
	   blockDefMaps[mapIndex+3] = NON_REFERENCE;
	   blockDefMaps[mapIndex+4] = NON_REFERENCE;
       }
       else {
           currBBMap[3]=SET_TO_NONREFERENCE;
           currBBMap[4]=SET_TO_NONREFERENCE;
       }
       if (inTryBlock) 
           setHandlersMapsNonRef(3, 2, reachableHandlerBBNums, 
				 reachableHandlersCount, inJSRSubroutine, 
				 bbStartMaps);
       currBBStkTop = currBBStkTop - 2;
       break;
     }

     case JBC_astore_0 : {
       blockDefMaps[mapIndex+0] = REFERENCE;
       if (inTryBlock && inJSRSubroutine)
	   setHandlersMapsRef(0, reachableHandlerBBNums, reachableHandlersCount, 
			      bbStartMaps);
       currBBStkTop--;
       break;
     }
     case JBC_astore_1 : {
       blockDefMaps[mapIndex+1]=currBBMap[currBBStkTop];
       if (inTryBlock && inJSRSubroutine)
	   setHandlersMapsRef(1, reachableHandlerBBNums, reachableHandlersCount, 
			      bbStartMaps);
       currBBStkTop--;
       break;
     }
     case JBC_astore_2 : {
       blockDefMaps[mapIndex+2]=currBBMap[currBBStkTop];
       if (inTryBlock && inJSRSubroutine)
	   setHandlersMapsRef(2, reachableHandlerBBNums, reachableHandlersCount, 
			      bbStartMaps);
       currBBStkTop--;
       break;
     }
     case JBC_astore_3 : {
       blockDefMaps[mapIndex+3]=currBBMap[currBBStkTop];
       if (inTryBlock && inJSRSubroutine)
	   setHandlersMapsRef(3, reachableHandlerBBNums, reachableHandlersCount, 
			      bbStartMaps);
       currBBStkTop--;
       break;
     }
     case JBC_pop : {
       currBBStkTop--;
       break;
     }
     case JBC_pop2 : {
       currBBStkTop = currBBStkTop - 2;
       break;
     }
     case JBC_dup : {
       currBBMap[currBBStkTop+1] = currBBMap[currBBStkTop];
       currBBStkTop++;
       break;
     }
     case JBC_dup2 : {
       currBBMap[currBBStkTop+1] = currBBMap[currBBStkTop-1];
       currBBMap[currBBStkTop+2] = currBBMap[currBBStkTop];
       currBBStkTop = currBBStkTop + 2;
       break;
     }
     case JBC_dup_x1 : {
       currBBMap[currBBStkTop+1] = currBBMap[currBBStkTop];
       currBBMap[currBBStkTop]   = currBBMap[currBBStkTop-1];
       currBBMap[currBBStkTop-1] = currBBMap[currBBStkTop+1];
       currBBStkTop++;
       break;
     }
     case JBC_dup2_x1 : {
       currBBMap[currBBStkTop+2] = currBBMap[currBBStkTop];
       currBBMap[currBBStkTop+1] = currBBMap[currBBStkTop-1];
       currBBMap[currBBStkTop]   = currBBMap[currBBStkTop-2];
       currBBMap[currBBStkTop-1] = currBBMap[currBBStkTop+2];
       currBBMap[currBBStkTop-2] = currBBMap[currBBStkTop+1];
       currBBStkTop = currBBStkTop + 2;
       break;
     }
     case JBC_dup_x2 : {
       currBBMap[currBBStkTop+1] = currBBMap[currBBStkTop];
       currBBMap[currBBStkTop]   = currBBMap[currBBStkTop-1];
       currBBMap[currBBStkTop-1] = currBBMap[currBBStkTop-2];
       currBBMap[currBBStkTop-2] = currBBMap[currBBStkTop+1];
       currBBStkTop++;
       break;
     }
     case JBC_dup2_x2 : {
       currBBMap[currBBStkTop+2] = currBBMap[currBBStkTop];
       currBBMap[currBBStkTop+1] = currBBMap[currBBStkTop-1];
       currBBMap[currBBStkTop]   = currBBMap[currBBStkTop-2];
       currBBMap[currBBStkTop-1] = currBBMap[currBBStkTop-3];
       currBBMap[currBBStkTop-2] = currBBMap[currBBStkTop+2];
       currBBMap[currBBStkTop-3] = currBBMap[currBBStkTop+1];
       currBBStkTop = currBBStkTop + 2;
       break;
     }
     case JBC_swap : {
       byte temp;
       temp                      = currBBMap[currBBStkTop];
       currBBMap[currBBStkTop]   = currBBMap[currBBStkTop-1];
       currBBMap[currBBStkTop-1] = temp;
       break;
     }
     case JBC_iadd :
     case JBC_fadd :
     case JBC_isub :
     case JBC_fsub :
     case JBC_imul :
     case JBC_fmul :
     case JBC_fdiv :
     case JBC_frem :
     case JBC_ishl :
     case JBC_ishr :
     case JBC_iushr :
     case JBC_lshl :      // long shifts that int shift value
     case JBC_lshr :
     case JBC_lushr :
     case JBC_iand :
     case JBC_ior :
     case JBC_ixor : {
       currBBStkTop--;
       break;
     }
     case JBC_irem :
     case JBC_idiv : {
       currBBStkTop = currBBStkTop-2;
       currBBGCPointCount++;
       currBBStkTop++;
       break;
     }
     case JBC_ladd :
     case JBC_dadd :
     case JBC_lsub :
     case JBC_dsub :
     case JBC_lmul :
     case JBC_dmul :
     case JBC_ddiv :
     case JBC_drem :
     case JBC_land :
     case JBC_lor :
     case JBC_lxor : {
       currBBStkTop = currBBStkTop - 2;
       break;
     }
     case JBC_lrem :
     case JBC_ldiv : {
       currBBStkTop = currBBStkTop - 4;
       currBBGCPointCount++;
       currBBStkTop = currBBStkTop + 2;
       break;
     }
     case JBC_ineg :
     case JBC_lneg :
     case JBC_fneg :
     case JBC_dneg : {
       break;
     }


     case JBC_iinc : {
       i = i + 2;
       break;
     }
     case JBC_i2l :
     case JBC_i2d :
     case JBC_f2l :
     case JBC_f2d : {
       currBBMap[++currBBStkTop] = NON_REFERENCE;
       break;
     }
     case JBC_i2f :
     case JBC_l2d :
     case JBC_f2i :
     case JBC_d2l :
     case JBC_int2byte :
     case JBC_int2char :
     case JBC_int2short : {
       break;
     }
     case JBC_l2i :
     case JBC_l2f :
     case JBC_d2i :
     case JBC_d2f : {
       currBBStkTop--;
       break;
     }
     case JBC_lcmp :
     case JBC_dcmpl :
     case JBC_dcmpg : {
       currBBStkTop = currBBStkTop - 3;
       break;
     }
     case JBC_fcmpl :
     case JBC_fcmpg : {
       currBBStkTop--;
       break;
     }
     case JBC_ifeq :
     case JBC_ifne :
     case JBC_iflt :
     case JBC_ifge :
     case JBC_ifgt :
     case JBC_ifle : {
       short offset = (short)(((int)bytecodes[i+1]) << 8 | 
			      (((int)bytecodes[i+2]) & 0xFF));
       currBBStkTop--;
       brBBNum = byteToBlockMap[i+offset];
       workStk = processBranchBB(brBBNum, currBBStkTop, currBBMap, currBBStkEmpty,
				 inJSRSubroutine, bbStartMaps, blockStkTop,
				 currPendingRET, bbPendingRETs, workStk);
       i = i + 2;
       break;
     }

     case JBC_if_icmpeq :
     case JBC_if_icmpne :
     case JBC_if_icmplt :
     case JBC_if_icmpge :
     case JBC_if_icmpgt :
     case JBC_if_icmple :
     case JBC_if_acmpeq :
     case JBC_if_acmpne : {
       short offset = (short)(((int)bytecodes[i+1]) << 8 | 
			      (((int)bytecodes[i+2]) & 0xFF));
       currBBStkTop = currBBStkTop - 2;
       brBBNum = byteToBlockMap[i+offset];
       workStk = processBranchBB(brBBNum, currBBStkTop, currBBMap, currBBStkEmpty,
				 inJSRSubroutine, bbStartMaps, blockStkTop, 
				 currPendingRET, bbPendingRETs, workStk);
       i = i + 2;
       break;
     }

     case JBC_ifnull :
     case JBC_ifnonnull : {
       short offset = (short)(((int)bytecodes[i+1]) << 8 |
			      (((int)bytecodes[i+2]) & 0xFF));
       currBBStkTop--;
       brBBNum = byteToBlockMap[i+offset];
       workStk = processBranchBB(brBBNum, currBBStkTop, currBBMap, currBBStkEmpty,
				 inJSRSubroutine, bbStartMaps, blockStkTop, 
				 currPendingRET, bbPendingRETs, workStk);
       i = i + 2;
       break;
     }

     case JBC_goto : {
       // The offset is a 16-bit value, but we sign extend to an int, and
       // it still works.
       int offset = (int)((short)(((int)bytecodes[i+1]) << 8 | 
				  (((int)bytecodes[i+2]) & 0xFF)));
       brBBNum = byteToBlockMap[i+offset];
       workStk = processBranchBB(brBBNum, currBBStkTop, currBBMap, currBBStkEmpty,
				 inJSRSubroutine, bbStartMaps, blockStkTop, 
				 currPendingRET, bbPendingRETs, workStk);
       processNextBlock = false;
       i = i + 2;
       break;
     }
     case JBC_goto_w : {
       int offset = getIntOffset(i, bytecodes);
       brBBNum = byteToBlockMap[i+offset];
       workStk = processBranchBB(brBBNum, currBBStkTop, currBBMap, currBBStkEmpty,
				 inJSRSubroutine, bbStartMaps, blockStkTop, 
				 currPendingRET, bbPendingRETs, workStk);
       processNextBlock = false;
       i = i + 4;
       break;
     }
     case JBC_tableswitch : {
       int j = i;           // save initial value

       currBBStkTop--; 
       i = i + 1;           // space past op code
       i = (((i + 3)/4)*4); // align to next word boundary
       // get default offset and generate basic block at default offset
       // getIntOffset expects byte before offset
       int def = getIntOffset(i-1,bytecodes);  
       workStk = processBranchBB(byteToBlockMap[j+def], currBBStkTop, currBBMap, 
				 currBBStkEmpty, inJSRSubroutine, bbStartMaps, 
				 blockStkTop, currPendingRET, bbPendingRETs, 
				 workStk);
 
       // get low offset
       i = i + 4;           // go past default br offset
       int low = getIntOffset(i-1,bytecodes);
       i = i + 4;           // space past low offset
 
       // get high offset
       int high = getIntOffset(i-1,bytecodes);
       i = i + 4;           // go past high offset
 
       // generate labels for offsets
       for (int k = 0; k < (high - low +1); k++) {
	   int l = i + k*4; // point to next offset
	   // get next offset
	   int offset = getIntOffset(l-1,bytecodes);
       workStk = processBranchBB(byteToBlockMap[j+offset], currBBStkTop, currBBMap, 
				 currBBStkEmpty, inJSRSubroutine, bbStartMaps, 
				 blockStkTop, currPendingRET, bbPendingRETs, 
				 workStk);
       }
       processNextBlock = false;       
       i = i + (high - low +1) * 4 - 1; // space past offsets
       break;
     }
     case JBC_lookupswitch : {
       int j = i;                         // save initial value for labels
       currBBStkTop--;
       i = i +1;                          // space past op code
       i = (((i + 3)/4)*4);               // align to next word boundary
       // get default offset and
       // process branch to default branch point
       int def = getIntOffset(i-1,bytecodes);
       workStk = processBranchBB(byteToBlockMap[j+def], currBBStkTop, currBBMap, 
				 currBBStkEmpty, inJSRSubroutine, bbStartMaps, 
				 blockStkTop, currPendingRET, bbPendingRETs, 
				 workStk);
       i = i + 4;                         // go past default  offset

       // get number of pairs
       int npairs = getIntOffset(i-1,bytecodes);
       i = i + 4;                         // space past  number of pairs

       // generate label for each offset in table
       for (int k = 0; k < npairs; k++) {
           int l = i + k*8 + 4;           // point to next offset
           // get next offset
           int offset = getIntOffset(l-1,bytecodes);
       workStk = processBranchBB(byteToBlockMap[j+offset], currBBStkTop, currBBMap, 
				 currBBStkEmpty, inJSRSubroutine, bbStartMaps, 
				 blockStkTop, currPendingRET, bbPendingRETs, 
				 workStk);
       }
       processNextBlock = false;
       i = i + (npairs) *8 -1; // space past match-offset pairs
       break;
     }
     case JBC_jsr : {
       processNextBlock = false;
       short offset = (short)(((int)bytecodes[i+1]) << 8 | 
			      (((int)bytecodes[i+2]) & 0xFF));
       currBBGCPointCount++;
       currBBStkTop++;
       currBBMap[currBBStkTop] = RETURN_ADDRESS; 
       workStk = processJSR(byteToBlockMap[i], i+offset, byteToBlockMap[i+offset], 
			    byteToBlockMap[i+3], bbStartMaps, currBBStkTop, 
			    currBBMap, currBBStkEmpty, blockStkTop, bbPendingRETs, 
			    currPendingRET, JSRSubroutines, workStk);
       i = i + 2;
       break;
     }
     case JBC_jsr_w : {
       processNextBlock = false;
       currBBGCPointCount++;
       currBBStkTop++;
       currBBMap[currBBStkTop] = RETURN_ADDRESS;  

       int offset = getIntOffset(i, bytecodes);
       workStk = processJSR(byteToBlockMap[i], i+offset, byteToBlockMap[i+offset], 
			    byteToBlockMap[i+5], bbStartMaps, currBBStkTop, 
			    currBBMap, currBBStkEmpty, blockStkTop, bbPendingRETs, 
			    currPendingRET, JSRSubroutines, workStk);
       i = i + 4;
       break;
     }
     case JBC_ret : {
       // Index of local variable (unsigned byte)
       int index = ((int)bytecodes[i+1]) & 0xFF; 

       // Can not be used again as a return addr. 
       //
       currBBMap[index] = SET_TO_NONREFERENCE;   
       processNextBlock = false;
       int subStart = currPendingRET.JSRSubStartByteIndex;
       int k;
       for (k=0; k<JSRSubNext; k++) {
	   if (JSRSubroutines[k].subroutineByteCodeStart == subStart) {
	     JSRSubroutines[k].newEndMaps(currBBMap, currBBStkTop);
	     break;
	   }
        }
 
       boolean JSRisInJSRSub = bbPendingRETs[currPendingRET.JSRBBNum] != null;
       workStk = computeJSRNextMaps(currPendingRET.JSRNextBBNum, currBBMap.length, 
				    k, JSRisInJSRSub, bbStartMaps, blockStkTop, 
				    JSRSubroutines, currBBStkEmpty, workStk);
       if (JSRisInJSRSub && bbPendingRETs[currPendingRET.JSRNextBBNum] == null) 
	   bbPendingRETs[currPendingRET.JSRNextBBNum] = 
                      new VM_PendingRETInfo(bbPendingRETs[currPendingRET.JSRBBNum]);
       i = i + 1;
       break;
     }
     case JBC_invokevirtual :
     case JBC_invokespecial : {
       int index = (((int)bytecodes[i+1]) << 8 | 
		    (((int)bytecodes[i+2]) & 0xFF)) & 0xFFFF;
       VM_Method calledMethod = declaringClass.getMethodRef(index);
       currBBStkTop = stkUpdateInvoke(calledMethod, currBBStkTop, currBBMap, false);
       currBBGCPointCount++;
       i = i + 2;
       break;
     }
     case JBC_invokestatic : {
       int index = (((int)bytecodes[i+1]) << 8 | 
		    (((int)bytecodes[i+2]) & 0xFF)) & 0xFFFF;
       VM_Method calledMethod = declaringClass.getMethodRef(index);
       if (!calledMethod.getDeclaringClass().isMagicType() || 
	   VM_MagicCompiler.checkForActualCall(calledMethod)) 
         currBBGCPointCount++;
       currBBStkTop = stkUpdateInvoke(calledMethod, currBBStkTop, currBBMap, true);
       i = i + 2;
       break;
     }
     case JBC_invokeinterface : {
       int index = (((int)bytecodes[i+1]) << 8 | 
		    (((int)bytecodes[i+2]) & 0xFF)) & 0xFFFF;
       currBBGCPointCount++;
       VM_Method calledMethod = declaringClass.getMethodRef(index);
       currBBStkTop = stkUpdateInvoke(calledMethod, currBBStkTop, currBBMap, false);
       i = i + 4;
       break;
     }
     case JBC_ireturn :
     case JBC_lreturn :
     case JBC_freturn :
     case JBC_dreturn :
     case JBC_areturn :
     case JBC_return  :{
       processNextBlock = false;
       break;
     }

     case JBC_getstatic : {
       int index = (((int)bytecodes[i+1]) << 8 | 
		    (((int)bytecodes[i+2]) & 0xFF)) & 0xFFFF;

       // Register the reference map
       //  note: getstatic could result in a call to the classloader

       currBBGCPointCount++;
       VM_Type fieldType = declaringClass.getFieldRef(index).getType();
       currBBMap[++currBBStkTop] = fieldType.isReferenceType() ? 
	                           REFERENCE : NON_REFERENCE;
       if (fieldType.getStackWords() == 2)
          currBBMap[++currBBStkTop] = NON_REFERENCE;
       i = i + 2;
       break;
     }
     case JBC_putstatic : {
       int index = (((int)bytecodes[i+1]) << 8 | 
		    (((int)bytecodes[i+2]) & 0xFF)) & 0xFFFF;

       // Register the reference map
       //  note: putstatic could result in a call to the classloader

       currBBGCPointCount++;
       VM_Type fieldType = declaringClass.getFieldRef(index).getType();
       currBBStkTop--;
       if (fieldType.getStackWords() == 2)
          currBBStkTop--;
       i = i + 2;
       break;
     }
     case JBC_getfield : {
       int index = (((int)bytecodes[i+1]) << 8 | 
		    (((int)bytecodes[i+2]) & 0xFF)) & 0xFFFF;
       VM_Type fieldType = declaringClass.getFieldRef(index).getType();

       // Register the reference map, minus the object pointer on the stack
       //  note: getfield could result in a call to the classloader

       currBBGCPointCount++;
       currBBStkTop--;    // pop object pointer
       currBBMap[++currBBStkTop] = fieldType.isReferenceType() ? 
	                             REFERENCE : NON_REFERENCE;
       if (fieldType.getStackWords() == 2)
          currBBMap[++currBBStkTop] = NON_REFERENCE;
       i = i + 2;
       break;
     }
     case JBC_putfield : {
       int index = (((int)bytecodes[i+1]) << 8 | 
		    (((int)bytecodes[i+2]) & 0xFF)) & 0xFFFF;
       VM_Type fieldType = declaringClass.getFieldRef(index).getType();

       currBBGCPointCount++;
       currBBStkTop -= 2;  // remove objectref and one value
       if (fieldType.getStackWords() == 2)
          currBBStkTop--;
       i = i + 2;
       break;
     }
     case JBC_checkcast : {
       currBBGCPointCount++;
       i = i + 2;
       break;
     }
     case JBC_instanceof : {
       currBBGCPointCount++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       i = i + 2;
       break;
     }
     case JBC_new : {
       currBBGCPointCount++;
       currBBStkTop++;
       currBBMap[currBBStkTop] = REFERENCE;
       i = i + 2;
       break;
     }
     case JBC_iaload :
     case JBC_faload :
     case JBC_baload :
     case JBC_caload :
     case JBC_saload : {
       currBBStkTop--;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       currBBGCPointCount++;
       break;
     }
     case JBC_laload :
     case JBC_daload : {
       currBBMap[currBBStkTop-1] = NON_REFERENCE;
       currBBGCPointCount++;
       break;
     }

     case JBC_aaload : {
       currBBStkTop--;
       currBBGCPointCount++;
       break;
     }

     case JBC_iastore :
     case JBC_fastore :
     case JBC_aastore :
     case JBC_bastore :
     case JBC_castore :
     case JBC_sastore : {
       currBBStkTop = currBBStkTop - 3;
       currBBGCPointCount++;
       break;
     }
     case JBC_lastore :
     case JBC_dastore : {
       currBBStkTop = currBBStkTop - 4;
       currBBGCPointCount++;
       break;
     }

     case JBC_newarray : {
       currBBGCPointCount++;
       currBBMap[currBBStkTop] = REFERENCE;
       i = i + 1;
       break;
     }
     case JBC_anewarray : {
       currBBGCPointCount++;
       currBBMap[currBBStkTop] = REFERENCE;
       i = i + 2;
       break;
     }
     case JBC_multianewarray : {
       short dim = (short)(((int)bytecodes[i+3]) & 0xFF);
       currBBGCPointCount++;
       currBBStkTop = currBBStkTop - dim + 1;
       currBBMap[currBBStkTop] = REFERENCE;
       i = i + 3;
       break;
     }
     case JBC_arraylength : {
       currBBMap[currBBStkTop] = NON_REFERENCE;
       break;
     }
     case JBC_athrow : {
       currBBGCPointCount++;
       currBBStkTop = currBBStkEmpty+1;
       currBBMap[currBBStkTop] = REFERENCE;
       processNextBlock = false;
       break;
     }
     case JBC_monitorenter : {
       ///???
       currBBStkTop--;
       break;
     }
     case JBC_monitorexit : {
       ///???
       currBBStkTop--;
       break;
     }
     case JBC_wide : {
       int wopcode = ((int)bytecodes[i+1]) & 0xFF;
       i = i + 1;

       if (wopcode == JBC_iinc) {
           i = i + 4;
       } else {
           int index = (((int)bytecodes[i+1]) << 8 | 
			(((int)bytecodes[i+2]) & 0xFF)) & 0xFFFF;
           switch (wopcode) {

             case JBC_iload :
             case JBC_fload : {
	       currBBStkTop++;
	       currBBMap[currBBStkTop] = NON_REFERENCE;
               break;
             }
             case JBC_lload :
             case JBC_dload : {
	       currBBStkTop++;
	       currBBMap[currBBStkTop] = NON_REFERENCE;
	       currBBStkTop++;
	       currBBMap[currBBStkTop] = NON_REFERENCE;
               break;
             }
             case JBC_aload : {
	       if (blockDefMaps[mapIndex+index] != REFERENCE)
		 blockReqMaps[mapIndex+index] = REFERENCE;
	       currBBStkTop++;
	       currBBMap[currBBStkTop] = REFERENCE;
               break;
             }
             case JBC_istore :
             case JBC_fstore : {
               if (!inJSRSubroutine)
                   blockDefMaps[mapIndex+index] = NON_REFERENCE;
               else
                   currBBMap[index]=SET_TO_NONREFERENCE;
	       currBBStkTop--;
               break;
             }
             case JBC_lstore :
             case JBC_dstore : {
               if (!inJSRSubroutine) {
                   blockDefMaps[mapIndex+index]   = NON_REFERENCE;
                   blockDefMaps[mapIndex+index+1] = NON_REFERENCE;
               }
               else {
                   currBBMap[index]   = SET_TO_NONREFERENCE;
                   currBBMap[index+1] = SET_TO_NONREFERENCE;
	       }
	       currBBStkTop = currBBStkTop - 2;
               break;
             }
	     case JBC_astore : {
	       blockDefMaps[mapIndex+index] = REFERENCE;
	       currBBStkTop--;
	       break;
	     }
           }
           i = i + 2;
       }
       break;
     }
     default : {
       VM.sysWrite("VM_BuildLiveRefMaps: Unknown opcode:");
       VM.sysWrite(opcode);
       VM.sysWrite("\n");
       System.exit(10);
     }

     }  // end switch (opcode)

    }  // for start to end

    if (processNextBlock) {
      short fallThruBBNum = byteToBlockMap[i];
      workStk = processBranchBB(fallThruBBNum, currBBStkTop, currBBMap, 
				currBBStkEmpty, inJSRSubroutine, bbStartMaps, 
				blockStkTop, currPendingRET, bbPendingRETs, workStk);

    }

    if (currBBGCPointCount != 0) {
      blockHasGCPoint[currBBNum] = true;
      if (currBBGCPointCount > maxBlockGCPointCount)
        maxBlockGCPointCount = currBBGCPointCount;
    }

    // if the work stack is empty, we must have processed the whole program
    // we can now process the try handlers
    // If a handler doesn't have a starting map already, then the associated try
    // has not been processed yet. The try and the handler must be in another 
    // handler, so that handler must be processed first.
    // If one handler is in the try block associated with a second handler, then 
    // the second handler must not be processed until the first handler has been.
    //
    if ((workStkTop == -1) && !handlersAllDone ) {
     for (i=0; i < tryHandlerLength; i++) {
       if (handlerProcessed[i] || 
	   bbStartMaps[byteToBlockMap[tryHandlerPC[i]]] == null)
	 continue;   // already processed this handler, or, haven't seen the 
                     // associated try block yet so no starting map is available,
                     // the try block must be in one of the other handlers
       else 
         break;
     }

     if (i == tryHandlerLength)
       handlersAllDone = true;
     else {
       int considerIndex = i;

       while (i != tryHandlerLength) {

         int tryStart = tryStartPC[considerIndex];
         int tryEnd   = tryEndPC[considerIndex];

         for (i=0; i<tryHandlerLength; i++)
	   // For every handler that has not yet been processed, 
	   // but already has a known starting map,
	   // make sure it is not in the try block part of the handler
	   // we are considering working on. 
	   if (!handlerProcessed[i] &&
	      tryStart <= tryHandlerPC[i] &&
	      tryHandlerPC[i] < tryEnd &&
	      bbStartMaps[byteToBlockMap[tryHandlerPC[i]]] != null)
	     break;
	 if (i != tryHandlerLength)
	   considerIndex = i;
       }

       short blockNum = byteToBlockMap[tryHandlerPC[considerIndex]];
       handlerProcessed[considerIndex] = true;
       workStk = addtoWorkStk(blockNum, workStk);
      }
    }

  }  // while workStk not empty  1st pass

  //----------------------------------------------------------
  //
  //  Second pass: compute - Exit Live local sets for each block
  //                       - variable and operand maps for each gc site
  //               record - live variable and operand map for each gc site
  //  Keep looping until the work stack is empty
  //
  //----------------------------------------------------------



  byte     blockLiveExitMaps[];                 // The defined map for each block
  byte     liveMap[]; 
  boolean  blockAddedtoWorkStk[];

  blockLiveExitMaps = new byte[blockCount*localCount];
  liveMap           = new byte[localCount];
  blockAddedtoWorkStk = new boolean[blockCount];

  // The work stack starts with all the predecessors of the EXIT block.
  int exitPredecessors[] = basicBlocks[VM_BasicBlock.EXITBLOCK].getPredecessors();
  int exitLength         = exitPredecessors.length;

  for (i = 0; i < exitLength; i++) {
    workStk = addtoWorkStk((short)(exitPredecessors[i]), workStk);
    blockAddedtoWorkStk[exitPredecessors[i]] = true;
  }
    
  while (workStkTop > -1) {

    // Get the next item off the work stack
    currBBNum = workStk[workStkTop];
    workStkTop--;

    if (bbStartMaps[currBBNum] == null)
      continue; // Must be a block that is not reachable from the method entry point
                // dead code. ignore.

    boolean inJSRSubroutine = false;

    // Calculate Live Start map for this block
    int n = (currBBNum-1) * localCount;
    for (i=0; i< localCount; i++) {
      if (blockDefMaps[n+i] == SET_TO_REFERENCE)
	liveMap[i] = NON_REFERENCE;
      else 
	liveMap[i] = blockLiveExitMaps[n+i];

      liveMap[i] =(byte)(liveMap[i] | blockReqMaps[n+i]);
    }

    for (int k = 0; k<=currBBStkEmpty; k++)
	bbStartMaps[currBBNum][k] = liveMap[k];


    // Get all the predecessors on the work Q

    int blockPreds[] = basicBlocks[currBBNum].getPredecessors();
    int blockPredsLength = blockPreds.length;
    for (i=0; i< blockPredsLength; i++) {

      n = (blockPreds[i]-1) * localCount;
      if (!blockAddedtoWorkStk[blockPreds[i]]) {
	workStk = addtoWorkStk((short)(blockPreds[i]), workStk);
	blockAddedtoWorkStk[blockPreds[i]] = true;
	for (int j=0; j< localCount; j++) 
	  blockLiveExitMaps[n+j] = liveMap[j];
      }
      else {
	int misMatchAt = -1;
	for (int j=0; j<localCount; j++)
	  if (liveMap[j] != blockLiveExitMaps[n+j]) {
	    misMatchAt = j;
	    break;
	  }
	if (misMatchAt != -1) {
	  workStk = addUniquetoWorkStk((short)(blockPreds[i]), workStk);
	  for (int j=misMatchAt; j<localCount; j++)
	    blockLiveExitMaps[n+j] =(byte)(blockLiveExitMaps[n+j] | liveMap[j]);
	}
      }
    }


    // if the work stack is empty, we must have processed the whole program
    // we can now process the try handlers
    // If a handler doesn't have a starting map already, then the associated try
    // has not been processed yet. The try and the handler must be in another 
    // handler, so that handler must be processed first.
    // If one handler is in the try block associated with a second handler, then 
    // the second handler must not be processed until the first handler has been.
    //
    if ((workStkTop == -1) && !handlersAllDone ) {
     for (i=0; i < tryHandlerLength; i++) {
       if (handlerProcessed[i] || 
	   bbStartMaps[byteToBlockMap[tryHandlerPC[i]]] == null)
	 continue;   // already processed this handler, or, haven't seen the 
                     // associated try block yet so no starting map is available,
                     // the try block must be in one of the other handlers
       else 
         break;
     }
     if (i == tryHandlerLength)
       handlersAllDone = true;
     else {
       int considerIndex = i;

       while (i != tryHandlerLength) {

         int tryStart = tryStartPC[considerIndex];
         int tryEnd   = tryEndPC[considerIndex];

         for (i=0; i<tryHandlerLength; i++) {
	   // For every handler that has not yet been processed, but already has 
	   // a known starting map, make sure it is not in the try block part of 
	   // the handler we are considering working on. 
	   //
	   if (!handlerProcessed[i] &&
	      tryStart <= tryHandlerPC[i] &&
	      tryHandlerPC[i] < tryEnd &&
	      bbStartMaps[byteToBlockMap[tryHandlerPC[i]]] != null)
	     break;
	 }

	 if (i != tryHandlerLength)
	   considerIndex = i;

       } // while (i != ...)

       short blockNum = byteToBlockMap[tryHandlerPC[considerIndex]];
       handlerProcessed[considerIndex] = true;
       workStk = addtoWorkStk(blockNum, workStk);
      }
    }
 
  }  // while workStk not empty  2nd pass

  //----------------------------------------------------------
  //
  //  Third pass:  compute - variable and operand maps for each gc site
  //               record - live variable and operand map for each gc site
  //     Proceed by walking each block that has some GC points.
  //
  //----------------------------------------------------------

  byte finalLocalMaps[] = new byte[maxBlockGCPointCount * localCount];

  for (currBBNum = 1; currBBNum < blockCount; currBBNum++) {

    // Don't process the content of the block, if it does NOT have any GC points
    if (!blockHasGCPoint[currBBNum])
      continue;

    int  currGCPointCount = 0;

    boolean inJSRSubroutine = false;

    int n = (currBBNum-1)*localCount;
    for (int k = 0; k < localCount; k++) 
      currBBMap[k] = blockLiveExitMaps[n+k];

    int start = basicBlocks[currBBNum].getStart();
    int end = basicBlocks[currBBNum].getEnd();


    // Pass 3 - backward walk through block, to determine locals 
    //          and save the locals map for each GC point

    for (i=end; i>= start; i--) {
      if (byteToBlockMap[i] == VM_BasicBlock.NOTBLOCK) continue;

      int opcode = ((int)bytecodes[i]) & 0x000000FF;

      switch (opcode) {

      case JBC_aload : {
       int index = ((int)bytecodes[i+1]) & 0xFF;  // Get local index - unsigned byte.
       currBBMap[index] = REFERENCE;
       break;
      }
 
      case JBC_aload_0 : {
       currBBMap[0] = REFERENCE;
       break;
      }
      
      case JBC_aload_1 : {
       currBBMap[1] = REFERENCE;
       break;
      }

      case JBC_aload_2 : {
       currBBMap[2] = REFERENCE;
       break;
      }

      case JBC_aload_3 : {
       currBBMap[3] = REFERENCE;
       break;
      }

      case JBC_astore : {
	// Get local index - unsigned byte.
       int index = ((int)bytecodes[i+1]) & 0xFF; 
       currBBMap[index] = NON_REFERENCE;
       break;
      }

      case JBC_astore_0 : {
       currBBMap[0] = NON_REFERENCE;
       break;
      }

      case JBC_astore_1 : {
       currBBMap[1] = NON_REFERENCE;
       break;
      }

      case JBC_astore_2 : {
       currBBMap[2] = NON_REFERENCE;
       break;
      }
      
      case JBC_astore_3 : {
       currBBMap[3] = NON_REFERENCE;
       break;
      }

      case JBC_irem :
      case JBC_idiv :
      case JBC_lrem :
      case JBC_ldiv :
      case JBC_invokevirtual :
      case JBC_invokespecial :
      case JBC_invokeinterface :
      case JBC_getstatic :
      case JBC_putstatic :
      case JBC_getfield :
      case JBC_putfield :
      case JBC_checkcast :
      case JBC_instanceof :
      case JBC_new :
      case JBC_iaload :
      case JBC_faload :
      case JBC_baload :
      case JBC_caload :
      case JBC_saload :
      case JBC_laload :
      case JBC_daload :
      case JBC_aaload :
      case JBC_iastore :
      case JBC_fastore :
      case JBC_aastore :
      case JBC_bastore :
      case JBC_castore :
      case JBC_sastore :
      case JBC_lastore :
      case JBC_dastore :
      case JBC_newarray :
      case JBC_anewarray :
      case JBC_multianewarray :
      case JBC_athrow : {
       int index = currGCPointCount*localCount;
       for (int k=0; k<localCount; k++) 
         finalLocalMaps[index+k] = currBBMap[k];
       currGCPointCount++;
       break;
      }

      case JBC_invokestatic : {
       int index = (((int)bytecodes[i+1]) << 8 | 
		    (((int)bytecodes[i+2]) & 0xFF)) & 0xFFFF;
       VM_Method calledMethod = declaringClass.getMethodRef(index);
       if (calledMethod.getDeclaringClass().isMagicType()) {
         boolean producesCall = VM_MagicCompiler.checkForActualCall(calledMethod);
         if (producesCall) {
           int mapIndex = currGCPointCount*localCount;
           for (int k=0; k<localCount; k++) 
             finalLocalMaps[mapIndex+k] = currBBMap[k];
           currGCPointCount++;
	 }
       }
       else {
           int mapIndex = currGCPointCount*localCount;
           for (int k=0; k<localCount; k++) 
             finalLocalMaps[mapIndex+k] = currBBMap[k];
           currGCPointCount++;
       }
       break;
      }

      case JBC_monitorenter : {
       ///???
       break;
      }
      case JBC_monitorexit : {
       ///???
       break;
      }

      case JBC_wide : {
       int wopcode = ((int)bytecodes[i+1]) & 0xFF;

       if (wopcode != JBC_iinc) {
           int index = (((int)bytecodes[i+2]) << 8 | 
			(((int)bytecodes[i+3]) & 0xFF)) & 0xFFFF;
           switch (wopcode) {

             case JBC_aload : {
               currBBMap[index] = REFERENCE;
               break;
             }
	     case JBC_astore : {
               currBBMap[index] = NON_REFERENCE;
	       break;
	     }
            default : {
               break;
             }
           }
       }
       break;
      }
      default : {
       break;
      }

      }  // end switch (opcode)

    }  // for start to end

    // Pass 3 - Forward walk through block get get detailed operand stack map
    //      - record the maps

    boolean inTryBlock = false;
    currPendingRET = null;

    currBBStkTop = blockStkTop[currBBNum];
    for (int k = currBBStkEmpty+1; k <= currBBStkTop; k++) 
      currBBMap[k] = bbStartMaps[currBBNum][k];
    int mapIndex;

    if (jsrCount > 0 && basicBlocks[currBBNum].isInJSR()) {
     inJSRSubroutine = true;
    }

    for (i=start; i<= end; i++) {
      int opcode = ((int)bytecodes[i]) & 0x000000FF;

     switch (opcode) {

     case JBC_nop : {
        break;
     }
     case JBC_aconst_null : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = REFERENCE;
       break;
     }
     case JBC_iconst_m1 :
     case JBC_iconst_0 :
     case JBC_iconst_1 :
     case JBC_iconst_2 :
     case JBC_iconst_3 :
     case JBC_iconst_4 :
     case JBC_iconst_5 :
     case JBC_fconst_0 :
     case JBC_fconst_1 :
     case JBC_fconst_2 : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       break;
     }
     case JBC_lconst_0 :
     case JBC_lconst_1 :
     case JBC_dconst_0 :
     case JBC_dconst_1 : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       break;
     }
     case JBC_bipush : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       i = i + 1;
       break;
     }
     case JBC_sipush : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       i = i + 2;
       break;
     }
     case JBC_ldc : {
       // Get cp index - unsigned 8-bit.
       int index = ((int)bytecodes[i+1]) & 0xFF;
       currBBStkTop++;

       if (declaringClass.getLiteralDescription(index) == VM_Statics.STRING_LITERAL)
         currBBMap[currBBStkTop] = REFERENCE;
       else
	 currBBMap[currBBStkTop] = NON_REFERENCE;
       i = i + 1;
       break;
     }
     case JBC_ldc_w : {
       // Get cpindex - unsigned 16-bit.
       int index = (((int)bytecodes[i+1]) << 8 |
		    (((int)bytecodes[i+2]) & 0xFF)) & 0xFFFF;
       currBBStkTop++;
       if (declaringClass.getLiteralDescription(index) == VM_Statics.STRING_LITERAL)
         currBBMap[currBBStkTop] = REFERENCE;
       else
	 currBBMap[currBBStkTop] = NON_REFERENCE;
       i = i + 2;
       break;
     }
     case JBC_ldc2_w : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       i = i + 2;
       break;
     }
     case JBC_iload :
     case JBC_fload : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       i = i + 1;
       break;
     }
     case JBC_lload :
     case JBC_dload : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       i = i + 1;
       break;
     }
     case JBC_aload : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = REFERENCE;
       i = i + 1;
       break;
     }
     case JBC_iload_0 :
     case JBC_iload_1 :
     case JBC_iload_2 :
     case JBC_iload_3 :
     case JBC_fload_0 :
     case JBC_fload_1 :
     case JBC_fload_2 :
     case JBC_fload_3 : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       break;
     }
     case JBC_lload_0 :
     case JBC_lload_1 :
     case JBC_lload_2 :
     case JBC_lload_3 :
     case JBC_dload_0 :
     case JBC_dload_1 :
     case JBC_dload_2 :
     case JBC_dload_3 : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       currBBStkTop++;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       break;
     }

     case JBC_aload_0 :
     case JBC_aload_1 :
     case JBC_aload_2 :
     case JBC_aload_3 : {
       currBBStkTop++;
       currBBMap[currBBStkTop] = REFERENCE;
       break;
     }

     case JBC_istore :
     case JBC_fstore : {
       int index = ((int)bytecodes[i+1]) & 0xFF;  // Get local index - unsigned byte.
       if (!inJSRSubroutine) 
	  currBBMap[index]=NON_REFERENCE;
       else
	  currBBMap[index]=SET_TO_NONREFERENCE;
       if (inTryBlock) 
	    setHandlersMapsNonRef(index, 1, reachableHandlerBBNums, 
				  reachableHandlersCount, inJSRSubroutine, 
				  bbStartMaps);
       currBBStkTop--;
       i = i + 1;
       break;
     }
     case JBC_lstore :
     case JBC_dstore : {
       // Get local index - unsigned byte.
       int index = ((int)bytecodes[i+1]) & 0xFF; 
       if (!inJSRSubroutine) {
         currBBMap[index]=NON_REFERENCE;
         currBBMap[index+1]=NON_REFERENCE;
       }
       else {
	  currBBMap[index]=SET_TO_NONREFERENCE;
	  currBBMap[index+1]=SET_TO_NONREFERENCE;
       }

       if (inTryBlock) 
	 setHandlersMapsNonRef(index, 2, reachableHandlerBBNums, 
			       reachableHandlersCount, inJSRSubroutine, bbStartMaps);
       currBBStkTop = currBBStkTop - 2;
       i = i + 1;
       break;
     }
     case JBC_astore : {
       // Get local index - unsigned byte.
       int index = ((int)bytecodes[i+1]) & 0xFF;   
       currBBMap[index] = currBBMap[currBBStkTop]; // may be a reference or a return 
                                                   // address
       if (currBBMap[index] == RETURN_ADDRESS) 
	 currPendingRET.updateReturnAddressLocation(index);
       if (inTryBlock && inJSRSubroutine) {
         if (currBBMap[index] == REFERENCE)
             setHandlersMapsRef(index, reachableHandlerBBNums, 
				reachableHandlersCount, bbStartMaps);
	 else
             setHandlersMapsReturnAddress(index, reachableHandlerBBNums, 
					  reachableHandlersCount, bbStartMaps);
       }
       currBBStkTop--;
       i = i + 1;
       break;
     }
     case JBC_istore_0 :
     case JBC_fstore_0 : {
       if (!inJSRSubroutine)
           currBBMap[0]=NON_REFERENCE;
       else 
	   currBBMap[0]=SET_TO_NONREFERENCE;
       if (inTryBlock) 
             setHandlersMapsNonRef(0, 1, reachableHandlerBBNums, 
				   reachableHandlersCount,inJSRSubroutine, 
				   bbStartMaps);
       currBBStkTop--;
       break;
     }
     case JBC_istore_1 :
     case JBC_fstore_1 : {
      if (!inJSRSubroutine)
        currBBMap[1]=NON_REFERENCE;
      else 
         currBBMap[1]=SET_TO_NONREFERENCE;
      if (inTryBlock) 
           setHandlersMapsNonRef(1, 1, reachableHandlerBBNums, 
				 reachableHandlersCount, inJSRSubroutine, 
				 bbStartMaps);
       currBBStkTop--;
       break;
     }
     case JBC_istore_2 :
     case JBC_fstore_2 : {
       if (!inJSRSubroutine)
           currBBMap[2]=NON_REFERENCE;
       else
	   currBBMap[2]=SET_TO_NONREFERENCE;
       if (inTryBlock) 
           setHandlersMapsNonRef(2, 1, reachableHandlerBBNums, 
				 reachableHandlersCount, inJSRSubroutine, 
				 bbStartMaps);
       currBBStkTop--;
       break;
     }
     case JBC_istore_3 :
     case JBC_fstore_3 : {
       if (!inJSRSubroutine)
           currBBMap[3]=NON_REFERENCE;
       else
	   currBBMap[3]=SET_TO_NONREFERENCE;
       if (inTryBlock) 
           setHandlersMapsNonRef(3, 1, reachableHandlerBBNums, 
				 reachableHandlersCount, inJSRSubroutine, 
				 bbStartMaps);
       currBBStkTop--;
       break;
     }
     case JBC_lstore_0 :
     case JBC_dstore_0 : {
       if (inJSRSubroutine) {
           currBBMap[0]=NON_REFERENCE;
           currBBMap[1]=NON_REFERENCE;
       }
       else {
	    currBBMap[0]=SET_TO_NONREFERENCE;
	    currBBMap[1]=SET_TO_NONREFERENCE;
       }
       if (inTryBlock) 
           setHandlersMapsNonRef(0,2,reachableHandlerBBNums, reachableHandlersCount,
				 inJSRSubroutine, bbStartMaps);
       currBBStkTop = currBBStkTop - 2;
       break;
     }
     case JBC_lstore_1 :
     case JBC_dstore_1 : {
       if (!inJSRSubroutine) {
           currBBMap[1]=NON_REFERENCE;
           currBBMap[2]=NON_REFERENCE;
       }
       else {
           currBBMap[1]=SET_TO_NONREFERENCE;
           currBBMap[2]=SET_TO_NONREFERENCE;
       }
       if (inTryBlock) 
           setHandlersMapsNonRef(1,2,reachableHandlerBBNums, reachableHandlersCount,
				 inJSRSubroutine, bbStartMaps);
       currBBStkTop = currBBStkTop - 2;
       break;
     }
     case JBC_lstore_2 :
     case JBC_dstore_2 : {
       if (!inJSRSubroutine) {
           currBBMap[2]=NON_REFERENCE;
           currBBMap[3]=NON_REFERENCE;
       }
       else {
           currBBMap[2]=SET_TO_NONREFERENCE;
           currBBMap[3]=SET_TO_NONREFERENCE;
       }
       if (inTryBlock) 
           setHandlersMapsNonRef(2,2,reachableHandlerBBNums, reachableHandlersCount,
				 inJSRSubroutine, bbStartMaps);
       currBBStkTop = currBBStkTop - 2;
       break;
     }
     case JBC_lstore_3 :
     case JBC_dstore_3 : {
       if (!inJSRSubroutine) {
           currBBMap[3]=NON_REFERENCE;
           currBBMap[4]=NON_REFERENCE;
       }
       else {
           currBBMap[3]=SET_TO_NONREFERENCE;
           currBBMap[4]=SET_TO_NONREFERENCE;
       }
       if (inTryBlock) 
           setHandlersMapsNonRef(3,2,reachableHandlerBBNums, reachableHandlersCount,
				 inJSRSubroutine, bbStartMaps);
       currBBStkTop = currBBStkTop - 2;
       break;
     }

     case JBC_astore_0 : {
       currBBMap[0]=currBBMap[currBBStkTop];
       if (inTryBlock && inJSRSubroutine)
	   setHandlersMapsRef(0, reachableHandlerBBNums, reachableHandlersCount, 
			      bbStartMaps);
       currBBStkTop--;
       break;
     }
     case JBC_astore_1 : {
       currBBMap[1]=currBBMap[currBBStkTop];
       if (inTryBlock && inJSRSubroutine)
	   setHandlersMapsRef(1, reachableHandlerBBNums, reachableHandlersCount, 
			      bbStartMaps);
       currBBStkTop--;
       break;
     }
     case JBC_astore_2 : {
       currBBMap[2]=currBBMap[currBBStkTop];
       if (inTryBlock && inJSRSubroutine)
	   setHandlersMapsRef(2, reachableHandlerBBNums, reachableHandlersCount, 
			      bbStartMaps);
       currBBStkTop--;
       break;
     }
     case JBC_astore_3 : {
       currBBMap[3]=currBBMap[currBBStkTop];
       if (inTryBlock && inJSRSubroutine)
	   setHandlersMapsRef(3, reachableHandlerBBNums, reachableHandlersCount, 
			      bbStartMaps);
       currBBStkTop--;
       break;
     }
     case JBC_pop : {
       currBBStkTop--;
       break;
     }
     case JBC_pop2 : {
       currBBStkTop = currBBStkTop - 2;
       break;
     }
     case JBC_dup : {
       currBBMap[currBBStkTop+1] = currBBMap[currBBStkTop];
       currBBStkTop++;
       break;
     }
     case JBC_dup2 : {
       currBBMap[currBBStkTop+1] = currBBMap[currBBStkTop-1];
       currBBMap[currBBStkTop+2] = currBBMap[currBBStkTop];
       currBBStkTop = currBBStkTop + 2;
       break;
     }
     case JBC_dup_x1 : {
       currBBMap[currBBStkTop+1] = currBBMap[currBBStkTop];
       currBBMap[currBBStkTop]   = currBBMap[currBBStkTop-1];
       currBBMap[currBBStkTop-1] = currBBMap[currBBStkTop+1];
       currBBStkTop++;
       break;
     }
     case JBC_dup2_x1 : {
       currBBMap[currBBStkTop+2] = currBBMap[currBBStkTop];
       currBBMap[currBBStkTop+1] = currBBMap[currBBStkTop-1];
       currBBMap[currBBStkTop]   = currBBMap[currBBStkTop-2];
       currBBMap[currBBStkTop-1] = currBBMap[currBBStkTop+2];
       currBBMap[currBBStkTop-2] = currBBMap[currBBStkTop+1];
       currBBStkTop = currBBStkTop + 2;
       break;
     }
     case JBC_dup_x2 : {
       currBBMap[currBBStkTop+1] = currBBMap[currBBStkTop];
       currBBMap[currBBStkTop]   = currBBMap[currBBStkTop-1];
       currBBMap[currBBStkTop-1] = currBBMap[currBBStkTop-2];
       currBBMap[currBBStkTop-2] = currBBMap[currBBStkTop+1];
       currBBStkTop++;
       break;
     }
     case JBC_dup2_x2 : {
       currBBMap[currBBStkTop+2] = currBBMap[currBBStkTop];
       currBBMap[currBBStkTop+1] = currBBMap[currBBStkTop-1];
       currBBMap[currBBStkTop]   = currBBMap[currBBStkTop-2];
       currBBMap[currBBStkTop-1] = currBBMap[currBBStkTop-3];
       currBBMap[currBBStkTop-2] = currBBMap[currBBStkTop+2];
       currBBMap[currBBStkTop-3] = currBBMap[currBBStkTop+1];
       currBBStkTop = currBBStkTop + 2;
       break;
     }
     case JBC_swap : {
       byte temp;
       temp = currBBMap[currBBStkTop];
       currBBMap[currBBStkTop]   = currBBMap[currBBStkTop-1];
       currBBMap[currBBStkTop-1] = temp;
       break;
     }
     case JBC_iadd :
     case JBC_fadd :
     case JBC_isub :
     case JBC_fsub :
     case JBC_imul :
     case JBC_fmul :
     case JBC_fdiv :
     case JBC_frem :
     case JBC_ishl :
     case JBC_ishr :
     case JBC_iushr :
     case JBC_lshl :      // long shifts that int shift value
     case JBC_lshr :
     case JBC_lushr :
     case JBC_iand :
     case JBC_ior :
     case JBC_ixor : {
       currBBStkTop--;
       break;
     }
     case JBC_irem :
     case JBC_idiv : {
       currBBStkTop = currBBStkTop-2;
       currGCPointCount--;
       mapIndex = currGCPointCount*localCount;
       for (int k = 0; k < localCount; k++) 
        currBBMap[k] = finalLocalMaps[mapIndex+k];

       // record map after 2 integers popped off stack
       if (!inJSRSubroutine) 
         referenceMaps.recordStkMap(i, currBBMap, currBBStkTop, 
				    blockSeen[currBBNum]);
       else
         referenceMaps.recordJSRSubroutineMap(i, currBBMap, currBBStkTop, 
					      currPendingRET.returnAddressLocation, 
					      blockSeen[currBBNum]);

      currBBStkTop++;
      break;
     }
     case JBC_ladd :
     case JBC_dadd :
     case JBC_lsub :
     case JBC_dsub :
     case JBC_lmul :
     case JBC_dmul :
     case JBC_ddiv :
     case JBC_drem :
     case JBC_land :
     case JBC_lor :
     case JBC_lxor : {
       currBBStkTop = currBBStkTop - 2;
       break;
     }
     case JBC_lrem :
     case JBC_ldiv : {
       currBBStkTop = currBBStkTop - 4;
       currGCPointCount--;
       mapIndex = currGCPointCount*localCount;
       for (int k = 0; k < localCount; k++) 
        currBBMap[k] = finalLocalMaps[mapIndex+k];
       // record map after 2 longs popped off stack
       if (!inJSRSubroutine)
         referenceMaps.recordStkMap(i, currBBMap, currBBStkTop, 
				    blockSeen[currBBNum]);
       else
         referenceMaps.recordJSRSubroutineMap(i, currBBMap, currBBStkTop, 
					      currPendingRET.returnAddressLocation, 
					      blockSeen[currBBNum]);
       currBBStkTop = currBBStkTop + 2;
       break;
     }
     case JBC_ineg :
     case JBC_lneg :
     case JBC_fneg :
     case JBC_dneg : {
       break;
     }


     case JBC_iinc : {
       i = i + 2;
       break;
     }
     case JBC_i2l :
     case JBC_i2d :
     case JBC_f2l :
     case JBC_f2d : {
       currBBMap[++currBBStkTop] = NON_REFERENCE;
       break;
     }
     case JBC_i2f :
     case JBC_l2d :
     case JBC_f2i :
     case JBC_d2l :
     case JBC_int2byte :
     case JBC_int2char :
     case JBC_int2short : {
       break;
     }
     case JBC_l2i :
     case JBC_l2f :
     case JBC_d2i :
     case JBC_d2f : {
       currBBStkTop--;
       break;
     }
     case JBC_lcmp :
     case JBC_dcmpl :
     case JBC_dcmpg : {
       currBBStkTop = currBBStkTop - 3;
       break;
     }
     case JBC_fcmpl :
     case JBC_fcmpg : {
       currBBStkTop--;
       break;
     }
     case JBC_ifeq :
     case JBC_ifne :
     case JBC_iflt :
     case JBC_ifge :
     case JBC_ifgt :
     case JBC_ifle : {
       short offset = (short)(((int)bytecodes[i+1]) << 8 
			      | (((int)bytecodes[i+2]) & 0xFF));
       currBBStkTop--;
       i = i + 2;
       break;
     }

     case JBC_if_icmpeq :
     case JBC_if_icmpne :
     case JBC_if_icmplt :
     case JBC_if_icmpge :
     case JBC_if_icmpgt :
     case JBC_if_icmple :
     case JBC_if_acmpeq :
     case JBC_if_acmpne : {
       short offset = (short)(((int)bytecodes[i+1]) << 8 
			      | (((int)bytecodes[i+2]) & 0xFF));
       currBBStkTop = currBBStkTop - 2;
       i = i + 2;
       break;
     }

     case JBC_ifnull :
     case JBC_ifnonnull : {
       short offset = (short)(((int)bytecodes[i+1]) << 8 
			      | (((int)bytecodes[i+2]) & 0xFF));
       currBBStkTop--;
       i = i + 2;
       break;
     }

     case JBC_goto : {
       // The offset is a 16-bit value, but we sign extend to an int, and
       // it still works.
       //
       int offset = (int)((short)(((int)bytecodes[i+1]) << 8 
				  | (((int)bytecodes[i+2]) & 0xFF)));
       i = i + 2;
       break;
     }
     case JBC_goto_w : {
       int offset = getIntOffset(i, bytecodes);
       i = i + 4;
       break;
     }
     case JBC_tableswitch : {
       i = end+1;  // must be last instruction of block, force loop thru 
                   // bytecode to finish
       break;
     }
     case JBC_lookupswitch : {
       i = end+1;  // must be last instruction of block, force loop thru 
                   // bytecode to finish
       break;
     }
     case JBC_jsr : {
       short offset = (short)(((int)bytecodes[i+1]) << 8 | 
			      (((int)bytecodes[i+2]) & 0xFF));
       currGCPointCount--;
       mapIndex = currGCPointCount*localCount;
       for (int k = 0; k < localCount; k++) 
        currBBMap[k] = finalLocalMaps[mapIndex+k];
       if (!inJSRSubroutine)
         referenceMaps.recordStkMap(i, currBBMap, currBBStkEmpty, 
				    blockSeen[currBBNum]);
       else
         referenceMaps.recordJSRSubroutineMap(i, currBBMap, currBBStkEmpty, 
					      currPendingRET.returnAddressLocation, 
					      blockSeen[currBBNum]);
       currBBStkTop++;
       currBBMap[currBBStkTop] = RETURN_ADDRESS; 
       workStk = processJSR(byteToBlockMap[i], i+offset, byteToBlockMap[i+offset], 
			    byteToBlockMap[i+3], bbStartMaps, currBBStkTop, 
			    currBBMap, currBBStkEmpty, blockStkTop, bbPendingRETs, 
			    currPendingRET, JSRSubroutines, workStk);
       i = i + 2;
       break;
     }
     case JBC_jsr_w : {
       currGCPointCount--;
       mapIndex = currGCPointCount*localCount;
       for (int k = 0; k < localCount; k++) 
        currBBMap[k] = finalLocalMaps[mapIndex+k];
       if (!inJSRSubroutine)
         referenceMaps.recordStkMap(i, currBBMap, currBBStkEmpty, 
				    blockSeen[currBBNum]);
       else
         referenceMaps.recordJSRSubroutineMap(i, currBBMap, currBBStkEmpty, 
					      currPendingRET.returnAddressLocation, 
					      blockSeen[currBBNum]);
       currBBStkTop++;
       currBBMap[currBBStkTop] = RETURN_ADDRESS;  

       int offset = getIntOffset(i, bytecodes);
       workStk = processJSR(byteToBlockMap[i], i+offset, byteToBlockMap[i+offset], 
			    byteToBlockMap[i+5], bbStartMaps, currBBStkTop, 
			    currBBMap, currBBStkEmpty, blockStkTop, bbPendingRETs, 
			    currPendingRET, JSRSubroutines, workStk);
       i = i + 4;
       break;
     }
     case JBC_ret : {
       // Index of local variable (unsigned byte)
       int index = ((int)bytecodes[i+1]) & 0xFF;

       // Can not be used again as a return addr. 
       //
       currBBMap[index] = SET_TO_NONREFERENCE;  
       int subStart = currPendingRET.JSRSubStartByteIndex;
       int k;
       for (k=0; k<JSRSubNext; k++) {
	 if (JSRSubroutines[k].subroutineByteCodeStart == subStart) {
	   JSRSubroutines[k].newEndMaps(currBBMap, currBBStkTop);
	   break;
	 }
       }
 
       boolean JSRisInJSRSub = bbPendingRETs[currPendingRET.JSRBBNum] != null;
       workStk = computeJSRNextMaps(currPendingRET.JSRNextBBNum, currBBMap.length, 
				    k, JSRisInJSRSub, bbStartMaps, blockStkTop,
				    JSRSubroutines, currBBStkEmpty, workStk);
       if (JSRisInJSRSub && bbPendingRETs[currPendingRET.JSRNextBBNum] == null) 
	   bbPendingRETs[currPendingRET.JSRNextBBNum] = 
                      new VM_PendingRETInfo(bbPendingRETs[currPendingRET.JSRBBNum]);
       i = i + 1;
       break;
     }
     case JBC_invokevirtual :
     case JBC_invokespecial : {
       int index = (((int)bytecodes[i+1]) << 8 | 
		    (((int)bytecodes[i+2]) & 0xFF)) & 0xFFFF;
       VM_Method calledMethod = declaringClass.getMethodRef(index);
       currGCPointCount--;
       mapIndex = currGCPointCount*localCount;
       for (int k = 0; k < localCount; k++) 
        currBBMap[k] = finalLocalMaps[mapIndex+k];
       currBBStkTop = processInvoke(calledMethod, i, currBBStkTop, currBBMap, false, 
                                    inJSRSubroutine, referenceMaps, currPendingRET, 
				    blockSeen[currBBNum], currBBStkEmpty);
       i = i + 2;
       break;
     }
     case JBC_invokestatic : {
       int index = (((int)bytecodes[i+1]) << 8 | 
		    (((int)bytecodes[i+2]) & 0xFF)) & 0xFFFF;
       VM_Method calledMethod = declaringClass.getMethodRef(index);
       if (calledMethod.getDeclaringClass().isMagicType()) {
         boolean producesCall = VM_MagicCompiler.checkForActualCall(calledMethod);
         if (producesCall) {
	   currGCPointCount--;
	   mapIndex = currGCPointCount*localCount;
	   for (int k = 0; k < localCount; k++) 
	     currBBMap[k] = finalLocalMaps[mapIndex+k];
	 }
       }
       else {
	 currGCPointCount--;
	 mapIndex = currGCPointCount*localCount;
	 for (int k = 0; k < localCount; k++) 
	   currBBMap[k] = finalLocalMaps[mapIndex+k];
       }

       currBBStkTop = processInvoke(calledMethod, i, currBBStkTop, currBBMap, true, 
                                    inJSRSubroutine, referenceMaps, currPendingRET, 
                                    blockSeen[currBBNum], currBBStkEmpty);
       i = i + 2;
       break;
     }
     case JBC_invokeinterface : {
       int index = (((int)bytecodes[i+1]) << 8 | 
		    (((int)bytecodes[i+2]) & 0xFF)) & 0xFFFF;
       VM_Method calledMethod = declaringClass.getMethodRef(index);
       currGCPointCount--;
       mapIndex = currGCPointCount*localCount;
       for (int k = 0; k < localCount; k++) 
        currBBMap[k] = finalLocalMaps[mapIndex+k];

       currBBStkTop = processInvoke(calledMethod, i, currBBStkTop, currBBMap, false,
                                    inJSRSubroutine, referenceMaps, currPendingRET, 
				    blockSeen[currBBNum], currBBStkEmpty);
       i = i + 4;
       break;
     }

     case JBC_ireturn :
     case JBC_lreturn :
     case JBC_freturn :
     case JBC_dreturn :
     case JBC_areturn :
     case JBC_return  :{
       break;
     }

     case JBC_getstatic : {
       int index = (((int)bytecodes[i+1]) << 8 | 
		    (((int)bytecodes[i+2]) & 0xFF)) & 0xFFFF;

       currGCPointCount--;
       mapIndex = currGCPointCount*localCount;
       for (int k = 0; k < localCount; k++) 
        currBBMap[k] = finalLocalMaps[mapIndex+k];

       // Register the reference map
       //  note: getstatic could result in a call to the classloader

       if (!inJSRSubroutine)
         referenceMaps.recordStkMap(i, currBBMap, currBBStkTop, 
				    blockSeen[currBBNum]);
       else
         referenceMaps.recordJSRSubroutineMap(i, currBBMap, currBBStkTop, 
					      currPendingRET.returnAddressLocation,
					      blockSeen[currBBNum]);

       VM_Type fieldType = declaringClass.getFieldRef(index).getType();
       currBBMap[++currBBStkTop] = fieldType.isReferenceType() ? 
	                            REFERENCE : NON_REFERENCE;
       if (fieldType.getStackWords() == 2)
          currBBMap[++currBBStkTop] = NON_REFERENCE;
       i = i + 2;
       break;
     }
     case JBC_putstatic : {
       int index = (((int)bytecodes[i+1]) << 8 | 
		    (((int)bytecodes[i+2]) & 0xFF)) & 0xFFFF;
       currGCPointCount--;
       mapIndex = currGCPointCount*localCount;
       for (int k = 0; k < localCount; k++) 
        currBBMap[k] = finalLocalMaps[mapIndex+k];

       // Register the reference map
       //  note: putstatic could result in a call to the classloader

       if (!inJSRSubroutine)
         referenceMaps.recordStkMap(i, currBBMap, currBBStkTop, 
				    blockSeen[currBBNum]);
       else
         referenceMaps.recordJSRSubroutineMap(i, currBBMap, currBBStkTop, 
					      currPendingRET.returnAddressLocation,
					      blockSeen[currBBNum]);
       VM_Type fieldType = declaringClass.getFieldRef(index).getType();
       currBBStkTop--;
       if (fieldType.getStackWords() == 2)
          currBBStkTop--;
       i = i + 2;
       break;
     }
     case JBC_getfield : {
       int index = (((int)bytecodes[i+1]) << 8 | 
		    (((int)bytecodes[i+2]) & 0xFF)) & 0xFFFF;
       VM_Type fieldType = declaringClass.getFieldRef(index).getType();
       currGCPointCount--;
       mapIndex = currGCPointCount*localCount;
       for (int k = 0; k < localCount; k++) 
        currBBMap[k] = finalLocalMaps[mapIndex+k];

       // Register the reference map, minus the object pointer on the stack
       //  note: getfield could result in a call to the classloader

       if (!inJSRSubroutine)
         referenceMaps.recordStkMap(i, currBBMap, currBBStkTop, 
				    blockSeen[currBBNum]);
       else
         referenceMaps.recordJSRSubroutineMap(i, currBBMap, currBBStkTop, 
					      currPendingRET.returnAddressLocation,
					      blockSeen[currBBNum]);
       currBBStkTop--;    // pop object pointer
       currBBMap[++currBBStkTop] = fieldType.isReferenceType() ? 
	                                REFERENCE : NON_REFERENCE;
       if (fieldType.getStackWords() == 2)
          currBBMap[++currBBStkTop] = NON_REFERENCE;
       i = i + 2;
       break;
     }
     case JBC_putfield : {
       int index = (((int)bytecodes[i+1]) << 8 | 
		    (((int)bytecodes[i+2]) & 0xFF)) & 0xFFFF;
       VM_Type fieldType = declaringClass.getFieldRef(index).getType();
       currGCPointCount--;
       mapIndex = currGCPointCount*localCount;
       for (int k = 0; k < localCount; k++) 
        currBBMap[k] = finalLocalMaps[mapIndex+k];

       // Register the reference map with the values still on the stack
       //  note: putfield could result in a call to the classloader 
       if (!inJSRSubroutine)
         referenceMaps.recordStkMap(i, currBBMap, currBBStkTop, 
				    blockSeen[currBBNum]);
       else
         referenceMaps.recordJSRSubroutineMap(i, currBBMap, currBBStkTop, 
					      currPendingRET.returnAddressLocation, 
					      blockSeen[currBBNum]);
       currBBStkTop -= 2;  // remove objectref and one value
       if (fieldType.getStackWords() == 2)
          currBBStkTop--;
       i = i + 2;
       break;
     }
     case JBC_checkcast : {
       currGCPointCount--;
       mapIndex = currGCPointCount*localCount;
       for (int k = 0; k < localCount; k++) 
        currBBMap[k] = finalLocalMaps[mapIndex+k];
       if (!inJSRSubroutine)
         referenceMaps.recordStkMap(i, currBBMap, currBBStkTop, 
				    blockSeen[currBBNum]);
       else
         referenceMaps.recordJSRSubroutineMap(i, currBBMap, currBBStkTop, 
					      currPendingRET.returnAddressLocation,
					      blockSeen[currBBNum]);
       i = i + 2;
       break;
     }
     case JBC_instanceof : {
       currGCPointCount--;
       mapIndex = currGCPointCount*localCount;
       for (int k = 0; k < localCount; k++) 
        currBBMap[k] = finalLocalMaps[mapIndex+k];
       if (!inJSRSubroutine)
         referenceMaps.recordStkMap(i, currBBMap, currBBStkTop, 
				    blockSeen[currBBNum]);
       else
         referenceMaps.recordJSRSubroutineMap(i, currBBMap, currBBStkTop, 
					      currPendingRET.returnAddressLocation,
					      blockSeen[currBBNum]);

       currBBMap[currBBStkTop] = NON_REFERENCE;
       i = i + 2;
       break;
     }
     case JBC_new : {
       currGCPointCount--;
       mapIndex = currGCPointCount*localCount;
       for (int k = 0; k < localCount; k++) 
        currBBMap[k] = finalLocalMaps[mapIndex+k];
       if (!inJSRSubroutine)
         referenceMaps.recordStkMap(i, currBBMap, currBBStkTop, 
				    blockSeen[currBBNum]);
       else
         referenceMaps.recordJSRSubroutineMap(i, currBBMap, currBBStkTop, 
					      currPendingRET.returnAddressLocation, 
					      blockSeen[currBBNum]);
       currBBStkTop++;
       currBBMap[currBBStkTop] = REFERENCE;
       i = i + 2;
       break;
     }
     case JBC_iaload :
     case JBC_faload :
     case JBC_baload :
     case JBC_caload :
     case JBC_saload : {
       currBBStkTop--;
       currBBMap[currBBStkTop] = NON_REFERENCE;
       currGCPointCount--;
       mapIndex = currGCPointCount*localCount;
       for (int k = 0; k < localCount; k++) 
        currBBMap[k] = finalLocalMaps[mapIndex+k];
       if (!inJSRSubroutine) 
         referenceMaps.recordStkMap(i, currBBMap, currBBStkTop, 
				    blockSeen[currBBNum]);
       else 
         referenceMaps.recordJSRSubroutineMap(i, currBBMap, currBBStkTop, 
					      currPendingRET.returnAddressLocation, 
					      blockSeen[currBBNum]);
       break;
     }
     case JBC_laload :
     case JBC_daload : {
       currBBMap[currBBStkTop-1] = NON_REFERENCE;
       currGCPointCount--;
       mapIndex = currGCPointCount*localCount;
       for (int k = 0; k < localCount; k++) 
        currBBMap[k] = finalLocalMaps[mapIndex+k];
       if (!inJSRSubroutine)
         referenceMaps.recordStkMap(i, currBBMap, currBBStkTop, 
				    blockSeen[currBBNum]);
       else
         referenceMaps.recordJSRSubroutineMap(i, currBBMap, currBBStkTop, 
					      currPendingRET.returnAddressLocation, 
					      blockSeen[currBBNum]);
       break;
     }

     case JBC_aaload : {
       currBBStkTop--;
       currGCPointCount--;
       mapIndex = currGCPointCount*localCount;
       for (int k = 0; k < localCount; k++) 
        currBBMap[k] = finalLocalMaps[mapIndex+k];
       if (!inJSRSubroutine)
         referenceMaps.recordStkMap(i, currBBMap, currBBStkTop, 
				    blockSeen[currBBNum]);
       else
         referenceMaps.recordJSRSubroutineMap(i, currBBMap, currBBStkTop, 
					      currPendingRET.returnAddressLocation, 
					      blockSeen[currBBNum]);
       break;
     }
 
     case JBC_iastore :
     case JBC_fastore :
     case JBC_aastore :
     case JBC_bastore :
     case JBC_castore :
     case JBC_sastore : {
       currBBStkTop = currBBStkTop - 3;
       currGCPointCount--;
       mapIndex = currGCPointCount*localCount;
       for (int k = 0; k < localCount; k++) 
        currBBMap[k] = finalLocalMaps[mapIndex+k];
       if (!inJSRSubroutine)
         referenceMaps.recordStkMap(i, currBBMap, currBBStkTop, 
				    blockSeen[currBBNum]);
       else
         referenceMaps.recordJSRSubroutineMap(i, currBBMap, currBBStkTop, 
					      currPendingRET.returnAddressLocation, 
					      blockSeen[currBBNum]);
       break;
     }
     case JBC_lastore :
     case JBC_dastore : {
       currBBStkTop = currBBStkTop - 4;
       currGCPointCount--;
       mapIndex = currGCPointCount*localCount;
       for (int k = 0; k < localCount; k++) 
        currBBMap[k] = finalLocalMaps[mapIndex+k];
       if (!inJSRSubroutine)
         referenceMaps.recordStkMap(i, currBBMap, currBBStkTop, 
				    blockSeen[currBBNum]);
       else
         referenceMaps.recordJSRSubroutineMap(i, currBBMap, currBBStkTop, 
					      currPendingRET.returnAddressLocation, 
					      blockSeen[currBBNum]);
       break;
     }

     case JBC_newarray : {
       currGCPointCount--;
       mapIndex = currGCPointCount*localCount;
       for (int k = 0; k < localCount; k++) 
        currBBMap[k] = finalLocalMaps[mapIndex+k];
       if (!inJSRSubroutine)
         referenceMaps.recordStkMap(i, currBBMap, currBBStkTop, 
				    blockSeen[currBBNum]);
       else
         referenceMaps.recordJSRSubroutineMap(i, currBBMap, currBBStkTop, 
					      currPendingRET.returnAddressLocation, 
					      blockSeen[currBBNum]);
       currBBMap[currBBStkTop] = REFERENCE;
       i = i + 1;
       break;
     }
     case JBC_anewarray : {
       currGCPointCount--;
       mapIndex = currGCPointCount*localCount;
       for (int k = 0; k < localCount; k++) 
        currBBMap[k] = finalLocalMaps[mapIndex+k];
       if (!inJSRSubroutine)
         referenceMaps.recordStkMap(i, currBBMap, currBBStkTop, 
				    blockSeen[currBBNum]);
       else
         referenceMaps.recordJSRSubroutineMap(i, currBBMap, currBBStkTop, 
					      currPendingRET.returnAddressLocation,
					      blockSeen[currBBNum]);
       currBBMap[currBBStkTop] = REFERENCE;
       i = i + 2;
       break;
     }
     case JBC_multianewarray : {
       short dim = (short)(((int)bytecodes[i+3]) & 0xFF);
       currGCPointCount--;
       mapIndex = currGCPointCount*localCount;
       for (int k = 0; k < localCount; k++) 
        currBBMap[k] = finalLocalMaps[mapIndex+k];
       if (!inJSRSubroutine)
         referenceMaps.recordStkMap(i, currBBMap, currBBStkTop, 
				    blockSeen[currBBNum]);
       else
         referenceMaps.recordJSRSubroutineMap(i, currBBMap, currBBStkTop, 
					      currPendingRET.returnAddressLocation,
					      blockSeen[currBBNum]);
       currBBStkTop = currBBStkTop - dim + 1;
       currBBMap[currBBStkTop] = REFERENCE;
       i = i + 3;
       break;
     }
     case JBC_arraylength : {
       currBBMap[currBBStkTop] = NON_REFERENCE;
       break;
     }
     case JBC_athrow : {
       currGCPointCount--;
       mapIndex = currGCPointCount*localCount;
       for (int k = 0; k < localCount; k++) 
        currBBMap[k] = finalLocalMaps[mapIndex+k];
       if (!inJSRSubroutine)
         referenceMaps.recordStkMap(i, currBBMap, currBBStkTop, 
				    blockSeen[currBBNum]);
       else
         referenceMaps.recordJSRSubroutineMap(i, currBBMap, currBBStkTop, 
					      currPendingRET.returnAddressLocation,
					      blockSeen[currBBNum]);
       currBBStkTop = currBBStkEmpty+1;
       currBBMap[currBBStkTop] = REFERENCE;
       break;
     }
     case JBC_monitorenter : {
       ///???
       currBBStkTop--;
       break;
     }
     case JBC_monitorexit : {
       ///???
       currBBStkTop--;
       break;
     }
     case JBC_wide : {
       int wopcode = ((int)bytecodes[i+1]) & 0xFF;
       i = i + 1;

       if (wopcode == JBC_iinc) {
           i = i + 4;
       } else {
           int index = (((int)bytecodes[i+1]) << 8 | 
			(((int)bytecodes[i+2]) & 0xFF)) & 0xFFFF;
           switch (wopcode) {

             case JBC_iload :
             case JBC_fload : {
	       currBBStkTop++;
	       currBBMap[currBBStkTop] = NON_REFERENCE;
               break;
             }
             case JBC_lload :
             case JBC_dload : {
	       currBBStkTop++;
	       currBBMap[currBBStkTop] = NON_REFERENCE;
	       currBBStkTop++;
	       currBBMap[currBBStkTop] = NON_REFERENCE;
               break;
             }
             case JBC_aload : {
	       currBBStkTop++;
	       currBBMap[currBBStkTop] = REFERENCE;
               break;
             }
             case JBC_istore :
             case JBC_fstore : {
               if (!inJSRSubroutine)
                   currBBMap[index] = NON_REFERENCE;
               else
                   currBBMap[index] = SET_TO_NONREFERENCE;
	       currBBStkTop--;
               break;
             }
             case JBC_lstore :
             case JBC_dstore : {
               if (!inJSRSubroutine) {
                   currBBMap[index]   = NON_REFERENCE;
                   currBBMap[index+1] = NON_REFERENCE;
               }
               else {
                   currBBMap[index]   = SET_TO_NONREFERENCE;
                   currBBMap[index+1] = SET_TO_NONREFERENCE;
	       }
	       currBBStkTop = currBBStkTop - 2;
               break;
             }
	     case JBC_astore : {
               currBBMap[index]=currBBMap[currBBStkTop];
	       currBBStkTop--;
	       break;
	     }
           }
           i = i + 2;
       }
       break;
     }
     default : {
       VM.sysWrite("VM_BuildLiveRefMaps: Unknown opcode:");
       VM.sysWrite(opcode);
       VM.sysWrite("\n");
       System.exit(10);
     }

     }  // end switch (opcode)

    }  // for start to end



  }

  // Indicate that any temporaries can be freed
  referenceMaps.recordingComplete();

  return;
}  

  // --------------------- Private Instance Method -----------------

private short[] 
addtoWorkStk(short blockNum, short[] workStk) {
  workStkTop++;
  if (workStkTop >= workStk.length) {
    short[] biggerQ = new short[workStk.length + 20];
    int workStkLength = workStk.length;
    for (int i=0; i<workStkLength; i++) {
	biggerQ[i] = workStk[i];
    }
    workStk = biggerQ;
    biggerQ = null;
  }
  workStk[workStkTop] = blockNum;
  return workStk;
}

private short[] 
addUniquetoWorkStk(short blockNum, short[] workStk) {
  if ((workStkTop+1) >= workStk.length) {
    short[] biggerQ = new short[workStk.length + 20];
    boolean matchFound = false;
    int workStkLength = workStk.length;
    for (int i=0; i<workStkLength; i++) {
	biggerQ[i] = workStk[i];
	matchFound =  (workStk[i] == blockNum);
    }
    workStk = biggerQ;
    biggerQ = null;
    if (matchFound) return workStk;
  }
  else {
    for (int i=0; i<=workStkTop; i++) {
      if (workStk[i] == blockNum)
	return workStk;
    }
  }
  workStkTop++;
  workStk[workStkTop] = blockNum;
  return workStk;
}

private short []
processBranchBB(short brBBNum, int currBBStkTop, byte currBBMap[], 
		int currBBStkEmpty, boolean inJSRSubroutine, byte bbStartMaps[][], 
		int blockStkTop[], VM_PendingRETInfo currPendingRET, 
		VM_PendingRETInfo bbPendingRETs[], short[] workStk) {

  short newworkStk[] = workStk;

  // If the destination block doesn't already have a map, then use this
  // map as its map and add it to the stack
 
 if (bbStartMaps[brBBNum] == null) {
   bbStartMaps[brBBNum] = new byte[currBBMap.length];
   for (int i=currBBStkEmpty+1; i<= currBBStkTop; i++)
     bbStartMaps[brBBNum][i] = currBBMap[i];
   blockStkTop[brBBNum] = currBBStkTop;
   newworkStk = addtoWorkStk(brBBNum, workStk);
   if (inJSRSubroutine) {
     bbPendingRETs[brBBNum] = new VM_PendingRETInfo(currPendingRET);
   }
 }

 return newworkStk;

}


private int
stkUpdateInvoke(VM_Method calledMethod, int currBBStkTop, byte currBBMap[], 
		boolean isStatic) {

   VM_Type[] parameterTypes = calledMethod.getParameterTypes();
   int pTypesLength = parameterTypes.length;
    // Pop the arguments for this call off the stack; 
   for (int i=0; i<pTypesLength; i++) {
     currBBStkTop -= parameterTypes[i].getStackWords();
     }
  
   if (!isStatic)
    currBBStkTop--; // pop implicit "this" object reference
  
   // Add the return value to the stack
   VM_Type returnType = calledMethod.getReturnType();
   if (returnType.getStackWords() != 0)
      { // a non-void return value
        currBBMap[++currBBStkTop] = returnType.isReferenceType() ? 
	                                  REFERENCE : NON_REFERENCE;
        if (returnType.getStackWords() == 2)
	  currBBMap[++currBBStkTop] = NON_REFERENCE;
      }

  // Return updated stack top
  return currBBStkTop;
}


private int
processInvoke(VM_Method calledMethod, int byteindex, int currBBStkTop, 
	      byte currBBMap[], boolean isStatic, boolean inJSRSubroutine, 
	      VM_ReferenceMaps referenceMaps, VM_PendingRETInfo currPendingRET, 
	      boolean blockSeen, int currBBStkEmpty ) {

 boolean skipRecordingReferenceMap = false;
 int stkDepth = currBBStkTop;

 if (isStatic && calledMethod.getDeclaringClass().isMagicType()) {
   boolean producesCall = VM_MagicCompiler.checkForActualCall(calledMethod);
   if (producesCall) {
     stkDepth = currBBStkEmpty;
   }
   else
     skipRecordingReferenceMap = true;
 }

  if (!skipRecordingReferenceMap) {
    // Register the reference map, including the arguments on the stack for 
    // this call 
    //
    if (!inJSRSubroutine)
      referenceMaps.recordStkMap(byteindex, currBBMap, stkDepth, blockSeen);
    else
       referenceMaps.recordJSRSubroutineMap(byteindex, currBBMap, stkDepth, 
				currPendingRET.returnAddressLocation, blockSeen);
  }

   VM_Type[] parameterTypes = calledMethod.getParameterTypes();
   int pTypesLength = parameterTypes.length;

    // Pop the arguments for this call off the stack; 
   for (int i=0; i<pTypesLength; i++) {
     currBBStkTop -= parameterTypes[i].getStackWords();
     }
  
   if (!isStatic)
    currBBStkTop--; // pop implicit "this" object reference
  
   // Add the return value to the stack
   VM_Type returnType = calledMethod.getReturnType();
   if (returnType.getStackWords() != 0)
      { // a non-void return value
        currBBMap[++currBBStkTop] = returnType.isReferenceType() ? 
	                               REFERENCE : NON_REFERENCE;
        if (returnType.getStackWords() == 2)
	  currBBMap[++currBBStkTop] = NON_REFERENCE;
      }

  // Return updated stack top
  return currBBStkTop;
}

private short[] 
processJSR(int JSRBBNum, int JSRSubStartIndex, short brBBNum, short nextBBNum, 
           byte[][] bbStartMaps, int currBBStkTop, byte currBBMap[],
           int currBBStkEmpty, int blockStkTop[],
           VM_PendingRETInfo bbPendingRETs[], VM_PendingRETInfo currPendingRET,
           VM_JSRSubroutineInfo[] JSRSubs, short[] workStk) {

  boolean inJSROnReturn;
  short newworkStk[] = workStk;

  // If the destination block doesn't already have a map, then use this map to build
  // the stack portion of the reference map and add the block to the work stack. 
  // The locals maps should be started with all zeros.
 
 if (bbStartMaps[brBBNum] == null) {
   bbStartMaps[brBBNum] = new byte[currBBMap.length];
   for (int i=currBBStkEmpty+1; i<= currBBStkTop; i++)
     bbStartMaps[brBBNum][i] = currBBMap[i];
   blockStkTop[brBBNum] = currBBStkTop;
   newworkStk = addtoWorkStk(brBBNum, workStk);

   bbPendingRETs[brBBNum] = new VM_PendingRETInfo(JSRSubStartIndex,JSRBBNum,
						  currBBStkTop, nextBBNum);
   JSRSubs[JSRSubNext++] = new VM_JSRSubroutineInfo(JSRSubStartIndex, 
						   currBBMap, currBBStkEmpty);
 }
 else {
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

   boolean JSRisInJSRSub = (currPendingRET != null);
   newworkStk = computeJSRNextMaps(nextBBNum, currBBMap.length, matchingJSRStart, 
				JSRisInJSRSub, bbStartMaps, blockStkTop, JSRSubs,
				currBBStkEmpty,	workStk);
   if (JSRisInJSRSub && bbPendingRETs[nextBBNum] == null) 
     bbPendingRETs[nextBBNum] = new VM_PendingRETInfo(currPendingRET);
 }

   return newworkStk;
} // processJSR

private short[]
computeJSRNextMaps(short nextBBNum, int maplength, int JSRSubIndex, 
                   boolean JSRisInJSRSub, byte[][] bbStartMaps, int blockStkTop[],
                   VM_JSRSubroutineInfo[] JSRSubs, int currBBStkEmpty,
                   short[] workStk) {

   short newworkStk[] = workStk;
   
   // Calculate the new map for the block starting at the instruction after the
   // JSR instruction (this is the block nextBBNum)

   byte[] refMap;

   refMap = JSRSubs[JSRSubIndex].computeResultingMaps(maplength);

   // If no map is computed, then the JSR Subroutine must have ended in a return
   // Do NOT add the next block to the work Q.

   if (refMap == null)
     return newworkStk;

   // If the block after the JSR instruction does not have a map, then the newly
   // computed map should be used and the block should be added to the workStk.

   if (bbStartMaps[nextBBNum] == null) {
     bbStartMaps[nextBBNum] = refMap;
     blockStkTop[nextBBNum] = JSRSubs[JSRSubIndex].endReferenceTop;
     newworkStk = addtoWorkStk(nextBBNum, workStk);
   }
   else {
     // The block after the JSR instruction already has a reference map.
     // Check if the computed map matches the previous map. If so, no further
     // work needed.

     int mismatchAt=-1;
     byte[] blockMap = bbStartMaps[nextBBNum];
     for (int i=0; i <= currBBStkEmpty; i++) {
       if ((refMap[i] != blockMap[i]) && (blockMap[i] != NON_REFERENCE)) {
          mismatchAt=i;
	  break;
       }
     }
     if (mismatchAt == -1) 
       return newworkStk;  // no further work to be done
     else {
       newworkStk = addUniquetoWorkStk(nextBBNum, workStk);
       for (int i = mismatchAt; i <= currBBStkEmpty; i++) {
	 if (!JSRisInJSRSub) 
           blockMap[i] = ( blockMap[i] == refMap[i] ) ? blockMap[i] : NON_REFERENCE;
	 else
           blockMap[i] = ( blockMap[i] == refMap[i] ) ? 
	                             blockMap[i] : SET_TO_NONREFERENCE;
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
   * @param localVariable variable index in the map
   * @param wordCount                 2 for doubles and longs, 1 otherwise
   * @param reachableHandlerBBNum     the array with all the block numbers of 
   *                                  reachable handlers
   * @param reachableHandlerCount     0 - reachableHandlerCount-1 valid array indices
   * @return Void
   */

  private void
  setHandlersMapsNonRef(int localVariable, int wordCount, 
			int[] reachableHandlerBBNums, int reachableHandlerCount, 
			boolean inJSRSubroutine, byte[][] bbStartMaps) {
    if (!inJSRSubroutine) {
      for (int i=0; i<reachableHandlerCount; i++) {
        bbStartMaps[reachableHandlerBBNums[i]][localVariable] = NON_REFERENCE;
        if (wordCount == 2)
          bbStartMaps[reachableHandlerBBNums[i]][localVariable+1] = NON_REFERENCE;
      }
    }
    else {
      for (int i=0; i<reachableHandlerCount; i++) {
        bbStartMaps[reachableHandlerBBNums[i]][localVariable] = SET_TO_NONREFERENCE;
        if (wordCount == 2)
          bbStartMaps[reachableHandlerBBNums[i]][localVariable+1] = 
	                                                       SET_TO_NONREFERENCE;
      }
    }

  }

  /**
   * For each of the reachable handlers (catch blocks) from the try block, track that
   * the local variable given by the index,  has been set to a reference value.
   * Only call this method if the try block is in a JSR subroutine.
   * If a non-reference value becomes a reference value, then it can not be used as
   * as reference value within the handler (there is a path to the handler where the
   * value is not a reference) so mark the local variable as a non-reference if we
   * are tracking the difference maps (for a JSR subroutine).
   *
   * @param localVariable             variable index in the map
   * @param reachableHandlerBBNum     the array with all the block numbers of 
   *                                  reachable handlers 
   * @param reachableHandlerCount     0 - reachableHandlerCount-1 valid array indices
   * @return Void
   */

private void
setHandlersMapsRef(int localVariable, int[] reachableHandlerBBNums, 
                   int reachableHandlerCount, byte[][] bbStartMaps) {

    for (int i=0; i<reachableHandlerCount; i++) {
      if (bbStartMaps[reachableHandlerBBNums[i]][localVariable] != REFERENCE)
	bbStartMaps[reachableHandlerBBNums[i]][localVariable] = SET_TO_NONREFERENCE;
    }    

  }


  /**
   * For each of the reachable handlers (catch blocks) from the try block, track that
   * the local variable given by the index, has been set to a return address value.
   * Only call this routine within a JSR subroutine.
   * If a non-reference, or reference value becomes a return address value, then it 
   * cannot be used as any of these values within the handler (there is a path to 
   * the handler where the value is not an internal reference, and a path where it 
   * is an internal reference) so mark the local variable as a non-reference if we
   * are tracking the difference maps (for a JSR subroutine).
   *
   * @param localVariable             variable index in the map
   * @param reachableHandlerBBNum     the array with all the block numbers of
   *                                  reachable handlers 
   * @param reachableHandlerCount     0 - reachableHandlerCount-1 valid array indices
   * @return Void
   */

private void
setHandlersMapsReturnAddress(int localVariable, int[] reachableHandlerBBNums, 
			     int reachableHandlerCount, byte[][]bbStartMaps) {

  for (int i=0; i<reachableHandlerCount; i++) 
    if (bbStartMaps[reachableHandlerBBNums[i]][localVariable] != RETURN_ADDRESS)
      bbStartMaps[reachableHandlerBBNums[i]][localVariable] = SET_TO_NONREFERENCE;
}


  // ------------------- Static Methods -----------------------

 // get 4 byte offset for wide instructions
 //
 private static int 
 getIntOffset(int index, byte[] bytecodes) {

      return (int)((((int)bytecodes[index+1]) << 24) | 
		   ((((int)bytecodes[index+2]) & 0xFF) << 16) |
		   ((((int)bytecodes[index+3]) & 0xFF) << 8) | 
		   (((int)bytecodes[index+4]) & 0xFF));
}


}

  







