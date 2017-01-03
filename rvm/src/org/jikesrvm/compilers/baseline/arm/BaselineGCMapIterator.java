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
package org.jikesrvm.compilers.baseline.arm;

import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_DOUBLE;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_FLOAT;
import static org.jikesrvm.arm.RegisterConstants.FIRST_VOLATILE_GPR;
import static org.jikesrvm.arm.RegisterConstants.FIRST_VOLATILE_FPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_VOLATILE_GPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_VOLATILE_FPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_VOLATILE_DPR;
import static org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_HEADER_SIZE;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.baseline.AbstractBaselineGCMapIterator;
import org.jikesrvm.compilers.baseline.ReferenceMaps;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.AddressArray;

/**
 * Iterator for stack frame built by the Baseline compiler.<p>
 *
 * An Instance of this class will iterate through a particular
 * reference map of a method returning the offsets of any references
 * that are part of the input parameters, local variables, and
 * java stack for the stack frame.
 */
@Uninterruptible
public final class BaselineGCMapIterator extends AbstractBaselineGCMapIterator {

  /** Compiled method for the frame */
  private ArchBaselineCompiledMethod currentCompiledMethod;

  private int nextBridgeGPR;
  private int nextBridgeFPR;
  private Address nextBridgeSpillSlot;

  //
  // Remember the location array for registers. This array needs to be updated
  // with the location of any saved registers.
  // This information is not used by this iterator but must be updated for the
  // other types of iterators (ones for the opt compiler built frames)
  // The locations are kept as addresses within the stack.
  //
  public BaselineGCMapIterator(AddressArray registerLocations) {
    super(registerLocations);
  }

