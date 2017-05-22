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
package org.jikesrvm.compilers.opt.regalloc;

import java.util.HashSet;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.GCIRMapElement;
import org.jikesrvm.compilers.opt.ir.GenericPhysicalRegisterSet;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.RegSpillListElement;
import org.jikesrvm.compilers.opt.ir.Register;

/**
 * Update GC Maps again, to account for changes induced by spill code.
 */
final class UpdateGCMaps2 extends CompilerPhase {
  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  @Override
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  @Override
  public boolean shouldPerform(OptOptions options) {
    return true;
  }

  @Override
  public String getName() {
    return "Update GCMaps 2";
  }

  @Override
  public boolean printingEnabled(OptOptions options, boolean before) {
    return false;
  }

  /**
   *  @param ir the IR
   */
  @Override
  public void perform(IR ir) {
    GenericPhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    ScratchMap scratchMap = ir.stackManager.getScratchMap();
    RegisterAllocatorState regAllocState = ir.MIRInfo.regAllocState;

    if (LinearScan.GC_DEBUG) {
      System.out.println("SCRATCH MAP:");
      System.out.println();
      System.out.println(scratchMap);
    }
    if (scratchMap.isEmpty()) return;

    // Walk over each instruction that has a GC point.
    for (GCIRMapElement GCelement : ir.MIRInfo.gcIRMap) {
      // new elements to add to the gc map
      HashSet<RegSpillListElement> newElements = new HashSet<RegSpillListElement>();

      Instruction GCinst = GCelement.getInstruction();

      int dfn = regAllocState.getDFN(GCinst);

      if (LinearScan.GC_DEBUG) {
        VM.sysWrite("GCelement at " + dfn + " , " + GCelement);
      }

      // a set of elements to delete from the GC Map
      HashSet<RegSpillListElement> toDelete = new HashSet<RegSpillListElement>(3);

      // For each element in the GC Map ...
      for (RegSpillListElement elem : GCelement.regSpillList()) {
        if (LinearScan.GC_DEBUG) {
          VM.sysWriteln("Update " + elem);
        }
        if (elem.isSpill()) {
          // check if the spilled value currently is cached in a scratch
          // register
          Register r = elem.getSymbolicReg();
          Register scratch = scratchMap.getScratch(r, dfn);
          if (scratch != null) {
            if (LinearScan.GC_DEBUG) {
              VM.sysWriteln("cached in scratch register " + scratch);
            }
            // we will add a new element noting that the scratch register
            // also must be including in the GC map
            RegSpillListElement newElem = new RegSpillListElement(r);
            newElem.setRealReg(scratch);
            newElements.add(newElem);
            // if the scratch register is dirty, then delete the spill
            // location from the map, since it doesn't currently hold a
            // valid value
            if (scratchMap.isDirty(GCinst, r)) {
              toDelete.add(elem);
            }
          }
        } else {
          // check if the physical register is currently spilled.
          int n = elem.getRealRegNumber();
          Register r = phys.get(n);
          if (scratchMap.isScratch(r, dfn)) {
            // The regalloc state knows where the physical register r is
            // spilled.
            if (LinearScan.GC_DEBUG) {
              VM.sysWriteln("CHANGE to spill location " + regAllocState.getSpill(r));
            }
            elem.setSpill(regAllocState.getSpill(r));
          }
        }

      }
      // delete all obsolete elements
      for (RegSpillListElement deadElem : toDelete) {
        GCelement.deleteRegSpillElement(deadElem);
      }

      // add each new Element to the gc map
      for (RegSpillListElement newElem : newElements) {
        GCelement.addRegSpillElement(newElem);
      }
    }
  }
}
