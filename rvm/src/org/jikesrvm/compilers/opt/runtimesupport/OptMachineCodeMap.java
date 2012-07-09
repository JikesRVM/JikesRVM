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
package org.jikesrvm.compilers.opt.runtimesupport;

import java.util.ArrayList;
import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.Constants;
import org.jikesrvm.adaptive.database.callgraph.CallSite;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.driver.OptConstants;
import org.jikesrvm.compilers.opt.inlining.CallSiteTree;
import org.jikesrvm.compilers.opt.ir.MIR_Call;
import org.jikesrvm.compilers.opt.ir.GCIRMap;
import org.jikesrvm.compilers.opt.ir.GCIRMapElement;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;

/**
 * A class that encapsulates mapping information about generated machine code.
 * Since there will be an instance of this class with every OptCompiledMethod,
 * we attempt to pack the data into a reasonably small number of bits.
 *
 * <p> The supported functions are:
 * <ul>
 *  <li> (1) Map from a machine code offset to a GC map (register & stack map).
 *  <li> (2) Map from machinecode offset to <method, bcIndex> pair.
 *        Used for:
 *                  <ul>
 *                  <li> dynamic linking
 *                  <li> lazy compilation
 *                  <li> adaptive system profiling
 *                  </ul>
 *  <li> (3) Map from a machine code offset to a tree of <method, bcIndex> pairs
 *      that encodes the inlining sequence.
 *        Used for:
 *                  <ul>
 *                  <li> IPA
 *                  <li> stack inspection (print stack trace,
 *                                         security manager, etc).
 *                  <li> general debugging support.
 *                  <li> adaptive system profiling
 *                  </ul>
 *</ul>
 *<p>
 *  Note: This file contains two types of methods
 *  <ul>
 *       <li>1) methods called during compilation to create the maps
 *       <li>2) methods called at GC time (no allocation allowed!)
 *  </ul>
 */
public final class OptMachineCodeMap implements Constants, OptConstants {

  /**
   * Private constructor, object should be created via create
   */
  private OptMachineCodeMap(int[] _MCInformation, int[] _gcMaps, int[] _inlineEncoding) {
    MCInformation = _MCInformation;
    gcMaps = _gcMaps;
    inlineEncoding = _inlineEncoding;
  }

  /**
   * Private constructor for no information.
   */
  private OptMachineCodeMap() {
    MCInformation = null;
    gcMaps = null;
    inlineEncoding = null;
  }

  /**
   * Create the map, called during compilation
   * @param ir   the ir object for this method
   * @param machineCodeSize the number of machine code instructions generated.
   */
  static OptMachineCodeMap create(IR ir, int machineCodeSize) {
    /** Dump maps as methods are compiled */
    final boolean DUMP_MAPS =
      ir.options.PRINT_GC_MAPS &&
         (!ir.options.hasMETHOD_TO_PRINT() ||
          (ir.options.hasMETHOD_TO_PRINT() && ir.options.fuzzyMatchMETHOD_TO_PRINT(ir.method.toString()))
          );
    /** Dump stats on map size as maps are compiled */
    final boolean DUMP_MAP_SIZES = false;
    if (DUMP_MAPS) {
      VM.sysWrite("Creating final machine code map for " + ir.method + "\n");
    }

    // create all machine code maps
    final OptMachineCodeMap map = generateMCInformation(ir.MIRInfo.gcIRMap, DUMP_MAPS);

    if (DUMP_MAP_SIZES) {
      map.recordStats(ir.method,
                      map.size(),
                      machineCodeSize << ArchitectureSpecific.RegisterConstants.LG_INSTRUCTION_WIDTH, DUMP_MAP_SIZES);
    }

    if (DUMP_MAPS) {
      VM.sysWrite("Final Machine code information:\n");
      map.dumpMCInformation(DUMP_MAPS);
      for (Instruction i = ir.firstInstructionInCodeOrder(); i != null; i = i.nextInstructionInCodeOrder()) {
        VM.sysWriteln(i.getmcOffset() + "\t" + i);
      }
    }
    return map;
  }

  /**
   * Get the bytecode index for a machine instruction offset.
   *
   * @param MCOffset the machine code offset of interest
   * @return -1 if unknown.
   */
  @Uninterruptible
  public int getBytecodeIndexForMCOffset(Offset MCOffset) {
    int entry = findMCEntry(MCOffset);
    if (entry == -1) {
      return -1;
    }
    return getBytecodeIndex(entry);
  }

