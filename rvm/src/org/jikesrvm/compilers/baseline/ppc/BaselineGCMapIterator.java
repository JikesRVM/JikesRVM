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
package org.jikesrvm.compilers.baseline.ppc;

import static org.jikesrvm.ppc.BaselineConstants.FIRST_FIXED_LOCAL_REGISTER;
import static org.jikesrvm.ppc.BaselineConstants.FIRST_FLOAT_LOCAL_REGISTER;
import static org.jikesrvm.ppc.RegisterConstants.FIRST_VOLATILE_FPR;
import static org.jikesrvm.ppc.RegisterConstants.FIRST_VOLATILE_GPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_NONVOLATILE_FPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_NONVOLATILE_GPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_VOLATILE_GPR;
import static org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_HEADER_SIZE;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_DOUBLE;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_FLOAT;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_LONG;
import static org.jikesrvm.runtime.JavaSizeConstants.LOG_BYTES_IN_DOUBLE;
import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.baseline.AbstractBaselineGCMapIterator;
import org.jikesrvm.compilers.baseline.ReferenceMaps;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.ppc.RegisterConstants.GPR;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;
import org.vmmagic.unboxed.Offset;

/**
 * Iterator for stack frame  built by the Baseline compiler.<p>
 *
 * An Instance of this class will iterate through a particular
 * reference map of a method returning the offsets of any references
 * that are part of the input parameters, local variables, and
 * java stack for the stack frame.
 */
@Uninterruptible
public final class BaselineGCMapIterator extends AbstractBaselineGCMapIterator {

  /** Compiled method for the frame */
  protected ArchBaselineCompiledMethod currentCompiledMethod;

