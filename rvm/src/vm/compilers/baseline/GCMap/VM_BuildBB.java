/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;

/**
 * Analyze the byte codes and determine the boundaries of the 
 * basic blocks. Used for building the reference maps for a 
 * method.
 *
 * @author Anthony Cocchi
 */
//-#if RVM_WITH_QUICK_COMPILER
public
  //-#endif
  final class VM_BuildBB
  implements VM_BytecodeConstants
             //-#if RVM_WITH_QUICK_COMPILER
             , VM_BBConstants 
             //-#endif
{


  // ---------------- Static Class Fields --------------------

  // Types of Instructions 
  static private final byte NONBRANCH = 1;
  static private final byte CONDITIONAL_BRANCH = 2;
  static private final byte BRANCH = 3;  

  //***************************************************************************//
  //                                                                           //
  //  Once the method determineTheBasicBlocks is complete, these 4 items       //
  //  basicBlocks, byteToBlockMap, numJsrs and gcPointCount will be            // 
  //  appropriately filled in. They will be accessed by VM_BuildReferenceMaps  //
  //  VM_BuildLiveRefMaps, so that the reference maps can be built.            //
  //                                                                           //
  //***************************************************************************//

  /** 
   * basic blocks of the byte code 
   */
  //-#if RVM_WITH_QUICK_COMPILER
  public 
    //-#endif
    VM_BasicBlockFactory      bbf;
  //-#if RVM_WITH_QUICK_COMPILER
  public 
    //-#endif
    VM_BasicBlock             basicBlocks[];       

  /** 
   * identify which block a byte is part of 
   */
  //-#if RVM_WITH_QUICK_COMPILER
  public 
    //-#endif
    short                     byteToBlockMap[]; 

  /**
   * Number of unique jsr targets processed
   */
  //-#if RVM_WITH_QUICK_COMPILER
  public 
    //-#endif
    int                       numJsrs; 
  
  /**
   * Number of GC points found
   */
  //-#if RVM_WITH_QUICK_COMPILER
  public
    //-#endif
    int                       gcPointCount;

  // This variable is used in multiple methods of this class, make it accessible
  int bytelength;

  //-#if RVM_WITH_QUICK_COMPILER
  int maxStackHeight;

  public static byte[][] stackSnapshots;

  private static int[] nextInstructionStart;
  private static byte[] stack;
  private static int currentStackHeight;
  private static int[] branchList;
  private static int branchListIndex;
  private static boolean[] branchVisited;
  //-#endif
 
  /**
   * Analyze the bytecodes and build the basic blocks with their predecessors.
   * The results will be used by VM_BuildReferenceMaps
   */
  
  //-#if RVM_WITH_QUICK_COMPILER
  public
    //-#endif
    void determineTheBasicBlocks(VM_NormalMethod method) {
    VM_ExceptionHandlerMap    exceptions;   // Used to get a hold of the try Start, End and Handler lists
    int                       retList[];    // List of basic block numbers that end with a "ret" instruction.
    VM_BytecodeStream         bcodes;       // The bytecodes being analyzed.
    VM_BasicBlock             currentBB;    // current basic block being processed
    byte                      lastInstrType;// type of the last instruction
    int                       lastInstrStart;// byte index where last instruction started

    //
    //  Initialization
    //
    int nextRetList = 0;
    numJsrs        = 0;
    gcPointCount   = 1;  // All methods have the possible thread switch in prologue

    bcodes         = method.getBytecodes();
    bytelength     = bcodes.length();
    //-#if RVM_WITH_QUICK_COMPILER
    maxStackHeight = method.getOperandWords();
    //-#endif

    byteToBlockMap = new short[bytelength];
    //-#if RVM_WITH_QUICK_COMPILER
    java.util.Arrays.fill(byteToBlockMap, (short)VM_BasicBlock.NOTBLOCK);
    //-#endif
    basicBlocks    = new VM_BasicBlock[2];  // many methods only have one block (+1 for EXIT)

    bbf = new VM_BasicBlockFactory();

    exceptions = method.getExceptionHandlerMap();

    retList = null;

    //-#if RVM_WITH_QUICK_COMPILER
    if (nextInstructionStart == null ||
        nextInstructionStart.length < bytelength) {
      nextInstructionStart = new int[bytelength];
    }
    //-#endif
    
    // 
    //  Set up the EXIT basic block
    // 
    basicBlocks[VM_BasicBlock.EXITBLOCK] = 
      new VM_BasicBlock(bytelength,bytelength,VM_BasicBlock.EXITBLOCK);

    //
    // Get the first basic block
    //
    currentBB = bbf.newBlock(0);
    addBasicBlock(currentBB);
    currentBB.setState(VM_BasicBlock.METHODENTRY);
    lastInstrType = NONBRANCH;
    lastInstrStart = 0;


    if (exceptions != null) {
      // Get blocks for any handlers, which tend to not be a clear block boundaries
      //
      setupHandlerBBs(exceptions);

      // Set up blocks for start of try block, which tend not be to at clear 
      // block boundaries
      //
      setupTryStartBBs(exceptions);
    }

    //
    // Scan the bytecodes for this method
    //
    while(bcodes.hasMoreBytecodes()) {
      // Determine if we are at a block boundary
      // We are at a block boundary if:
      //   1) non-branch instruction followed by a known block 
      //   2) last instruction was a conditional branch
      //   3) last instruction was a branch
      // Note that forward branches mean that the byteToBlockMap will have
      // a basic block value prior to us examining that destination byte code
      //
      if (lastInstrType == NONBRANCH) {
        if (byteToBlockMap[bcodes.index()] == VM_BasicBlock.NOTBLOCK) {
          // Not a new block
          // Make note of current block 
          byteToBlockMap[bcodes.index()] = (short)currentBB.getBlockNumber();
        } else {
          // Earlier forward branch must have started this block
          currentBB.setEnd(lastInstrStart);
          basicBlocks[byteToBlockMap[bcodes.index()]].addPredecessor(currentBB);
          currentBB = basicBlocks[byteToBlockMap[bcodes.index()]];
        }
      } else { // we are at a block boundary, last instr was some type of branch
        if (lastInstrType == CONDITIONAL_BRANCH) {
          currentBB.setEnd(lastInstrStart);
          // See if we need a new block
          if (byteToBlockMap[bcodes.index()] == VM_BasicBlock.NOTBLOCK) {
            VM_BasicBlock newBB = bbf.newBlock(bcodes.index());
            addBasicBlock(newBB);
            newBB.addPredecessor(currentBB);
            currentBB = newBB;
            // Make note of current block 
            byteToBlockMap[bcodes.index()] = (short)currentBB.getBlockNumber();
          } else {
            // From an earlier forward branch 
            basicBlocks[byteToBlockMap[bcodes.index()]].addPredecessor(currentBB);          
            currentBB = basicBlocks[byteToBlockMap[bcodes.index()]];
          }
        } else {
          if (lastInstrType == BRANCH) {
            currentBB.setEnd(lastInstrStart);
            // See if we need a new block
            if (byteToBlockMap[bcodes.index()] == VM_BasicBlock.NOTBLOCK) {
              VM_BasicBlock newBB = bbf.newBlock(bcodes.index());
              addBasicBlock(newBB);
              currentBB = newBB;
              // Make note of current block 
              byteToBlockMap[bcodes.index()] = (short)currentBB.getBlockNumber();
            } else {
              // From an earlier forward branch 
              currentBB = basicBlocks[byteToBlockMap[bcodes.index()]];
            }
          }
        }
      }
      // end of determining if at block boundary


      // Now examine this instruction
      //-#if RVM_WITH_QUICK_COMPILER
      int savedStart = lastInstrStart;
      //-#endif
      lastInstrStart = bcodes.index();  // Instruction starts here
      //-#if RVM_WITH_QUICK_COMPILER
      nextInstructionStart[savedStart] = lastInstrStart;
      //-#endif
      lastInstrType = NONBRANCH;        // assume it will be a non-branch
      switch(bcodes.nextInstruction()) {
      case JBC_ifeq:
      case JBC_ifne:
      case JBC_iflt:
      case JBC_ifge:
      case JBC_ifgt:
      case JBC_ifle:
      case JBC_if_icmpeq:
      case JBC_if_icmpne:
      case JBC_if_icmplt:
      case JBC_if_icmpge:
      case JBC_if_icmpgt:
      case JBC_if_icmple:
      case JBC_if_acmpeq:
      case JBC_if_acmpne:
      case JBC_ifnull:
      case JBC_ifnonnull:
        {
          lastInstrType = CONDITIONAL_BRANCH;
          int offset = bcodes.getBranchOffset();
          if(offset < 0) gcPointCount++; // gc map required if backward edge
          int branchtarget = lastInstrStart + offset;
          processBranchTarget(lastInstrStart, branchtarget);
          break;
        }
               
      case JBC_jsr:
        {
          lastInstrType = BRANCH;
          int offset = bcodes.getBranchOffset();
          int branchtarget = lastInstrStart + offset;
          processBranchTarget(lastInstrStart, branchtarget);
          int jsrentryBBNum = byteToBlockMap[branchtarget];
          VM_BasicBlock bb = basicBlocks[jsrentryBBNum];
          if ((bb.getState() & VM_BasicBlock.JSRENTRY) == 0) numJsrs++;
          bb.setState(VM_BasicBlock.JSRENTRY);  
          gcPointCount = gcPointCount+1;
          break;
        }

      case JBC_jsr_w:
        {
          lastInstrType = BRANCH;
          int offset = bcodes.getWideBranchOffset();
          int branchtarget = lastInstrStart + offset;
          processBranchTarget(lastInstrStart, branchtarget);
          int jsrentryBBNum = byteToBlockMap[branchtarget];
          VM_BasicBlock bb = basicBlocks[jsrentryBBNum];
          if ((bb.getState() & VM_BasicBlock.JSRENTRY) == 0) numJsrs++;
          bb.setState(VM_BasicBlock.JSRENTRY);  
          gcPointCount = gcPointCount+1;
          break;
        }

      case JBC_goto:
        {
          lastInstrType = BRANCH;
          int offset = bcodes.getBranchOffset();
          if(offset < 0) gcPointCount++; // gc map required if backward edge
          int branchtarget = lastInstrStart + offset;
          processBranchTarget(lastInstrStart, branchtarget);
          break;
        }

      case JBC_goto_w:
        {
          int offset = bcodes.getWideBranchOffset();
          if(offset < 0) gcPointCount++; // gc map required if backward edge
          int branchtarget = lastInstrStart + offset;
          processBranchTarget(lastInstrStart, branchtarget);
          break;
        }

      case JBC_tableswitch:
        {
          bcodes.alignSwitch();
          int def = bcodes.getDefaultSwitchOffset();
          processBranchTarget(lastInstrStart, lastInstrStart+def);
          int low = bcodes.getLowSwitchValue();
          int high = bcodes.getHighSwitchValue();
          int n = high-low+1;                        // n = number of normal cases (0..n-1)

          // generate labels for offsets
          for (int i=0; i<n; i++) {
            int offset = bcodes.getTableSwitchOffset(i);
            processBranchTarget(lastInstrStart, lastInstrStart+offset);
          }
          bcodes.skipTableSwitchOffsets(n);
          break;
        }
          
      case JBC_lookupswitch:
        {
          bcodes.alignSwitch();
          int def = bcodes.getDefaultSwitchOffset();
          int npairs = bcodes.getSwitchLength();
          processBranchTarget(lastInstrStart, lastInstrStart+def);

          // generate label for each offset in table
          for (int i=0; i<npairs; i++) {
            int offset  = bcodes.getLookupSwitchOffset(i);
            processBranchTarget(lastInstrStart, lastInstrStart+offset);
          }        
          bcodes.skipLookupSwitchPairs(npairs);
          break;
        }

      case JBC_ireturn:
      case JBC_lreturn:
      case JBC_freturn:
      case JBC_dreturn:
      case JBC_areturn:
      case JBC_return:
        {
          lastInstrType = BRANCH;
          basicBlocks[VM_BasicBlock.EXITBLOCK].addPredecessor(currentBB); 
          if (method.isSynchronized() || VM.UseEpilogueYieldPoints)
            gcPointCount++;
          break;
        }

      case JBC_ret:
        {
          lastInstrType = BRANCH;
          int index = bcodes.getLocalNumber();
          int blocknum = currentBB.getBlockNumber();
          basicBlocks[blocknum].setState(VM_BasicBlock.JSREXIT);

          // Worry about growing retListarray
          if (retList == null) retList = new int[10];
          if (nextRetList >= retList.length) {
            int[] biggerRetList = new int[nextRetList + 10];
            for (int i=0; i<nextRetList; i++) 
              biggerRetList[i] = retList[i];
            retList = biggerRetList;
            biggerRetList = null;
          }
          retList[nextRetList++] = blocknum;      
          break;
        }

      case JBC_wide: {
        int widecode = bcodes.getWideOpcode();
        int index = bcodes.getWideLocalNumber();
        if (widecode == JBC_ret) {
          lastInstrType = BRANCH;
          int blocknum = currentBB.getBlockNumber();
          basicBlocks[blocknum].setState(VM_BasicBlock.JSREXIT);
          
          // Worry about growing retListarray
          if (retList == null) retList = new int[10];
          if (nextRetList >= retList.length) {
            int[] biggerRetList = new int[nextRetList + 10];
            for (int i=0; i<nextRetList; i++) 
              biggerRetList[i] = retList[i];
            retList = biggerRetList;
            biggerRetList = null;
          }
          retList[nextRetList++] = blocknum;      
        } else if (widecode == JBC_iinc) {
          int val = bcodes.getWideIncrement();
        } else {
          // nothing more to do
        }
      }
       
      case JBC_athrow:
        {
          lastInstrType = BRANCH;
          processAthrow(exceptions, lastInstrStart);
          gcPointCount++;
          break;
        }

      case JBC_aaload:
      case JBC_iaload:
      case JBC_faload:
      case JBC_baload:
      case JBC_caload:
      case JBC_saload:
      case JBC_laload:
      case JBC_daload:
      case JBC_lastore:
      case JBC_dastore:
      case JBC_iastore:
      case JBC_fastore:
      case JBC_aastore:
      case JBC_bastore:
      case JBC_castore:
      case JBC_sastore:
      case JBC_putfield:
      case JBC_getfield:
      case JBC_getstatic:
      case JBC_putstatic:
      case JBC_irem:
      case JBC_idiv:
      case JBC_lrem:
      case JBC_ldiv:
      case JBC_invokevirtual:
      case JBC_invokespecial:
      case JBC_invokestatic:
      case JBC_invokeinterface:
      case JBC_instanceof:
      case JBC_checkcast:
      case JBC_monitorenter:
      case JBC_monitorexit:
      case JBC_new:
      case JBC_newarray:
      case JBC_anewarray:
      case JBC_multianewarray:
        {
          bcodes.skipInstruction();
          byteToBlockMap[lastInstrStart] = (short)currentBB.getBlockNumber(); 
          gcPointCount = gcPointCount+1;
          break;
        }

      default:
        {
          bcodes.skipInstruction();
          byteToBlockMap[lastInstrStart] = (short)currentBB.getBlockNumber();
          break;
        }
      } // switch (opcode) 
    } // while (bcodes.hasMoreBytecodes)

    currentBB.setEnd(lastInstrStart);   // close off last block

    // process try and catch blocks
    if (exceptions != null) {
      // process catch blocks
      processExceptionHandlers(exceptions);
      // mark all blocks in try sections as being part of a try
      markTryBlocks(exceptions);
    }

    // process ret instructions as last step
    if (retList != null) {
      processRetList(retList, nextRetList);
    }
  } 


  /********************************/
  /*                              */
  /*   Routines for Branches      */
  /*                              */
  /********************************/

  /**
   * Processing a branch that appears at location index in the byte code and has a 
   * target index of branchtarget in the byte code. The target of a branch must 
   * start a basic block. So if the byteToBlockMap doesn't already show a basic 
   * block at the target, make one start there. If a basic block is already set 
   * up and this is a branch forward then only need to adjust predecessor list
   * (we know it is not a branch into the middle of a block as only starts are 
   * marked in byte code beyond "index"). If the basic block is already set up and
   * this is a backward branch then we must check if the block needs splitting,
   * branching to the middle of a block is not allowed.
   */
  private void processBranchTarget(int index, int branchtarget) {

    VM_BasicBlock newBB, currentBB;
    if (byteToBlockMap[branchtarget] == VM_BasicBlock.NOTBLOCK) {
      newBB = bbf.newBlock(branchtarget);
      addBasicBlock(newBB);              
      byteToBlockMap[branchtarget] = (short)newBB.getBlockNumber();
      currentBB = basicBlocks[byteToBlockMap[index]];
      newBB.addPredecessor(currentBB);
    } else if (index > branchtarget) {
      // This is a backwards branch
      processBackwardBranch(index, branchtarget);
    } else {
      // This is a forward branch to an existing block, need to register 
      // the predecessor
      currentBB = basicBlocks[byteToBlockMap[index]];
      basicBlocks[byteToBlockMap[branchtarget]].addPredecessor(currentBB);
    }
  }

  /**
   * A backwards branch has been found from the byte code at location "index" 
   * to a target location of "branchtarget". Need to make sure that the 
   * branchtarget location is the start of a block (and if not, then split the 
   * existing block into two) Need to register the block that ends at "index" 
   * as a predecessor of the block that starts at branchtarget.
   */
  private void processBackwardBranch(int index, int branchtarget) {
    VM_BasicBlock existingBB, currentBB, newBB, targetBB;
    int newBlockNum, i, newBlockEnd;

    existingBB = basicBlocks[byteToBlockMap[branchtarget]];
    if (existingBB.getStart() != branchtarget) {
      // Need to split the existing block in two, by ending the existing block
      // at the previous instruction and starting a new block at the branchtarget.
      // Need to split the existing block in two. It is best to set up the new
      // block to end at the instruction before the target and the existing
      // block to start at the target. That way the tail stays the same. 

      newBB = bbf.newBlock(existingBB.getStart());
      addBasicBlock(newBB);
      newBlockNum = newBB.getBlockNumber();
      existingBB.setStart(branchtarget);

      // Find the last instruction prior to the branch target;
      //  that's the end of the new block
      //
      for (i=branchtarget-1; byteToBlockMap[i]==VM_BasicBlock.NOTBLOCK; i--) {}

      newBlockEnd = i;
      newBB.setEnd(i);

      // Going forwards, mark the start of each instruction with the new block 
      // number
      //
      for (i=newBB.getStart(); i<=newBlockEnd; i++) {
        if (byteToBlockMap[i] != VM_BasicBlock.NOTBLOCK)
          byteToBlockMap[i]= (short)newBlockNum;
      }

      VM_BasicBlock.transferPredecessors(existingBB, newBB);

      // The new block is a predecessor of the existing block
      existingBB.addPredecessor(newBB);
    } else {
      // Nice coincidence, the existing block starts at "branchtarget"
    }


    // Now mark the "current" block (the one that ends at "index") as a predecessor
    // of the target block (which is either the existing block or a newly made 
    // block)
    //
    currentBB = basicBlocks[byteToBlockMap[index]];
    existingBB.addPredecessor(currentBB);
  }


  /********************************/
  /*                              */
  /*   Routines for JSR/Ret       */
  /*                              */
  /********************************/

  /**
   * process the effect of the ret instructions on the precedance table
   */
  private void processRetList(int retList[], int nextRetList) {
    // block 0 not used
    int otherRetCount;
    for (int i = 0; i<nextRetList; i++) {
      int retBlockNum       = retList[i];
      VM_BasicBlock retBB   = basicBlocks[retBlockNum];
      boolean[] seenAlready = new boolean[bbf.getNumberofBlocks()+1]; 
      otherRetCount = 0;
      findAndSetJSRCallSite(retBlockNum, retBB, otherRetCount, seenAlready);
    }
  }

  /**
   * scan back from ret instruction to jsr call sites 
   */
  private void findAndSetJSRCallSite(int  pred, VM_BasicBlock retBB, int otherRetCount, boolean seenAlready[]) {
    seenAlready[pred] = true;
    VM_BasicBlock jsrBB =  basicBlocks[pred]; 
    jsrBB.setState(VM_BasicBlock.INJSR);       
    
    if (basicBlocks[pred].isJSRExit() && pred != retBB.getBlockNumber())
      otherRetCount++;

    if (basicBlocks[pred].isJSREntry()) {
      if (otherRetCount == 0) {
        // setup call site
        setupJSRCallSite(basicBlocks[pred], retBB); 
        return;  
      } else {
        otherRetCount--;
      }
    }
    int[] preds = basicBlocks[pred].getPredecessors();
    for( int i = 0; i < preds.length; i++) {
      int pred2 = preds[i];
      if (!seenAlready[pred2])
        findAndSetJSRCallSite(pred2,retBB,otherRetCount, seenAlready);
    }
  }

  /**
   * setup jsr call site
   */
  private void setupJSRCallSite(VM_BasicBlock entryBB, VM_BasicBlock retBB) {
    int newBB;
    int[] callsites = entryBB.getPredecessors();
    int callLength = callsites.length;
    for( int i = 0; i < callLength; i++){
      int callsite = callsites[i];
      int blockend = basicBlocks[callsite].getEnd();
      for (newBB = blockend+1; byteToBlockMap[newBB] == VM_BasicBlock.NOTBLOCK; newBB++);
      int nextBlock = byteToBlockMap[newBB];
      basicBlocks[nextBlock].addPredecessor(retBB);
    }
  }

  /********************************/
  /*                              */
  /*   Routines for Try/catch     */
  /*                              */
  /********************************/

  /**
   * For every handler, make a block that starts with the handler PC
   * Only called when exceptions is not null.
   */
  private void setupHandlerBBs(VM_ExceptionHandlerMap exceptions) {
    int[] tryHandlerPC = exceptions.getHandlerPC();
    int tryLength = tryHandlerPC.length;
    for (int i=0; i<tryLength; i++) {
      if (byteToBlockMap[tryHandlerPC[i]] == VM_BasicBlock.NOTBLOCK) {
        VM_BasicBlock handlerBB = bbf.newBlock(tryHandlerPC[i]);
        handlerBB.setState(VM_BasicBlock.TRYHANDLERSTART);
        addBasicBlock(handlerBB); 
        byteToBlockMap[tryHandlerPC[i]] = (short)handlerBB.getBlockNumber();
      }
    }
  }

  /**
   * For every try start, make a block that starts with the Try start,
   * mark it as a try start. Only called when exceptions is not null.
   */
  private void setupTryStartBBs(VM_ExceptionHandlerMap exceptions) {
    int[] tryStartPC = exceptions.getStartPC();
    int tryLength = tryStartPC.length;
    for (int i=0; i< tryLength; i++) {
      if (byteToBlockMap[tryStartPC[i]] == VM_BasicBlock.NOTBLOCK) {
        VM_BasicBlock tryStartBB = bbf.newBlock(tryStartPC[i]);
        addBasicBlock(tryStartBB); 
        byteToBlockMap[tryStartPC[i]] = (short)tryStartBB.getBlockNumber();
        tryStartBB.setState(VM_BasicBlock.TRYSTART);
      }
    }
  }

  /**
   * For every handler, mark the blocks in its try block as its predecessors.
   * Only called when exceptions is not null.
   */
  private void processExceptionHandlers(VM_ExceptionHandlerMap exceptions) {
    int[] tryStartPC = exceptions.getStartPC();
    int[] tryEndPC = exceptions.getEndPC();
    int[] tryHandlerPC = exceptions.getHandlerPC();
    int tryLength = tryHandlerPC.length;
    for (int i=0; i< tryLength; i++) {
      int handlerBBNum = byteToBlockMap[tryHandlerPC[i]];
      VM_BasicBlock tryHandlerBB = basicBlocks[handlerBBNum];
      int throwBBNum = 0;
      for (int k=tryStartPC[i]; k < tryEndPC[i]; k++) {
        if (byteToBlockMap[k] == VM_BasicBlock.NOTBLOCK) continue;

        if (byteToBlockMap[k] != throwBBNum) {
          throwBBNum = byteToBlockMap[k];
          VM_BasicBlock throwBB = basicBlocks[throwBBNum];
          tryHandlerBB.addUniquePredecessor(throwBB);
        }
      }
    }
  }

  /**
   * Mark all the blocks within try range as being Try blocks
   * used for determining the stack maps for Handler blocks
   * Only called when exceptions is not null.
   */
  private void markTryBlocks(VM_ExceptionHandlerMap exceptions) {
    int[] tryStartPC = exceptions.getStartPC();
    int[] tryEndPC = exceptions.getEndPC();
    int tryLength = tryStartPC.length;
    int tryBlockNum = 0;
    for (int i=0; i< tryLength; i++) {
      for (int j=tryStartPC[i]; j< tryEndPC[i]; j++) {
        if (byteToBlockMap[j] != VM_BasicBlock.NOTBLOCK) {
          if (tryBlockNum != byteToBlockMap[j]) {
            tryBlockNum = byteToBlockMap[j];
            basicBlocks[tryBlockNum].setState(VM_BasicBlock.TRYBLOCK);
          }
        }
      }
    }
  }

  /**
   * Check if an athrow is within a try block, if it is, then handlers have this
   * block as their predecessor; which is registered in "processExceptionHandlers"
   * Otherwise, the athrow acts as a branch to the exit and that should be marked 
   * here. Note exceptions may be null. 
   */
  private void processAthrow(VM_ExceptionHandlerMap exceptions, int athrowIndex) {
    if (exceptions != null) {
      int[] tryStartPC   = exceptions.getStartPC();
      int[] tryEndPC     = exceptions.getEndPC();
      int   tryLength    = tryStartPC.length;
      // Check if this athrow index is within any of the try blocks
      for (int i=0; i<tryLength; i++) {
        if (tryStartPC[i] <= athrowIndex && athrowIndex < tryEndPC[i]) {
          return; // found it
        }
      }
    } 

    VM_BasicBlock athrowBB = basicBlocks[byteToBlockMap[athrowIndex]];
    basicBlocks[VM_BasicBlock.EXITBLOCK].addPredecessor(athrowBB);
  }

  
  /********************************/
  /*                              */
  /*   Misc routines              */
  /*                              */
  /********************************/


  /**
   * add a basic block to the list
   */
  private void addBasicBlock(VM_BasicBlock newBB) {
    // Check whether basicBlock array must be grown.
    //
    int blocknum = newBB.getBlockNumber();
    if (blocknum >= basicBlocks.length) {
      int currentSize = basicBlocks.length;
      int newSize = 15;
      if (currentSize!=2) {
        if (currentSize==15)
          newSize = bytelength >> 4; // assume 16 bytecodes per basic block
        else
          newSize = currentSize + currentSize >> 3;  // increase by 12.5%
        if (newSize <= blocknum)
          newSize = blocknum + 20;
      }
      VM_BasicBlock biggerBlocks[] = new VM_BasicBlock[newSize];
      for (int i=0; i<currentSize; i++) 
        biggerBlocks[i] = basicBlocks[i];
      basicBlocks = biggerBlocks;
    } 

    // Go ahead and add block
    basicBlocks[blocknum] = newBB;        
  }
  //-#if RVM_WITH_QUICK_COMPILER
   private static class SnapshotNode {
     byte[] snapshot;
     SnapshotNode wordNode;
     SnapshotNode longNode;
     SnapshotNode floatNode;
     SnapshotNode doubleNode;
     SnapshotNode objectNode;
     SnapshotNode arrayNode;
     SnapshotNode returnNode;
     static SnapshotNode emptyStackNode = new SnapshotNode(null, 0);
     
     SnapshotNode(byte[] stack, int stackHeight) {
       snapshot = new byte[stackHeight];
       if (stackHeight > 0)
         System.arraycopy(stack, 0, snapshot, 0, stackHeight);
     }
   }
   
   protected static byte[] createStackSnapshot(byte[] stack, int stackHeight) {
     if (stackHeight == 0) {
       return SnapshotNode.emptyStackNode.snapshot;
     }
     
     SnapshotNode node = SnapshotNode.emptyStackNode;
     for (int i=0; i<stackHeight; i++) {
       byte stackElement = stack[i];
       switch (stackElement) {
       case WORD_TYPE:
         if (node.wordNode == null)
           node.wordNode = new SnapshotNode(stack, i+1);
         node = node.wordNode;
         break;
       case LONG_TYPE:
         if (node.longNode == null)
           node.longNode = new SnapshotNode(stack, i+1);
         node = node.longNode;
         break;
       case FLOAT_TYPE:
         if (node.floatNode == null)
           node.floatNode = new SnapshotNode(stack, i+1);
         node = node.floatNode;
         break;
       case DOUBLE_TYPE:
         if (node.doubleNode == null)
           node.doubleNode = new SnapshotNode(stack, i+1);
         node = node.doubleNode;
         break;
       case OBJECT_TYPE:
         if (node.objectNode == null)
           node.objectNode = new SnapshotNode(stack, i+1);
         node = node.objectNode;
         break;
       case ARRAY_TYPE:
         if (node.arrayNode == null)
           node.arrayNode = new SnapshotNode(stack, i+1);
         node = node.arrayNode;
         break;
       case RETURN_ADDRESS_TYPE:
         if (node.returnNode == null)
           node.returnNode = new SnapshotNode(stack, i+1);
         node = node.returnNode;
         break;
       } // end switch on type
     } // end loop through stack
     return node.snapshot;
   }
   
   protected byte[] createStackSnapshot() {
     return createStackSnapshot(stack, currentStackHeight);
   }
 
   protected void createStackSnapshot(int index) {
     if (stackSnapshots[index] == null) {
        stackSnapshots[index] = createStackSnapshot(stack, currentStackHeight);
     }
   }
 
   private void pushBranch(int bci, byte[] stackSnapshot) {
     if (branchVisited[bci]) {
       return;
     }
 
     branchVisited[bci] = true;
     
     if (stackSnapshots[bci] == null)
       stackSnapshots[bci] = stackSnapshot;
     branchListIndex += 1;
     if (branchList == null || branchListIndex >= branchList.length) {
       int [] newbl = new int[branchListIndex + 100];
       if (branchList != null)
         System.arraycopy(branchList, 0,
                          newbl, 0, branchList.length);
       branchList = newbl;
     }
     branchList[branchListIndex] = bci;
     
   }
 
   private int popBranch() {
     if (branchListIndex == 0)
       return -1;
     else {
       int resumePoint = branchList[branchListIndex];
       currentStackHeight = stackSnapshots[resumePoint].length;
       System.arraycopy(stackSnapshots[resumePoint], 0,
                        stack, 0, currentStackHeight);
       branchListIndex -= 1;
       return resumePoint;
     }
   }
 
   public void generateStackSnapshots(VM_NormalMethod method) {
 
     if (byteToBlockMap == null)
       determineTheBasicBlocks(method);
 
     VM_BytecodeStream bcodes         = method.getBytecodes();
     int               lastInstrStart;// byte index where last instruction started
     byte[] snap;
       
     if (stackSnapshots == null ||
         stackSnapshots.length < bytelength) {
       stackSnapshots = new byte[bytelength][];
       stack = new byte[bytelength];
       branchVisited = new boolean[bytelength];
     }
 
     branchListIndex = 0;
     lastInstrStart = -1;
     java.util.Arrays.fill(branchVisited, false);
     java.util.Arrays.fill(stackSnapshots, null);
     
 
     // Add handler bodies to lists of blocks to process
     VM_ExceptionHandlerMap    exceptions = method.getExceptionHandlerMap();
     if (exceptions != null) {
       int[] tryHandlerPC =     exceptions.getHandlerPC();
       int tryLength = tryHandlerPC.length;
       stack[0] = OBJECT_TYPE;
       snap = createStackSnapshot(stack, 1);
       for (int i=0; i<tryLength; i++) {
         pushBranch(tryHandlerPC[i], snap);
       }
     }
 
     // Add first line of bytecode to list of blocks to process
     bcodes.reset();
     currentStackHeight = 0;
     pushBranch(bcodes.index(), createStackSnapshot(null, 0));
     boolean branchDone = true;
 
     short lastBlock = VM_BasicBlock.NOTBLOCK;
     
     bcLoop: while (true) {
       
       if (branchDone) {
         int target = popBranch();
         if (target < 0)
           break bcLoop;
         else
           bcodes.reset(target);
         branchDone = false;
       } else {
           bcodes.reset(nextInstructionStart[lastInstrStart]);
       }
 
       if (lastBlock != byteToBlockMap[bcodes.index()]) {
         createStackSnapshot(bcodes.index());
       }
       
       lastInstrStart = bcodes.index();  // Instruction starts here
       int opcode = bcodes.nextInstruction();

       switch (opcode) {
 
       case JBC_aconst_null :
         {
           push(OBJECT_TYPE);
           break;
         }
       case JBC_aload_0 :
         {
           push(OBJECT_TYPE);
           break;
         }
       case JBC_iload_0 :
         {
           push(WORD_TYPE);
           break;
         }
       case JBC_aload_1 :
         {
           push(OBJECT_TYPE);
           break;
         }
       case JBC_iload_1 :
         {
           push(WORD_TYPE);
           break;
         }
       case JBC_aload_2 :
         {
           push(OBJECT_TYPE);
           break;
         }
       case JBC_iload_2 :
         {
           push(WORD_TYPE);
           break;
         }
       case JBC_aload_3 :
         {
           push(OBJECT_TYPE);
           break;
         }
       case JBC_iload_3 :
         {
           push(WORD_TYPE);
           break;
         }
       case JBC_aload : 
         {
           push(OBJECT_TYPE);
           break;
         }
       case JBC_iload :
         {
           push(WORD_TYPE);
           break;
         }
         
       case JBC_fload_0 :
         {
           push(FLOAT_TYPE);
           break;
         }
       case JBC_fload_1 :
         {
           push(FLOAT_TYPE);
           break;
         }
       case JBC_fload_2 :
         {
           push(FLOAT_TYPE);
           break;
         }
       case JBC_fload_3 :
         {
           push(FLOAT_TYPE);
           break;
         }
       case JBC_fload : 
         {
           push(FLOAT_TYPE);
           break;
         }
        
       case JBC_lload_0 :
         {
           push(LONG_TYPE);
           break;
         }
       case JBC_lload_1 :
         {
           push(LONG_TYPE);
           break;
         }
       case JBC_lload_2 :
         {
           push(LONG_TYPE);
           break;
         }
       case JBC_lload_3 :
         {
           push(LONG_TYPE);
           break;
         }
       case JBC_lload : 
         {
           push(LONG_TYPE);
           break;
         }
 
       case JBC_dload_0 :
         {
           push(DOUBLE_TYPE);
           break;
         }
       case JBC_dload_1 :
         {
           push(DOUBLE_TYPE);
           break;
         }
       case JBC_dload_2 :
         {
           push(DOUBLE_TYPE);
           break;
         }
       case JBC_dload_3 :
         {
           push(DOUBLE_TYPE);
           break;
         }
       case JBC_dload : 
         {
           push(DOUBLE_TYPE);
           break;
         }
        
       case JBC_astore_0 :
         {
           pop(OBJECT_TYPE);
           break;
         }
       case JBC_istore_0 :
         {
           pop(WORD_TYPE);
           break;
         }
       case JBC_astore_1 :
         {
           pop(OBJECT_TYPE);
           break;
         }
       case JBC_istore_1 :
         {
           pop(WORD_TYPE);
           break;
         }
       case JBC_astore_2 :
         {
           pop(OBJECT_TYPE);
           break;
         }
       case JBC_istore_2 :
         {
           pop(WORD_TYPE);
           break;
         }
       case JBC_astore_3 :
         {
           pop(OBJECT_TYPE);
           break;
         }
       case JBC_istore_3 :
         {
           pop(WORD_TYPE);
           break;
         }
       case JBC_astore : 
         {
           pop(OBJECT_TYPE);
           break;
         }
       case JBC_istore :
         {
           pop(WORD_TYPE);
           break;
         }
         
       case JBC_fstore_0 :
         {
           pop(FLOAT_TYPE);
           break;
         }
       case JBC_fstore_1 :
         {
           pop(FLOAT_TYPE);
           break;
         }
       case JBC_fstore_2 :
         {
           pop(FLOAT_TYPE);
           break;
         }
       case JBC_fstore_3 :
         {
           pop(FLOAT_TYPE);
           break;
         }
       case JBC_fstore : 
         {
           pop(FLOAT_TYPE);
           break;
         }
        
       case JBC_lstore_0 :
         {
           pop(LONG_TYPE);
           break;
         }
       case JBC_lstore_1 :
         {
           pop(LONG_TYPE);
           break;
         }
       case JBC_lstore_2 :
         {
           pop(LONG_TYPE);
           break;
         }
       case JBC_lstore_3 :
         {
           pop(LONG_TYPE);
           break;
         }
       case JBC_lstore : 
         {
           int index;
           pop(LONG_TYPE);
           break;
         }
        
       case JBC_dstore_0 :
         {
           pop(DOUBLE_TYPE);
           break;
         }
       case JBC_dstore_1 :
         {
           pop(DOUBLE_TYPE);
           break;
         }
       case JBC_dstore_2 :
         {
           pop(DOUBLE_TYPE);
           break;
         }
       case JBC_dstore_3 :
         {
           pop(DOUBLE_TYPE);
           break;
         }
       case JBC_dstore : 
         {
           pop(DOUBLE_TYPE);
           break;
         }
        
 
       case JBC_ldc :
       case JBC_ldc_w :
         {
           int index;
           if (opcode == JBC_ldc_w)
             index =  bcodes.getWideConstantIndex();
           else
             index = bcodes.getConstantIndex();
           byte type = method.getDeclaringClass().getLiteralDescription(index);
           switch (type) {
           case VM_Statics.FLOAT_LITERAL:
             type = FLOAT_TYPE;
             break;
           case VM_Statics.STRING_LITERAL:
             type = OBJECT_TYPE;
             break;
           default:
             type = WORD_TYPE;  
             break;
           }
           push(type);
           break;
         }
       case JBC_ldc2_w :
         {
           int index = bcodes.getWideConstantIndex();
           byte type = method.getDeclaringClass().getLiteralDescription(index);
           if (type == VM_Statics.LONG_LITERAL) {
             type = LONG_TYPE;
           }
           else {
             type = DOUBLE_TYPE;
           }
           push(type);
           break;
         }
        
       case JBC_iconst_m1 :
       case JBC_iconst_0 :
       case JBC_iconst_1 :
       case JBC_iconst_2 :
       case JBC_iconst_3 :
       case JBC_iconst_4 :
       case JBC_iconst_5 :
         {
           push(WORD_TYPE);
           break;
         }
       case JBC_fconst_0 :
       case JBC_fconst_1 :
       case JBC_fconst_2 :
         {
           push(FLOAT_TYPE);
           break;
         }
         
       case JBC_lconst_0 :
       case JBC_lconst_1 :
         {
           push(LONG_TYPE);
           break;
         }
         
       case JBC_dconst_0 :
       case JBC_dconst_1 :
         {
           push(DOUBLE_TYPE);
           break;
         }
         
       case JBC_bipush :
         {
           push(WORD_TYPE);
           break;
         }
       case JBC_sipush :
         {
           push(WORD_TYPE);
           break;
         }
       case JBC_i2l :
         {
           popAndPush(WORD_TYPE, LONG_TYPE);
           break;
         }
         
       case JBC_i2d :
         {
           popAndPush(WORD_TYPE, DOUBLE_TYPE);
           break;
         }
       case JBC_f2l :
         {
           popAndPush(FLOAT_TYPE, LONG_TYPE);
           break;
         }
       case JBC_f2d : 
         {
           popAndPush(FLOAT_TYPE, DOUBLE_TYPE);
           break;
         }
 
         // Stack management
       case JBC_dup :
       case JBC_dup2 :
       case JBC_dup_x1 :
       case JBC_dup2_x1 :
       case JBC_dup_x2 :
       case JBC_dup2_x2 :
       case JBC_swap :
       case JBC_pop  :
       case JBC_pop2  :
         {
           currentStackHeight = rearrangeStack(opcode, stack, currentStackHeight);
           break;
         }
         
       case JBC_fadd :
       case JBC_fsub :
       case JBC_fmul :
       case JBC_fdiv :
       case JBC_frem :
         {
           popAndPush(FLOAT_TYPE, FLOAT_TYPE, FLOAT_TYPE);
           break;
         }
       case JBC_iadd :
       case JBC_isub :
       case JBC_imul :
       case JBC_ishl :
       case JBC_ishr :
       case JBC_iushr :
       case JBC_iand :
       case JBC_ior :
       case JBC_ixor :
       case JBC_irem :
       case JBC_idiv : 
         {
           popAndPush(WORD_TYPE, WORD_TYPE, WORD_TYPE);
           break;
         }
 
       case JBC_lshl :      // long shifts that int shift value
       case JBC_lshr :
       case JBC_lushr :
         {
           popAndPush(WORD_TYPE, LONG_TYPE, LONG_TYPE);
           break;
         }
         
       case JBC_l2i :
         {
           popAndPush(LONG_TYPE, WORD_TYPE);
           break;
         }
       case JBC_d2i :
         {
           popAndPush(DOUBLE_TYPE, WORD_TYPE);
           break;
         }
       case JBC_f2i :
         {
           popAndPush(FLOAT_TYPE, WORD_TYPE);
           break;
         }
       case JBC_int2byte :
       case JBC_int2char :
       case JBC_int2short :
         {
           break;
         }
       case JBC_i2f :
         {
           popAndPush(WORD_TYPE, FLOAT_TYPE);
           break;
         }
       case JBC_l2f :
         {
           popAndPush(LONG_TYPE, FLOAT_TYPE);
           break;
         }
       case JBC_d2f :
         {
           popAndPush(DOUBLE_TYPE, FLOAT_TYPE);
           break;
         }
        
       case JBC_l2d :
         {
           popAndPush(LONG_TYPE, DOUBLE_TYPE);
           break;
         }
       
       case JBC_d2l :
         {
           popAndPush(DOUBLE_TYPE, LONG_TYPE);
           break;
         }
        
       case JBC_fcmpl :
       case JBC_fcmpg :
         {
           popAndPush(FLOAT_TYPE, FLOAT_TYPE, WORD_TYPE);
           break;
         }
       case JBC_lcmp :
         {
           popAndPush(LONG_TYPE, LONG_TYPE, WORD_TYPE);
           break;
         }
       case JBC_dcmpl :
       case JBC_dcmpg : 
         {
           popAndPush(DOUBLE_TYPE, DOUBLE_TYPE, WORD_TYPE);
           break;
         }
        
       case JBC_ladd :
       case JBC_lsub :
       case JBC_lmul :
       case JBC_land :
       case JBC_lor :
       case JBC_lxor :
       case JBC_lrem :
       case JBC_ldiv :
         {
           popAndPush(LONG_TYPE, LONG_TYPE, LONG_TYPE);
           break;
         }
       case JBC_dadd :
       case JBC_dsub :
       case JBC_dmul :
       case JBC_ddiv :
       case JBC_drem :
         {
           popAndPush(DOUBLE_TYPE, DOUBLE_TYPE, DOUBLE_TYPE);
           break;
         }
        
        
       case JBC_ineg :
       case JBC_lneg :
       case JBC_fneg :
       case JBC_dneg :
       case JBC_iinc :
         break;
        
       case JBC_ifeq :
       case JBC_ifne :
       case JBC_iflt :
       case JBC_ifge :
       case JBC_ifgt :
       case JBC_ifle :
         {
           pop(WORD_TYPE);
           snap = createStackSnapshot();
           int offset = bcodes.getBranchOffset();
           pushBranch(lastInstrStart+offset, snap);
           pushBranch(nextInstructionStart[lastInstrStart], snap);
           branchDone = true;
           break;
         }
       case JBC_ifnull :
       case JBC_ifnonnull :
         {
           pop(OBJECT_TYPE);
           snap = createStackSnapshot();
           int offset = bcodes.getBranchOffset();
           pushBranch(lastInstrStart+offset, snap);
           pushBranch(nextInstructionStart[lastInstrStart], snap);
           branchDone = true;
           break;
         }
       case JBC_if_icmpeq :
       case JBC_if_icmpne :
       case JBC_if_icmplt :
       case JBC_if_icmpge :
       case JBC_if_icmpgt :
       case JBC_if_icmple :
         {
           pop(WORD_TYPE, WORD_TYPE);
           snap = createStackSnapshot();
           int offset = bcodes.getBranchOffset();
           pushBranch(lastInstrStart+offset, snap);
           pushBranch(nextInstructionStart[lastInstrStart], snap);
           branchDone = true;
           break;
         }
       case JBC_if_acmpeq :
       case JBC_if_acmpne :
         {
           pop(OBJECT_TYPE, OBJECT_TYPE);
           snap = createStackSnapshot();
           int offset = bcodes.getBranchOffset();
           pushBranch(lastInstrStart+offset, snap);
           pushBranch(nextInstructionStart[lastInstrStart], snap);
           branchDone = true;
           break;
         }
 	       
       case JBC_jsr :
       case JBC_jsr_w :
         {
           // Technically, the return address is pushed on stack, but it
           // is consumed by the jsr routine before it returns.
           snap = createStackSnapshot();
           pushBranch(nextInstructionStart[lastInstrStart], snap);
           push(OBJECT_TYPE);
           snap = createStackSnapshot();
           int offset = bcodes.getBranchOffset();
           pushBranch(lastInstrStart+offset, snap);
           branchDone = true;
           break;
         }
 
       case JBC_goto :
         {
           snap = createStackSnapshot();
           int offset = bcodes.getBranchOffset();
           pushBranch(lastInstrStart+offset, snap);
           branchDone = true;
           break;
         }
       case JBC_goto_w :
         {
           snap = createStackSnapshot();
           int offset = bcodes.getWideBranchOffset();
           pushBranch(lastInstrStart+offset, snap);
           branchDone = true;
           break;
         }
       case JBC_tableswitch :
         {
           pop(WORD_TYPE);
           snap = createStackSnapshot();
           bcodes.alignSwitch();
           int def = bcodes.getDefaultSwitchOffset();
           pushBranch(lastInstrStart+def, snap);
           int low = bcodes.getLowSwitchValue();
           int high = bcodes.getHighSwitchValue();
           int n = high-low+1;             
           // generate labels for offsets
           for (int i=0; i<n; i++) {
             int offset = bcodes.getTableSwitchOffset(i);
             pushBranch(lastInstrStart+offset, snap);
           }
           bcodes.skipTableSwitchOffsets(n);
           branchDone = true;
           break;
         }
       case JBC_lookupswitch :
         {
           pop(WORD_TYPE);
           snap = createStackSnapshot();
           bcodes.alignSwitch();
           int def = bcodes.getDefaultSwitchOffset();
           int npairs = bcodes.getSwitchLength();
           pushBranch(lastInstrStart+def, snap);
 
           // generate label for each offset in table
           for (int i=0; i<npairs; i++) {
             int offset  = bcodes.getLookupSwitchOffset(i);
             pushBranch(lastInstrStart+offset, snap);
           }	   
           bcodes.skipLookupSwitchPairs(npairs);
           branchDone = true;
           break;
         }
 	  
       case JBC_invokespecial :
       case JBC_invokeinterface :
       case JBC_invokevirtual :
       case JBC_invokestatic :
         {
           VM_MethodReference calledMethod = bcodes.getMethodReference();
 
     
           VM_TypeReference[] argTypes = calledMethod.getParameterTypes();
           if (argTypes != null)
             for (int i= argTypes.length-1; i >= 0; i--)
               pop(getJavaStackType(argTypes[i]));
 
           if (opcode != JBC_invokestatic)
             pop(WORD_TYPE);
 
           push(getJavaStackType(calledMethod.getReturnType()));
           break;
         }
        
       case JBC_dreturn:
         {
           pop(DOUBLE_TYPE);
           branchDone = true;
             break;
         }
       case JBC_freturn:
         {
           pop(FLOAT_TYPE);
           branchDone = true;
             break;
         }
       case JBC_lreturn:
         {
           pop(LONG_TYPE);
           branchDone = true;
             break;
         }
       case JBC_ireturn :
         {
           pop(WORD_TYPE);
           branchDone = true;
             break;
         }
       case JBC_areturn :
         {
           pop(OBJECT_TYPE);
           branchDone = true;
             break;
         }
       case JBC_return  :
         branchDone = true;
           break;
 
       case JBC_ret :
         {
           int index = bcodes.getLocalNumber();
           branchDone = true;
             break;
         }
 
       case JBC_athrow :
         {
           pop(OBJECT_TYPE);
           branchDone = true;
             break;
         }
 
       case JBC_getfield :
       case JBC_getstatic :
         {
           VM_FieldReference fieldRef = bcodes.getFieldReference();
           if (opcode == JBC_getfield)
             pop(OBJECT_TYPE);
 
           push(getJavaStackType(fieldRef.getFieldContentsType()));
           break;
         }
       case JBC_putfield :
       case JBC_putstatic : 
         {
           VM_FieldReference fieldRef = bcodes.getFieldReference();
           pop(getJavaStackType(fieldRef.getFieldContentsType()));
           if (opcode == JBC_putfield)
             pop(OBJECT_TYPE);
           break;
         }
 
       case JBC_checkcast :
         {
           break;
         }
       case JBC_instanceof :
         {
           popAndPush(OBJECT_TYPE, WORD_TYPE);
           break;
         }
       case JBC_new : 
         {
           push(OBJECT_TYPE);
           break;
         }
        
       case JBC_iaload :
       case JBC_baload :
       case JBC_caload :
       case JBC_saload :
         {
           popAndPush(WORD_TYPE, ARRAY_TYPE, WORD_TYPE);
           break;
         }
       case JBC_faload :
         {
           popAndPush(WORD_TYPE, ARRAY_TYPE, FLOAT_TYPE);
           break;
         }
       case JBC_daload :
         {
           popAndPush(WORD_TYPE, ARRAY_TYPE, DOUBLE_TYPE);
           break;
         }
       case JBC_laload :
         {
           popAndPush(WORD_TYPE, ARRAY_TYPE, LONG_TYPE);
           break;
         }
       case JBC_aaload :
         {
           popAndPush(WORD_TYPE, ARRAY_TYPE, OBJECT_TYPE);
           break;
         }
 
       case JBC_iastore :
       case JBC_bastore :
       case JBC_castore :
       case JBC_sastore :
         {
           pop (WORD_TYPE, WORD_TYPE, OBJECT_TYPE);
           break;
         }
       case JBC_fastore :
         {
           pop (FLOAT_TYPE, WORD_TYPE, OBJECT_TYPE);
           break;
         }
       case JBC_dastore :
         {
           pop (DOUBLE_TYPE, WORD_TYPE, OBJECT_TYPE);
           break;
         }
       case JBC_lastore :
         {
           pop (LONG_TYPE, WORD_TYPE, OBJECT_TYPE);
           break;
         }
       case JBC_aastore :
         {
           pop (OBJECT_TYPE, WORD_TYPE, OBJECT_TYPE);
           break;
         }
 
 
       case JBC_newarray :
         {
           popAndPush(WORD_TYPE, ARRAY_TYPE);
           break;
         }
       case JBC_anewarray :
         {
           popAndPush(WORD_TYPE, ARRAY_TYPE);
           break;
         }
       case JBC_multianewarray :
         {
           VM_TypeReference typeRef = bcodes.getTypeReference();
           int dimensions        = bcodes.getArrayDimension();
           for (int i=0; i < dimensions; i++)
             pop (WORD_TYPE);
           push(OBJECT_TYPE);
           break;
         }
        
       case JBC_arraylength :
         {
           popAndPush(ARRAY_TYPE, WORD_TYPE);
           break;
         }
        
       case JBC_monitorenter :
       case JBC_monitorexit :
         {
           pop(OBJECT_TYPE);
           break;
         }
           
       case JBC_wide :
         {
           int widecode = bcodes.getWideOpcode();
           if (widecode == JBC_ret) {
             int index = bcodes.getWideLocalNumber();
           } else if (widecode == JBC_iinc) {
             int val = bcodes.getWideIncrement();
           } else {
             // nothing more to do
           }
           break;
         }
          
 
       case 0xba: /* --- unused --- */
         break;
         
       case JBC_nop: 
         break;
       
       default :
         {
           //throw new ClassFormatError("Unknown opcode:" + opcode);
           break;
         }
       } // end switch
     }
   }  //////////////////////////////////////////////////////////////////////
 
   byte peek() {
     return peek(0);
   }
 
   byte peek(int index) {
     return stack[currentStackHeight-index];
   }
 
   void push(byte type) {
     if (type == VOID_TYPE)
       return;
     stack[currentStackHeight] = type;
     if (getJavaStackSize(type) == 1)
       currentStackHeight += 1;
     else {
       stack[++currentStackHeight] = type;
       currentStackHeight += 1;
     }
     if (VM.VerifyAssertions) VM._assert(currentStackHeight <= maxStackHeight);
   }
 
   void popAndPush(byte firstPopType, byte secondPopType, byte pushType) {
     currentStackHeight -=
       getJavaStackSize(firstPopType) + getJavaStackSize(secondPopType);
     stack[currentStackHeight] = pushType;
     if (getJavaStackSize(pushType) == 1)
       currentStackHeight += 1;
     else {
       stack[++currentStackHeight] = pushType;
       currentStackHeight += 1;
     }
     if (VM.VerifyAssertions) VM._assert(currentStackHeight >= 0);
   }
 
   void popAndPush(byte popType, byte pushType) {
     currentStackHeight -= getJavaStackSize(popType);
     stack[currentStackHeight] = pushType;
     if (getJavaStackSize(pushType) == 1)
       currentStackHeight += 1;
     else {
       stack[++currentStackHeight] = pushType;
       currentStackHeight += 1;
     }
     if (VM.VerifyAssertions) VM._assert(currentStackHeight >= 0);
   }
 
   void pop(byte type) {
     currentStackHeight -= getJavaStackSize(type);
     if (VM.VerifyAssertions) VM._assert(currentStackHeight >= 0);
   }
 
   void pop(byte firstPopType, byte secondPopType) {
     currentStackHeight -=
       getJavaStackSize(firstPopType) + getJavaStackSize(secondPopType);
     if (VM.VerifyAssertions) VM._assert(currentStackHeight >= 0);
   }
 
   void pop(byte firstPopType, byte secondPopType, byte thirdPopType) {
     currentStackHeight -=
       getJavaStackSize(firstPopType) +
       getJavaStackSize(secondPopType) +
       getJavaStackSize(thirdPopType);
     if (VM.VerifyAssertions) VM._assert(currentStackHeight >= 0);
   }
 
   private final void setStack(byte[] newStack) {
     if (newStack == null)
       currentStackHeight = 0;
     else {
       currentStackHeight = (short)newStack.length;
       System.arraycopy(newStack, 0, stack, 0, currentStackHeight);
     }
   }
   
   final int getCurrentStackHeight() {
     return currentStackHeight;
   }
 
   static short rearrangeStack(int operator, Object stack, int height) {
     int top = height - 1;
     int  newTop = top;
     switch (operator) {
     case JBC_dup:
       System.arraycopy(stack, top  , stack, top+1, 1);
       newTop += 1;
       break;
     case JBC_dup2:
       System.arraycopy(stack, top-1, stack, top+1, 2);
       newTop += 2;
       break;
     case JBC_dup_x1:
       System.arraycopy(stack, top-1, stack, top  , 2);
       System.arraycopy(stack, top+1, stack, top-1, 1);
       newTop += 1;
       break;
     case JBC_dup2_x1:
       System.arraycopy(stack, top-2, stack, top  , 3);
       System.arraycopy(stack, top+1, stack, top-2, 2);
       newTop += 2;
       break;
     case JBC_dup_x2:
       System.arraycopy(stack, top-2, stack, top-1, 3);
       System.arraycopy(stack, top+1, stack, top-2, 1);
       newTop += 1;
       break;
     case JBC_dup2_x2:
       System.arraycopy(stack, top-3, stack, top-1, 4);
       System.arraycopy(stack, top+1, stack, top-3, 2);
       newTop += 2;
       break;
     case JBC_swap:
       // stack must be one larger than top!
       System.arraycopy(stack, top-1, stack, top+1, 1);
       System.arraycopy(stack, top  , stack, top-1, 2);
       break;
     case JBC_pop:
       newTop -= 1;
       break;
     case JBC_pop2:
       newTop -= 2;
       break;
     default:
       newTop = top;
     }
     return (short)(newTop+1);
   }
   public static int getJavaStackSize(byte type) {
     return type & LENGTH_MASK;
   }
 
   public static int getJavaStackSize(VM_TypeReference typeRef) {
     return getJavaStackType(typeRef) & LENGTH_MASK;
   }
   public static byte getJavaStackType(VM_TypeReference typeRef) {
     byte stackType;
 
     
     if (!typeRef.isResolved())
       stackType = OBJECT_TYPE;
     else {
       VM_Type type = typeRef.peekResolvedType();
       
       if (type.isLongType())
       stackType = LONG_TYPE;
     else if (type.isDoubleType())
       stackType = DOUBLE_TYPE;
     else if (type.isFloatType())
       stackType = FLOAT_TYPE;
     else if (type.isArrayType())
       stackType = ARRAY_TYPE;
     else if (type.isClassType())
       stackType = OBJECT_TYPE;
     else if (type.isVoidType())
       stackType = VOID_TYPE;
     else 
       stackType = WORD_TYPE;
     }
     return stackType;
   }
   
   public static String stackDump(byte[] stack, int stackTop) {
     if (stackTop == 0)
       return "<empty>";
     else if (stackTop < 0 || stackTop > stack.length)
       return "BAD STACK INDEX " + stackTop +" > " + stack.length;
     StringBuffer result = new StringBuffer("");
     
     for (int i = 0; i < stackTop; i++) {
       String s;
       switch (stack[i]) {
       case VOID_TYPE:
         s = "V ";
         break;
       case OBJECT_TYPE:
         s = "O ";
         break;
       case ARRAY_TYPE:
         s = "A ";
         break;
       case WORD_TYPE:
         s = "W ";
         break;
       case LONG_TYPE:
         s = "L ";
         break;
       case FLOAT_TYPE:
         s = "F ";
         break;
       case DOUBLE_TYPE:
         s = "D ";
         break;
       default:
         s = "? ";
         break;
       }
       result.append(s);
     }
     return result.toString();
    }
  //-#endif
}