  /**
   * Get the RVMMethod for a machine instruction offset.
   * This method is the source method that the instruction came from.
   *
   * @param MCOffset the machine code offset of interest
   * @return {@code null} if unknown
   */
  @Uninterruptible
  public NormalMethod getMethodForMCOffset(Offset MCOffset) {
    int entry = findMCEntry(MCOffset);
    if (entry == -1) {
      return null;
    }
    int iei = getInlineEncodingIndex(entry);
    if (iei == -1) {
      return null;
    }
    int mid = OptEncodedCallSiteTree.getMethodID(iei, inlineEncoding);
    return (NormalMethod) MemberReference.getMemberRef(mid).asMethodReference().getResolvedMember();
  }

  /**
   * Return the inlining encoding index for the machine instruction offset.
   *
   * @param MCOffset the machine code offset of interest
   * @return -1 if unknown.
   */
  @Uninterruptible
  public int getInlineEncodingForMCOffset(Offset MCOffset) {
    int entry = findMCEntry(MCOffset);
    if (entry == -1) {
      return -1;
    }
    return getInlineEncodingIndex(entry);
  }

  /**
   *  This method searches for the GC map corresponding to the
   *  passed machine code offset.
   *  If no map is present, an error has occurred and OptGCMap.ERROR
   *  is returned.
   *
   *  @param MCOffset the machine code offset to look for
   *  @return the GC map index or OptGCMap.ERROR
   */
  @Uninterruptible
  public int findGCMapIndex(Offset MCOffset) {
    int entry = findMCEntry(MCOffset);
    if (entry == -1) return OptGCMap.ERROR;
    return getGCMapIndex(entry);
  }

  /**
   * @return an arraylist of CallSite objects representing all non-inlined
   *         callsites in the method. Returns null if there are no such callsites.
   */
  public ArrayList<CallSite> getNonInlinedCallSites() {
    ArrayList<CallSite> ans = null;
    if (MCInformation == null) return ans;
    for (int entry = 0; entry < MCInformation.length;) {
      int callInfo = getCallInfo(entry);
      if (callInfo == IS_UNGUARDED_CALL) {
        int bcIndex = getBytecodeIndex(entry);
        if (bcIndex != -1) {
          int iei = getInlineEncodingIndex(entry);
          if (iei != -1) {
            int mid = OptEncodedCallSiteTree.getMethodID(iei, inlineEncoding);
            RVMMethod caller = MemberReference.getMemberRef(mid).asMethodReference().peekResolvedMethod();
            if (caller != null) {
              if (ans == null) ans = new ArrayList<CallSite>();
              ans.add(new CallSite(caller, bcIndex));
            }
          }
        }
      }
      entry = nextEntry(entry);
    }
    return ans;
  }

  /**
   * This method searches the machine code maps and determines if
   * the given call edge is definitely inlined into the method.
   * NOTE: This current implementation may return false even if the
   * edge actually was inlined.  This happens when no GC point occurs within
   * the inlined body.  This is less than ideal; we need to fix this at some point.
   * @param caller caller RVMMethod
   * @param bcIndex bytecode index of the caller method
   * @param callee callee RVMMethod
   * @return {@code true} if the call edge is <em>definitely</em> inlined in this compiled method.
   */
  public boolean hasInlinedEdge(RVMMethod caller, int bcIndex, RVMMethod callee) {
    if (MCInformation == null) return false;
    if (inlineEncoding == null) return false;
    return OptEncodedCallSiteTree.edgePresent(caller.getId(), bcIndex, callee.getId(), inlineEncoding);
  }

  /**
   * Returns the GC map information for the GC map information entry passed
   * @param  index     GCmap entry
   */
  @Uninterruptible
  public int gcMapInformation(int index) {
    return OptGCMap.gcMapInformation(index, gcMaps);
  }

  /**
   * Determines if the register map information for the entry passed is true
   * @param  entry            map entry
   * @param  registerNumber   the register number
   */
  @Uninterruptible
  public boolean registerIsSet(int entry, int registerNumber) {
    return OptGCMap.registerIsSet(entry, registerNumber, gcMaps);
  }

  /**
   * @return the next (relative) location or -1 for no more locations
   */
  @Uninterruptible
  public int nextLocation(int currentIndex) {
    return OptGCMap.nextLocation(currentIndex, gcMaps);
  }

