/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;

/**
 * A class that encapsulates mapping information about generated machine code.
 * Since there will be an instance of this class with every VM_OptCompiledMethod,
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
 *
 *  Note: This file contains two types of methods
 *         1) methods called during compilation to create the maps
 *         2) methods called at GC time (no allocation allowed!)
 *
 * @author Julian Dolby
 * @author Stephen Fink
 * @author Dave Grove
 * @author Michael Hind
 * @author Mauricio Serrano
 */
public final class VM_OptMachineCodeMap implements VM_Constants, 
                                                   OPT_Constants,
                                                   VM_Uninterruptible {
  
  /**
   * Constructor, called during compilation
   * @param ir   the ir object for this method
   * @param machineCodeSize the number of machine code instructions generated.
   */
  VM_OptMachineCodeMap(OPT_IR ir, int machineCodeSize) {
    if (DUMP_MAPS) {
      VM.sysWrite("Creating final machine code map for " + ir.method + "\n");
    }
    
    // create all machine code maps
    generateMCInformation(ir.MIRInfo.gcIRMap);

    if (DUMP_MAP_SIZES) {
      recordStats(ir.method, size(), machineCodeSize << LG_INSTRUCTION_WIDTH);
    }

    if (DUMP_MAPS) {
      VM.sysWrite("Final Machine code information:\n");
      dumpMCInformation();
      for (OPT_Instruction i = ir.firstInstructionInCodeOrder(); 
           i != null; 
           i = i.nextInstructionInCodeOrder())
        VM.sysWrite(i.getmcOffset() + "\t" + i);
    }
  }

  /**
   * Get the bytecode index for a machine instruction offset.
   * 
   * @param MCOffset the machine code offset of interest
   * @return -1 if unknown.
   */
  public final int getBytecodeIndexForMCOffset(int MCOffset) {
    int entry = findMCEntry(MCOffset);
    if (entry == -1)
      return  -1;
    return getBytecodeIndex(entry);
  }

  /**
   * Get the VM_Method for a machine instruction offset.
   * This method is the source method that the instruction came from.
   * 
   * @param MCOffset the machine code offset of interest
   * @return null if unknown
   */
  public final VM_NormalMethod getMethodForMCOffset(int MCOffset) {
    int entry = findMCEntry(MCOffset);
    if (entry == -1)
      return  null;
    int iei = getInlineEncodingIndex(entry);
    if (iei == -1)
      return  null;
    int mid = VM_OptEncodedCallSiteTree.getMethodID(iei, inlineEncoding);
    return (VM_NormalMethod)VM_MemberReference.getMemberRef(mid).asMethodReference().getResolvedMember();
  }

  /**
   * Return the inlining encoding index for the machine instruction offset.
   * 
   * @param MCOffset the machine code offset of interest
   * @return -1 if unknown.
   */
  public final int getInlineEncodingForMCOffset(int MCOffset) {
    int entry = findMCEntry(MCOffset);
    if (entry == -1)
      return  -1;
    return getInlineEncodingIndex(entry);
  }

  /**
   *  This method searches for the GC map corresponding to the
   *  passed machine code offset.  
   *  If no map is present, an error has occurred and VM_OptGCMap.ERROR
   *  is returned.
   *
   *  @param MCOFfset the machine code offset to look for
   *  @return         the GC map index or VM_OptGCMap.ERROR
   */
  public int findGCMapIndex(int MCOffset) {
    int entry = findMCEntry(MCOffset);
    if (entry == -1) return VM_OptGCMap.ERROR;
    return getGCMapIndex(entry);
  }

  /**
   * This method searches the machine code maps and determines if
   * there is a call instruction in the compiled code that corresponds to
   * a source level call of <caller, bytecodeIndex>.
   * It only returns true when the callsite is defintely present and
   * is not the off-branch of a guarded inlining.
   * 
   * @param caller  the source-level caller method
   * @param bcIndex the bci of the source-level call
   */
  public boolean callsitePresent(VM_Method caller, 
                                 int bcIndex) {
    for (int entry = 0; entry < MCInformation.length;) {
      if (getBytecodeIndex(entry) == bcIndex) {         // bytecode matches
        int iei = getInlineEncodingIndex(entry);
        if (iei != -1) {
          int mid = VM_OptEncodedCallSiteTree.getMethodID(iei, inlineEncoding);
          if (mid == caller.getId()) {        // caller matches
            int callInfo = getCallInfo(entry);
            if (callInfo == IS_UNGUARDED_CALL) return true;
          }
        }
      }
      entry += isBigEntry(entry) ? 2 : 1;
    }
    return false;
  }

  /**
   * Returns the GC map information for the GC map information entry passed
   * @param  entry     GCmap entry
   */
  public final int gcMapInformation(int index) {
    return VM_OptGCMap.gcMapInformation(index, gcMaps);
  }

  /**
   * Determines if the register map information for the entry passed is true
   * @param  entry            map entry
   * @param  registerNumber   the register number
   */
  public final boolean registerIsSet(int entry, int registerNumber) {
    return VM_OptGCMap.registerIsSet(entry, registerNumber, gcMaps);
  }

  /**
   * @return the next (relative) location or -1 for no more locations
   */
  public final int nextLocation(int currentIndex) {
    return VM_OptGCMap.nextLocation(currentIndex, gcMaps);
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
  private int findMCEntry(int MCOffset) {
    // Given a machine code instruction MCOffset, find the corresponding entry
    if (MCInformation == null) return -1;
    int left = 0;
    int right = MCInformation.length-1;
    while (left <= right) {
      int middle = (left + right) >> 1;         // take the average
      while ((MCInformation[middle] & START_OF_ENTRY) != START_OF_ENTRY) {
        // if necessary, step backwards to beginning of entry.
        middle--;
      }
      int offset = getMCOffset(middle);
      if (MCOffset == offset) {
        return middle;
      } else if (MCOffset > offset) {
        // middle is too small, shift interval to the right
        left = middle + 1; 
        if (left >= MCInformation.length) return -1;
        while ((MCInformation[left] & START_OF_ENTRY) != START_OF_ENTRY) {
          // if necessary, step forward to find next entry, but not passed end
          // Need to do this to avoid finding middle again
          left++;
          if (left >= MCInformation.length)
            return -1;
        }
      } else {
        // middle is too small, shift interval to the left
        right = middle - 1;
        // Note no need to adjust as, we won't chance finding middle again
      }
    }
    return  -1;
  }

  ////////////////////////////////////////////
  // Create the map (at compile time)
  ////////////////////////////////////////////

  /**
   *  This method walks the IR map, and for each entry it creates
   *  the machine code mapping information for the entry.
   *  It is called during the compilation of the method, not at GC time.
   *  @param irMap  the irmap to translate from
   *  @param gcMap  the VM_OptGCMap instance that is building the encoded GCMap
   */
  private void generateMCInformation(OPT_GCIRMap irMap) throws VM_PragmaInterruptible {
    OPT_CallSiteTree inliningMap = new OPT_CallSiteTree();
    int numEntries = 0;
    
    // (1) Count how many entries we are going to have and
    //     construct and encode the inlining information for those entries.
    for (OPT_GCIRMapEnumerator irMapEnum = irMap.enumerator(); 
         irMapEnum.hasMoreElements(); numEntries++) {
      OPT_GCIRMapElement irMapElem = 
        (OPT_GCIRMapElement)irMapEnum.nextElement();
      OPT_Instruction instr = irMapElem.getInstruction();
      if (instr.position == null && instr.bcIndex != INSTRUMENTATION_BCI) {
        if (MIR_Call.conforms(instr) && MIR_Call.hasMethod(instr)) {
          throw new OPT_OptimizingCompilerException("position required for all call instructions "
                                                    + instr);
        }
      } else {
        inliningMap.addLocation(instr.position);
      }
    }

    if (numEntries == 0) return; // if no entries, then we are done.

    inlineEncoding = VM_OptEncodedCallSiteTree.getEncoding(inliningMap);

    // (2) Encode the primary machine code mapping information and the GCMaps.
    VM_OptGCMap gcMapBuilder = new VM_OptGCMap();
    MCInformation = new long[numEntries]; // assume no need for BIG entries
    int lastMCInfoEntry = -1;
    for (OPT_GCIRMapEnumerator irMapEnum = irMap.enumerator(); 
        irMapEnum.hasMoreElements();) {
      OPT_GCIRMapElement irMapElem = 
        (OPT_GCIRMapElement)irMapEnum.nextElement();
      OPT_Instruction instr = irMapElem.getInstruction();
      if (DUMP_MAPS) VM.sysWrite("IR Map for " + instr + "\n\t" + irMapElem);

      // retrieve the machine code offset (in bytes) from the instruction,
      int mco= instr.getmcOffset();
      if (mco < 0) {
        VM.sysWrite("Negative machine code MCOffset found:" + mco);
        OPT_Instruction i = irMapElem.getInstruction();
        VM.sysWrite(i.bcIndex+", "+i+", "+i.getmcOffset()+"\n");
        throw new OPT_OptimizingCompilerException("Negative machine code MCOffset found");
      }
      // create GC map and get GCI
      int gci = gcMapBuilder.generateGCMapEntry(irMapElem);
      // get bci information
      int bci = instr.getBytecodeIndex();
      if (bci < 0) {
        if (bci == UNKNOWN_BCI && MIR_Call.conforms(instr) && MIR_Call.hasMethod(instr)) {
          throw new OPT_OptimizingCompilerException("valid bytecode index required for all calls " + instr);
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
        OPT_MethodOperand mo = MIR_Call.getMethod(instr);
        if (mo != null && mo.isGuardedInlineOffBranch()) {
          cm = IS_GUARDED_CALL;
        } else {
          cm = IS_UNGUARDED_CALL;
        }
      }

      // Encode this entry into MCInformation
      if (bci < INVALID_BCI &&
          iei < INVALID_IEI &&
          gci < INVALID_GCI && 
          mco < (OFFSET_MASK >>> OFFSET_SHIFT)) {
        // use a normal entry
        if (bci == -1) bci = INVALID_BCI;
        if (iei == -1) iei = INVALID_IEI;
        if (gci == -1) gci = INVALID_GCI;
        if (VM.VerifyAssertions) {
          VM._assert((cm  & (CALL_MASK>>>CALL_SHIFT)) == cm);
          VM._assert((bci & (BCI_MASK>>>BCI_SHIFT)) == bci);
          VM._assert((iei & (IEI_MASK>>>IEI_SHIFT)) == iei);
          VM._assert((gci & (GCI_MASK>>>GCI_SHIFT)) == gci);
          VM._assert((mco & (OFFSET_MASK>>>OFFSET_SHIFT)) == mco);
        }
        long t = START_OF_ENTRY;
        t |= (((long)cm) << CALL_SHIFT);
        t |= (((long)bci) << BCI_SHIFT);
        t |= (((long)iei) << IEI_SHIFT);
        t |= (((long)gci) << GCI_SHIFT);
        t |= (((long)mco) << OFFSET_SHIFT);
        MCInformation[++lastMCInfoEntry] = t;
      } else {
        // use a big entry (assume this is very rare, so only make room for one)
        long[] tmp = new long[MCInformation.length+1];
        System.arraycopy(MCInformation, 0, tmp, 0, MCInformation.length);
        MCInformation = tmp;
        if (bci == -1) bci = BIG_INVALID_BCI;
        if (iei == -1) iei = BIG_INVALID_IEI;
        if (gci == -1) gci = BIG_INVALID_GCI;
        if (VM.VerifyAssertions) {
          VM._assert(BIG_CALL_IDX_ADJ == 0 &&
                    BIG_BCI_IDX_ADJ == 0 &&
                    BIG_OFFSET_IDX_ADJ == 0);
          VM._assert(BIG_GCI_IDX_ADJ == 1 &&
                    BIG_IEI_IDX_ADJ == 1);
          VM._assert((cm  & (BIG_CALL_MASK>>>BIG_CALL_SHIFT)) == cm);
          VM._assert((bci & (BIG_BCI_MASK>>>BIG_BCI_SHIFT)) == bci);
          VM._assert((iei & (BIG_IEI_MASK>>>BIG_IEI_SHIFT)) == iei);
          VM._assert((gci & (BIG_GCI_MASK>>>BIG_GCI_SHIFT)) == gci);
          VM._assert((mco & (BIG_OFFSET_MASK>>>BIG_OFFSET_SHIFT)) == mco);
        }
        // long 1
        long t = START_OF_BIG_ENTRY;
        t |= (((long)cm) << BIG_CALL_SHIFT);
        t |= (((long)bci) << BIG_BCI_SHIFT);
        t |= (((long)mco) << BIG_OFFSET_SHIFT);
        MCInformation[++lastMCInfoEntry] = t;
        // long 2 
        t = (((long)gci) << BIG_GCI_SHIFT);
        t |= (((long)iei) << BIG_IEI_SHIFT);
        MCInformation[++lastMCInfoEntry] = t;
      }  
    }
    gcMaps = gcMapBuilder.finish();
  }


  ////////////////////////////////////////////
  //  Accessors
  //  NB: The accessors take an entry number, which is defined to
  //      be the index of word I of the MCInformation entry
  ////////////////////////////////////////////
  /**
   * Returns the MCOffset for the entry passed
   * @param  entry the index of the start of the entry
   * @return       the MCOffset for this entry
   */
  private final int getMCOffset(int entry) {
    if (isBigEntry(entry)) {
      long t = MCInformation[entry+BIG_OFFSET_IDX_ADJ];
      return (int)((t & BIG_OFFSET_MASK) >>> BIG_OFFSET_SHIFT);
    } else {
      long t = MCInformation[entry];
      return (int)((t & OFFSET_MASK) >>> OFFSET_SHIFT);
    }
  }

  /**
   * Returns the GC map index for the entry passed
   * @param   entry the index of the start of the entry
   * @return        the GC map entry index for this entry (or -1 if none)
   */
  private final int getGCMapIndex(int entry) {
    if (isBigEntry(entry)) {
      long t  = MCInformation[entry+BIG_GCI_IDX_ADJ];
      int gci = (int)((t & BIG_GCI_MASK) >>> BIG_GCI_SHIFT);
      if (gci == BIG_INVALID_GCI) return -1;
      return gci;
    } else {
      long t =  MCInformation[entry];
      int gci = (int)((t & GCI_MASK) >>> GCI_SHIFT);
      if (gci == INVALID_GCI) return -1;
      return gci;
    }
  }

  /**
   * Returns the Bytecode index for the entry passed
   * @param entry the index of the start of the entry
   * @return      the bytecode index for this entry (-1 if unknown)
   */
  private final int getBytecodeIndex(int entry) { 
    if (isBigEntry(entry)) {
      long t  = MCInformation[entry+BIG_BCI_IDX_ADJ];
      int bci = (int)((t & BIG_BCI_MASK) >>> BIG_BCI_SHIFT);
      if (bci == BIG_INVALID_BCI) return -1;
      return bci;
    } else {
      long t  = MCInformation[entry];
      int bci = (int)((t & BCI_MASK) >>> BCI_SHIFT);
      if (bci == INVALID_BCI) return -1;
      return bci;
    }
  }

  /**
   * Returns the inline encoding index for the entry passed.
   * @param entry the index of the start of the entry
   * @return      the inline encoding index for this entry (-1 if unknown)
   */
  private final int getInlineEncodingIndex(int entry) {
    if (isBigEntry(entry)) {
      long t  = MCInformation[entry+BIG_IEI_IDX_ADJ];
      int iei = (int)((t & BIG_IEI_MASK) >>> BIG_IEI_SHIFT);
      if (iei == BIG_INVALID_IEI) return -1;
      return iei;
    } else {
      long t  = MCInformation[entry];
      int iei = (int)((t & IEI_MASK) >>> IEI_SHIFT);
      if (iei == INVALID_IEI) return -1;
      return iei;
    }
  }

  /**
   * Returns the call info for the entry passed.
   * @param entry the index of the start of the entry
   * @return      the call info for this entry 
   */
  private final int getCallInfo(int entry) {
    if (isBigEntry(entry)) {
      long t = MCInformation[entry+BIG_CALL_IDX_ADJ];
      return (int)((t & BIG_CALL_MASK) >>> BIG_CALL_SHIFT);
    } else {
      long t = MCInformation[entry];
      return (int)((t & CALL_MASK) >>> CALL_SHIFT);
    }
  }
  
  /**
   * Is the entry a big entry?
   */
  private final boolean isBigEntry(int entry) {
    if (VM.VerifyAssertions) {
      VM._assert((MCInformation[entry] & START_OF_ENTRY) == START_OF_ENTRY);
    }
    return (MCInformation[entry] & START_OF_BIG_ENTRY) == START_OF_BIG_ENTRY;
  }

  ////////////////////////////////////////////
  //  Debugging
  ////////////////////////////////////////////

  public void dumpMCInformation() throws VM_PragmaInterruptible {
    if (DUMP_MAPS) {
      VM.sysWrite("  Dumping the MCInformation\n");
      for (int idx = 0; idx<MCInformation.length;) {
        printMCInformationEntry(idx);
        idx += isBigEntry(idx) ? 2 : 1;
      }
    }
  }

  /**
   * Prints the MCInformation for this entry
   * @param entry  the entry to print
   */
  private final void printMCInformationEntry(int entry) throws VM_PragmaInterruptible {
    if (DUMP_MAPS) {
      VM.sysWrite(entry + "\tMC: " + getMCOffset(entry));
      int bci = getBytecodeIndex(entry);
      if (bci != -1)
        VM.sysWrite("\n\tBCI: " + bci);
      int iei = getInlineEncodingIndex(entry);
      boolean first = true;
      while (iei >= 0) {
        int mid = VM_OptEncodedCallSiteTree.getMethodID(iei, inlineEncoding);
        VM_Method meth = VM_MemberReference.getMemberRef(mid).asMethodReference().getResolvedMember();
        if (first) {
          first = false;
          VM.sysWrite("\n\tIn method    " + meth + " at bytecode " + bci);
        } else {
          VM.sysWrite("\n\tInlined into " + meth + " at bytecode " + bci);
        }
        if (iei > 0) {
          bci = VM_OptEncodedCallSiteTree.getByteCodeOffset(iei, inlineEncoding);
        }
        iei = VM_OptEncodedCallSiteTree.getParent(iei, inlineEncoding);
      }
      if (getGCMapIndex(entry) != VM_OptGCMap.NO_MAP_ENTRY) {
        VM.sysWrite("\n\tGC Map Idx: " + getGCMapIndex(entry) + " ");
        VM_OptGCMap.dumpMap(getGCMapIndex(entry), gcMaps);
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
   */
  private void recordStats(VM_Method method, int mapSize, 
                           int machineCodeSize) throws VM_PragmaInterruptible {
    if (DUMP_MAP_SIZES) {
      double mapMCPercent = (double)mapSize/machineCodeSize;
      VM.sysWrite(method);
      VM.sysWrite(" map is " + (int)(mapMCPercent*100) + "% (" 
                  + mapSize + "/" + machineCodeSize + ") of MC.\n");
      totalMCSize += machineCodeSize;
      totalMapSize += mapSize;
      double MCPct = (double)totalMapSize/totalMCSize;
      VM.sysWrite("  Cumulative maps are now " + (int)(MCPct*100) + 
                  "% (" + totalMapSize + "/" + totalMCSize + ") of MC.\n");
    }
  }

  /**
   * Total bytes used by arrays of primitives to encode the machine code map
   */
  int size() {
    int size = TYPE.peekResolvedType().asClass().getInstanceSize();
    if (MCInformation != null) size += VM_Array.LongArray.getInstanceSize(MCInformation.length);
    if (inlineEncoding != null) size += VM_Array.IntArray.getInstanceSize(inlineEncoding.length);
    if (gcMaps != null) size += VM_Array.IntArray.getInstanceSize(gcMaps.length);
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
  // We support two entry formats.
  // 
  // A normal entry is 1 long used as follows:
  //   10cc bbbb bbbb bbbb biii iiii iiii iiii gggg gggg gggg ggoo oooo oooo oooo oooo 
  //        2^18-1 = 262,143 bytes of machine code.
  //        2^14-2 =  16,383 words of gc mapping info.
  //        2^15-2 =  32,766 words of inline encoding 
  //                (supports at least 10,922 inlined methods)
  //        2^13-2 =   bytecode indices from 0...8,190
  // 
  // A large entry is two longs, used as follows:
  //   11cc UUUU UUUU UUUU bbbb bbbb bbbb bbbb Uooo oooo oooo oooo oooo oooo oooo oooo
  //   00gg gggg gggg gggg gggg gggg gggg gggg Uiii iiii iiii iiii iiii iiii iiii iiii
  // 
  private static final long START_OF_ENTRY    = 0x8000000000000000L;
  private static final long START_OF_BIG_ENTRY= 0xc000000000000000L;
  // mask, shift, and invalid  values for normal entry
  private static final long CALL_MASK         = 0x3000000000000000L;
  private static final int  CALL_SHIFT        = 60;
  private static final long BCI_MASK          = 0x0fff800000000000L;
  private static final int  BCI_SHIFT         = 47;
  private static final long IEI_MASK          = 0x00007fff00000000L;
  private static final int  IEI_SHIFT         = 32;
  private static final long GCI_MASK          = 0x00000000fffc0000L;
  private static final int  GCI_SHIFT         = 18;
  private static final long OFFSET_MASK       = 0x000000000003ffffL;
  private static final int  OFFSET_SHIFT      = 0;
  private static final int  INVALID_GCI       = (int)(GCI_MASK >>> GCI_SHIFT);
  private static final int  INVALID_BCI       = (int)(BCI_MASK >>> BCI_SHIFT);
  private static final int  INVALID_IEI       = (int)(IEI_MASK >>> IEI_SHIFT);
  // mask, shift, and invalid  values for big entry
  private static final long BIG_CALL_MASK     = 0x3000000000000000L;
  private static final int  BIG_CALL_SHIFT    = 60;
  private static final int  BIG_CALL_IDX_ADJ  = 0;
  private static final long BIG_BCI_MASK      = 0x0000ffff00000000L;
  private static final int  BIG_BCI_SHIFT     = 32;
  private static final int  BIG_BCI_IDX_ADJ   = 0;
  private static final long BIG_OFFSET_MASK   = 0x000000007fffffffL;
  private static final int  BIG_OFFSET_SHIFT  = 0;
  private static final int  BIG_OFFSET_IDX_ADJ= 0;
  private static final long BIG_GCI_MASK      = 0x7fffffff00000000L;
  private static final int  BIG_GCI_SHIFT     = 32;
  private static final int  BIG_GCI_IDX_ADJ   = 1;
  private static final long BIG_IEI_MASK      = 0x000000007fffffffL;
  private static final int  BIG_IEI_SHIFT     = 0;
  private static final int  BIG_IEI_IDX_ADJ   = 1;
  private static final int  BIG_INVALID_GCI   = (int)(BIG_GCI_MASK >>> BIG_GCI_SHIFT);
  private static final int  BIG_INVALID_BCI   = (int)(BIG_BCI_MASK >>> BIG_BCI_SHIFT);  
  private static final int  BIG_INVALID_IEI   = (int)(BIG_IEI_MASK >>> BIG_IEI_SHIFT);

  // bit patterns for cc portion of machine code map */
  private static final int  IS_UNGUARDED_CALL = 0x1;
  private static final int  IS_GUARDED_CALL   = 0x3;

  /**
   * Hold entries as defined by the constants above.
   */
  private long[] MCInformation;
  /**
   * array of GC maps as defined by VM_OptGCMap
   */ 
  private int[] gcMaps;
  /**
   * encoded data as defined by VM_OptEncodedCallSiteTree.
   */
  public int[] inlineEncoding;
  /**
   * Dump maps as methods are compiled. 
   */
  private static final boolean DUMP_MAPS = false;
  /**
   * Dump stats on map size as maps are compiled.
   */
  private static final boolean DUMP_MAP_SIZES = false;
  /**
   * Running totals for the size of machine code and maps
   */
  private static int totalMCSize = 0;
  private static int totalMapSize = 0;

  private static final VM_TypeReference TYPE = VM_TypeReference.findOrCreate(VM_SystemClassLoader.getVMClassLoader(),
                                                                             VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/opt/VM_OptMachineCodeMap;"));
}
