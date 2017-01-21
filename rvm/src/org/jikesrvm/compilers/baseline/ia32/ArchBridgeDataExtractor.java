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

import static org.jikesrvm.compilers.baseline.AbstractBaselineGCMapIterator.TRACE_ALL;
import static org.jikesrvm.compilers.baseline.AbstractBaselineGCMapIterator.TRACE_DL;
import static org.jikesrvm.ia32.BaselineConstants.LG_WORDSIZE;
import static org.jikesrvm.ia32.BaselineConstants.WORDSIZE;
import static org.jikesrvm.ia32.RegisterConstants.NUM_PARAMETER_GPRS;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.baseline.AbstractBridgeDataExtractor;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * This class implements the extracting of bridge data for the IA32 compilers.
 * This includes processing of parameters spilled by the IA32 opt compiler.
 */
@Uninterruptible
class ArchBridgeDataExtractor extends AbstractBridgeDataExtractor {


  /** gpr register it lives in */
  private int bridgeRegisterIndex;

  /** do we need to map spilled params (baseline compiler = no, opt = yes) */
  private boolean bridgeSpilledParameterMappingRequired;
  /** starting offset to stack location for param0 */
  private int bridgeSpilledParamInitialOffset;

  @Override
  protected final void setupBridgeRegisterIndex() {
    bridgeRegisterIndex = 0;
  }

  @Override
  public final void resetBridgeRegisterIndex() {
    bridgeRegisterIndex = 0;
  }

  @Override
  protected final void setupArchitectureSpecificDynamicBridgeMapping(Address fp) {
    Address ip = Magic.getReturnAddressUnchecked(fp);
    fp = Magic.getCallerFramePointer(fp);
    int callingCompiledMethodId = Magic.getCompiledMethodID(fp);
    CompiledMethod callingCompiledMethod = CompiledMethods.getCompiledMethod(callingCompiledMethodId);
    Offset callingInstructionOffset = callingCompiledMethod.getInstructionOffset(ip);

    updateWithInfoForDynamicLink(callingCompiledMethod, callingInstructionOffset);
    if (dynamicLink.isInvokedWithImplicitThisParameter()) {
      bridgeSpilledParamInitialOffset = 2 * WORDSIZE; // this + return addr
    } else {
      bridgeSpilledParamInitialOffset = WORDSIZE; // return addr
    }

    bridgeSpilledParamInitialOffset += (bridgeTarget.getParameterWords() << LG_WORDSIZE);
    bridgeSpilledParameterMappingRequired = callingCompiledMethod.getCompilerType() != CompiledMethod.BASELINE;
  }

  @Override
  public final void resetBridgeSpilledParamLocation(Address framePtr) {
    bridgeSpilledParamLocation = framePtr.plus(bridgeSpilledParamInitialOffset);
  }

  @Override
  public final void incBridgeRegisterIndex() {
    bridgeRegisterIndex++;
  }

  @Override
  public final boolean unprocessedBridgeRegistersRemain() {
    return bridgeRegisterIndex <= NUM_PARAMETER_GPRS;
  }

  public final boolean isBridgeSpilledParameterMappingRequired() {
    return bridgeSpilledParameterMappingRequired;
  }

  @Override
  public final Address getImplicitThisAddress() {
    bridgeParameterIndex++;
    incBridgeRegisterIndex();
    decBrigeRegisterLocation(WORDSIZE);
    bridgeSpilledParamLocation = bridgeSpilledParamLocation.minus(WORDSIZE);

    if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
      VM.sysWrite("BaselineGCMapIterator getNextReferenceOffset = dynamic link GPR this ");
      VM.sysWrite(bridgeRegisterLocation.plus(WORDSIZE));
      VM.sysWriteln(".");
    }
    return bridgeRegisterLocation.plus(WORDSIZE);
  }

  @Override
  public  final Address getNextBridgeParameterAddress() {
    if (!hasMoreBridgeParameters()) {
      return Address.zero();
    }

    TypeReference bridgeParameterType = bridgeParameterTypes[bridgeParameterIndex++];
    boolean paramIsRef = bridgeParameterType.isReferenceType();

    // Skip over non-reference parameters
    while (!paramIsRef) {
      if (bridgeParameterType.isLongType()) {
        if (VM.BuildFor32Addr) {
          incBridgeRegisterIndex();
          incBridgeRegisterIndex();
          decBrigeRegisterLocation(2 * WORDSIZE);
        } else {
          incBridgeRegisterIndex();
          decBrigeRegisterLocation(WORDSIZE);
        }
        bridgeSpilledParamLocation = bridgeSpilledParamLocation.minus(2 * WORDSIZE);
      } else if (bridgeParameterType.isDoubleType()) {
        bridgeSpilledParamLocation = bridgeSpilledParamLocation.minus(2 * WORDSIZE);
      } else if (bridgeParameterType.isFloatType()) {
        bridgeSpilledParamLocation = bridgeSpilledParamLocation.minus(WORDSIZE);
      } else {
        // boolean, byte, char, short, int
        incBridgeRegisterIndex();
        decBrigeRegisterLocation(WORDSIZE);
        bridgeSpilledParamLocation = bridgeSpilledParamLocation.minus(WORDSIZE);
      }

      if (hasMoreBridgeParameters()) {
        bridgeParameterType = bridgeParameterTypes[bridgeParameterIndex++];
        paramIsRef = bridgeParameterType.isReferenceType();
      } else {
        return Address.zero();
      }
    }

    if (VM.VerifyAssertions) VM._assert(bridgeParameterType.isReferenceType());

    incBridgeRegisterIndex();
    decBrigeRegisterLocation(WORDSIZE);
    bridgeSpilledParamLocation = bridgeSpilledParamLocation.minus(WORDSIZE);

    if (unprocessedBridgeRegistersRemain()) {
      if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
        VM.sysWrite("BaselineGCMapIterator getNextReferenceOffset = dynamic link GPR parameter ");
        VM.sysWrite(bridgeRegisterLocation.plus(WORDSIZE));
        VM.sysWriteln(".");
      }
      return bridgeRegisterLocation.plus(WORDSIZE);
    } else {
      if (isBridgeSpilledParameterMappingRequired()) {
        if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
          VM.sysWrite("BaselineGCMapIterator getNextReferenceOffset = dynamic link spilled parameter ");
          VM.sysWrite(bridgeSpilledParamLocation.plus(WORDSIZE));
          VM.sysWriteln(".");
        }
        return bridgeSpilledParamLocation.plus(WORDSIZE);
      } else {
        // fallthrough to return
      }
    }

    return Address.zero();
  }

  public final void traceBridgeTarget() {
    VM.sysWrite("getNextReferenceAddress: bridgeTarget=");
    VM.sysWrite(bridgeTarget);
    VM.sysWriteln();
  }

}