  ///////////////////////////////////////
  // Implementation
  ///////////////////////////////////////

  /**
   * Do a binary search of the machine code maps to find the index
   * in MCInformation where the entry for the argument machine code
   * offset starts. Will return -1 if the entry doesn't exist.
   *
   * @param MCOffset the machine code offset of interest
   */
  @Uninterruptible
  private int findMCEntry(Offset MCOffset) {
    // Given a machine code instruction MCOffset, find the corresponding entry
    if (MCInformation == null) return -1;
    if (MCInformation.length == 0) return -1;

    int left = 0;
    int right = MCInformation.length - 1;
    while (left <= right) {
      int middle = (left + right) >> 1;         // take the average
      while ((MCInformation[middle] & START_OF_ENTRY) != START_OF_ENTRY) {
        // if necessary, step backwards to beginning of entry.
        middle--;
      }
      Offset offset = Offset.fromIntSignExtend(getMCOffset(middle));
      if (MCOffset.EQ(offset)) {
        return middle;
      } else if (MCOffset.sGT(offset)) {
        // middle is too small, shift interval to the right
        left = middle + 1;
        if (left >= MCInformation.length) return -1;
        while ((MCInformation[left] & START_OF_ENTRY) != START_OF_ENTRY) {
          // if necessary, step forward to find next entry, but not passed end
          // Need to do this to avoid finding middle again
          left++;
          if (left >= MCInformation.length) {
            return -1;
          }
        }
      } else {
        // middle is too small, shift interval to the left
        right = middle - 1;
        // Note no need to adjust as, we won't chance finding middle again
      }
    }
    return -1;
  }

  private int nextEntry(int entry) {
    if (isBigEntry(entry)) return entry + SIZEOF_BIG_ENTRY;
    if (isHugeEntry(entry)) return entry + SIZEOF_HUGE_ENTRY;
    return entry + SIZEOF_ENTRY;
  }

  ////////////////////////////////////////////
  // Create the map (at compile time)
  ////////////////////////////////////////////

