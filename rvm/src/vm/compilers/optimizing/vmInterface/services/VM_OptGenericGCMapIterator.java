/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * This class contains its architecture-independent code for iteration
 * across the references represented by a frame built by the OPT compiler.
 *
 * @see the architecture-specific class VM_OptGCMapIterator for the final
 * details.
 *
 * @author Michael Hind
 */

abstract class VM_OptGenericGCMapIterator extends VM_GCMapIterator 
  implements VM_OptGCMapIteratorConstants {

  // Constructor 
  VM_OptGenericGCMapIterator(int[] registerLocations) {
    super();
    this.registerLocations = registerLocations;
  }

  /**
   * Initialize the iterator for another stack frame scan
   * @param compiledMethod the compiled method we are interested in
   * @param instructionOffset the place in the method we current are
   * @param framePtr the current frame pointer
   */
  public void setupIterator(VM_CompiledMethod compiledMethod, 
			    int instructionOffset, int framePtr) {
    if (debug) {
      VM.sysWrite("\n\t   ==========================\n");
      VM.sysWrite("Reference map request made");
      VM.sysWrite(" for machine code offset: ");
      VM.sysWrite(instructionOffset);
      VM.sysWrite("\n");
      VM.sysWrite("\tframePtr: ");
      VM.sysWrite(framePtr);
      VM.sysWrite("\n");
    }

    reset();

    // retrieve and save the corresponding VM_OptMachineCodeMap for
    // this method and instructionOffset
    compilerInfo = (VM_OptCompilerInfo)compiledMethod.getCompilerInfo();
    map = compilerInfo.getMCMap();
    mapIndex = map.findGCMapIndex(instructionOffset);
    if (mapIndex == VM_OptGCMap.ERROR) {
      VM.sysWrite("VM_OptMachineCodeMap: findGCMapIndex failed\n");
      VM.sysWrite("Method: ");
      VM.sysWrite(compiledMethod.getMethod());
      VM.sysWrite(", Machine Code (MC) Offset: ");
      VM.sysWrite(instructionOffset);
      VM.sysWrite("\n");
      VM.sysFail("VM_OptGenericMapIterator: findGCMapIndex failed\n");
    }

    // save the frame pointer
    this.framePtr = framePtr;

    if (debug) {
      VM.sysWrite("\tMethod: ");
      VM.sysWrite(compiledMethod.getMethod());
      VM.sysWrite("\n ");

      if (mapIndex == VM_OptGCMap.NO_MAP_ENTRY) {
        VM.sysWrite("... empty map found\n");
      } else {
        VM.sysWrite("... found a map\n");
      }

      if (lookForMissedReferencesInSpills) {
	VM.sysWrite("FramePtr: ");
	VM.sysWrite(framePtr);
	VM.sysWrite("\tFirst Spill: ");
	VM.sysWrite(getFirstSpillLoc());
	VM.sysWrite("\tLast Spill: ");
	VM.sysWrite(getLastSpillLoc());
	VM.sysWrite("\n");
      }
    }
  }

  /**
   * Returns the next address that contains a reference
   * @return the value of the next reference
   */
  public int getNextReferenceAddress() {
    if (debug) {   VM.sysWrite("  next => ");    }

    // make sure we have a map entry to look at 
    if (mapIndex == VM_OptGCMap.NO_MAP_ENTRY) {
      if (debug) {
        VM.sysWrite("  No Map, returning 0\n");
      }
      if (lookForMissedReferencesInRegs) {
        checkAllRegistersForMissedReferences();
      }

      // make sure we update the registerLocations before returning!
      updateLocateRegisters();
      return 0;
    }

    // Have we gone through all the registers yet?
    if (currentRegisterIsValid()) {
      // See if there are any more
      while (currentRegisterIsValid() && 
	     !map.registerIsSet(mapIndex, getCurrentRegister())) {
        if (lookForMissedReferencesInRegs) {
          // inspect the register we are skipping
	  checkCurrentRegisterForMissedReferences();
        }
	updateCurrentRegister();
      }

      // If we found a register, return the value
      if (currentRegisterIsValid()) {
        int regLocation;
        // currentRegister contains a reference, return that location
        regLocation = registerLocations[getCurrentRegister()];
	if (debug) {
          VM.sysWrite(" *** Ref found in reg#");
          VM.sysWrite(getCurrentRegister());
          VM.sysWrite(", location ==>");
          VM.sysWrite(regLocation);
          VM.sysWrite(", contents ==>");
          VM.sysWrite(VM_Magic.getMemoryWord(regLocation));
          VM.sysWrite("\n");
        }

        // update for the next call to this routine
	updateCurrentRegister();
        return regLocation;
      }
    }

    // we already processes the registers, check to see if there are any
    // references in spill locations.
    // To do this we request the nextLocation from the ref map.
    // If it returns a non-sentinel value we have a reference is a spill.
    mapIndex = map.nextLocation(mapIndex);
    if (mapIndex == VM_OptGCMap.NO_MAP_ENTRY) {
      if (debug) {
        VM.sysWrite("  No more to return, returning 0\n");
      }

      if (lookForMissedReferencesInSpills) {
	if (spillLoc == 0) {
	  // Didn't have any refs in spill locations, so we should
	  // check for spills among the whole spill area
	  checkForMissedSpills(0, 0);
	} else {
	  // check for spills after the last one we saw
	  checkForMissedSpills(spillLoc, 0);
	}
      }

      // OK, we are done returning references for this GC point/method/FP
      //   so now we must update the LocateRegister array for the next
      //   stack frame
      updateLocateRegisters();
      return 0;
    } else {
      // Determine the spill location given the frame ptr and spill offset.
      // (The location of spills varies among architectures.)
      int newSpillLoc = getStackLocation(framePtr, 
					 map.gcMapInformation(mapIndex));

      if (debug) {
        VM.sysWrite(" *** Ref found in Spill Loc: ");
        VM.sysWrite(newSpillLoc);
        VM.sysWrite(", offset: ");
        VM.sysWrite(map.gcMapInformation(mapIndex));
	VM.sysWrite(", value ==>");
	VM.sysWrite(VM_Magic.getMemoryWord(newSpillLoc));
        VM.sysWrite("\n");
      }

      if (lookForMissedReferencesInSpills) {
	checkForMissedSpills(spillLoc, newSpillLoc);
      }

      spillLoc = newSpillLoc;
      // found another ref, return it
      return spillLoc;
    }
  }

  /**
   * This method is called repeatedly to process derived pointers related
   *  to JSRs.  (They are pointers to code and need to be updated if the
   *  code moves.)
   * @return the next code pointer or 0 if no more exist
   */
  public int getNextReturnAddressAddress() {
    // Since the Opt compiler inlines JSRs, this method will always return 0
    //  signaling the end of the list of such pointers.
    if (debug) {
      VM.sysWrite("\t\t getNextReturnAddressOffset returning 0\n");
    }
    return 0;
  }

  /**
   * scan of this frame is complete
   * clean up any pointers to allow GC to reclaim dead objects
   */
  public void cleanupPointers() {
    // primitive types aren't worth reinitialing because setUpIterator
    //   will take care of this.
    map = null;
    compilerInfo = null;
  }

  /**
   * lets GC ask what type of iterator without using instanceof which can
   * cause an allocation
   */
  public int getType() {
    return VM_GCMapIterator.OPT;
  }

  /**
   * Externally visible method called to reset internal state
   */
  public void reset() {
    currentRegister = FIRST_GCMAP_REG;
    spillLoc = 0;
  }

  /**
   * return the current register we are processing
   * @return the current register we are processing
   */
  public int getCurrentRegister() {
    return currentRegister;
  }

  /**
   * update the state of the current register we are processing
   */
  public void updateCurrentRegister() {
    currentRegister++;
  }

  /**
   * Determines if the value of "currentRegister" is valid, or if we
   * processed all registers
   * @return whether the currentRegister is valid
   */
  public boolean currentRegisterIsValid() {
    return currentRegister <= LAST_GCMAP_REG;
  }

  /** 
   * If any non-volatile gprs were saved by the method being processed
   * then update the registerLocations array with the locations where the
   * registers were saved.
   */
  abstract void updateLocateRegisters();

  /** 
   *  Determine the stack location given the frame ptr and spill offset.
   *  (The offset direction varies among architectures.)
   *  @param framePtr the frame pointer
   *  @param offset  the offset 
   *  @return the resulting stack location
   */
  abstract int getStackLocation(int framePtr, int offset);

  /** 
   *  Get address of the first spill location for the given frame ptr
   *  (The location of spills varies among architectures.)
   *  @param the frame pointer
   *  @return the first spill location
   */
  abstract int getFirstSpillLoc();

  /** 
   *  Get address of the last spill location for the given frame ptr
   *  (The location of spills varies among architectures.)
   *  @param the frame pointer
   *  @return the last spill location
   */
  abstract int getLastSpillLoc();

  /**
   * This method inspects the "current" register for values that look like refs.
   */
  void checkCurrentRegisterForMissedReferences() {
    int currentReg = getCurrentRegister();
    if (verbose) {
      VM.sysWrite(" Inspecting Regs: ");
      VM.sysWrite(currentReg);
      VM.sysWrite("\n");
    }
    checkRegistersForMissedReferences(currentReg, currentReg);
  }

  /**
   * This method inspects all the registers for values that look like refs.
   */
  void checkAllRegistersForMissedReferences() {
    if (verbose) {
      VM.sysWrite(" Inspecting Regs: ");
      VM.sysWrite(FIRST_GCMAP_REG);
      VM.sysWrite(" ... ");
      VM.sysWrite(LAST_GCMAP_REG);
      VM.sysWrite("\n");
    }
    checkRegistersForMissedReferences(FIRST_GCMAP_REG, LAST_GCMAP_REG);
  }

  /**
   * This method inspects the registers from firstReg to lastReg (inclusive)
   * for values that look like pointers.
   * @param firstReg first reg to check
   * @param lastReg  last reg to check
   */
 void checkRegistersForMissedReferences(int firstReg, int lastReg) {
    for (int i = firstReg; i <= lastReg; i++) {
      int regLocation = registerLocations[i];
      if (VM_Allocator.inRange(i)) {
        VM.sysWrite("  reg#");
        VM.sysWrite(getCurrentRegister());
        VM.sysWrite(" contains a suspicious value ==>");
        VM.sysWrite(regLocation);
        VM.sysWrite("\n");
      }
    }
  }

  /**
   * This method inspects spill locations between the parameters passed
   * to determine if they look like heap points
   * If the first parameter is 0, it looks from the begining of the frame
   * until new.
   * @param old the last spill found as a reference
   * @param new the next spill found as a reference
   */
  void checkForMissedSpills(int ref1, int ref2) {
    if (ref1 == 0) {
      // Search from start of spill area
      ref1 = getFirstSpillLoc();
      if (debug) {
	VM.sysWrite("Updated, ref1: ");
	VM.sysWrite(ref1);
	VM.sysWrite("\n");
      }
    } 

    if (ref2 == 0) {
      // Search up to end of spill area
      ref2 = getLastSpillLoc();
      if (debug) {
	VM.sysWrite("Updated, ref2: ");
	VM.sysWrite(ref2);
	VM.sysWrite("\n");
      }
    }

    // since different archs will have the relative order of ref1, ref2
    // differently, we normalize them by ensuring that ref1 < ref2; 
    if (ref1 > ref2) {
      int tmp = ref1;
      ref1 =ref2;
      ref2 = ref1;
    }

    for (int i = ref1 + 4; i < ref2; i = i + 4) {
      int ptr = VM_Magic.getMemoryWord(i);
      if (debug) {
	VM.sysWrite(" Inspecting Spill: ");
	VM.sysWrite(i);
	VM.sysWrite(" with value ==>");
	VM.sysWrite(ptr);
	VM.sysWrite("\n");
      }

      if (VM_Allocator.inRange(ptr)) {
	VM.sysWrite("  spill location:");
	VM.sysWrite(i);
	VM.sysWrite(" contains a suspicious value ==>");
	VM.sysWrite(ptr);
	VM.sysWrite("\n");
	VM.sysWrite("FramePtr: ");
	VM.sysWrite(framePtr);
	VM.sysWrite("\tFirst Spill: ");
	VM.sysWrite(getFirstSpillLoc());
	VM.sysWrite("\tLast Spill: ");
	VM.sysWrite(getLastSpillLoc());
	VM.sysWrite("\n");
      }
    }
  }

  /**
   * The compiler info for this method
   */
  protected VM_OptCompilerInfo compilerInfo;

  /**
   *  The GC map for this method
   */
  private VM_OptMachineCodeMap map;

  /**
   *  Used to index into the GC map
   */
  private int mapIndex;

  /**
   * This shows which register to inspect and report on.
   * If it is bigger than LAST_GCMAP_REG than we should look at the spills
   */
  private int currentRegister;

  /**
   * This caches the spill location, so that we can check for missed refs
   * hiding in spills
   */
  private int spillLoc;

  /**
   * just used for debugging, all output statements use VM.syswrite
   */
  static final boolean debug = false;

  /**
   * just used for verbose debugging, all output statements use VM.syswrite
   */
  static final boolean verbose = false;

  /**
   * when set to true, all registers and spills will be inspected for
   * values that look like references.
   * 
   * THIS CAN BE COSTLY.  USE WITH CARE
   */
  static final boolean lookForMissedReferencesInRegs = false;
  static final boolean lookForMissedReferencesInSpills = false;
}
