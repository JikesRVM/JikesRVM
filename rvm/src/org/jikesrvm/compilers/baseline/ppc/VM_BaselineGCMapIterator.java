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
package org.jikesrvm.compilers.baseline.ppc;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_MethodReference;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.baseline.VM_BaselineCompiledMethod;
import org.jikesrvm.compilers.baseline.VM_ReferenceMaps;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethods;
import org.jikesrvm.memorymanagers.mminterface.VM_GCMapIterator;
import org.jikesrvm.ppc.VM_BaselineConstants;
import org.jikesrvm.runtime.VM_DynamicLink;
import org.jikesrvm.runtime.VM_Magic;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.WordArray;

/**
 * Iterator for stack frame  built by the Baseline compiler
 * An Instance of this class will iterate through a particular
 * reference map of a method returning the offsets of any refereces
 * that are part of the input parameters, local variables, and
 * java stack for the stack frame.
 */
@Uninterruptible
public abstract class VM_BaselineGCMapIterator extends VM_GCMapIterator implements VM_BaselineConstants {

  // Iterator state for mapping any stackframe.
  //
  private int mapIndex; // current offset in current map
  private int mapId;     // id of current map out of all maps
  private VM_ReferenceMaps maps;      // set of maps for this method

  // Additional iterator state for mapping dynamic bridge stackframes.
  //
  private VM_DynamicLink dynamicLink;                    // place to keep info returned by VM_CompiledMethod.getDynamicLink
  private VM_MethodReference bridgeTarget;               // method to be invoked via dynamic bridge (null: current frame is not a dynamic bridge)
  private VM_NormalMethod currentMethod;                  // method for the frame
  private VM_BaselineCompiledMethod currentCompiledMethod;                  // compiled method for the frame
  private int currentNumLocals;
  private VM_TypeReference[] bridgeParameterTypes;           // parameter types passed by that method
  private boolean bridgeParameterMappingRequired; // have all bridge parameters been mapped yet?
  private boolean bridgeRegistersLocationUpdated; // have the register location been updated
  private boolean finishedWithRegularMap;         // have we processed all the values in the regular map yet?
  private int bridgeParameterInitialIndex;    // first parameter to be mapped (-1 == "this")
  private int bridgeParameterIndex;           // current parameter being mapped (-1 == "this")
  private int bridgeRegisterIndex;            // gpr register it lives in
  private Address bridgeRegisterLocation;         // memory address at which that register was saved
  private Address bridgeSpilledParamLocation;     // current spilled param location

  //
  // Remember the location array for registers. This array needs to be updated
  // with the location of any saved registers.
  // This information is not used by this iterator but must be updated for the
  // other types of iterators (ones for the opt compiler built frames)
  // The locations are kept as addresses within the stack.
  //
  public VM_BaselineGCMapIterator(WordArray registerLocations) {
    this.registerLocations = registerLocations; // (in superclass)
    dynamicLink = new VM_DynamicLink();
  }