  /**
   *  This method walks the IR map, and for each entry it creates
   *  the machine code mapping information for the entry.
   *  It is called during the compilation of the method, not at GC time.
   *  @param irMap  the irmap to translate from
   *  @param DUMP_MAPS dump while we work
   */
  private static OptMachineCodeMap generateMCInformation(GCIRMap irMap, boolean DUMP_MAPS) {
    CallSiteTree inliningMap = new CallSiteTree();
    int numEntries = 0;

    // (1) Count how many entries we are going to have and
    //     construct and encode the inlining information for those entries.
    for (GCIRMapElement irMapElem : irMap) {
      numEntries++;
      Instruction instr = irMapElem.getInstruction();
      if (instr.position == null && instr.bcIndex != INSTRUMENTATION_BCI) {
        if (MIR_Call.conforms(instr) && MIR_Call.hasMethod(instr)) {
          throw new OptimizingCompilerException("position required for all call instructions " + instr);
        }
      } else {
        inliningMap.addLocation(instr.position);
      }
    }

    if (numEntries == 0) return emptyMachineCodeMap; // if no entries, then we are done.

    int[] inlineEncoding = OptEncodedCallSiteTree.getEncoding(inliningMap);

    // (2) Encode the primary machine code mapping information and the GCMaps.
    OptGCMap gcMapBuilder = new OptGCMap();
    int[] tmpMC = new int[numEntries * SIZEOF_HUGE_ENTRY];
    int lastMCInfoEntry = 0;
    for (GCIRMapElement irMapElem : irMap) {
      Instruction instr = irMapElem.getInstruction();
      if (DUMP_MAPS) VM.sysWrite("IR Map for " + instr + "\n\t" + irMapElem);

      // retrieve the machine code offset (in bytes) from the instruction,
      int mco = instr.getmcOffset();
      if (mco < 0) {
        VM.sysWrite("Negative machine code MCOffset found:" + mco);
        Instruction i = irMapElem.getInstruction();
        VM.sysWrite(i.bcIndex + ", " + i + ", " + i.getmcOffset() + "\n");
        throw new OptimizingCompilerException("Negative machine code MCOffset found");
      }
      // create GC map and get GCI
      int gci = gcMapBuilder.generateGCMapEntry(irMapElem);
      // get bci information
      int bci = instr.getBytecodeIndex();
      if (bci < 0) {
        if (bci == UNKNOWN_BCI && MIR_Call.conforms(instr) && MIR_Call.hasMethod(instr)) {
          throw new OptimizingCompilerException("valid bytecode index required for all calls " + instr);
        }
        bci = -1;
      }
      // get index into inline encoding
      int iei = -1;
      if (instr.position != null) {
        iei = inliningMap.find(instr.position).encodedOffset;
      }
      // set the call info
      int cm = 0;
      if (MIR_Call.conforms(instr)) {
        MethodOperand mo = MIR_Call.getMethod(instr);
        if (mo != null && mo.isGuardedInlineOffBranch()) {
          cm = IS_GUARDED_CALL;
        } else {
          cm = IS_UNGUARDED_CALL;
        }
      }

      // Encode this entry into MCInformation
      if (bci < INVALID_BCI && iei < INVALID_IEI && gci < INVALID_GCI && mco < (OFFSET_MASK >>> OFFSET_SHIFT)) {
        // use a small entry
        if (bci == -1) bci = INVALID_BCI;
        if (iei == -1) iei = INVALID_IEI;
        if (gci == -1) gci = INVALID_GCI;
        if (VM.VerifyAssertions) {
          VM._assert((cm & (CALL_MASK >>> CALL_SHIFT)) == cm);
          VM._assert((bci & (BCI_MASK >>> BCI_SHIFT)) == bci);
          VM._assert((iei & (IEI_MASK >>> IEI_SHIFT)) == iei);
          VM._assert((gci & (GCI_MASK >>> GCI_SHIFT)) == gci);
          VM._assert((mco & (OFFSET_MASK >>> OFFSET_SHIFT)) == mco);
        }
        int t = START_OF_ENTRY;
        t |= (cm << CALL_SHIFT);
        t |= (bci << BCI_SHIFT);
        t |= (iei << IEI_SHIFT);
        t |= (gci << GCI_SHIFT);
        t |= (mco << OFFSET_SHIFT);
        tmpMC[lastMCInfoEntry++] = t;
      } else if (bci < BIG_INVALID_BCI &&
                 iei < BIG_INVALID_IEI &&
                 gci < BIG_INVALID_GCI &&
                 mco < (BIG_OFFSET_MASK >>> BIG_OFFSET_SHIFT)) {
        // use a big entry
        if (bci == -1) bci = BIG_INVALID_BCI;
        if (iei == -1) iei = BIG_INVALID_IEI;
        if (gci == -1) gci = BIG_INVALID_GCI;
        if (VM.VerifyAssertions) {
          VM._assert((cm & (BIG_CALL_MASK >>> BIG_CALL_SHIFT)) == cm);
          VM._assert((bci & (BIG_BCI_MASK >>> BIG_BCI_SHIFT)) == bci);
          VM._assert((iei & (BIG_IEI_MASK >>> BIG_IEI_SHIFT)) == iei);
          VM._assert((gci & (BIG_GCI_MASK >>> BIG_GCI_SHIFT)) == gci);
          VM._assert((mco & (BIG_OFFSET_MASK >>> BIG_OFFSET_SHIFT)) == mco);
        }
        int startIdx = lastMCInfoEntry;
        tmpMC[startIdx] = START_OF_BIG_ENTRY;
        tmpMC[startIdx + BIG_CALL_IDX_ADJ] |= (cm << BIG_CALL_SHIFT);
        tmpMC[startIdx + BIG_BCI_IDX_ADJ] |= (bci << BIG_BCI_SHIFT);
        tmpMC[startIdx + BIG_OFFSET_IDX_ADJ] |= (mco << BIG_OFFSET_SHIFT);
        tmpMC[startIdx + BIG_GCI_IDX_ADJ] |= (gci << BIG_GCI_SHIFT);
        tmpMC[startIdx + BIG_IEI_IDX_ADJ] |= (iei << BIG_IEI_SHIFT);
        lastMCInfoEntry += SIZEOF_BIG_ENTRY;
      } else {
        // use a huge entry
        if (bci == -1) bci = HUGE_INVALID_BCI;
        if (iei == -1) iei = HUGE_INVALID_IEI;
        if (gci == -1) gci = HUGE_INVALID_GCI;
        if (VM.VerifyAssertions) {
          VM._assert((cm & (HUGE_CALL_MASK >>> HUGE_CALL_SHIFT)) == cm);
          VM._assert((bci & (HUGE_BCI_MASK >>> HUGE_BCI_SHIFT)) == bci);
          VM._assert((iei & (HUGE_IEI_MASK >>> HUGE_IEI_SHIFT)) == iei);
          VM._assert((gci & (HUGE_GCI_MASK >>> HUGE_GCI_SHIFT)) == gci);
          VM._assert((mco & (HUGE_OFFSET_MASK >>> HUGE_OFFSET_SHIFT)) == mco);
        }
        int startIdx = lastMCInfoEntry;
        tmpMC[startIdx] = START_OF_HUGE_ENTRY;
        tmpMC[startIdx + HUGE_CALL_IDX_ADJ] |= (cm << HUGE_CALL_SHIFT);
        tmpMC[startIdx + HUGE_BCI_IDX_ADJ] |= (bci << HUGE_BCI_SHIFT);
        tmpMC[startIdx + HUGE_OFFSET_IDX_ADJ] |= (mco << HUGE_OFFSET_SHIFT);
        tmpMC[startIdx + HUGE_GCI_IDX_ADJ] |= (gci << HUGE_GCI_SHIFT);
        tmpMC[startIdx + HUGE_IEI_IDX_ADJ] |= (iei << HUGE_IEI_SHIFT);
        lastMCInfoEntry += SIZEOF_HUGE_ENTRY;
      }
    }
    int[] mcInformation = new int[lastMCInfoEntry];
    System.arraycopy(tmpMC, 0, mcInformation, 0, mcInformation.length);
    int[] gcMaps = gcMapBuilder.finish();

    return new OptMachineCodeMap(mcInformation, gcMaps, inlineEncoding);
  }

