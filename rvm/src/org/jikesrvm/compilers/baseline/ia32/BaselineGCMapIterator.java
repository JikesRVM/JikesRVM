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
package org.jikesrvm.compilers.baseline.ia32;

import static org.jikesrvm.ia32.BaselineConstants.BRIDGE_FRAME_EXTRA_SIZE;
import static org.jikesrvm.ia32.BaselineConstants.EBP_SAVE_OFFSET;
import static org.jikesrvm.ia32.BaselineConstants.EBX_SAVE_OFFSET;
import static org.jikesrvm.ia32.BaselineConstants.EDI_SAVE_OFFSET;
import static org.jikesrvm.ia32.BaselineConstants.LG_WORDSIZE;
import static org.jikesrvm.ia32.BaselineConstants.STACKFRAME_FIRST_PARAMETER_OFFSET;
import static org.jikesrvm.ia32.BaselineConstants.T0;
import static org.jikesrvm.ia32.BaselineConstants.T0_SAVE_OFFSET;
import static org.jikesrvm.ia32.BaselineConstants.T1;
import static org.jikesrvm.ia32.BaselineConstants.T1_SAVE_OFFSET;
import static org.jikesrvm.ia32.BaselineConstants.WORDSIZE;
import static org.jikesrvm.ia32.RegisterConstants.EBP;
import static org.jikesrvm.ia32.RegisterConstants.EBX;
import static org.jikesrvm.ia32.RegisterConstants.EDI;
import static org.jikesrvm.ia32.RegisterConstants.NUM_PARAMETER_GPRS;
import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.baseline.AbstractBaselineGCMapIterator;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;
import org.vmmagic.unboxed.Offset;

/**
 * Iterator for stack frame  built by the Baseline compiler.
 * <p>
 * An Instance of this class will iterate through a particular
 * reference map of a method returning the offsets of any references
 * that are part of the input parameters, local variables, and
 * java stack for the stack frame.
 */
@Uninterruptible
public final class BaselineGCMapIterator extends AbstractBaselineGCMapIterator {

  /** Compiled method for the frame */
  protected ArchBaselineCompiledMethod currentCompiledMethod;
  /** have we reported the base ptr of the edge counter array? */
  private boolean counterArrayBase;

  /** do we need to map spilled params (baseline compiler = no, opt = yes) */
  private boolean bridgeSpilledParameterMappingRequired;
  /** gpr register it lives in */
  private int bridgeRegisterIndex;
  /** starting offset to stack location for param0 */
  private int bridgeSpilledParamInitialOffset;

  public BaselineGCMapIterator(AddressArray registerLocations) {
    super(registerLocations);
  }

  /*
   * Interface
   */

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
   *          identifies a specific occurrence of this method and allows for
   *          processing instance specific information i.e JSR return address
   *          values
   */
  @Override
  public void setupIterator(CompiledMethod compiledMethod, Offset instructionOffset, Address fp) {
    currentCompiledMethod = (ArchBaselineCompiledMethod) compiledMethod;
    currentMethod = (NormalMethod) currentCompiledMethod.getMethod();
    currentNumLocals = currentMethod.getLocalWords();

    // setup superclass
    //
    framePtr = fp;

    // setup stackframe mapping
    //
    mapReader.setMethod(currentMethod, compiledMethod);
    mapReader.locateGCPoint(instructionOffset);
    if (mapReader.currentMapIsForJSR()) {
      mapReader.acquireLockForJSRProcessing();
      int JSRindex = mapReader.setupJSRSubroutineMap();
      while (JSRindex != 0) {
        Address nextCallerAddress = framePtr.plus(convertIndexToOffset(JSRindex)).loadAddress();
        Offset nextMachineCodeOffset = compiledMethod.getInstructionOffset(nextCallerAddress);
        if (VM.TraceStkMaps) {
          traceSetupJSRsubroutineMap(JSRindex, nextCallerAddress,
              nextMachineCodeOffset);
        }
        JSRindex = mapReader.getNextJSRAddressIndex(nextMachineCodeOffset);
      }
    }
    if (VM.TraceStkMaps || TRACE_ALL) {
      VM.sysWrite("BaselineGCMapIterator setupIterator mapId = ");
      VM.sysWrite(mapReader.getMapId());
      VM.sysWrite(" for ");
      VM.sysWrite(compiledMethod.getMethod());
      VM.sysWriteln(".");
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
      Address ip = Magic.getReturnAddressUnchecked(fp);
      fp = Magic.getCallerFramePointer(fp);
      int callingCompiledMethodId = Magic.getCompiledMethodID(fp);
      CompiledMethod callingCompiledMethod = CompiledMethods.getCompiledMethod(callingCompiledMethodId);
      Offset callingInstructionOffset = callingCompiledMethod.getInstructionOffset(ip);

      callingCompiledMethod.getDynamicLink(dynamicLink, callingInstructionOffset);
      bridgeTarget = dynamicLink.methodRef();
      bridgeParameterTypes = bridgeTarget.getParameterTypes();
      if (dynamicLink.isInvokedWithImplicitThisParameter()) {
        bridgeParameterInitialIndex = -1;
        bridgeSpilledParamInitialOffset = 2 * WORDSIZE; // this + return addr
      } else {
        bridgeParameterInitialIndex = 0;
        bridgeSpilledParamInitialOffset = WORDSIZE; // return addr
      }
      bridgeSpilledParamInitialOffset += (bridgeTarget.getParameterWords() << LG_WORDSIZE);
      bridgeSpilledParameterMappingRequired = callingCompiledMethod.getCompilerType() != CompiledMethod.BASELINE;
    }

    reset();
  }

