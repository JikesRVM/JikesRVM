/*
 * (C) Copyright IBM Corp. 2001, 2004
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.memoryManagers.mmInterface.VM_GCMapIterator;
import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class contains its architecture-independent code for iteration
 * across the references represented by a frame built by the OPT compiler.
 *
 * @see VM_OptGCMapIterator 
 *
 * @author Michael Hind
 */
abstract class VM_OptGenericGCMapIterator extends VM_GCMapIterator 
  implements VM_OptGCMapIteratorConstants, VM_Constants,
             Uninterruptible {

  /**
   * The compiled method
   */
  protected VM_OptCompiledMethod compiledMethod;

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
  private Address spillLoc;

  /**
   * just used for debugging, all output statements use VM.syswrite
   */
  private static final boolean DEBUG = false;

  /**
   * just used for verbose debugging, all output statements use VM.syswrite
   */
  static final boolean VERBOSE = false;

  /**
   * when set to true, all registers and spills will be inspected for
   * values that look like references.
   * 
   * THIS CAN BE COSTLY.  USE WITH CARE
   */
  static final boolean lookForMissedReferencesInRegs = false;
  static final boolean lookForMissedReferencesInSpills = false;

  // Constructor 
  VM_OptGenericGCMapIterator(WordArray registerLocations) {
    super();
    this.registerLocations = registerLocations;
  }

  /**
   * Initialize the iterator for another stack frame scan
   * @param cm                The compiled method we are interested in
   * @param instructionOffset The place in the method where we currently are
   * @param framePtr          The current frame pointer
   */
  public final void setupIterator(VM_CompiledMethod cm, 
                                  int instructionOffset, 
                                  Address framePtr) {
    if (DEBUG) {
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
    compiledMethod = (VM_OptCompiledMethod)cm;
    map = compiledMethod.getMCMap();
    mapIndex = map.findGCMapIndex(instructionOffset);
    if (mapIndex == VM_OptGCMap.ERROR) {
      if (instructionOffset < 0) {
        VM.sysWriteln("VM_OptGenericGCMapIterator.setupIterator called with negative instructionOffset", instructionOffset);
      } else {
        int possibleLen = cm.getInstructions().length() << VM.LG_INSTRUCTION_WIDTH;
        if (possibleLen < instructionOffset) {
          VM.sysWriteln("VM_OptGenericGCMapIterator.setupIterator called with too big of an instructionOffset");
          VM.sysWriteln("offset is", instructionOffset, " bytes of machine code for method ",possibleLen);
        } else {
          VM.sysWriteln("VM_OptGenericGCMapIterator.setupIterator called with apparently valid offset, but no GC map found!");
          VM.sysWrite("Method: ");
          VM.sysWrite(compiledMethod.getMethod());
          VM.sysWrite(", Machine Code (MC) Offset: ");
          VM.sysWriteln(instructionOffset);
          VM.sysFail("VM_OptGenericMapIterator: findGCMapIndex failed\n");
        }
      }
      VM.sysWrite("Supposed method: ");
      VM.sysWrite(compiledMethod.getMethod());
      VM.sysWriteln("\nBase of its code array", VM_Magic.objectAsAddress(cm.getInstructions()));
      Address ra = VM_Magic.objectAsAddress(cm.getInstructions()).add(instructionOffset);
      VM.sysWriteln("Calculated actual return address is ", ra);
      VM_CompiledMethod realCM = VM_CompiledMethods.findMethodForInstruction(ra);
      if (realCM == null) {
        VM.sysWriteln("Unable to find compiled method corresponding to this return address");
      } else {
        VM.sysWrite("Found compiled method ");
        VM.sysWrite(realCM.getMethod());
        VM.sysWriteln(" whose code contains this return address");
      }
      VM.sysFail("VM_OptGenericMapIterator: setupIterator failed\n");
    }

    // save the frame pointer
    this.framePtr = framePtr;

    if (DEBUG) {
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
  public final Address getNextReferenceAddress() {
    if (DEBUG) {   VM.sysWrite("  next => ");    }

    // make sure we have a map entry to look at 
    if (mapIndex == VM_OptGCMap.NO_MAP_ENTRY) {
      if (DEBUG) {
        VM.sysWrite("  No Map, returning 0\n");
      }
      if (lookForMissedReferencesInRegs) {
        checkAllRegistersForMissedReferences();
      }

      // make sure we update the registerLocations before returning!
      updateLocateRegisters();
      return Address.zero();
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
        Address regLocation;
        // currentRegister contains a reference, return that location
        regLocation = registerLocations.get(getCurrentRegister()).toAddress();
        if (DEBUG) {
          VM.sysWrite(" *** Ref found in reg#");
          VM.sysWrite(getCurrentRegister());
          VM.sysWrite(", location ==>");
          VM.sysWrite(regLocation);
          VM.sysWrite(", contents ==>");
          VM.sysWrite(regLocation.loadWord());
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
      if (DEBUG) {
        VM.sysWrite("  No more to return, returning 0\n");
      }

      if (lookForMissedReferencesInSpills) {
        if (spillLoc.isZero()) {
          // Didn't have any refs in spill locations, so we should
          // check for spills among the whole spill area
          checkForMissedSpills(Address.zero(), Address.zero());
        } else {
          // check for spills after the last one we saw
          checkForMissedSpills(spillLoc, Address.zero());
        }
      }

      // OK, we are done returning references for this GC point/method/FP
      //   so now we must update the LocateRegister array for the next
      //   stack frame
      updateLocateRegisters();
      return Address.zero();
    } else {
      // Determine the spill location given the frame ptr and spill offset.
      // (The location of spills varies among architectures.)
      Address newSpillLoc = getStackLocation(framePtr, 
                                         map.gcMapInformation(mapIndex));

      if (DEBUG) {
        VM.sysWrite(" *** Ref found in Spill Loc: ");
        VM.sysWrite(newSpillLoc);
        VM.sysWrite(", offset: ");
        VM.sysWrite(map.gcMapInformation(mapIndex));
        VM.sysWrite(", value ==>");
        VM.sysWrite(newSpillLoc.loadWord());
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
  public final Address getNextReturnAddressAddress() {
    // Since the Opt compiler inlines JSRs, this method will always return 0
    //  signaling the end of the list of such pointers.
    if (DEBUG) {
      VM.sysWrite("\t\t getNextReturnAddressOffset returning 0\n");
    }
    return Address.zero();
  }

  /**
   * scan of this frame is complete
   * clean up any pointers to allow GC to reclaim dead objects
   */
  public final void cleanupPointers() {
    // primitive types aren't worth reinitialing because setUpIterator
    //   will take care of this.
    map = null;
    compiledMethod = null;
  }

  /**
   * lets GC ask what type of iterator without using instanceof which can
   * cause an allocation
   */
  public final int getType() {
    return VM_CompiledMethod.OPT;
  }

  /**
   * Externally visible method called to reset internal state
   */
  public final void reset() {
    currentRegister = FIRST_GCMAP_REG;
    spillLoc = Address.zero();
  }

  /**
   * return the current register we are processing
   * @return the current register we are processing
   */
  public final int getCurrentRegister() {
    return currentRegister;
  }

  /**
   * update the state of the current register we are processing
   */
  public final void updateCurrentRegister() {
    currentRegister++;
  }

  /**
   * Determines if the value of "currentRegister" is valid, or if we
   * processed all registers
   * @return whether the currentRegister is valid
   */
  public final boolean currentRegisterIsValid() {
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
  abstract Address getStackLocation(Address framePtr, int offset);

  /** 
   *  Get address of the first spill location
   *  (The location of spills varies among architectures.)
   *  @return the first spill location
   */
  abstract Address getFirstSpillLoc();

  /** 
   *  Get address of the last spill location
   *  (The location of spills varies among architectures.)
   *  @param the frame pointer
   *  @return the last spill location
   */
  abstract Address getLastSpillLoc();

  /**
   * This method inspects the "current" register for values that look like refs.
   */
  final void checkCurrentRegisterForMissedReferences() {
    int currentReg = getCurrentRegister();
    if (VERBOSE) {
      VM.sysWrite(" Inspecting Regs: ");
      VM.sysWrite(currentReg);
      VM.sysWrite("\n");
    }
    checkRegistersForMissedReferences(currentReg, currentReg);
  }

  /**
   * This method inspects all the registers for values that look like refs.
   */
  final void checkAllRegistersForMissedReferences() {
    if (VERBOSE) {
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
  final void checkRegistersForMissedReferences(int firstReg, int lastReg) {
    for (int i = firstReg; i <= lastReg; i++) {
      Address regLocation = registerLocations.get(i).toAddress();
      Address regValue = regLocation.loadAddress();
      if (MM_Interface.addressInVM(regValue)) {
        VM.sysWrite("  reg#", getCurrentRegister());
        VM.sysWrite(", location ==>", regLocation);
        VM.sysWriteln(", suspicious value ==>", regValue);
      }
    }
  }

  /**
   * This method inspects spill locations between the parameters passed
   * to determine if they look like heap points
   * If the first parameter is 0, it looks from the begining of the frame
   * until new.
   * @param ref1 the last spill found as a reference
   * @param ref2 the next spill found as a reference
   */
  final void checkForMissedSpills(Address ref1, Address ref2) {
    if (ref1.isZero()) {
      // Search from start of spill area
      ref1 = getFirstSpillLoc();
      if (DEBUG) {
        VM.sysWrite("Updated, ref1: ");
        VM.sysWrite(ref1);
        VM.sysWrite("\n");
      }
    } 

    if (ref2.isZero()) {
      // Search up to end of spill area
      ref2 = getLastSpillLoc();
      if (DEBUG) {
        VM.sysWrite("Updated, ref2: ");
        VM.sysWrite(ref2);
        VM.sysWrite("\n");
      }
    }

    // since different archs will have the relative order of ref1, ref2
    // differently, we normalize them by ensuring that ref1 < ref2; 
    if (ref1.GT(ref2)) {
      Address tmp = ref1;
      ref1 =ref2;
      ref2 = ref1;
    }

    for (Address i = ref1.add(BYTES_IN_ADDRESS); i.LT(ref2); i = i.add(BYTES_IN_ADDRESS)) {
      Address ptr = i.loadAddress();
      if (DEBUG) {
        VM.sysWrite(" Inspecting Spill: ");
        VM.sysWrite(i);
        VM.sysWrite(" with value ==>");
        VM.sysWrite(ptr);
        VM.sysWrite("\n");
      }

      if (MM_Interface.addressInVM(ptr)) {
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
}