  ////////////////////////////////////////////
  //  Accessors
  //  NB: The accessors take an entry number, which is defined to
  //      be the index of word I of the MCInformation entry
  ////////////////////////////////////////////
  /**
   * Returns the MCOffset for the entry passed
   * @param  entry the index of the start of the entry
   * @return the MCOffset for this entry
   */
  @Uninterruptible
  private int getMCOffset(int entry) {
    if (isBigEntry(entry)) {
      int t = MCInformation[entry + BIG_OFFSET_IDX_ADJ];
      return (t & BIG_OFFSET_MASK) >>> BIG_OFFSET_SHIFT;
    } else if (isHugeEntry(entry)) {
      int t = MCInformation[entry + HUGE_OFFSET_IDX_ADJ];
      return (t & HUGE_OFFSET_MASK) >>> HUGE_OFFSET_SHIFT;
    } else {
      int t = MCInformation[entry];
      return (t & OFFSET_MASK) >>> OFFSET_SHIFT;
    }
  }

  /**
   * Returns the GC map index for the entry passed
   * @param   entry the index of the start of the entry
   * @return the GC map entry index for this entry (or -1 if none)
   */
  @Uninterruptible
  private int getGCMapIndex(int entry) {
    if (isBigEntry(entry)) {
      int t = MCInformation[entry + BIG_GCI_IDX_ADJ];
      int gci = (t & BIG_GCI_MASK) >>> BIG_GCI_SHIFT;
      if (gci == BIG_INVALID_GCI) return -1;
      return gci;
    } else if (isHugeEntry(entry)) {
      int t = MCInformation[entry + HUGE_GCI_IDX_ADJ];
      int gci = (t & HUGE_GCI_MASK) >>> HUGE_GCI_SHIFT;
      if (gci == HUGE_INVALID_GCI) return -1;
      return gci;
    } else {
      int t = MCInformation[entry];
      int gci = (t & GCI_MASK) >>> GCI_SHIFT;
      if (gci == INVALID_GCI) return -1;
      return gci;
    }
  }