  //
  // Set the iterator to scan the map at the machine instruction offset provided.
  // The iterator is positioned to the beginning of the map
  //
  //   method - identifies the method and class
  //   instruction offset - identifies the map to be scanned.
  //   fp  - identifies a specific occurrance of this method and
  //         allows for processing instance specific information
  //         i.e JSR return address values
  //
  //  NOTE: An iterator may be reused to scan a different method and map.
  //
  public void setupIterator(VM_CompiledMethod compiledMethod, Offset instructionOffset, Address fp) {
    currentCompiledMethod = (VM_BaselineCompiledMethod) compiledMethod;
    currentMethod = (VM_NormalMethod) compiledMethod.getMethod();
    currentNumLocals = currentMethod.getLocalWords();
    // setup superclass
    //
    framePtr = fp;

    // setup stackframe mapping
    //
    maps = currentCompiledMethod.referenceMaps;
    mapId = maps.locateGCPoint(instructionOffset, currentMethod);
    mapIndex = 0;
    if (mapId < 0) {
      // lock the jsr lock to serialize jsr processing
      VM_ReferenceMaps.jsrLock.lock("jsr lock");
      int JSRindex = maps.setupJSRSubroutineMap(mapId);
      while (JSRindex != 0) {
        Address nextCallerAddress;
        int location = convertIndexToLocation(JSRindex);
        if (VM_BaselineCompilerImpl.isRegister(location)) {
          nextCallerAddress = registerLocations.get(location).toAddress();
        } else {
          nextCallerAddress =
              framePtr.plus(VM_BaselineCompilerImpl.locationToOffset(location) -
                            BYTES_IN_ADDRESS); //location offsets are positioned on top of stackslot
        }
        nextCallerAddress = nextCallerAddress.loadAddress();
        Offset nextMachineCodeOffset = compiledMethod.getInstructionOffset(nextCallerAddress);
        if (VM.TraceStkMaps) {
          VM.sysWriteln("     setupJSRsubroutineMap- nested jsrs end of loop- = ");
          VM.sysWriteln("      next jsraddress offset = ", JSRindex);
          VM.sysWriteln("      next callers address = ", nextCallerAddress);
          VM.sysWriteln("      next machinecodeoffset = ", nextMachineCodeOffset);
          if (nextMachineCodeOffset.sLT(Offset.zero())) {
            VM.sysWriteln("BAD MACHINE CODE OFFSET");
          }
        }
        JSRindex = maps.getNextJSRAddressIndex(nextMachineCodeOffset, currentMethod);
      }
    }
    if (VM.TraceStkMaps) {
      VM.sysWrite("VM_BaselineGCMapIterator setupIterator mapId = ");
      VM.sysWrite(mapId);
      VM.sysWrite(" for ");
      VM.sysWrite(currentMethod);
      VM.sysWrite(".\n");
    }

    // setup dynamic bridge mapping
    //
    bridgeTarget = null;
    bridgeParameterTypes = null;
    bridgeParameterMappingRequired = false;
    bridgeRegistersLocationUpdated = false;
    bridgeParameterIndex = 0;
    bridgeRegisterIndex = 0;
    bridgeRegisterLocation = Address.zero();
    bridgeSpilledParamLocation = Address.zero();

    if (currentMethod.getDeclaringClass().hasDynamicBridgeAnnotation()) {
      fp = VM_Magic.getCallerFramePointer(fp);
      Address ip = VM_Magic.getNextInstructionAddress(fp);
      int callingCompiledMethodId = VM_Magic.getCompiledMethodID(fp);
      VM_CompiledMethod callingCompiledMethod = VM_CompiledMethods.getCompiledMethod(callingCompiledMethodId);
      Offset callingInstructionOffset = callingCompiledMethod.getInstructionOffset(ip);

      callingCompiledMethod.getDynamicLink(dynamicLink, callingInstructionOffset);
      bridgeTarget = dynamicLink.methodRef();
      bridgeParameterInitialIndex = dynamicLink.isInvokedWithImplicitThisParameter() ? -1 : 0;
      bridgeParameterTypes = bridgeTarget.getParameterTypes();
    }

    reset();
  }

  // Reset iteration to initial state.
  // This allows a map to be scanned multiple times
  //
  public void reset() {

    mapIndex = 0;
    finishedWithRegularMap = false;

    if (bridgeTarget != null) {
      // point to first saved gpr
      bridgeParameterMappingRequired = true;
      bridgeParameterIndex = bridgeParameterInitialIndex;
      bridgeRegisterIndex = FIRST_VOLATILE_GPR;
      bridgeRegisterLocation = framePtr.loadAddress();
      bridgeRegisterLocation =
          bridgeRegisterLocation.minus(BYTES_IN_DOUBLE * (LAST_NONVOLATILE_FPR - FIRST_VOLATILE_FPR + 1) +
                                       BYTES_IN_ADDRESS * (LAST_NONVOLATILE_GPR - FIRST_VOLATILE_GPR + 1));

      // get to my caller's frameptr and then walk up to the spill area
      Address callersFP = VM_Magic.getCallerFramePointer(framePtr);
      bridgeSpilledParamLocation = callersFP.plus(STACKFRAME_HEADER_SIZE);
    }
  }

  /**
   * given a index in the local area (biased : local0 has index 1)
   *   this routine determines the correspondig offset in the stack
   */
  public int convertIndexToLocation(int index) {
    if (index == 0) return 0;

    if (index <= currentNumLocals) { //index is biased by 1;
      return currentCompiledMethod.getGeneralLocalLocation(index -
                                                           1); //register (positive value) or stacklocation (negative value)
    } else {
      return currentCompiledMethod.getGeneralStackLocation(index - 1 - currentNumLocals);
      //(VM_BaselineCompilerImpl.offsetToLocation(maps.convertIndexToOffset(index) + BYTES_IN_STACKSLOT)); //locations must point to the top of the slot
    }
  }

