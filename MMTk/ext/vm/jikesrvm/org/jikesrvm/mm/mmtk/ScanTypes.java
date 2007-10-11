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
package org.jikesrvm.mm.mmtk;

import org.mmtk.plan.TraceLocal;
import org.mmtk.utility.Constants;
import org.jikesrvm.VM;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.memorymanagers.mminterface.VM_CollectorThread;
import org.jikesrvm.objectmodel.VM_ObjectModel;
import org.jikesrvm.objectmodel.VM_TIB;
import org.jikesrvm.objectmodel.VM_TIBLayoutConstants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Class that processes the type information.
 *
 * Eventually we can use this for class unloading.
 */
public final class ScanTypes implements Constants, VM_TIBLayoutConstants {
  @Inline
  @Uninterruptible
  public static void scanTypes(TraceLocal trace) {
    // The number of collector threads
    final int numberOfCollectors = VM_CollectorThread.numCollectors();
    // This thread as a collector
    final VM_CollectorThread ct = VM_Magic.threadAsCollectorThread(VM_Scheduler.getCurrentThread());
    // The number of this collector thread (1...n)
    final int threadOrdinal = ct.getGCOrdinal();
    // The number of references
    final int numberOfReferences = VM_Type.numTypes();
    // The chunk size per processor
    final int chunkSize = numberOfReferences / numberOfCollectors;

    // Start and end of types region to be processed
    final int start = 1 + (threadOrdinal - 1) * chunkSize;
    final int end = 1 + ((threadOrdinal == numberOfCollectors) ? numberOfReferences : threadOrdinal * chunkSize);

    // Process region
    for(int i = start; i < end; i++) {
      VM_Type type = VM_Type.getType(i);
      if (type != null) {
        trace.processRootEdge(VM_Magic.objectAsAddress(type).plus(VM_Entrypoints.classForTypeField.getOffset()), true);

        if (type.isArrayType()) {
          trace.processRootEdge(VM_Magic.objectAsAddress(type).plus(VM_Entrypoints.innermostElementTypeField.getOffset()), true);
        }

        if (type.isReferenceType() && type.isResolved()) {
          VM_TIB tib = type.getTypeInformationBlock();
          Address tibAddress = VM_Magic.objectAsAddress(tib);

          trace.processRootEdge(tibAddress.plus(TIB_TYPE_INDEX << LOG_BYTES_IN_ADDRESS), true);
          trace.processRootEdge(tibAddress.plus(TIB_SUPERCLASS_IDS_INDEX << LOG_BYTES_IN_ADDRESS), true);
          trace.processRootEdge(tibAddress.plus(TIB_DOES_IMPLEMENT_INDEX << LOG_BYTES_IN_ADDRESS), true);

          if (type.isArrayType()) {
            trace.processRootEdge(tibAddress.plus(TIB_ARRAY_ELEMENT_TIB_INDEX << LOG_BYTES_IN_ADDRESS), true);
          } else if (type.isClassType()) {
            if (VM.BuildForITableInterfaceInvocation) {
              Address itablesSlot = tibAddress.plus(TIB_ITABLES_TIB_INDEX << LOG_BYTES_IN_ADDRESS);
              trace.processRootEdge(itablesSlot, true);

              Address itables = itablesSlot.loadAddress();
              for(int j=0; j < VM_ObjectModel.getArrayLength(VM_Magic.addressAsObject(itables)); j++) {
                Address itableSlot = itables.plus(j << LOG_BYTES_IN_ADDRESS);
                trace.processRootEdge(itableSlot, true);
                Address itable = itableSlot.loadAddress();
                for(int k=0; k < VM_ObjectModel.getArrayLength(VM_Magic.addressAsObject(itable)); k++) {
                  trace.processRootEdge(itable.plus(k << LOG_BYTES_IN_ADDRESS), true);
                }
              }
            }

            if (VM.BuildForIMTInterfaceInvocation) {
              Address imtAddress = Address.zero();

              /* Determine if this TIB has an IMT either embedded or off to the side */
              if (VM.BuildForIndirectIMT) {
                if (TIB_IMT_TIB_INDEX < tib.length()) {
                  Address imtSlot = tibAddress.plus(TIB_IMT_TIB_INDEX << LOG_BYTES_IN_ADDRESS);
                  trace.processRootEdge(imtSlot, true);
                  /* Load the (possibly null) IMT */
                  imtAddress = imtSlot.loadAddress();
                }
              } else {
                if ((TIB_FIRST_INTERFACE_METHOD_INDEX + IMT_METHOD_SLOTS) < tib.length()) {
                  /* The TIB is large enough, so we can safely process them */
                  imtAddress = tibAddress.plus(TIB_FIRST_INTERFACE_METHOD_INDEX << LOG_BYTES_IN_ADDRESS);
                }
              }

              if (!imtAddress.isZero()) {
                for(int j=0; j < IMT_METHOD_SLOTS; j++) {
                  trace.processRootEdge(imtAddress.plus(j << LOG_BYTES_IN_ADDRESS), true);
                }
              }
            }
          }

          for(int j=0; j < tib.numVirtualMethods(); j++) {
            trace.processRootEdge(tibAddress.plus(VM_TIB.getVirtualMethodOffset(j)), true);
          }
        }
      }
    }
  }
}