  /**
   * Returns the Bytecode index for the entry passed
   * @param entry the index of the start of the entry
   * @return the bytecode index for this entry (-1 if unknown)
   */
  @Uninterruptible
  private int getBytecodeIndex(int entry) {
    if (isBigEntry(entry)) {
      int t = MCInformation[entry + BIG_BCI_IDX_ADJ];
      int bci = (t & BIG_BCI_MASK) >>> BIG_BCI_SHIFT;
      if (bci == BIG_INVALID_BCI) return -1;
      return bci;
    } else if (isHugeEntry(entry)) {
      int t = MCInformation[entry + HUGE_BCI_IDX_ADJ];
      int bci = (t & HUGE_BCI_MASK) >>> HUGE_BCI_SHIFT;
      if (bci == HUGE_INVALID_BCI) return -1;
      return bci;
    } else {
      int t = MCInformation[entry];
      int bci = (t & BCI_MASK) >>> BCI_SHIFT;
      if (bci == INVALID_BCI) return -1;
      return bci;
    }
  }

  /**
   * Returns the inline encoding index for the entry passed.
   * @param entry the index of the start of the entry
   * @return the inline encoding index for this entry (-1 if unknown)
   */
  @Uninterruptible
  private int getInlineEncodingIndex(int entry) {
    if (isBigEntry(entry)) {
      int t = MCInformation[entry + BIG_IEI_IDX_ADJ];
      int iei = (t & BIG_IEI_MASK) >>> BIG_IEI_SHIFT;
      if (iei == BIG_INVALID_IEI) return -1;
      return iei;
    } else if (isHugeEntry(entry)) {
      int t = MCInformation[entry + HUGE_IEI_IDX_ADJ];
      int iei = (t & HUGE_IEI_MASK) >>> HUGE_IEI_SHIFT;
      if (iei == HUGE_INVALID_IEI) return -1;
      return iei;
    } else {
      int t = MCInformation[entry];
      int iei = (t & IEI_MASK) >>> IEI_SHIFT;
      if (iei == INVALID_IEI) return -1;
      return iei;
    }
  }

  /**
   * Returns the call info for the entry passed.
   * @param entry the index of the start of the entry
   * @return the call info for this entry
   */
  @Uninterruptible
  private int getCallInfo(int entry) {
    if (isBigEntry(entry)) {
      int t = MCInformation[entry + BIG_CALL_IDX_ADJ];
      return (t & BIG_CALL_MASK) >>> BIG_CALL_SHIFT;
    } else if (isHugeEntry(entry)) {
      int t = MCInformation[entry + HUGE_CALL_IDX_ADJ];
      return (t & HUGE_CALL_MASK) >>> HUGE_CALL_SHIFT;
    } else {
      int t = MCInformation[entry];
      return (t & CALL_MASK) >>> CALL_SHIFT;
    }
  }

  /**
   * Is the entry a big entry?
   */
  @Uninterruptible
  @Inline
  private boolean isBigEntry(int entry) {
    if (VM.VerifyAssertions) {
      VM._assert((MCInformation[entry] & START_OF_ENTRY) == START_OF_ENTRY);
    }
    return (MCInformation[entry] & START_BITS) == START_OF_BIG_ENTRY;
  }

  /**
   * Is the entry a big entry?
   */
  @Uninterruptible
  @Inline
  private boolean isHugeEntry(int entry) {
    if (VM.VerifyAssertions) {
      VM._assert((MCInformation[entry] & START_OF_ENTRY) == START_OF_ENTRY);
    }
    return (MCInformation[entry] & START_BITS) == START_OF_HUGE_ENTRY;
  }

  ////////////////////////////////////////////
  //  Debugging
  ////////////////////////////////////////////

  public void dumpMCInformation(boolean DUMP_MAPS) {
    if (DUMP_MAPS) {
      VM.sysWrite("  Dumping the MCInformation\n");
      if (MCInformation == null) return;
      for (int idx = 0; idx < MCInformation.length;) {
        printMCInformationEntry(idx, DUMP_MAPS);
        idx = nextEntry(idx);
      }
    }
  }