  /**  gpr register it lives in */
  private GPR bridgeRegisterIndex;

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
        Address nextCallerAddress;
        short location = convertIndexToLocation(JSRindex);
        if (BaselineCompilerImpl.isRegister(location)) {
          nextCallerAddress = registerLocations.get(location);
        } else {
          nextCallerAddress =
              framePtr.plus(BaselineCompilerImpl.locationToOffset(location) -
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
    if (VM.TraceStkMaps || TRACE_ALL) {
      VM.sysWrite("BaselineGCMapIterator setupIterator mapId = ");
      VM.sysWrite(mapId);
      VM.sysWrite(" for ");
      VM.sysWrite(currentMethod);
      VM.sysWriteln(".");
    }

    // setup dynamic bridge mapping
    //
    bridgeTarget = null;
    bridgeParameterTypes = null;
    bridgeParameterMappingRequired = false;
    bridgeRegistersLocationUpdated = false;
    bridgeParameterIndex = 0;
    bridgeRegisterIndex = GPR.R0;
    bridgeRegisterLocation = Address.zero();
    bridgeSpilledParamLocation = Address.zero();

    if (currentMethod.getDeclaringClass().hasDynamicBridgeAnnotation()) {
      fp = Magic.getCallerFramePointer(fp);
      Address ip = Magic.getNextInstructionAddress(fp);
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

  @Override
  protected void resetArchitectureSpecificBridgeSState() {
    bridgeRegisterIndex = FIRST_VOLATILE_GPR;
    bridgeRegisterLocation = framePtr.loadAddress();
    // point to first saved gpr
    bridgeRegisterLocation =
        bridgeRegisterLocation.minus(BYTES_IN_DOUBLE * (LAST_NONVOLATILE_FPR.value() - FIRST_VOLATILE_FPR.value() + 1) +
                                     BYTES_IN_ADDRESS * (LAST_NONVOLATILE_GPR.value() - FIRST_VOLATILE_GPR.value() + 1));

    // get to my caller's frameptr and then walk up to the spill area
    Address callersFP = Magic.getCallerFramePointer(framePtr);
    bridgeSpilledParamLocation = callersFP.plus(STACKFRAME_HEADER_SIZE);
  }

  /**
   * given a index in the local area (biased : local0 has index 1)
   *   this routine determines the correspondig offset in the stack
   */
  public short convertIndexToLocation(int index) {
    if (index == 0) return 0;

    if (index <= currentNumLocals) { //index is biased by 1;
      //register (positive value) or stacklocation (negative value)
      return currentCompiledMethod.getGeneralLocalLocation(index - 1);
    } else {
      //locations must point to the top of the slot
      return currentCompiledMethod.getGeneralStackLocation(index - 1 - currentNumLocals);
    }
  }

  @Override
  public Address getNextReferenceAddress() {

    if (!finishedWithRegularMap) {
      if (mapId < 0) {
        mapIndex = maps.getNextJSRRefIndex(mapIndex);
      } else {
        mapIndex = maps.getNextRefIndex(mapIndex, mapId);
      }

      if (VM.TraceStkMaps || TRACE_ALL) {
        VM.sysWrite("BaselineGCMapIterator getNextReferenceIndex = ");
        VM.sysWrite(mapIndex);
        VM.sysWriteln(".");
        if (mapId < 0) {
          VM.sysWriteln("Index is a JSR return address ie internal pointer.");
        }
      }

      if (mapIndex != 0) {
        short location = convertIndexToLocation(mapIndex);
        if (VM.TraceStkMaps || TRACE_ALL) {
          VM.sysWrite("BaselineGCMapIterator getNextReference location = ");
          VM.sysWrite(location);
          VM.sysWriteln();
        }

        if (BaselineCompilerImpl.isRegister(location)) {
          return registerLocations.get(location);
        } else {
          return framePtr.plus(BaselineCompilerImpl.locationToOffset(location) -
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
        Address location = framePtr.plus(currentCompiledMethod.getFrameSize());
        location = location.minus((LAST_NONVOLATILE_FPR.value() - FIRST_VOLATILE_FPR.value() + 1) * BYTES_IN_DOUBLE);
        // skip non-volatile and volatile fprs
        for (int i = LAST_NONVOLATILE_GPR.value(); i >= FIRST_VOLATILE_GPR.value(); --i) {
          location = location.minus(BYTES_IN_ADDRESS);
          registerLocations.set(i, location);
        }

        bridgeRegistersLocationUpdated = true;
      }

      // handle implicit "this" parameter, if any
      //
      if (bridgeParameterIndex == -1) {
        bridgeParameterIndex += 1;
        bridgeRegisterIndex = bridgeRegisterIndex.nextGPR();
        bridgeRegisterLocation = bridgeRegisterLocation.plus(BYTES_IN_ADDRESS);

        if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
          VM.sysWrite("BaselineGCMapIterator getNextReferenceOffset, ");
          VM.sysWrite("  this, bridge, returning: ");
          VM.sysWrite(bridgeRegisterLocation.minus(BYTES_IN_ADDRESS));
          VM.sysWriteln();
        }
        return bridgeRegisterLocation.minus(BYTES_IN_ADDRESS);
      }

      // now the remaining parameters
      //
      while (bridgeParameterIndex < bridgeParameterTypes.length) {
        TypeReference bridgeParameterType = bridgeParameterTypes[bridgeParameterIndex++];

        // are we still processing the regs?
        if (bridgeRegisterIndex.value() <= LAST_VOLATILE_GPR.value()) {

          // update the bridgeRegisterLocation (based on type) and return a value if it is a ref
          if (bridgeParameterType.isReferenceType()) {
            bridgeRegisterLocation = bridgeRegisterLocation.plus(BYTES_IN_ADDRESS);
            bridgeRegisterIndex = bridgeRegisterIndex.nextGPR();

            if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
              VM.sysWrite("BaselineGCMapIterator getNextReferenceOffset, ");
              VM.sysWrite("  parm: ");
              VM.sysWrite(bridgeRegisterLocation.minus(BYTES_IN_ADDRESS));
              VM.sysWriteln();
            }
            return bridgeRegisterLocation.minus(BYTES_IN_ADDRESS);
          } else if (bridgeParameterType.isLongType()) {
            bridgeRegisterIndex = bridgeRegisterIndex.nextGPR();
            if (VM.BuildFor32Addr) bridgeRegisterIndex = bridgeRegisterIndex.nextGPR();
            bridgeRegisterLocation = bridgeRegisterLocation.plus(BYTES_IN_LONG);
          } else if (bridgeParameterType.isDoubleType() || bridgeParameterType.isFloatType()) {
            // nothing to do, these are not stored in gprs
          } else {
            // boolean, byte, char, short, int
            bridgeRegisterIndex = bridgeRegisterIndex.nextGPR();
            bridgeRegisterLocation = bridgeRegisterLocation.plus(BYTES_IN_ADDRESS);
          }
        } else {  // now process the register spill area for the remain params
          // no need to update BridgeRegisterIndex anymore, it isn't used and is already
          //  big enough (> LAST_VOLATILE_GPR) to ensure we'll live in this else code in the future
          if (bridgeParameterType.isReferenceType()) {
            bridgeSpilledParamLocation = bridgeSpilledParamLocation.plus(BYTES_IN_ADDRESS);

            if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
              VM.sysWrite("BaselineGCMapIterator getNextReferenceOffset, dynamic link spilled parameter, returning: ");
              VM.sysWrite(bridgeSpilledParamLocation.minus(BYTES_IN_ADDRESS));
              VM.sysWriteln(".");
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

  @Override
  public Address getNextReturnAddressAddress() {

    if (mapId >= 0) {
      if (VM.TraceStkMaps || TRACE_ALL) {
        VM.sysWrite("BaselineGCMapIterator getNextReturnAddressOffset mapId = ");
        VM.sysWrite(mapId);
        VM.sysWriteln(".");
      }
      return Address.zero();
    }
    mapIndex = maps.getNextJSRReturnAddrIndex(mapIndex);
    if (VM.TraceStkMaps || TRACE_ALL) {
      VM.sysWrite("BaselineGCMapIterator getNextReturnAddressIndex = ");
      VM.sysWrite(mapIndex);
      VM.sysWriteln(".");
    }

    if (mapIndex == 0) return Address.zero();

    short location = convertIndexToLocation(mapIndex);
    if (VM.TraceStkMaps || TRACE_ALL) {
      VM.sysWrite("BaselineGCMapIterator getNextReturnAddress location = ");
      VM.sysWrite(location);
      VM.sysWriteln(".");
    }

    if (BaselineCompilerImpl.isRegister(location)) {
      return registerLocations.get(location);
    } else {
      return framePtr.plus(BaselineCompilerImpl.locationToOffset(location) -
                           BYTES_IN_ADDRESS); //location offsets are positioned on top of stackslot
    }
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
      if (VM.TraceStkMaps || TRACE_ALL) VM.sysWriteln("    Update Caller RegisterLocations");
      Address addr = framePtr.plus(currentCompiledMethod.getFrameSize());
      addr =
          addr.minus((currentCompiledMethod.getLastFloatStackRegister() - FIRST_FLOAT_LOCAL_REGISTER.value() + 1) <<
                     LOG_BYTES_IN_DOUBLE); //skip float registers

      for (int i = currentCompiledMethod.getLastFixedStackRegister(); i >= FIRST_FIXED_LOCAL_REGISTER.value(); --i) {
        addr = addr.minus(BYTES_IN_ADDRESS);
        registerLocations.set(i, addr);
      }
    }
  }

}