  @Override
  protected void resetOtherState() {
    // setup map to report EBX if this method is holding the base of counter array in it.
    counterArrayBase = currentCompiledMethod.hasCounterArray();
  }

  @Override
  protected void resetArchitectureSpecificBridgeSState() {
    if (bridgeTarget != null) {
      bridgeRegisterIndex = 0;
      bridgeRegisterLocation = framePtr.plus(STACKFRAME_FIRST_PARAMETER_OFFSET); // top of frame
      bridgeSpilledParamLocation = framePtr.plus(bridgeSpilledParamInitialOffset);
    }
  }

  /**
   * Converts a biased index from a local area into an offset in the stack.
   *
   * @param index index in the local area (biased : local0 has index 1)
   * @return corresponding offset in the stack
   */
  public short convertIndexToLocation(int index) {
    if (index == 0) return 0;
    if (index <= currentNumLocals) { //index is biased by 1;
      return currentCompiledMethod.getGeneralLocalLocation(index - 1);
    } else {
      return currentCompiledMethod.getGeneralStackLocation(index - 1 - currentNumLocals);
    }
  }

  private int convertIndexToOffset(int index) {
    //for ia32: always offset, never registers
    if (index == 0) return 0; //invalid

    // index is biased by 1, index 1 means local 0, this is at offset -BYTES_IN_ADDRESS from startLocalOffset
    int offset = BaselineCompilerImpl.locationToOffset(convertIndexToLocation(index)) - BYTES_IN_ADDRESS; // no jsrbit here
    if (VM.TraceStkMaps) {
      VM.sysWriteln("convertIndexToOffset- input index = ", index, "  offset = ", offset);
    }
    return offset;
  }

  @Override
  public Address getNextReferenceAddress() {
    if (!mapReader.isFinishedWithRegularMap()) {
      if (counterArrayBase) {
        counterArrayBase = false;
        return registerLocations.get(EBX.value());
      }
      mapReader.updateMapIndex();

      if (mapReader.currentMapHasMorePointers()) {
        int mapOffset = convertIndexToOffset(mapReader.getMapIndex());
        if (VM.TraceStkMaps || TRACE_ALL) {
          VM.sysWrite("BaselineGCMapIterator getNextReferenceOffset = ");
          VM.sysWriteHex(mapOffset);
          VM.sysWrite(" (= ");
          VM.sysWrite(mapOffset);
          VM.sysWrite(")");
          VM.sysWriteln(".");
          VM.sysWrite("Reference is ");
        }
        if (bridgeParameterMappingRequired) {
          if (VM.TraceStkMaps || TRACE_ALL) {
            VM.sysWriteHex(framePtr.plus(mapOffset - BRIDGE_FRAME_EXTRA_SIZE).loadAddress());
            VM.sysWriteln(".");
            if (mapReader.currentMapIsForJSR()) {
              VM.sysWriteln("Offset is a JSR return address ie internal pointer.");
            }
          }

          // TODO  clean this
          return (framePtr.plus(mapOffset - BRIDGE_FRAME_EXTRA_SIZE));
        } else {
          if (VM.TraceStkMaps || TRACE_ALL) {
            VM.sysWriteHex(framePtr.plus(mapOffset).loadAddress());
            VM.sysWriteln(".");
            if (mapReader.currentMapIsForJSR()) {
              VM.sysWriteln("Offset is a JSR return address ie internal pointer.");
            }
          }
          return (framePtr.plus(mapOffset));
        }
      } else {
        // remember that we are done with the map for future calls, and then
        //   drop down to the code below
        mapReader.setFinishedWithRegularMap();
      }
    }

    if (bridgeParameterMappingRequired) {
      if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
        VM.sysWrite("getNextReferenceAddress: bridgeTarget=");
        VM.sysWrite(bridgeTarget);
        VM.sysWriteln();
      }

      if (!bridgeRegistersLocationUpdated) {
        // point registerLocations[] to our callers stackframe
        //
        registerLocations.set(EDI.value(), framePtr.plus(EDI_SAVE_OFFSET));
        registerLocations.set(T0.value(), framePtr.plus(T0_SAVE_OFFSET));
        registerLocations.set(T1.value(), framePtr.plus(T1_SAVE_OFFSET));
        registerLocations.set(EBX.value(), framePtr.plus(EBX_SAVE_OFFSET));

        bridgeRegistersLocationUpdated = true;
      }

      // handle implicit "this" parameter, if any
      //
      if (bridgeParameterIndex == -1) {
        bridgeParameterIndex += 1;
        bridgeRegisterIndex += 1;
        bridgeRegisterLocation = bridgeRegisterLocation.minus(WORDSIZE);
        bridgeSpilledParamLocation = bridgeSpilledParamLocation.minus(WORDSIZE);

        if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
          VM.sysWrite("BaselineGCMapIterator getNextReferenceOffset = dynamic link GPR this ");
          VM.sysWrite(bridgeRegisterLocation.plus(WORDSIZE));
          VM.sysWriteln(".");
        }
        return bridgeRegisterLocation.plus(WORDSIZE);
      }