  /**
   * Set the iterator to scan the map at the machine instruction offset
   * provided. The iterator is positioned to the beginning of the map. NOTE: An
   * iterator may be reused to scan a different method and map.
   *
   * @param compiledMethod
   *          identifies the method and class
   * @param instructionOffset
   *          identifies the map to be scanned.
   * @param fp
   *          identifies a specific occurrance of this method and allows for
   *          processing instance specific information i.e JSR return address
   *          values
   */
  @Override
  public void setupIterator(CompiledMethod compiledMethod, Offset instructionOffset, Address fp) {
    currentCompiledMethod = (ArchBaselineCompiledMethod) compiledMethod;
    currentMethod = (NormalMethod) compiledMethod.getMethod();
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
      ReferenceMaps.jsrLock.lock();
      int JSRindex = maps.setupJSRSubroutineMap(mapId);
      while (JSRindex != 0) {
        Address nextCallerAddress = getAddressOfIndex(JSRindex).loadAddress();
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
      VM.sysWrite("BaselineGCMapIterator setupIterator mapId = ");
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

    if (currentMethod.getDeclaringClass().hasDynamicBridgeAnnotation()) {
      Address ip = Magic.getNextInstructionAddress(fp);
      fp = Magic.getCallerFramePointer(fp);
      int callingCompiledMethodId = Magic.getCompiledMethodID(fp);
      CompiledMethod callingCompiledMethod = CompiledMethods.getCompiledMethod(callingCompiledMethodId);
      Offset callingInstructionOffset = callingCompiledMethod.getInstructionOffset(ip);

      callingCompiledMethod.getDynamicLink(dynamicLink, callingInstructionOffset);
      bridgeTarget = dynamicLink.methodRef();
      bridgeParameterInitialIndex = dynamicLink.isInvokedWithImplicitThisParameter() ? -1 : 0;
      bridgeParameterTypes = bridgeTarget.getParameterTypes();
    }

    reset();
  }

  /**
   * Reset iteration to initial state.
   * This allows a map to be scanned multiple times
   */
  @Override
  protected void resetArchitectureSpecificBridgeSState() {
    nextBridgeGPR = FIRST_VOLATILE_GPR.value();
    nextBridgeFPR = FIRST_VOLATILE_FPR.value();
    nextBridgeSpillSlot = Magic.getCallerFramePointer(framePtr).plus(STACKFRAME_HEADER_SIZE);
  }

  /**
   * given a index in the local area (biased : local0 has index 1), or a value outside this range giving a location on the operand stack
   *   this routine returns the address at which that local resides, or the address of that value on the stack
   */
  @Uninterruptible
  private Address getAddressOfIndex(int index) {
    if (index <= currentNumLocals)
      return currentCompiledMethod.getLocalAddressForGCMap(index - 1, registerLocations, framePtr);
    else
      return currentCompiledMethod.getStackAddressForGCMap(index - 1 - currentNumLocals, framePtr);
  }

  @Override
  public Address getNextReferenceAddress() {
    if (!finishedWithRegularMap) {
      if (mapId < 0) {
        mapIndex = maps.getNextJSRRefIndex(mapIndex);
      } else {
        mapIndex = maps.getNextRefIndex(mapIndex, mapId);
      }

      if (VM.TraceStkMaps) {
        VM.sysWrite("BaselineGCMapIterator getNextReferenceIndex = ");
        VM.sysWrite(mapIndex);
        VM.sysWrite(".\n");
        if (mapId < 0) {
          VM.sysWrite("Index is a JSR return address ie internal pointer.\n");
        }
      }

      if (mapIndex != 0) {
        return getAddressOfIndex(mapIndex);
        /*if (VM.TraceStkMaps) {
          VM.sysWrite("BaselineGCMapIterator getNextReference location = ");
          VM.sysWrite(location);
          VM.sysWriteln();
        }*/
      } else {
        // remember that we are done with the map for future calls, and then
        //   drop down to the code below
        finishedWithRegularMap = true;
      }
    }

    if (bridgeParameterMappingRequired) {
      if (!bridgeRegistersLocationUpdated) {
        currentCompiledMethod.recordSavedBridgeRegisterLocations(registerLocations, framePtr);
        bridgeRegistersLocationUpdated = true;
      }

      // handle implicit "this" parameter, if any
      //
      if (bridgeParameterIndex == -1) {
        Address addr = registerLocations.get(nextBridgeGPR);
        bridgeParameterIndex++;
        nextBridgeGPR++;

        if (VM.TraceStkMaps) {
          VM.sysWrite("BaselineGCMapIterator getNextReferenceOffset, ");
          VM.sysWrite("  this, bridge, returning: ");
          VM.sysWrite(addr);
          VM.sysWrite("\n");
        }
        return addr;
      }

      // now the remaining parameters
      //
      while (bridgeParameterIndex < bridgeParameterTypes.length) {
        TypeReference bridgeParameterType = bridgeParameterTypes[bridgeParameterIndex];
        bridgeParameterIndex++;

        if (bridgeParameterType.isReferenceType()) {
          if (nextBridgeGPR <= LAST_VOLATILE_GPR.value()) {
            Address addr = registerLocations.get(nextBridgeGPR);
            nextBridgeGPR++;

            if (VM.TraceStkMaps) {
              VM.sysWrite("BaselineGCMapIterator getNextReferenceOffset, ");
              VM.sysWrite("  parm: ");
              VM.sysWrite(addr);
              VM.sysWrite("\n");
            }
            return addr;
          } else {
            Address addr = nextBridgeSpillSlot;
            nextBridgeSpillSlot = nextBridgeSpillSlot.plus(BYTES_IN_ADDRESS);

            if (VM.TraceStkMaps) {
              VM.sysWrite("BaselineGCMapIterator getNextReferenceOffset, dynamic link spilled parameter, returning: ");
              VM.sysWrite(addr);
              VM.sysWrite(".\n");
            }
            return addr;
          }
        } else if (bridgeParameterType.isLongType() || bridgeParameterType.isDoubleType()) {
          if ((nextBridgeFPR & 1) == 1) nextBridgeFPR++; // 64-bit align
          if (nextBridgeFPR <= LAST_VOLATILE_DPR.value())
            nextBridgeFPR += 2;
          else
            nextBridgeSpillSlot = nextBridgeSpillSlot.plus(BYTES_IN_DOUBLE);
        } else if (bridgeParameterType.isFloatType()) {
          if (nextBridgeFPR <= LAST_VOLATILE_FPR.value())
            nextBridgeFPR++;
          else
            nextBridgeSpillSlot = nextBridgeSpillSlot.plus(BYTES_IN_FLOAT);
        } else {
          // boolean, byte, char, short, int
          if (nextBridgeGPR <= LAST_VOLATILE_GPR.value())
            nextBridgeGPR++;
          else
            nextBridgeSpillSlot = nextBridgeSpillSlot.plus(BYTES_IN_ADDRESS);
        }
      }
    }
    return Address.zero();
  }

  @Override
  public Address getNextReturnAddressAddress() {
    if (mapId >= 0) {
      if (VM.TraceStkMaps) {
        VM.sysWrite("BaselineGCMapIterator getNextReturnAddressOffset mapId = ");
        VM.sysWrite(mapId);
        VM.sysWrite(".\n");
      }
      return Address.zero();
    }
    mapIndex = maps.getNextJSRReturnAddrIndex(mapIndex);
    if (VM.TraceStkMaps) {
      VM.sysWrite("BaselineGCMapIterator getNextReturnAddressIndex = ");
      VM.sysWrite(mapIndex);
      VM.sysWrite(".\n");
    }

    if (mapIndex == 0) return Address.zero();

    return getAddressOfIndex(mapIndex);
  }

  @Override
  public void cleanupPointers() {
    // Make sure that the registerLocation array is updated with the
    // locations where this method cached its callers registers.
    // [[Yuck. I hate having to do this here, but it seems to be the
    // only safe way to do this...]]
    updateCallerRegisterLocations();

    super.cleanupPointers();
  }

  private void updateCallerRegisterLocations() {
    //dynamic bridge's registers already restored by calls to getNextReferenceAddress()
    if (!currentMethod.getDeclaringClass().hasDynamicBridgeAnnotation()) {
      if (VM.TraceStkMaps) VM.sysWriteln("    Update Caller RegisterLocations");
      currentCompiledMethod.recordSavedRegisterLocations(registerLocations, framePtr);
    }
  }
}