  /**
   * Prints the MCInformation for this entry
   * @param entry  the entry to print
   */
  private void printMCInformationEntry(int entry, boolean DUMP_MAPS) {
    if (DUMP_MAPS) {
      String sep = "\tMC: ";
      if (isBigEntry(entry)) sep = "B\tMC: ";
      if (isHugeEntry(entry)) sep = "H\tMC: ";
      VM.sysWrite(entry + sep + getMCOffset(entry));
      int bci = getBytecodeIndex(entry);
      if (bci != -1) {
        VM.sysWrite("\n\tBCI: " + bci);
      }
      int iei = getInlineEncodingIndex(entry);
      if (iei != -1) {
        VM.sysWrite("\n\tIEI: " + iei);
      }
      boolean first = true;
      while (iei >= 0) {
        int mid = OptEncodedCallSiteTree.getMethodID(iei, inlineEncoding);
        RVMMethod meth = MemberReference.getMemberRef(mid).asMethodReference().getResolvedMember();
        if (first) {
          first = false;
          VM.sysWrite("\n\tIn method    " + meth + " at bytecode " + bci);
        } else {
          VM.sysWrite("\n\tInlined into " + meth + " at bytecode " + bci);
        }
        if (iei > 0) {
          bci = OptEncodedCallSiteTree.getByteCodeOffset(iei, inlineEncoding);
        }
        iei = OptEncodedCallSiteTree.getParent(iei, inlineEncoding);
      }
      if (getGCMapIndex(entry) != OptGCMap.NO_MAP_ENTRY) {
        VM.sysWrite("\n\tGC Map Idx: " + getGCMapIndex(entry) + " ");
        OptGCMap.dumpMap(getGCMapIndex(entry), gcMaps);
      } else {
        VM.sysWrite("\n\tno GC map");
      }
      VM.sysWrite("\n");
    }
  }

  /**
   * Gather cumulative stats about the space consumed by maps.
   * @param method
   * @param mapSize
   * @param machineCodeSize
   * @param DUMP_MAP_SIZES
   */
  private void recordStats(RVMMethod method, int mapSize, int machineCodeSize, boolean DUMP_MAP_SIZES) {
    if (DUMP_MAP_SIZES) {
      double mapMCPercent = (double) mapSize / machineCodeSize;
      VM.sysWrite(method);
      VM.sysWrite(" map is " + (int) (mapMCPercent * 100) + "% (" + mapSize + "/" + machineCodeSize + ") of MC.\n");
      totalMCSize += machineCodeSize;
      totalMapSize += mapSize;
      double MCPct = (double) totalMapSize / totalMCSize;
      VM.sysWrite("  Cumulative maps are now " +
                  (int) (MCPct * 100) +
                  "% (" +
                  totalMapSize +
                  "/" +
                  totalMCSize +
                  ") of MC.\n");
    }
  }

  /**
   * Total bytes of machine code maps
   */
  int size() {
    int size = TYPE.peekType().asClass().getInstanceSize();
    if (MCInformation != null) size += RVMArray.IntArray.getInstanceSize(MCInformation.length);
    if (inlineEncoding != null) size += RVMArray.IntArray.getInstanceSize(inlineEncoding.length);
    if (gcMaps != null) size += RVMArray.IntArray.getInstanceSize(gcMaps.length);
    return size;
  }

  ////////////////////////////////////////////
  //
  //  Encoding constants and backing data.
  //
  ////////////////////////////////////////////
  // An entry contains the following data:
  //   o: a machine code offset (in bytes)
  //   g: an index into the GC maps array
  //   b: bytecode index of the instruction
  //   i: index into the inline encoding.
  //   c: bits to encode one of three possibilites
  //      (a) the instruction is not a call
  //      (b) the instruction is a "normal" call
  //      (c) the instruction is a call in the off-branch
  //          of a guarded inline.
  //   U indicates an unused bit; its value is undefined.
  //
  // We support three entry formats as defined below
  //
  private static final int START_OF_ENTRY = 0x80000000;
  private static final int START_OF_BIG_ENTRY = 0xc0000000;
  private static final int START_OF_HUGE_ENTRY = 0xe0000000;
  private static final int START_BITS = 0xe0000000;

  // A small entry is 1 int used as follows:
  // 10cc bbbb bbii iiii iggg ggoo oooo oooo
  private static final int CALL_MASK = 0x30000000;
  private static final int CALL_SHIFT = 28;
  private static final int BCI_MASK = 0x0fc00000;
  private static final int BCI_SHIFT = 22;
  private static final int IEI_MASK = 0x003f8000;
  private static final int IEI_SHIFT = 15;
  private static final int GCI_MASK = 0x00007c00;
  private static final int GCI_SHIFT = 10;
  private static final int OFFSET_MASK = 0x000003ff;
  private static final int OFFSET_SHIFT = 0;
  private static final int INVALID_GCI = (GCI_MASK >>> GCI_SHIFT);
  private static final int INVALID_BCI = (BCI_MASK >>> BCI_SHIFT);
  private static final int INVALID_IEI = (IEI_MASK >>> IEI_SHIFT);
  private static final int SIZEOF_ENTRY = 1;

