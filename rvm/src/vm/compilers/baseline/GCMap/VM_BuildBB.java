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
final class VM_BuildBB implements VM_BytecodeConstants {

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
  VM_BasicBlockFactory      bbf;
  VM_BasicBlock             basicBlocks[];       

  /** 
   * identify which block a byte is part of 
   */
  short                     byteToBlockMap[]; 

  /**
   * Number of unique jsr targets processed
   */
  int                       numJsrs; 
  
  /**
   * Number of GC points found
   */
  int                       gcPointCount;

  // This variable is used in multiple methods of this class, make it accessible
  int bytelength; 

  /**
   * Analyze the bytecodes and build the basic blocks with their predecessors.
   * The results will be used by VM_BuildReferenceMaps
   */
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

    byteToBlockMap = new short[bytelength];
    basicBlocks    = new VM_BasicBlock[2];  // many methods only have one block (+1 for EXIT)

    bbf = new VM_BasicBlockFactory();

    exceptions = method.getExceptionHandlerMap();

    retList = null;

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
      lastInstrStart = bcodes.index();  // Instruction starts here
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
}