      // now the remaining parameters
      //
      while (bridgeParameterIndex < bridgeParameterTypes.length) {
        TypeReference bridgeParameterType = bridgeParameterTypes[bridgeParameterIndex++];

        if (bridgeParameterType.isReferenceType()) {
          bridgeRegisterIndex += 1;
          bridgeRegisterLocation = bridgeRegisterLocation.minus(WORDSIZE);
          bridgeSpilledParamLocation = bridgeSpilledParamLocation.minus(WORDSIZE);

          if (bridgeRegisterIndex <= NUM_PARAMETER_GPRS) {
            if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
              VM.sysWrite("BaselineGCMapIterator getNextReferenceOffset = dynamic link GPR parameter ");
              VM.sysWrite(bridgeRegisterLocation.plus(WORDSIZE));
              VM.sysWriteln(".");
            }
            return bridgeRegisterLocation.plus(WORDSIZE);
          } else {
            if (bridgeSpilledParameterMappingRequired) {
              if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
                VM.sysWrite("BaselineGCMapIterator getNextReferenceOffset = dynamic link spilled parameter ");
                VM.sysWrite(bridgeSpilledParamLocation.plus(WORDSIZE));
                VM.sysWriteln(".");
              }
              return bridgeSpilledParamLocation.plus(WORDSIZE);
            } else {
              break;
            }
          }
        } else if (bridgeParameterType.isLongType()) {
          bridgeRegisterIndex += VM.BuildFor32Addr ? 2 : 1;
          bridgeRegisterLocation = bridgeRegisterLocation.minus(2 * WORDSIZE);
          bridgeSpilledParamLocation = bridgeSpilledParamLocation.minus(2 * WORDSIZE);
        } else if (bridgeParameterType.isDoubleType()) {
          bridgeSpilledParamLocation = bridgeSpilledParamLocation.minus(2 * WORDSIZE);
        } else if (bridgeParameterType.isFloatType()) {
          bridgeSpilledParamLocation = bridgeSpilledParamLocation.minus(WORDSIZE);
        } else {
          // boolean, byte, char, short, int
          bridgeRegisterIndex += 1;
          bridgeRegisterLocation = bridgeRegisterLocation.minus(WORDSIZE);
          bridgeSpilledParamLocation = bridgeSpilledParamLocation.minus(WORDSIZE);
        }
      }
    } else {
      // point registerLocations[] to our callers stackframe
      //
      registerLocations.set(EDI.value(), framePtr.plus(EDI_SAVE_OFFSET));
      registerLocations.set(EBX.value(), framePtr.plus(EBX_SAVE_OFFSET));
      if (currentMethod.hasBaselineSaveLSRegistersAnnotation()) {
        registerLocations.set(EBP.value(), framePtr.plus(EBP_SAVE_OFFSET));
      }
    }

    if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
      VM.sysWriteln("BaselineGCMapIterator getNextReferenceOffset: all references processed.");
    }
    return Address.zero();
  }

  @Override
  public Address getNextReturnAddressAddress() {
    if (!mapReader.currentMapIsForJSR()) {
      if (VM.TraceStkMaps || TRACE_ALL) {
        traceMapIdForGetNextReturnAddressAddress();
      }
      return Address.zero();
    }
    mapReader.updateMapIndexWithJSRReturnAddrIndex();
    if (VM.TraceStkMaps || TRACE_ALL) {
      VM.sysWrite("BaselineGCMapIterator getNextReturnAddressOffset = ");
      VM.sysWrite(convertIndexToOffset(mapReader.getMapIndex()));
      VM.sysWriteln(".");
    }
    return (!mapReader.currentMapHasMorePointers()) ? Address.zero() : framePtr.plus(convertIndexToOffset(mapReader.getMapIndex()));
  }

}