  // Get location of next reference.
  // A zero return indicates that no more references exist.
  //
  public Address getNextReferenceAddress() {

    if (!finishedWithRegularMap) {
      if (mapId < 0) {
        mapIndex = maps.getNextJSRRefIndex(mapIndex);
      } else {
        mapIndex = maps.getNextRefIndex(mapIndex, mapId);
      }

      if (VM.TraceStkMaps) {
        VM.sysWrite("VM_BaselineGCMapIterator getNextReferenceIndex = ");
        VM.sysWrite(mapIndex);
        VM.sysWrite(".\n");
        if (mapId < 0) {
          VM.sysWrite("Index is a JSR return address ie internal pointer.\n");
        }
      }

      if (mapIndex != 0) {
        int location = convertIndexToLocation(mapIndex);
        if (VM.TraceStkMaps) {
          VM.sysWrite("VM_BaselineGCMapIterator getNextReference location = ");
          VM.sysWrite(location);
          VM.sysWriteln();
        }

        Address nextCallerAddress;
        if (VM_BaselineCompilerImpl.isRegister(location)) {
          nextCallerAddress = registerLocations.get(location).toAddress();
        } else {
          nextCallerAddress =
              framePtr.plus(VM_BaselineCompilerImpl.locationToOffset(location) -
                            BYTES_IN_ADDRESS); //location offsets are positioned on top of stackslot
        }
        nextCallerAddress = nextCallerAddress.loadAddress();
        if (VM_BaselineCompilerImpl.isRegister(location)) {
          return registerLocations.get(location).toAddress();
        } else {
          return framePtr.plus(VM_BaselineCompilerImpl.locationToOffset(location) -
                               BYTES_IN_ADDRESS); //location offsets are positioned on top of stackslot
        }
      } else {
        // remember that we are done with the map for future calls, and then
        //   drop down to the code below
        finishedWithRegularMap = true;
      }
    }

    if (bridgeParameterMappingRequired) {
      if (!bridgeRegistersLocationUpdated) {
        // point registerLocations[] to our callers stackframe
        //
        Address location = framePtr.plus(VM_BaselineCompilerImpl.getFrameSize(currentCompiledMethod));
        location = location.minus((LAST_NONVOLATILE_FPR - FIRST_VOLATILE_FPR + 1) * BYTES_IN_DOUBLE);
        // skip non-volatile and volatile fprs
        for (int i = LAST_NONVOLATILE_GPR; i >= FIRST_VOLATILE_GPR; --i) {
          location = location.minus(BYTES_IN_ADDRESS);
          registerLocations.set(i, location.toWord());
        }

        bridgeRegistersLocationUpdated = true;
      }

      // handle implicit "this" parameter, if any
      //
      if (bridgeParameterIndex == -1) {
        bridgeParameterIndex += 1;
        bridgeRegisterIndex += 1;
        bridgeRegisterLocation = bridgeRegisterLocation.plus(BYTES_IN_ADDRESS);

        if (VM.TraceStkMaps) {
          VM.sysWrite("VM_BaselineGCMapIterator getNextReferenceOffset, ");
          VM.sysWrite("  this, bridge, returning: ");
          VM.sysWrite(bridgeRegisterLocation.minus(BYTES_IN_ADDRESS));
          VM.sysWrite("\n");
        }
        return bridgeRegisterLocation.minus(BYTES_IN_ADDRESS);
      }

      // now the remaining parameters
      //
      while (bridgeParameterIndex < bridgeParameterTypes.length) {
        VM_TypeReference bridgeParameterType = bridgeParameterTypes[bridgeParameterIndex++];

        // are we still processing the regs?
        if (bridgeRegisterIndex <= LAST_VOLATILE_GPR) {

          // update the bridgeRegisterLocation (based on type) and return a value if it is a ref
          if (bridgeParameterType.isReferenceType()) {
            bridgeRegisterLocation = bridgeRegisterLocation.plus(BYTES_IN_ADDRESS);
            bridgeRegisterIndex += 1;

            if (VM.TraceStkMaps) {
              VM.sysWrite("VM_BaselineGCMapIterator getNextReferenceOffset, ");
              VM.sysWrite("  parm: ");
              VM.sysWrite(bridgeRegisterLocation.minus(BYTES_IN_ADDRESS));
              VM.sysWrite("\n");
            }
            return bridgeRegisterLocation.minus(BYTES_IN_ADDRESS);
          } else if (bridgeParameterType.isLongType()) {
            bridgeRegisterIndex += VM.BuildFor64Addr ? 1 : 2;
            bridgeRegisterLocation = bridgeRegisterLocation.plus(BYTES_IN_LONG);
          } else if (bridgeParameterType.isDoubleType() || bridgeParameterType.isFloatType()) {
            // nothing to do, these are not stored in gprs
          } else {
            // boolean, byte, char, short, int
            bridgeRegisterIndex += 1;
            bridgeRegisterLocation = bridgeRegisterLocation.plus(BYTES_IN_ADDRESS);
          }
        } else {  // now process the register spill area for the remain params
          // no need to update BridgeRegisterIndex anymore, it isn't used and is already
          //  big enough (> LAST_VOLATILE_GPR) to ensure we'll live in this else code in the future
          if (bridgeParameterType.isReferenceType()) {
            bridgeSpilledParamLocation = bridgeSpilledParamLocation.plus(BYTES_IN_ADDRESS);

            if (VM.TraceStkMaps) {
              VM.sysWrite("VM_BaselineGCMapIterator getNextReferenceOffset, dynamic link spilled parameter, returning: ");
              VM.sysWrite(bridgeSpilledParamLocation.minus(BYTES_IN_ADDRESS));
              VM.sysWrite(".\n");
            }
            return bridgeSpilledParamLocation.minus(BYTES_IN_ADDRESS);
          } else if (bridgeParameterType.isLongType()) {
            bridgeSpilledParamLocation = bridgeSpilledParamLocation.plus(BYTES_IN_LONG);
          } else if (bridgeParameterType.isDoubleType()) {
            bridgeSpilledParamLocation = bridgeSpilledParamLocation.plus(BYTES_IN_DOUBLE);
          } else if (bridgeParameterType.isFloatType()) {
            bridgeSpilledParamLocation = bridgeSpilledParamLocation.plus(BYTES_IN_FLOAT);
          } else {
            // boolean, byte, char, short, int
            bridgeSpilledParamLocation = bridgeSpilledParamLocation.plus(BYTES_IN_ADDRESS);
          }
        }
      }
    }
    return Address.zero();
  }

