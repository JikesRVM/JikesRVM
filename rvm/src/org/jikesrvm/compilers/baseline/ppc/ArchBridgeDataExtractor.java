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

import static org.jikesrvm.compilers.baseline.AbstractBaselineGCMapIterator.TRACE_ALL;
import static org.jikesrvm.compilers.baseline.AbstractBaselineGCMapIterator.TRACE_DL;
import static org.jikesrvm.ppc.RegisterConstants.FIRST_VOLATILE_GPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_VOLATILE_GPR;
import static org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_HEADER_SIZE;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_DOUBLE;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_FLOAT;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_LONG;
import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.baseline.AbstractBridgeDataExtractor;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.ppc.RegisterConstants.GPR;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * This class implements the extracting of bridge data for the PowerPC compilers.
 * This includes processing of the register spill areas.
 */
@Uninterruptible
final class ArchBridgeDataExtractor extends AbstractBridgeDataExtractor {

  /**  gpr register it lives in */
  private GPR bridgeRegisterIndex;

  @Override
  protected void setupBridgeRegisterIndex() {
    bridgeRegisterIndex = GPR.R0;
  }

  @Override
  public void resetBridgeRegisterIndex() {
    bridgeRegisterIndex = FIRST_VOLATILE_GPR;
  }

  @Override
  protected void setupArchitectureSpecificDynamicBridgeMapping(Address fp) {
    fp = Magic.getCallerFramePointer(fp);
    Address ip = Magic.getNextInstructionAddress(fp);
    int callingCompiledMethodId = Magic.getCompiledMethodID(fp);
    CompiledMethod callingCompiledMethod = CompiledMethods.getCompiledMethod(callingCompiledMethodId);
    Offset callingInstructionOffset = callingCompiledMethod.getInstructionOffset(ip);

    updateWithInfoForDynamicLink(callingCompiledMethod, callingInstructionOffset);
  }

  @Override
  public void resetBridgeSpilledParamLocation(Address callersFp) {
    bridgeSpilledParamLocation = callersFp.plus(STACKFRAME_HEADER_SIZE);
  }

  @Override
  public void incBridgeRegisterIndex() {
    bridgeRegisterIndex = bridgeRegisterIndex.nextGPR();
  }

  @Override
  public boolean unprocessedBridgeRegistersRemain() {
    return bridgeRegisterIndex.value() <= LAST_VOLATILE_GPR.value();
  }

  @Override
  public Address getImplicitThisAddress() {
    bridgeParameterIndex++;
    incBridgeRegisterIndex();
    incBrigeRegisterLocation(BYTES_IN_ADDRESS);

    if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
      VM.sysWrite("BaselineGCMapIterator getNextReferenceOffset, ");
      VM.sysWrite("  this, bridge, returning: ");
      VM.sysWrite(bridgeRegisterLocation.minus(BYTES_IN_ADDRESS));
      VM.sysWriteln();
    }
    return bridgeRegisterLocation.minus(BYTES_IN_ADDRESS);
  }

  @Override
  public Address getNextBridgeParameterAddress() {
    while (hasMoreBridgeParameters()) {
      TypeReference bridgeParameterType = bridgeParameterTypes[bridgeParameterIndex++];

      // are we still processing the regs?
      if (unprocessedBridgeRegistersRemain()) {
        // update the bridgeRegisterLocation (based on type) and return a value if it is a ref
        if (bridgeParameterType.isReferenceType()) {
          incBrigeRegisterLocation(BYTES_IN_ADDRESS);
          incBridgeRegisterIndex();

          if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
            VM.sysWrite("BaselineGCMapIterator getNextReferenceOffset, ");
            VM.sysWrite("  parm: ");
            VM.sysWrite(bridgeRegisterLocation.minus(BYTES_IN_ADDRESS));
            VM.sysWriteln();
          }
          return bridgeRegisterLocation.minus(BYTES_IN_ADDRESS);
        } else if (bridgeParameterType.isLongType()) {
          incBridgeRegisterIndex();
          if (VM.BuildFor32Addr) incBridgeRegisterIndex();
          incBrigeRegisterLocation(BYTES_IN_LONG);
        } else if (bridgeParameterType.isFloatingPointType()) {
          // nothing to do, these are not stored in gprs
        } else {
          // boolean, byte, char, short, int
          incBridgeRegisterIndex();
          incBrigeRegisterLocation(BYTES_IN_ADDRESS);
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

    return Address.zero();
  }

}