  // A big entry is 2 ints used as follows:
  // 110c cbbb bbbb bbbb biii iiii iiii iiii
  // 0ggg gggg gggg ggoo oooo oooo oooo oooo
  private static final int BIG_CALL_MASK = 0x18000000;
  private static final int BIG_CALL_SHIFT = 27;
  private static final int BIG_CALL_IDX_ADJ = 0;
  private static final int BIG_BCI_MASK = 0x07ff8000;
  private static final int BIG_BCI_SHIFT = 15;
  private static final int BIG_BCI_IDX_ADJ = 0;
  private static final int BIG_IEI_MASK = 0x00007fff;
  private static final int BIG_IEI_SHIFT = 0;
  private static final int BIG_IEI_IDX_ADJ = 0;
  private static final int BIG_GCI_MASK = 0x7ffc0000;
  private static final int BIG_GCI_SHIFT = 18;
  private static final int BIG_GCI_IDX_ADJ = 1;
  private static final int BIG_OFFSET_MASK = 0x0003ffff;
  private static final int BIG_OFFSET_SHIFT = 0;
  private static final int BIG_OFFSET_IDX_ADJ = 1;
  private static final int BIG_INVALID_GCI = (BIG_GCI_MASK >>> BIG_GCI_SHIFT);
  private static final int BIG_INVALID_BCI = (BIG_BCI_MASK >>> BIG_BCI_SHIFT);
  private static final int BIG_INVALID_IEI = (BIG_IEI_MASK >>> BIG_IEI_SHIFT);
  private static final int SIZEOF_BIG_ENTRY = 2;

  // A huge entry is 4 ints used as follows:
  // 111c cUUU UUUU UUUU bbbb bbbb bbbb bbbb
  // 0iii iiii iiii iiii iiii iiii iiii iiii
  // 0ggg gggg gggg gggg gggg gggg gggg gggg
  // 0ooo oooo oooo oooo oooo oooo oooo oooo
  private static final int HUGE_CALL_MASK = 0x18000000;
  private static final int HUGE_CALL_SHIFT = 27;
  private static final int HUGE_CALL_IDX_ADJ = 0;
  private static final int HUGE_BCI_MASK = 0x0000ffff;
  private static final int HUGE_BCI_SHIFT = 0;
  private static final int HUGE_BCI_IDX_ADJ = 0;
  private static final int HUGE_IEI_MASK = 0x7fffffff;
  private static final int HUGE_IEI_SHIFT = 0;
  private static final int HUGE_IEI_IDX_ADJ = 1;
  private static final int HUGE_GCI_MASK = 0x7fffffff;
  private static final int HUGE_GCI_SHIFT = 0;
  private static final int HUGE_GCI_IDX_ADJ = 2;
  private static final int HUGE_OFFSET_MASK = 0x7fffffff;
  private static final int HUGE_OFFSET_SHIFT = 0;
  private static final int HUGE_OFFSET_IDX_ADJ = 3;
  private static final int HUGE_INVALID_GCI = (HUGE_GCI_MASK >>> HUGE_GCI_SHIFT);
  private static final int HUGE_INVALID_BCI = (HUGE_BCI_MASK >>> HUGE_BCI_SHIFT);
  private static final int HUGE_INVALID_IEI = (HUGE_IEI_MASK >>> HUGE_IEI_SHIFT);
  private static final int SIZEOF_HUGE_ENTRY = 4;

  // bit patterns for cc portion of machine code map */
  private static final int IS_UNGUARDED_CALL = 0x1;
  private static final int IS_GUARDED_CALL = 0x3;

  /**
   * Hold entries as defined by the constants above.
   */
  private final int[] MCInformation;
  /**
   * array of GC maps as defined by OptGCMap
   */
  private final int[] gcMaps;
  /**
   * encoded data as defined by OptEncodedCallSiteTree.
   */
  public final int[] inlineEncoding;
  /**
   * Running totals for the size of machine code and maps
   */
  private static int totalMCSize = 0;
  private static int totalMapSize = 0;
  /**
   * A machine code map when no information is present
   */
  private static final OptMachineCodeMap emptyMachineCodeMap = new OptMachineCodeMap();

  private static final TypeReference TYPE = TypeReference.findOrCreate(OptMachineCodeMap.class);
}