  //
  // Gets the location of the next return address
  // after the current position.
  //  a zero return indicates that no more references exist
  //
  public Address getNextReturnAddressAddress() {

    if (mapId >= 0) {
      if (VM.TraceStkMaps) {
        VM.sysWrite("VM_BaselineGCMapIterator getNextReturnAddressOffset mapId = ");
        VM.sysWrite(mapId);
        VM.sysWrite(".\n");
      }
      return Address.zero();
    }
    mapIndex = maps.getNextJSRReturnAddrIndex(mapIndex);
    if (VM.TraceStkMaps) {
      VM.sysWrite("VM_BaselineGCMapIterator getNextReturnAddressIndex = ");
      VM.sysWrite(mapIndex);
      VM.sysWrite(".\n");
    }

    if (mapIndex == 0) return Address.zero();

    int location = convertIndexToLocation(mapIndex);
    if (VM.TraceStkMaps) {
      VM.sysWrite("VM_BaselineGCMapIterator getNextReturnAddress location = ");
      VM.sysWrite(location);
      VM.sysWrite(".\n");
    }
    Address nextCallerAddress;
    if (VM_BaselineCompilerImpl.isRegister(location)) {
      nextCallerAddress = registerLocations.get(location).toAddress();
    } else {
      nextCallerAddress =
          framePtr.plus(VM_BaselineCompilerImpl.locationToOffset(location) -
                        BYTES_IN_ADDRESS); //location offsets are positioned on top of stackslot
    }
    nextCallerAddress = nextCallerAddress.loadAddress();
    if (VM_BaselineCompilerImpl.isRegister(location)) {
      return registerLocations.get(location).toAddress();
    } else {
      return framePtr.plus(VM_BaselineCompilerImpl.locationToOffset(location) -
                           BYTES_IN_ADDRESS); //location offsets are positioned on top of stackslot
    }
  }

  // cleanup pointers - used with method maps to release data structures
  //    early ... they may be in temporary storage ie storage only used
  //    during garbage collection
  //
  public void cleanupPointers() {
    // Make sure that the registerLocation array is updated with the
    // locations where this method cached its callers registers.
    // [[Yuck. I hate having to do this here, but it seems to be the
    // only safe way to do this...]]
    updateCallerRegisterLocations();

    maps.cleanupPointers();
    maps = null;
    if (mapId < 0) {
      VM_ReferenceMaps.jsrLock.unlock();
    }
    bridgeTarget = null;
    bridgeParameterTypes = null;
  }

  public int getType() {
    return VM_CompiledMethod.BASELINE;
  }

  private void updateCallerRegisterLocations() {
    //dynamic bridge's registers already restored by calls to getNextReferenceAddress()
    if (!currentMethod.getDeclaringClass().hasDynamicBridgeAnnotation()) {
      if (VM.TraceStkMaps) VM.sysWriteln("    Update Caller RegisterLocations");
      Address addr = framePtr.plus(VM_BaselineCompilerImpl.getFrameSize(currentCompiledMethod));
      addr =
          addr.minus((currentCompiledMethod.getLastFloatStackRegister() - FIRST_FLOAT_LOCAL_REGISTER + 1) <<
                     LOG_BYTES_IN_DOUBLE); //skip float registers

      for (int i = currentCompiledMethod.getLastFixedStackRegister(); i >= FIRST_FIXED_LOCAL_REGISTER; --i) {
        addr = addr.minus(BYTES_IN_ADDRESS);
        registerLocations.set(i, addr.toWord());
      }
    }
  }

  // For debugging (used with checkRefMap)
  //
  public int getStackDepth() {
    return maps.getStackDepth(mapId);
  }

}
