/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;

/**
 * class that provides stack (and local var) map for a baseline compiled method
 * GC uses the methods provided here
 * 
 * @author Anthony Cocchi
 * @modified Perry Cheng
 * @modified Dave Grove
 */
public final class VM_ReferenceMaps implements VM_BaselineConstants, VM_Uninterruptible  {

  public static final byte JSR_MASK = -128;     // byte = x'80'
  public static final byte JSR_INDEX_MASK = 0x7F;

  public static final int STARTOFFSET = 0;
  public static final int NOMORE=0;
  private static final byte OR = 1;
  private static final byte NAND = 2;
  private static final byte COPY = 3;
  private static final int BITS_PER_MAP_ELEMENT = 8;

  // statics for tracking statistics
  private static int methodcount;
  private static int mapcount;
  public  static int bytecount;
  private static int bytespermap;  // in words right now
  private static int numMapsLE8;  // number of maps less than or equal 8 in size
  private static int numMapsLE16;
  private static int maxbytesPerMap;

  // The following field is used for serialization of JSR processing 
  static VM_ProcessorLock jsrLock = new VM_ProcessorLock();

  private byte[] referenceMaps;
  private int MCSites[];
  private int bytesPerMap;
  private int mapCount;

  private int bitsPerMap;   // number of bits in each map
  private int local0Offset; // distance from frame pointer to first Local area

  // the following fields are used for jsr processing
  private int              numberUnusualMaps;
  private VM_UnusualMaps[] unusualMaps;
  private byte[]           unusualReferenceMaps;
  private int              freeMapSlot = 0;
  private VM_UnusualMaps   extraUnusualMap = null; //merged jsr ret  and callers maps
  private int              tempIndex = 0;
  private int              mergedReferenceMap = 0;       // result of jsrmerged maps - stored in referenceMaps
  private int              mergedReturnAddressMap = 0;   // result of jsrmerged maps - stored return addresses


  VM_ReferenceMaps(VM_BaselineCompiledMethod cm, int[] stackHeights) {
    VM_NormalMethod method = (VM_NormalMethod)cm.getMethod();
    // save input information and compute related data
    this.bitsPerMap   = (method.getLocalWords() + method.getOperandWords()+1); // +1 for jsr bit
    this.bytesPerMap  = ((this.bitsPerMap + 7)/8)+1 ; // calc size of individul maps
    this.local0Offset = VM_Compiler.getFirstLocalOffset(method);

    if (VM.TraceStkMaps) {
      VM.sysWrite("VM_ReferenceMaps constructor. Method name is:");
      VM.sysWrite(method.getName());
      VM.sysWrite(" -Class name is :");
      VM.sysWrite(method.getDeclaringClass().getName());
      VM.sysWrite("\n");
      VM.sysWrite(" bytesPerMap = ");
      VM.sysWrite(bytesPerMap);
      VM.sysWrite(" - bitsPerMap = ");
      VM.sysWrite(bitsPerMap);
      VM.sysWrite(" - local0Offset = ");
      VM.sysWrite(local0Offset);
      VM.sysWrite("\n");
    }

    // define the basic blocks
    VM_BuildBB buildBB = new VM_BuildBB();
    buildBB.determineTheBasicBlocks(method);

    // determine if we are going to insert edge counters for this method
    if (buildBB.basicBlocks.length > 2 &&
	VM_BaselineCompiler.options.EDGE_COUNTERS && 
	!method.getDeclaringClass().isBridgeFromNative()) {
      cm.setHasCounterArray(); // yes, we will inject counters for this method.
    }
    VM_BuildReferenceMaps buildRefMaps = new VM_BuildReferenceMaps();
    buildRefMaps.buildReferenceMaps(method, stackHeights, this, buildBB);

    if (VM.ReferenceMapsBitStatistics) {
      showReferenceMapStatistics(method);
    }
  }

  /** 
   * Given a machine code instruction offset, return an index to
   * identify the stack map closest to the offset ( but not beyond)
   *
   * Usage note: "machCodeOffset" must point to the instruction *following*
   *              the actual instruction
   * whose stack map is sought. This allows us to properly handle the case where
   * the only address we have to work with is a return address (ie. from a stackframe)
   * or an exception address (ie. from a null pointer dereference, array bounds check,
   * or divide by zero) on a machine architecture with variable length instructions.
   * In such situations we'd have no idea how far to back up the instruction pointer
   * to point to the "call site" or "exception site".
   *
   * If the located site is within the scope of a jsr subroutine
   *  the index value returned is a negative number
   */
  public int locateGCPoint(int machCodeOffset, VM_Method method)  {

    machCodeOffset = machCodeOffset - (1 << VM.LG_INSTRUCTION_WIDTH);  // this assumes that machCodeOffset points
    // to "next" instruction eg bal type instruction

    if (VM.TraceStkMaps) {
      VM.sysWrite("VM_ReferenceMaps-locateGCPoint for machine code offset = ");
      VM.sysWrite(machCodeOffset);
      VM.sysWrite("  --- in  method = ");
      VM.sysWrite(method.getName());
      VM.sysWrite("\n");
    }

    //  Scan the list of machine code addresses to find the
    //  closest site offset BEFORE the input machine code index ( offset in the code)
    int distance = 0;
    int index = 0;
    // get the first possible location
    for (int i = 0; i < mapCount; i++) {
      // get an initial non zero distance
      distance = machCodeOffset - MCSites[i];
      if (distance >= 0) {
        index = i;
        break;
      }
    }
    // scan to find any better location ie closer to the site
    for(int i = index+1; i < mapCount; i++) {
      int dist =  machCodeOffset- MCSites[i];
      if (dist < 0) continue;
      if (dist <= distance) {
        index = i;
        distance = dist;
      }
    }

    if (VM.TraceStkMaps) {
      showInfo();
      VM.sysWrite(" VM_ReferenceMaps-locateGCPoint located index  = ");
      VM.sysWrite(index);
      VM.sysWrite("  byte  = ");
      VM.sysWrite(referenceMaps[index]);
      VM.sysWrite( "\n");
      if (index - 1 >= 0) {
        VM.sysWrite(" MCSites[index-1] = "); VM.sysWrite(machCodeOffset - MCSites[index-1]); VM.sysWrite("\n");
      }
      VM.sysWrite(" MCSites[index  ] = "); VM.sysWrite(machCodeOffset - MCSites[index  ]); VM.sysWrite("\n");
      if (index + 1 < MCSites.length) {
        VM.sysWrite(" MCSites[index+1] = "); VM.sysWrite(machCodeOffset - MCSites[index+1]); VM.sysWrite("\n");
      }
    }

    // test for a site within a jsr subroutine
    if ((0x000000FF & (referenceMaps[index*bytesPerMap] &  JSR_MASK)) == (0x000000FF & JSR_MASK))  { // test for jsr map
      index = -index;                       // indicate site within a jsr to caller
      if (VM.TraceStkMaps) {
        VM.sysWrite(" VM_ReferenceMaps-locateGCPoint jsr mapid = ");
        VM.sysWrite( -index);
        VM.sysWrite( "\n");
      }
    }

    if (VM.TraceStkMaps) {
      VM.sysWrite(" VM_ReferenceMaps-locateGCPoint  machine offset = ");
      VM.sysWrite(  machCodeOffset);
      VM.sysWrite("  - return map index = ");
      VM.sysWrite( index);
      VM.sysWrite( "\n");
    }

    return index;
  }

  /**
   * @param offset offset in the reference stack frame,
   * @param siteindex index that indicates the callsite (siteindex),
   * @return return the offset where the next reference can be found.
   * @return NOMORE when no more pointers can be found
   */
  public int getNextRef(int offset, int siteindex)  {
    if (VM.TraceStkMaps) {
      VM.sysWrite("VM_ReferenceMaps-getNextRef-inputs offset = ");
      VM.sysWrite( offset);
      VM.sysWrite( " -siteindex = ");
      VM.sysWrite( siteindex);
      VM.sysWrite( "\n");
    }

    // use index to locate the gc point of interest
    int mapindex  = siteindex * bytesPerMap;
    if (bytesPerMap == 0) return 0;           // no map ie no refs

    int bitnum;
    if (offset == STARTOFFSET) {
      // this is the initial scan for the map
      int mapByteNum = mapindex;
      int startbitnumb = 1;      // start search from beginning
      bitnum = scanForNextRef(startbitnumb, mapByteNum, bitsPerMap, referenceMaps);

      if (VM.TraceStkMaps) {
        VM.sysWriteln("VM_ReferenceMaps-getNextRef-initial call bitnum = ", bitnum);
      }
    } else {
      // get bitnum and determine mapword to restart scan
      bitnum = convertOffsetToBitNum(offset);  // get the bit number

      if (VM.TraceStkMaps) {
	VM.sysWriteln("VM_ReferenceMaps-getnextref- not initial- entry offset,bitnum  = ", offset, " ", bitnum);
      }

      // scan forward from current position to next ref
      bitnum = scanForNextRef(bitnum+1, mapindex,(bitsPerMap - (bitnum - 1)),referenceMaps);

      if (VM.TraceStkMaps) {
	VM.sysWriteln("VM_ReferenceMaps-getnextref- not initial- scan returned bitnum = ", bitnum);
      }
    }      

    if (bitnum == NOMORE) {
      if (VM.TraceStkMaps) VM.sysWriteln("  NOMORE");
      return NOMORE;
    } else {
      int ans = convertBitNumToOffset(bitnum);
      if (VM.TraceStkMaps) VM.sysWriteln("  result = ", ans);
      return ans;
    }
  }

  /**
   * @param offset offset in the jsr reference map,
   * @return the offset where the next reference can be found.
   *
   * NOTE: there is only one jsr map for the entire method because it has to be
   *       be constructed a GC time and would normally require additional storage.
   *       To avoid this, the space for one map is pre-allocated and the map
   *       is built in that space. When multiple threads exist and if GC runs
   *       in multiple threads concurrently, then the MethodMap must be locked
   *       when a jsr map is being scanned.
   *       This should be a low probability event.
   *
   * Return NOMORE when no
   * more pointers can be found
   */
  public int getNextJSRRef(int offset)  {
    // user index to locate the gc point of interest
    int mapword   = mergedReferenceMap;
    if (bytesPerMap == 0) return 0;           // no map ie no refs

    int bitnum;
    if (offset == STARTOFFSET) {
      // this is the initial scan for the map
      int startbitnumb = 1;      // start search from beginning
      bitnum = scanForNextRef(startbitnumb, mapword,  bitsPerMap, unusualReferenceMaps);
      if (true || VM.TraceStkMaps) {
        VM.sysWrite("VM_ReferenceMaps-getJSRNextRef-initial call - startbitnum =", startbitnumb);
        VM.sysWrite("  mapword = ", mapword);
        VM.sysWrite(" bitspermap = ", bitsPerMap);
        VM.sysWrite("      bitnum = ", bitnum);
      }
    } else {
      // get bitnum and determine mapword to restart scan
      bitnum = convertJsrOffsetToBitNum(offset);  // get the bit number from last time 

      // scan forward from current position to next ref
      if (true || VM.TraceStkMaps) {
	VM.sysWrite("VM_ReferenceMaps.getJSRnextref - not initial- starting (offset,bitnum) = ");
	VM.sysWrite(offset);
	VM.sysWrite(", ");
	VM.sysWrite(bitnum);
      }

      bitnum = scanForNextRef(bitnum+1,mapword, (bitsPerMap - (bitnum -1)), unusualReferenceMaps);
    }

    if (bitnum == NOMORE) {
      if (VM.TraceStkMaps) VM.sysWriteln("  NOMORE");
      return NOMORE;
    } else {
      int ans =  convertJsrBitNumToOffset(bitnum);
      if (true || VM.TraceStkMaps) VM.sysWriteln("  result = ", ans);
      return ans;
    }
  }


  /**
   * Given an offset in the jsr returnAddress map,
   *   return the offset where the next returnAddress can be found.
   *
   * NOTE: there is only one jsr returnAddress map for the entire method because it has to be
   *       be constructed a GC time and would normally require additional storage.
   *       To avoid this, the space for one map is pre-allocated and the map
   *       is built in that space. When multiple threads exist and if GC runs
   *       in multiple threads concurrently, then the MethodMap must be locked
   *       when a jsr map is being scanned.
   *       This shoulkd be a low probability event.
   *
   * NOTE: return addresses are handled seperately from references because they point
   *       inside an object ( internal pointers)
   *
   * Return NOMORE when no
   * more pointers can be found
   */
  public int getNextJSRReturnAddr(int offset)  {
    // use the preallocated map to locate the current point of interest
    int mapword = mergedReturnAddressMap;
    if (bytesPerMap == 0) {
      if (VM.TraceStkMaps)
        VM.sysWriteln("VM_ReferenceMaps-getJSRNextReturnAddr-initial call no returnaddresses");
      return 0;  // no map ie no refs
    }

    int bitnum;
    if (offset == STARTOFFSET) {
      // this is the initial scan for the map
      int startbitnumb = 1;      // start search from beginning
      bitnum = scanForNextRef(startbitnumb, mapword,  bitsPerMap,  unusualReferenceMaps);
      if (VM.TraceStkMaps) {
        VM.sysWrite("VM_ReferenceMaps-getJSRNextReturnAddr-initial call startbitnum, mapword, bitspermap = ");
        VM.sysWrite(startbitnumb );
        VM.sysWrite(" , ");
        VM.sysWrite(mapword );
        VM.sysWrite(" , ");
        VM.sysWrite(bitsPerMap);
        VM.sysWrite(" \n ");
        VM.sysWrite("VM_ReferenceMaps-getJSRNextReturnAddr-initial call return bitnum = ");
        VM.sysWrite( bitnum);
        VM.sysWrite( "\n");
      }
    } else {
      // get bitnum and determine mapword to restart scan
      bitnum = convertJsrOffsetToBitNum(offset);  // get the bit number
      if (VM.TraceStkMaps) {
	VM.sysWriteln("VM_ReferenceMaps-getJSRnextReturnAddr- not initial- starting offset, starting bitnum  = ", offset, " ", bitnum);
      }

      // scan forward from current position to next ref
      bitnum = scanForNextRef(bitnum+1, mapword, (bitsPerMap - (bitnum -1)), unusualReferenceMaps);
      
      if (VM.TraceStkMaps) {
	VM.sysWriteln("VM_ReferenceMaps-getJSRnextref- not initial- scan returned bitnum = ", bitnum);
      }
    }

    if (bitnum == NOMORE) {
      if (VM.TraceStkMaps) VM.sysWriteln("  NOMORE");
      return NOMORE;
    } else {
      int ans =  convertJsrBitNumToOffset(bitnum);
      if (VM.TraceStkMaps) VM.sysWrite("VM_ReferenceMaps-getJSRNextReturnAddr-return = ", ans );
      return ans;
    }
  }


  /**
   * For debugging (used with CheckRefMaps)
   *  Note: all maps are the same size
   */ 
  int getStackDepth(int mapid)  {
    return bytesPerMap;
  }

  private static final VM_Class TYPE = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_ReferenceMaps;"), VM_SystemClassLoader.getVMClassLoader()).asClass();
  int size() {
    int size = TYPE.getInstanceSize();
    if (MCSites != null) size += VM_Array.arrayOfIntType.getInstanceSize(MCSites.length);
    if (referenceMaps != null) size += VM_Array.arrayOfByteType.getInstanceSize(referenceMaps.length);
    if (unusualReferenceMaps != null) size += VM_Type.JavaLangObjectArrayType.getInstanceSize(unusualReferenceMaps.length);
    return size;
  }

  /**
   * start setting up the reference maps for this method.
   */
  public void startNewMaps(int gcPointCount, int jsrCount, int parameterWords) throws VM_PragmaInterruptible {
    //  normal map information
    mapCount      = 0;
    MCSites       = new int[gcPointCount];
    referenceMaps = new byte[gcPointCount * bytesPerMap];

    //  jsr ie unusual map information
    unusualMaps = null;
    numberUnusualMaps = 0;
    extraUnusualMap = null;
    mergedReferenceMap = 0;
    mergedReturnAddressMap = 0;

    if (VM.TraceStkMaps) {
      VM.sysWrite("VM_ReferenceMaps-startNewMaps-  gcPointCount =  ");
      VM.sysWrite( gcPointCount);
      VM.sysWrite(" -jsrCount = ");
      VM.sysWrite( jsrCount);
      VM.sysWrite( "\n");
    }

    if (jsrCount > 0) {

      // generate jsr processing objects
      unusualMaps = new VM_UnusualMaps[jsrCount*2];
      extraUnusualMap = new VM_UnusualMaps();

      // reserve a map for merging maps
      tempIndex = getNextMapElement();

      // reserve map words for merged reference map
      mergedReferenceMap = getNextMapElement();

      // reserve map words for merged return address map
      mergedReturnAddressMap = getNextMapElement();

      //reserve maps for the extraUnusualMapObject
      // the reference map
      int mapstart = getNextMapElement();
      extraUnusualMap.setReferenceMapIndex(mapstart);

      //the set of non reference stores
      mapstart = getNextMapElement();
      extraUnusualMap.setNonReferenceMapIndex(mapstart);

      // the return address map
      mapstart = getNextMapElement();
      extraUnusualMap.setReturnAddressMapIndex(mapstart);

    }

    if (VM.ReferenceMapsStatistics) {
      methodcount = methodcount + 1;
      mapcount = mapcount + gcPointCount;
    }
  }


  /**
   * Given the information about a GC point, record the information in the proper tables
   * 
   *  The information is the following
   *      the index in the bytecode of this site,
   *      a byte array that describes the contents of the local variables and the java stack,
   *      the last offset of a byte that contains information about the map,
   *      a boolean to indicate that this map is a replacement for a currently
   *        existing map.
   */ 
  public void recordStkMap(int byteindex, byte[] byteMap, int BBLastPtr, boolean replacemap) throws VM_PragmaInterruptible {

    int mapNum = 0;

    if (VM.TraceStkMaps){
      VM.sysWrite(" VM_ReferenceMaps-recordStkMap bytecode offset = ");
      VM.sysWrite(byteindex);
      VM.sysWrite("\n");
      VM.sysWrite(" input byte map = ");
      for ( int j = 0; j <= BBLastPtr; j++)
        VM.sysWrite(byteMap[j]);
      VM.sysWrite("\n");
      if ( replacemap) {
        VM.sysWrite(" VM_ReferenceMaps-recordStkMap- replacing map at byteindex = ");
        VM.sysWrite(byteindex);
        VM.sysWrite("\n");
      }
    }

    if (replacemap) {
      // replace a map that already exists in the table
      //  locate the site
      for(mapNum = 0; mapNum < mapCount; mapNum++) {
        if (MCSites[mapNum] == byteindex) {
          // location found -clear out old map
          int start = mapNum * bytesPerMap;  // get starting byte in map
          for ( int i = start; i < start + bytesPerMap; i++) {
            referenceMaps[i] = 0;
	  }
          if (VM.TraceStkMaps) {
            VM.sysWrite(" VM_ReferenceMaps-recordStkMap replacing map number = ", mapNum);
            VM.sysWriteln("  for machinecode index = ", MCSites[mapNum]);
          }
          break;
        }
      }
    } else {
      // add a map to the table - its a new site
      //  allocate a new site
      mapNum = mapCount++;
      // fill in basic information
      MCSites[mapNum] = byteindex; // gen and save bytecode offset
      if (BBLastPtr == -1)  return;   // empty map for this gc point
    }

    if (VM.TraceStkMaps){
      VM.sysWrite(" VM_ReferenceMaps-recordStkMap map id  = ");
      VM.sysWrite(mapNum);
      VM.sysWrite("\n");
    }


    // convert Boolean array into array of bits ie create the map
    int mapslot  = mapNum * bytesPerMap;
    int len    = (BBLastPtr + 1);      // get last ptr in map
    int offset = 0;              // offset from origin
    int convertLength;                             //to start in the map
    int word = mapslot;

    // convert first byte of map
    // get correct length for first map byte - smaller of bits in first byte or size of map
    if (len < (BITS_PER_MAP_ELEMENT -1))
      convertLength = len;
    else
      convertLength = BITS_PER_MAP_ELEMENT -1;
    byte firstByte = convertMapElement(byteMap, offset, convertLength, VM_BuildReferenceMaps.REFERENCE);
    referenceMaps[word] = (byte)((0x000000ff & firstByte) >>>1); // shift for jsr bit ie set it to 0

    if (VM.TraceStkMaps) {
      VM.sysWrite(" VM_ReferenceMaps-recordStkMap convert first map bytes- byte number = ");
      VM.sysWrite(word);
      VM.sysWrite(" byte value in map = ");
      VM.sysWrite(referenceMaps[word]);
      VM.sysWrite(" - before shift = "); VM.sysWrite(firstByte); 
      VM.sysWrite("\n");
    }

    // update indexes for additional bytes
    word++;                                // next byte in bit map
    len    -= (BITS_PER_MAP_ELEMENT -1);   // remaining count
    offset += (BITS_PER_MAP_ELEMENT -1);   // offset into input array



    // convert remaining byte array to bit array -
    while ( len > 0 ) {
      // map takes multiple bytes -convert 1  at a time
      if (len <= (BITS_PER_MAP_ELEMENT -1))
        convertLength = len;
      else
        convertLength = BITS_PER_MAP_ELEMENT;
      // map takes multiple bytes -convert 1  at a time
      referenceMaps[word] = convertMapElement(byteMap, offset, convertLength, VM_BuildReferenceMaps.REFERENCE);

      if (VM.TraceStkMaps) {
        VM.sysWriteln(" VM_ReferenceMaps-recordStkMap convert another map byte- byte number = ", word, " byte value = ", referenceMaps[word]);
      }

      len    -= BITS_PER_MAP_ELEMENT;                // update remaining words
      offset += BITS_PER_MAP_ELEMENT;                // and offset
      word++;
    }  // end of while

    // update stats
    if (VM.ReferenceMapsStatistics) {
      if (!replacemap) {
        if (BBLastPtr < 8)
          numMapsLE8++;
        if (BBLastPtr <16)
          numMapsLE16++;
        if (BBLastPtr >  maxbytesPerMap)
          maxbytesPerMap=BBLastPtr;
      }
    }
    return;
  }

  /**
   * Record a map for a point within a JSR Subroutine. This requires setting up one
   * of the unusual maps.
   * @param byteindex         index into the byte code array of the point for the map
   * @param currReferenceMap  map of references and return addresses that were set
   *                          within the JSR Subroutine
   * @param BBLastPtr         map runs from -1  to BBLastPtr inclusively
   * @param returnAddrIndex   Index in the stack where the return address
   *                            for the jsr routine (in which this gcpoint is located)
   *                            can be found
   * @param replacemap        False if this is the first time this map point has been
   *                          recorded.
   */
  public void recordJSRSubroutineMap(int byteindex, byte[] currReferenceMap, int BBLastPtr, 
                                     int returnAddrIndex, boolean replacemap) throws VM_PragmaInterruptible {
    int mapNum = 0;
    int unusualMapIndex = 0;
    int returnOffset;
    VM_UnusualMaps jsrSiteMap;

    if (replacemap) {
      // update an already existing map
      //  locate existing site  in table
      jsrSiteMap = null;
    findJSRSiteMap:
      for( mapNum = 0; mapNum < mapCount; mapNum++) {
        if ( MCSites[mapNum] == byteindex) {
          // gc site found - get index in unusual map table and the unusual Map
          unusualMapIndex = JSR_INDEX_MASK & referenceMaps[ mapNum*bytesPerMap];
          returnOffset = convertBitNumToOffset(returnAddrIndex);
          if (unusualMapIndex == JSR_INDEX_MASK) {
            // greater than 127 unusualMaps- sequential scan of locate others unusual map
            for (unusualMapIndex = JSR_INDEX_MASK; unusualMapIndex < numberUnusualMaps; unusualMapIndex++) {
              if (unusualMaps[unusualMapIndex].getReturnAddressOffset() == returnOffset) {
                jsrSiteMap = unusualMaps[unusualMapIndex];
		break findJSRSiteMap;
	      }
            }
            VM.sysFail(" can't find unusual map !!!!!!! - should never occur");
          } else {
            jsrSiteMap = unusualMaps[unusualMapIndex];
            break findJSRSiteMap;
	  }
        }
      }
    } else {
      // new map, add to end of table
      mapNum = mapCount++;          // get slot and update count
      MCSites[mapNum] = byteindex;  // gen and save bytecode offset

      // generate an UnusualMap for the site
      jsrSiteMap = new VM_UnusualMaps();

      // add unusual map to UnusualMap table (table may need to be expanded)
      unusualMapIndex = addUnusualMap(jsrSiteMap);

      // set back pointer ie pointer from unusual maps back into referencemaps
      jsrSiteMap.setNormalMapIndex( mapNum);

      // setup index in reference maps
      if (unusualMapIndex > JSR_INDEX_MASK) 
        unusualMapIndex = JSR_INDEX_MASK;
      referenceMaps[mapNum*bytesPerMap] = (byte)((byte)unusualMapIndex | JSR_MASK);

      // setup new unusual Map
      int retOffset = convertBitNumToOffset(returnAddrIndex + 2 ); // +2  to convert to our index 
      jsrSiteMap.setReturnAddressOffset(retOffset);

      if (VM.TraceStkMaps) {
        VM.sysWrite("VM_ReferenceMaps-recordJSRSubroutineMap- input map = ");
        for(int i = 0; i < BBLastPtr + 1; i++) {
          VM.sysWrite( currReferenceMap[i]);
        }
        VM.sysWrite( "\n");
        VM.sysWrite("VM_ReferenceMaps-recordJSRSubroutineMap- mapNum = ");
        VM.sysWrite( mapNum);
        VM.sysWrite(" - byteindex = ");
        VM.sysWrite( byteindex);
        VM.sysWrite(" - return address offset = ");
        VM.sysWrite( retOffset);
        VM.sysWrite(" - reference map byte = ");
        VM.sysWrite( referenceMaps[mapNum*bytesPerMap]);
        VM.sysWrite( "\n");
      }
    }	 // end else clause - add new map

    // for new maps, setup maps in UnusualMap, for existing map replace them

    // setup Reference Map
    int refindex = scanByteArray(currReferenceMap, BBLastPtr, VM_BuildReferenceMaps.SET_TO_REFERENCE,
                                 jsrSiteMap.getReferenceMapIndex(), true);
    jsrSiteMap.setReferenceMapIndex( refindex);

    if (VM.TraceStkMaps) {
      VM.sysWrite("                 - reference map index = ");
      VM.sysWrite( refindex);
      VM.sysWrite(" - reference map  = ");
      for (int i = refindex; i < refindex+bytesPerMap; i++)
        VM.sysWrite( unusualReferenceMaps[i]);
      VM.sysWrite( "\n");
    }

    // setup NONReference Map
    int nonrefindex = scanByteArray(currReferenceMap, BBLastPtr, VM_BuildReferenceMaps.SET_TO_NONREFERENCE,
                                    jsrSiteMap.getNonReferenceMapIndex(), true);
    jsrSiteMap.setNonReferenceMapIndex( nonrefindex);

    if (VM.TraceStkMaps) {
      VM.sysWrite("                 - NONreference map index = ");
      VM.sysWrite( nonrefindex);
      VM.sysWrite(" - NON reference map  = ");
      for (int i = nonrefindex; i < nonrefindex+bytesPerMap; i++)
        VM.sysWrite( unusualReferenceMaps[i]);
      VM.sysWrite( "\n");
    }

    // setup returnAddress Map
    int addrindex = scanByteArray(currReferenceMap, BBLastPtr, VM_BuildReferenceMaps.RETURN_ADDRESS,
                                  jsrSiteMap.getReturnAddressMapIndex(), false);
    jsrSiteMap.setReturnAddressMapIndex( addrindex);

    if (VM.TraceStkMaps) {
      VM.sysWrite("                 - returnAddress map index = ");
      VM.sysWrite( addrindex);
      VM.sysWrite(" - return Address map  = ");
      for (int i = addrindex; i < addrindex+bytesPerMap; i++)
        VM.sysWrite( unusualReferenceMaps[i]);
      VM.sysWrite( "\n");
    }

    if (VM.TraceStkMaps) {
      VM.sysWrite("VM_ReferenceMaps-recordJSRSubroutineMap- unusualmap index = ");
      VM.sysWrite( unusualMapIndex);
      VM.sysWrite( "\n");
    }

    // update stats
    if (VM.ReferenceMapsStatistics) {
      if (!replacemap) {
        if (BBLastPtr < 8)
          numMapsLE8++;
        if (BBLastPtr <16)
          numMapsLE16++;
        if (BBLastPtr > maxbytesPerMap)
          maxbytesPerMap=BBLastPtr;
      }
    }
    return;
  }


  /**
   * Add an VM_UnusualMap to the array of uunusual maps,
   *   expand the array and referencemap array if necessary
   *
   * @param jsrSiteMap   unusualMap to be added to array
   *
   */
  private int addUnusualMap(VM_UnusualMaps jsrSiteMap) throws VM_PragmaInterruptible {
    if (unusualMaps == null) {
      // start up code
      unusualMaps = new VM_UnusualMaps[5];
      numberUnusualMaps = 0;
    }
    // add to array and bump count
    unusualMaps[numberUnusualMaps] = jsrSiteMap;
    int returnnumber = numberUnusualMaps;
    numberUnusualMaps++;

    // do we need to extend the maps
    if (numberUnusualMaps == unusualMaps.length) {
      // array is full, expand arrays for unusualMaps and unusual referencemaps
      VM_UnusualMaps[] temp = new VM_UnusualMaps[numberUnusualMaps +5];
      for (int i = 0; i < numberUnusualMaps; i++) {
        temp[i] = unusualMaps[i];
      }
      unusualMaps = temp;

      byte[] temp2 = new byte[unusualReferenceMaps.length  +(5 * bytesPerMap * 3)];
      for (int i = 0; i < unusualReferenceMaps.length; i++) {
        temp2[i] = unusualReferenceMaps[i];
      }
      unusualReferenceMaps = temp2;
    }
    return returnnumber;
  }


  /**
   * Setup a map  within a JSR Subroutine. This requires using up one
   * of the unusual maps. This routine is called when the caller gets a
   *  negative mapindex value return from locateGCPoint. This routine
   *  searches the map tables and uses its stack frameAddress input to build
   *  reference and returnAddress maps. The caller uses the getNext...
   *  routines to scan these maps for offsets in the frame of the
   *  related references.
   *
   * @param frameAddr         address of stack frame being scanned
   * @param mapid             index of map of instruction where map is required
   *                          ( this value was returned by locateGCpoint)
   * steps for this routine
   *   use the mapid to get index of the Unusual Map
   *   from the unusual map and the frame - get the location of the jsr invoker
   *   from the invoker address and the code base address - get the machine code offset
   *   from the machine code offset locate the map for that instruction
   *   if the invoker was itself in a jsr- merge the delta maps of each jsr and
   *     compute the new total delta maps
   *   else the invoker was not already in a jsr merge the unusual map differences
   *     with the invoker map
   */
  public void setupJSRSubroutineMap(VM_Address frameAddress, int mapid, VM_CompiledMethod compiledMethod)  {

    // first clear the  maps in the extraUnusualMap
    int j = extraUnusualMap.getReferenceMapIndex();
    int k = extraUnusualMap.getNonReferenceMapIndex();
    int l = extraUnusualMap.getReturnAddressMapIndex();
    for ( int i = 0; i <  bytesPerMap; i++) {
      unusualReferenceMaps[j + i] = 0;
      unusualReferenceMaps[k + i] = 0;
      unusualReferenceMaps[l + i] = 0;
    }

    // use the mapid to get index of the Unusual Map
    //
    if (VM.TraceStkMaps) {
      VM.sysWriteln("VM_ReferenceMaps-setupJSRSubroutineMap- mapid = ", mapid, "   - mapid = ", - mapid);
      VM.sysWriteln("  -referenceMaps[(- mapid) * bytesPerMap] = ", referenceMaps[(- mapid) * bytesPerMap]);
      VM.sysWriteln("        unusual mapid index = ", referenceMaps[(- mapid) * bytesPerMap]&JSR_INDEX_MASK);
    }

    int unusualMapid = (referenceMaps[(-mapid) * bytesPerMap] & JSR_INDEX_MASK);

    // if jsr map is > 127 go search for the right one
    if (unusualMapid == JSR_INDEX_MASK) {
      unusualMapid = findUnusualMap(-mapid);
    }

    VM_UnusualMaps unusualMap = unusualMaps[unusualMapid];
    //    unusualMapcopy(unusualMap, -mapid);      // deep copy unusual map into the extra map
    unusualMapcopy(unusualMap);      // deep copy unusual map into the extra map


    // from the unusual map and the frame - get the location of the jsr invoker
    //
    int jsrAddressOffset = unusualMap.getReturnAddressOffset();
    VM_Address callerAddress = VM_Magic.getMemoryAddress(frameAddress.add(jsrAddressOffset));
    // NOTE: -4 is subtracted when the map is determined ie locateGCpoint

    // from the invoker address and the code base address - get the machine code offset
    //
    int machineCodeOffset = callerAddress.diff(VM_Magic.objectAsAddress(compiledMethod.getInstructions())).toInt();

    if (VM.TraceStkMaps) {
      VM.sysWriteln("VM_ReferenceMaps-setupJSRMap- inputMapid = ", mapid);
      VM.sysWriteln("       jsrReturnAddressOffset = ", jsrAddressOffset);
      VM.sysWriteln("       jsr callers address = ", callerAddress);
      VM.sysWriteln("       machine code offset of caller = ", machineCodeOffset);
      if (machineCodeOffset <0)
        VM.sysWriteln("BAD MACHINE CODE OFFSET");
    }

    // from the machine code offset locate the map for the jsr instruction
    //
    int jsrMapid = locateGCPoint(machineCodeOffset, compiledMethod.getMethod());
    if (true || VM.TraceStkMaps) {
      VM.sysWriteln("VM_ReferenceMaps-setupJSRMap- locateGCpoint returns mapid = ", jsrMapid);
    }

    // if the invoker was in a jsr (ie nested jsrs)- merge the delta maps of each jsr and
    //   compute the new total delta
    //
    while ( jsrMapid < 0) {
      jsrMapid = -jsrMapid;

      if (true || VM.TraceStkMaps) {
        VM.sysWriteln("VM_ReferenceMaps-setupJSRsubroutineMap- outer MapIndex = ", jsrMapid, "  unusualMapIndex = ", referenceMaps[jsrMapid]);
      }

      // merge unusual maps- occurs in nested jsr conditions
      //  merge each nested delta into the maps of the extraUnusualmap
      int unusualMapIndex = JSR_INDEX_MASK & referenceMaps[ jsrMapid*bytesPerMap];
      if (unusualMapIndex == JSR_INDEX_MASK) {
        unusualMapIndex = findUnusualMap(jsrMapid);
      }
      // extraUnusualMap = combineDeltaMaps(jsrMapid, unusualMapid);
      extraUnusualMap = combineDeltaMaps(unusualMapIndex);


      // locate the next jsr from the current
      //
      VM_UnusualMaps thisMap = unusualMaps[unusualMapIndex];
      int thisJsrAddressOffset = thisMap.getReturnAddressOffset();
      VM_Address nextCallerAddress = VM_Magic.getMemoryAddress(frameAddress.add(thisJsrAddressOffset));
      int nextMachineCodeOffset = nextCallerAddress.diff(VM_Magic.objectAsAddress(compiledMethod.getInstructions())).toInt();
      jsrMapid = locateGCPoint(nextMachineCodeOffset, compiledMethod.getMethod());

      if (VM.TraceStkMaps) {
        VM.sysWriteln("VM_ReferenceMaps-setupJSRsubroutineMap- nested jsrs extraUnusualMap = ");
        extraUnusualMap.showInfo();
        VM.sysWriteln();
        VM.sysWriteln("VM_ReferenceMaps-setupJSRsubroutineMap- nested jsrs thisMap = ");
        thisMap.showInfo();
        VM.sysWriteln();
        VM.sysWriteln("     setupJSRsubroutineMap- nested jsrs end of loop- = ");
        VM.sysWriteln("      next jsraddress offset = ", thisJsrAddressOffset);
        VM.sysWriteln("      next callers address = ", nextCallerAddress);
        VM.sysWriteln("      next machinecodeoffset = ", nextMachineCodeOffset);
      }
    }  // end while

    // merge the jsr (unusual )map  with the base level (ie jsr instruction) map(s)
    //  the results are stored in mergedReferenceMap and mergedReturnAddresMap
    //  as indices in the referenceMaps table
    //
    finalMergeMaps((jsrMapid * bytesPerMap), extraUnusualMap);

    if (VM.TraceStkMaps) {
      VM.sysWriteln("VM_ReferenceMaps-setupJSRsubroutineMap- afterfinalMerge extraUnusualMap = ");
      extraUnusualMap.showInfo();
      VM.sysWriteln();
      VM.sysWriteln("     mergedReferenceMap Index = ", mergedReferenceMap);
      VM.sysWrite("     mergedReferenceMap  = ");
      showAnUnusualMap(mergedReferenceMap);
      VM.sysWriteln(unusualReferenceMaps[mergedReferenceMap]);
      VM.sysWriteln("     mergedReturnAddressMap Index = ", mergedReturnAddressMap);
      VM.sysWriteln("    mergedReturnAddressMap  = ", unusualReferenceMaps[mergedReturnAddressMap]);
      showInfo();
      showUnusualMapInfo();
    }
  }

  /**
   * Called when all the recording for this map is complete
   *   Can now sort or perform other cleanups
   */
  public void recordingComplete() {
    if (VM.ReferenceMapsStatistics) {
      bytespermap = bytespermap + mapCount;
    }
  }


  /**
   * After code is generated, translate the bytecode indices 
   * recorded in MCSites array into real machine code offsets.
   */
  public void translateByte2Machine(int[] b2m) {
    for (int i=0; i< MCSites.length; i++) {
      MCSites[i] = b2m[MCSites[i]] << VM.LG_INSTRUCTION_WIDTH;
    }
  }

  /**
   * convert a portion of an array word of Bytes into a bitmap of references
   * ie given a byte array,
   *    a starting offset in the array,
   *    the length to scan,
   *    and the type of byte to scan for
   *   ... convert the area in the array to a
   *        word of bits ... max length is 31 ie BITS_PER_MAP_ELEMENT
   */
  private byte convertMapElement ( byte[] curBBMap, int offset, int len, byte reftype){
    byte bitmap = 0;
    byte mask = JSR_MASK;     // starting bit mask
    for ( int i = offset; i < offset + len; i++) {
      if ( curBBMap[i] == reftype) {
        bitmap = (byte)(bitmap | mask);  // add bit to mask
      }
      mask = (byte)((0x000000ff & mask) >>> 1);             // shift for next byte and bit
    }
    return bitmap;
  }

  /**
   * get Next free word in referencemaps for gc call sites
   */ 
  private int getNextMapElement() throws VM_PragmaInterruptible {
    if (unusualReferenceMaps == null) {
      // start up code
      unusualReferenceMaps = new byte[ ((6 * 3) + 1) * bytesPerMap ];  // 3 maps per unusual map
    }

    if ( freeMapSlot >= unusualReferenceMaps.length) {
      // map is full - get new array, twice the size
      byte[] newArray = new byte[unusualReferenceMaps.length <<1];
      // copy array from old to new
      for ( int i = 0; i < unusualReferenceMaps.length; i++)
        newArray[i] = unusualReferenceMaps[i];
      // replace old array with the new
      unusualReferenceMaps = newArray;   // replace array
    }

    int allocate = freeMapSlot;
    freeMapSlot = freeMapSlot + bytesPerMap;
    return allocate;
  }

  /**
   * given the offset of a word in the stack,
   *  this routine calculates the bitnumber that represents
   *    the given offset
   */ 
  private int convertOffsetToBitNum(int offset)   {
    if (offset == 0) return 1; // initial call return first map bit

    // determine offset in bit area
    int diff = local0Offset - offset;
    // convert from offset to bitnumber
    int bitnum = (diff >>>2) + 1 +1; // 1 for being 1 based +1 for jsr bit

    if (VM.TraceStkMaps) {
      VM.sysWriteln("convertOffsetToBitnum- offset = ", offset, "  bitnum = ", bitnum);
    }

    return bitnum;
  }

  /**
   * given a bit number in the map
   *   this routine determines the correspondig offset in the stack
   */ 
  private int convertBitNumToOffset(int bitnum)   {
    // local0Offset is the distance from the frame pointer to the first local word
    //   it includes the Linkage area ( 12 bytes)
    //               the Local area and
    //               the Java operand stack area
    //               and possibly a parameter spill area

    // convert from top of local words
    int offset = local0Offset - ((bitnum -1 -1) <<2); // minus 1 for being 1 based, minus 1 for jsrbit
    if (VM.TraceStkMaps) {
      VM.sysWriteln("convertBitnumToOffset- bitnum = ", bitnum, "  offset = ", offset);
    }
    return offset;
  }

  /**
   * given a bit number in a jsr map
   *   this routine determines the correspondig offset in the stack
   */
  private int convertJsrBitNumToOffset(int bitnum)   {
    // convert from top of local words
    int jsroffset = local0Offset - ((bitnum -1) <<2); // minus 1 for being 1 based, no jsrbit here
    if (VM.TraceStkMaps) {
      VM.sysWriteln("convertJsrBitnumToOffset- input bitnum = ", bitnum, "  offset = ", jsroffset);
    }
    return jsroffset;
  }


  /**
   * given the offset of a word in the stack,
   *  this routine calculates the bitnumber in a jsr that represents
   *    the given offset
   */
  private int convertJsrOffsetToBitNum(int offset)   {
    if (offset==0) return 1; // initial call return first map bit

    int diff = local0Offset - offset;
    // convert from offset to bitnumber
    int bitnum = (diff >>>2) + 1; // 1 for being 1 based; no jsr bit

    if (true || VM.TraceStkMaps) {
      VM.sysWrite("convertJsrOffsetToBitnum- local0Offset = ", local0Offset);
      VM.sysWrite("    Input offset = ", offset );
      VM.sysWriteln(  " jsr  bitnum = ", bitnum);
    }

    return bitnum;
  }

  /**
   * given a starting bitnumber in a map,
   * the index of the corresponding byte,
   * and the remaining number of bits in the map,
   * this routine scans forward to find the next ref in
   * the map (inclusive search ie include bitnum)
   */
  private int scanForNextRef(int bitnum, int wordnum, int remaining, byte[] map)   {
    int  retbit, count = 0;

    // adjust bitnum and wordnum to bit within word
    while(bitnum > BITS_PER_MAP_ELEMENT) {
      wordnum++;
      bitnum -= BITS_PER_MAP_ELEMENT;
      count += BITS_PER_MAP_ELEMENT;
    }

    // determine remaining bits in this byte - first byte of scan
    int remain = (BITS_PER_MAP_ELEMENT+1) - bitnum;    // remaining bits in this word
    if ( remain >= remaining) {
      // last word in this map
      retbit = scanByte( bitnum, wordnum, remaining, map);
      if (retbit == 0) return 0;
      return (retbit + count);
    }
    // search at least the rest of this byte
    int startbit = bitnum;    // start at this bit
    retbit = scanByte( startbit, wordnum, remain, map);
    if (retbit != 0) return (retbit + count);

    // search additional bytes of map
    startbit = 1;            // start from beginning from now on
    remaining -= remain;     // remaing bits in map
    count += BITS_PER_MAP_ELEMENT; // remember you did the first byte
    while ( remaining > BITS_PER_MAP_ELEMENT ) {
      wordnum++;       // bump to next word
      remaining -= BITS_PER_MAP_ELEMENT; // search this wordd
      retbit = scanByte( startbit, wordnum, BITS_PER_MAP_ELEMENT, map);
      if (retbit != 0) return ( retbit + count);
      count += BITS_PER_MAP_ELEMENT;
    } // end while

    // scan last byte of map
    wordnum++;
    retbit = scanByte(startbit, wordnum, remaining, map); // last word
    if (retbit != 0) return ( retbit + count);
    return 0;
  }

  /**
   * given a bitnumber in a map,
   * the index of the corresponding map byte,
   * and the remaining number of bits in the byte,
   * this routine scans forward to find the next ref in
   * the byte or return zero if not found
   */
  private int scanByte(int bitnum, int bytenum,  int toscan,  byte[] map )  {
    int count = 0, mask;

    if (VM.TraceStkMaps) {
      VM.sysWrite(" scanByte- inputs  bitnum = ", bitnum);
      VM.sysWrite("  bytenum = ", bytenum);
      VM.sysWriteln(" toscan = ", toscan);
      VM.sysWriteln("     stackmap byte = ", map[bytenum]);
    }

    // convert bitnum to mask
    mask = (1 << (BITS_PER_MAP_ELEMENT - bitnum));  // generate mask

    // scan rest of word
    while (toscan > 0) {
      if ((mask & map[bytenum]) == 0) {
        // this bit not a ref
        mask = mask >>>1; // move mask bit
        count++;        // inc count of bits checked
        toscan--;    // decrement remaining count
      } else {
        // ref bit found
        if (VM.TraceStkMaps) {
          VM.sysWriteln(" scanByte- return bit number = ", bitnum + count);
        }
        return bitnum + count;
      }
    } // end while
    return 0;   // no more refs
  }

  /**
   * given a bytearray where each byte describes the corresponding word on a stack,
   *  the length of the byte array,
   *  and the type of information that is desired to be scanned
   * this subroutine scans the byte array looking for the type of information requested
   * and builds a bit array in the stack maps with the information
   * Skip one bits in the bitarray if skipOnBit is true - we need to skip one bit
   *   for setRef and setNonRef maps so they are properly merged with jsr base maps.
   *   However, we leave the retAddrMap alone.
   * it returns the index of the map in the reference map
   */
  int scanByteArray(byte[] byteMap, int BBLastPtr, byte refType, int mapslot, boolean skipOneBit) throws VM_PragmaInterruptible {
    skipOneBit = false;

    if (BBLastPtr == -1) return -1;     // no map for this jsr

    // get a place to hold the map if necessary
    if (mapslot == 0) 
      mapslot  = getNextMapElement();     // get first word of map

    // initialize search variables
    int len    = (BBLastPtr + 1);      // get length of map
    int offset = 0;                    // offset from origin
    int word = mapslot;                // first word of map

    // map may take multiple words -convert 1 at a time
    while (len > 0) {
      boolean doSkip = (offset == 0 && skipOneBit);  // skip a bit if first word and skipOneBit is set
      int bitsToDo = doSkip ? BITS_PER_MAP_ELEMENT - 1 : BITS_PER_MAP_ELEMENT;
      if (len < bitsToDo)                        
        bitsToDo = len;

      byte result = convertMapElement(byteMap, offset, bitsToDo, refType);
      if (doSkip)
        result = (byte)((0x000000ff & result) >>> 1);   // shift right to skip high bit for jsr to be consistent with normal maps
      unusualReferenceMaps[word] = result;

      len    -= bitsToDo;                // update remaining words
      offset += bitsToDo;                // and offset
      word++;                            // get next word
    }
    return mapslot;
  }

  /**
   * subroutine to deep copy an UnusualMap into the extraUnusualMap
   */
  private void unusualMapcopy(VM_UnusualMaps from)  {
    extraUnusualMap.setReturnAddressOffset( from.getReturnAddressOffset());
    copyBitMap(extraUnusualMap.getReferenceMapIndex(),from.getReferenceMapIndex());
    copyBitMap(extraUnusualMap.getNonReferenceMapIndex(),from.getNonReferenceMapIndex());
    copyBitMap(extraUnusualMap.getReturnAddressMapIndex(),from.getReturnAddressMapIndex());
  }

  /**
   * subroutine to copy a bitmap into the extra unusualmap
   * inputs
   *   the index of the map in the extraUnusualMap ie the "to" map
   *   the index of the map to copy ie the "from" map
   *   mapid is used to get the length of the map
   * output is in the extraunusual map
   */
  private void copyBitMap(int extramapindex, int index)  {
    if (VM.TraceStkMaps) {
      VM.sysWriteln(" copyBitMap from map index = ", index, " copyBitMap from value = ", unusualReferenceMaps[index]);
    }

    // copy the map over to the extra map
    for (int i = 0; i < bytesPerMap; i++)
      unusualReferenceMaps[extramapindex + i] = unusualReferenceMaps[index + i];

    if (VM.TraceStkMaps) {
      VM.sysWriteln(" extraUnusualBitMap index = ", extramapindex, " extraunusualBitMap value = ", unusualReferenceMaps[extramapindex]);
    }
  }


  /**
   * merge unusual maps- occurs in nested jsr conditions
   *  merge each nested delta map ( as represented by the jsrMapid of the location site)
   *   into the extraUnusualMap where the deltas are accumulated
   *  NOTE: while the routine is written to combine 2 unusualMaps in general
   *      in reality the target map is always the same ( the extraUnusualMap)
   *
   */  
  private VM_UnusualMaps combineDeltaMaps( int jsrUnusualMapid)   {
    //get the delta unusualMap
    VM_UnusualMaps deltaMap = unusualMaps[jsrUnusualMapid];

    // get the map indicies of the inner jsr map
    int reftargetindex = extraUnusualMap.getReferenceMapIndex();
    int nreftargetindex = extraUnusualMap.getNonReferenceMapIndex();
    int addrtargetindex = extraUnusualMap.getReturnAddressMapIndex();

    // get the map indices of the outer jsr map
    int refdeltaindex  = deltaMap.getReferenceMapIndex();
    int nrefdeltaindex  = deltaMap.getNonReferenceMapIndex();
    int addrdeltaindex  = deltaMap.getReturnAddressMapIndex();

    if (VM.TraceStkMaps) {
      // display original maps
      VM.sysWriteln("combineDeltaMaps- original ref map id  = ", reftargetindex);
      VM.sysWrite("combineDeltaMaps- original ref map  = " );
      for (int i = 0; i < bytesPerMap; i++) {
        VM.sysWrite(unusualReferenceMaps[reftargetindex + i]);
      }
      VM.sysWriteln();
      VM.sysWriteln("combineDeltaMaps- original nref map id  = ", nreftargetindex);
      VM.sysWrite("combineDeltaMaps original nref map  = " );
      for (int i = 0; i < bytesPerMap; i++) {
        VM.sysWrite(unusualReferenceMaps[nreftargetindex + i]);
      }
      VM.sysWriteln();
      VM.sysWriteln("combineDeltaMaps- original retaddr map id  = ", addrtargetindex);
      VM.sysWrite("combineDeltaMaps original retaddr map  = " );
      for (int i = 0; i < bytesPerMap; i++) {
        VM.sysWrite( unusualReferenceMaps[addrtargetindex + i]);
      }
      VM.sysWriteln();

      VM.sysWriteln("combineDeltaMaps- delta ref map id  = ", refdeltaindex);
      VM.sysWrite("combineDeltaMaps- original delta  ref map  = " );
      for (int i = 0; i < bytesPerMap; i++) {
        VM.sysWrite( unusualReferenceMaps[refdeltaindex + i]);
      }
      VM.sysWriteln();
      VM.sysWriteln("combineDeltaMaps- delta nref map id  = ", nrefdeltaindex);
      VM.sysWrite("combineDeltaMaps original delta nref map  = " );
      for (int i = 0; i < bytesPerMap; i++) {
        VM.sysWrite( unusualReferenceMaps[nrefdeltaindex + i]);
      }
      VM.sysWriteln();
      VM.sysWriteln("combineDeltaMaps- delta retaddr map id  = ", addrdeltaindex);
      VM.sysWrite("combineDeltaMaps original  delta retaddr map  = " );
      for (int i = 0; i < bytesPerMap; i++) {
        VM.sysWrite( unusualReferenceMaps[addrdeltaindex + i]);
      }
      VM.sysWriteln();


      // display indices 
      VM.sysWriteln("combineDeltaMaps- ref target mapid  = ", reftargetindex);
      VM.sysWriteln("                        ref delta mapid = ", refdeltaindex);
      VM.sysWriteln("combineDeltaMaps- NONref target mapid  = ", nreftargetindex);
      VM.sysWriteln("                        NONref delta mapid = ", nrefdeltaindex);
      VM.sysWriteln("combineDeltaMaps- retaddr target mapid  = ", addrtargetindex);
      VM.sysWriteln("                         retaddr delta mapid = ", addrdeltaindex);
      VM.sysWriteln("                         tempIndex = ", tempIndex);
    }

    // merge the reference maps
    mergeMap(tempIndex, reftargetindex, COPY);        // save refs made in inner jsr sub(s)
    mergeMap(reftargetindex, refdeltaindex, OR);      // get refs from outer loop
    mergeMap(reftargetindex, nreftargetindex, NAND);  // turn off non refs made in inner jsr sub(s)
    mergeMap(reftargetindex, addrtargetindex, NAND);  // then the return adresses
    mergeMap(reftargetindex, tempIndex, OR);           // OR inrefs made in inner jsr sub(s)

    // merge the non reference maps
    mergeMap(tempIndex, nreftargetindex, COPY); // save nonrefs made in inner loop(s)
    mergeMap(nreftargetindex, nrefdeltaindex, OR);      // get nrefs from outer loop
    mergeMap(nreftargetindex, reftargetindex, NAND);  // turn off refs made in inner jsr sub(s)
    mergeMap(nreftargetindex, addrtargetindex, NAND);  // then the return adresses
    mergeMap(nreftargetindex, tempIndex, OR);           // OR in non refs made in inner jsr sub(s)

    // merge return address maps
    mergeMap(addrtargetindex, addrdeltaindex,OR);

    if (VM.TraceStkMaps) {
      //display final maps
      VM.sysWrite("setupjsrmap-combineDeltaMaps- merged ref map  = " );
      for (int i = 0; i < bytesPerMap; i++) {
        VM.sysWrite( unusualReferenceMaps[reftargetindex + i]);
      }
      VM.sysWriteln();
      VM.sysWrite("setupjsrmap-combineDeltaMaps- merged nonref map  = " );
      for (int i = 0; i < bytesPerMap; i++) {
        VM.sysWrite( unusualReferenceMaps[nreftargetindex + i]);
      }
      VM.sysWriteln();
      VM.sysWrite("setupjsrmap-combineDeltaMaps- merged retaddr map  = " );
      for (int i = 0; i < bytesPerMap; i++) {
        VM.sysWrite( unusualReferenceMaps[addrtargetindex + i]);
      }
      VM.sysWriteln();
    }

    return extraUnusualMap;
  }

  /**
   * merge a delta map ( as represented by it index in the referencemap table) into
   * a target map ( similarly represented)
   * and use the operation indicted ( OR or NAND or COPY)
   */
  private void mergeMap(int targetindex, int deltaindex,  byte Op)  {
    int i;
    // merge the  maps
    if (Op == COPY) {
      for (i = 0; i < bytesPerMap; i++)
        unusualReferenceMaps[targetindex+i] = (byte)(unusualReferenceMaps[deltaindex+i] );
    }
    if (Op == OR) {
      for (i = 0; i < bytesPerMap; i++)
        unusualReferenceMaps[targetindex+i] = (byte)(unusualReferenceMaps[targetindex+i] 
                                                     | unusualReferenceMaps[deltaindex+i]);
    }
    if (Op == NAND) {
      for ( i = 0; i < bytesPerMap; i++) {
        short temp = (byte)(~(unusualReferenceMaps[deltaindex + i]));
        unusualReferenceMaps[targetindex+i] = (byte)(unusualReferenceMaps[targetindex + i] & temp);
      }
    }
    return;
  }

  /**
   *  merge the changes made in the jsr subroutine with the
   *  map found at the jsr instruction to get the next map
   *   with the invoker map
   */
  private void finalMergeMaps( int jsrBaseMapIndex,  VM_UnusualMaps deltaMap)  {
    int i;

    // clear out merged maps ie the destination maps)
    for ( i = 0; i < bytesPerMap; i++){
      unusualReferenceMaps[mergedReferenceMap + i] = 0;
      unusualReferenceMaps[mergedReturnAddressMap + i] = 0;
    }

    // get the indices of the maps for the unusual map
    int refMapIndex           = deltaMap.getReferenceMapIndex();
    int nonRefMapIndex        = deltaMap.getNonReferenceMapIndex();
    int returnAddressMapIndex = deltaMap.getReturnAddressMapIndex();

    // merge the delta map into the jsr map
    for ( i = 0; i < bytesPerMap ; i++) {
      // jsrBaseMap use high bit of first byte for jsr bit; shift to compensate
      byte base = referenceMaps[jsrBaseMapIndex+i];
      byte nextBase = (i + 1 < bytesPerMap) ? referenceMaps[jsrBaseMapIndex+i+1] : 0;
      byte finalBase = (byte) ((base << 1) | ((0xff & nextBase) >>> 7));
      byte newRef = unusualReferenceMaps[refMapIndex+i];
      byte newNonRef = unusualReferenceMaps[nonRefMapIndex + i];
      byte res = (byte)((finalBase | newRef) & (~newNonRef));
      unusualReferenceMaps[mergedReferenceMap+i] = res;
      unusualReferenceMaps[mergedReturnAddressMap+i] = (byte)(unusualReferenceMaps[mergedReturnAddressMap+i] |
                                                              unusualReferenceMaps[returnAddressMapIndex + i]);
      /*
         VM.sysWrite("   **** base = "); VM.sysWrite(base);
         VM.sysWrite("     nextBase = "); VM.sysWrite(nextBase);
         VM.sysWrite("     newRef = "); VM.sysWrite(newRef);
         VM.sysWrite("     newNonRef = "); VM.sysWrite(newNonRef);
         VM.sysWrite("     finalBase = "); VM.sysWrite(finalBase);
         VM.sysWrite("     res = "); VM.sysWrite(res);
         VM.sysWrite("\n");
       */
    }

    if ( true || VM.TraceStkMaps) {
      //note this displays each byte as a word ... only look at low order byte
      VM.sysWrite("finalmergemaps-jsr total set2ref delta map  = " );
      for ( i = 0; i < bytesPerMap ; i++)
        VM.sysWrite( unusualReferenceMaps[refMapIndex + i]);
      VM.sysWrite( "\n");

      VM.sysWrite("              -jsr total set2nonref delta map  = " );
      for ( i = 0; i < bytesPerMap ; i++)
        VM.sysWrite( unusualReferenceMaps[nonRefMapIndex + i]);
      VM.sysWrite( "\n");

      VM.sysWrite("              -jsr base map  = " );
      for ( i = 0; i < bytesPerMap ; i++)
        // ORIGINAL VM.sysWrite( unusualReferenceMaps[jsrBaseMapIndex + i]);
        VM.sysWrite( referenceMaps[jsrBaseMapIndex + i]);
      VM.sysWrite( "\n");

      VM.sysWrite("              -combined merged ref map  = " );
      for ( i = 0; i < bytesPerMap ; i++)
        VM.sysWrite( unusualReferenceMaps[mergedReferenceMap + i]);
      VM.sysWrite( "\n");

      VM.sysWrite("              -combined merged return address map  = " );
      for ( i = 0; i < bytesPerMap ; i++)
        VM.sysWrite( unusualReferenceMaps[mergedReturnAddressMap + i]);
      VM.sysWrite( "\n");
    }
    return;
  }

  /**
   * This routine is used to clean out the MethodMap of structures that
   * were allocated from temporary storage. Temporary storage is freed up
   *  between stack frames as the GC scans the stack.
   */
  public void cleanupPointers()  {
    if (VM.TraceStkMaps) VM.sysWrite("VM_ReferenceMaps- cleanupPointers\n" );
  }

  /**
   * This routine is used to find an Unusual map with an index 
   * greater than 127
   * it returns the index by doing a sequential scan and looking for the mapid 
   *  in the normal map directory
   */
  int findUnusualMap(int mapid)  {
    int i;
    // greater than 127 map sites- can't use direct index.. 
    // do sequestial scan for rest of maps ..it's slow but should almost never happen

    for ( i = JSR_INDEX_MASK; i < numberUnusualMaps; i++) {
      if ( unusualMaps[i].getNormalMapIndex() == mapid)
        break;
    } 
    if (i >= numberUnusualMaps)
      VM.sysFail(" can't find jsr map - PANIC !!!!"); 
    return i;
  }

  /**
   * show the basic information for each of the maps
   *    this is for testing use
   */
  public void showInfo() {
    VM.sysWriteln("showInfo- reference maps");
    if (MCSites == null) {
      VM.sysWrite(" no MCSites array - assume using cached data - can't do showInfo()");
      return;
    }

    VM.sysWrite(" MCSites.length = ", MCSites.length );
    VM.sysWrite(" mapCount = ", mapCount);
    VM.sysWriteln(" local0Offset = ", local0Offset);

    for (int i=0; i<mapCount; i++) {
      VM.sysWrite("mapid = ", i);
      VM.sysWrite(" - machine  code offset ", MCSites[i]);
      VM.sysWrite("  -reference Map  =  ");
      for (int j = 0; j <bytesPerMap; j++) {
        VM.sysWriteHex(referenceMaps[(i * bytesPerMap) + j]);
      }
      VM.sysWriteln();
    }
  }

  /**
   * show the basic information for a single map
   *    this is for testing use
   */
  public void showAMap(int MCSiteIndex) {
    VM.sysWriteln("show the map for MCSite index= ", MCSiteIndex);
    VM.sysWrite("machine code offset = ", MCSites[MCSiteIndex]);
    VM.sysWrite("   reference Map  =  ");
    for (int i = 0; i < bytesPerMap; i++) {
      VM.sysWrite(referenceMaps[(MCSiteIndex * bytesPerMap) + i]);
    }
    VM.sysWriteln();
  }

  /**
   * show the basic information for each of the unusual maps
   *    this is for testing use
   */
  public void showUnusualMapInfo() {
    VM.sysWrite("-------------------------------------------------\n");
    VM.sysWrite("showUnusualMapInfo- map count = ", mapCount);
    VM.sysWriteln("     numberUnusualMaps = ", numberUnusualMaps);

    for (int i=0; i<numberUnusualMaps; i++) {
      VM.sysWrite("-----------------\n");
      VM.sysWrite("Unusual map #");
      VM.sysWrite(i,false);
      VM.sysWrite(":\n");
      unusualMaps[i].showInfo();
      VM.sysWrite("    -- reference Map:   ");
      showAnUnusualMap(unusualMaps[i].getReferenceMapIndex());
      VM.sysWrite("\n");
      VM.sysWrite("    -- non-reference Map:   ");
      showAnUnusualMap(unusualMaps[i].getNonReferenceMapIndex());
      VM.sysWrite("\n");
      VM.sysWrite("    -- returnAddress Map:   ");
      showAnUnusualMap(unusualMaps[i].getReturnAddressMapIndex());
      VM.sysWrite("\n");
    }
    VM.sysWrite("------ extraUnusualMap:   ");
    extraUnusualMap.showInfo();
    showAnUnusualMap(extraUnusualMap.getReferenceMapIndex());
    showAnUnusualMap(extraUnusualMap.getNonReferenceMapIndex());
    showAnUnusualMap(extraUnusualMap.getReturnAddressMapIndex());
    VM.sysWrite("\n");
  }

  /**
   * show the basic information for a single unusualmap
   *    this is for testing use
   */
  public void showAnUnusualMap(int mapIndex) {
    VM.sysWrite("unusualMap with index = ");
    VM.sysWrite(mapIndex, false);
    VM.sysWrite("   Map bytes =  ");
    for (int i = 0; i < bytesPerMap; i++) {
      VM.sysWrite(unusualReferenceMaps[mapIndex + i]);
      VM.sysWrite("   ");
    }
    VM.sysWrite("   ");
  }

  /**
   * show the offsets for all the maps
   * this is for test use
   */
  public void showOffsets() {
    VM.sysWrite("in showOffset- #maps = ");
    VM.sysWrite(mapCount);
    VM.sysWrite("\n");
    int i,j, toffset = 0;

    if (mapCount == 0) {
      VM.sysWrite(" no maps for method");
      return;
    }
    for (i=0; i<mapCount; i++) {
      toffset = getNextRef(toffset, i);
      VM.sysWrite("initial offset  = ");
      VM.sysWrite(toffset);
      VM.sysWrite(" for map ");
      VM.sysWrite(i);
      VM.sysWrite("\n");
      while( toffset != 0) {
        toffset = getNextRef(toffset, i);
        VM.sysWrite("next offset = " );
        VM.sysWrite(toffset);
        if (toffset ==0) VM.sysWrite("---------------- end of map");
      }
    }
  }

  public static void resetStats() {
    methodcount = 0;
    mapcount = 0;
    bytecount = 0;
    bytespermap = 0;
    numMapsLE8 = 0;
    numMapsLE16 = 0;
    maxbytesPerMap = 0;
  }

  public static void showStats() {
    if (VM.ReferenceMapsStatistics) {
      VM.sysWrite(">> VM_StkVarMap statistics\n");
      VM.sysWrite( "method count " + methodcount+"\n" );
      VM.sysWrite( "number of maps " + mapcount+"\n" );
      VM.sysWrite( "number of bytecodes processed " + bytecount +"\n");
      VM.sysWrite( "numbers of shorts of maps " + bytespermap+"\n" );  // in words right now
      VM.sysWrite( "number of maps LE 8 in size " + numMapsLE8 +"\n" );
      VM.sysWrite( "number of maps LE 16 in size " + numMapsLE16+"\n" );
      VM.sysWrite( "largest individual map " + (maxbytesPerMap+1) + "\n");
    }
  }

  public int showReferenceMapStatistics(VM_Method method) throws VM_PragmaInterruptible {
    int offset = 0;
    int totalCount  = 0;
    int count;

    VM.sysWrite("-- Number of refs for method =  ");
    VM.sysWrite(method.getDeclaringClass().getName());
    VM.sysWrite(".");
    VM.sysWrite(method.getName());
    VM.sysWrite("---------------------------\n");

    for (int i=0; i<mapCount; i++) {
      byte mapindex  = referenceMaps[i * bytesPerMap];
      if (mapindex < 0) {
	// check for non jsr map
        VM.sysWrite("  -----skipping jsr map------- \n ");
        continue;
      }
      offset = getNextRef(offset, i);
      count  = 0;
      while(offset != 0) {
        totalCount++;
        count++;
        offset = getNextRef(offset, i);
        // display number of refs at each site - very noisy
        if (offset ==0) {
          VM.sysWriteln("  -----map machine code offset = ", MCSites[i], "    number of refs in this map = ", count);
        }
      }
    }
    VM.sysWrite("----- Total number of refs in method  = ");
    VM.sysWrite(totalCount);
    VM.sysWrite("  total number of maps in method = ");
    VM.sysWrite(mapCount);
    VM.sysWrite("\n");

    return totalCount;
  }

  //-#if RVM_WITH_OSR
  /* Interface for general queries such as given a GC point, if a stack slot or a local
   * variable is a reference
   */

  /* query if a local variable at a bytecode index has a reference type value
   * @param bcidx, the bytecode index
   * @param lidx, the local index
   * @return true, if it is a reference type
   *         false, otherwise
   */
  public boolean isLocalRefType(VM_Method method, int mcoff, int lidx) {
    int bytenum, bitnum;
    byte[] maps;
	
    if (bytesPerMap == 0) return false;           // no map ie no refs
    int mapid = locateGCPoint(mcoff, method);

    if (mapid >= 0) {
      // normal case
      bytenum  = mapid * bytesPerMap;
      bitnum = lidx + 1 + 1; // 1 for being 1 based +1 for jsr bit      
      maps = referenceMaps;
    } else {
      // in JSR
      bytenum = mergedReferenceMap;
      bitnum = lidx + 1; // 1 for being 1 based
      maps = unusualReferenceMaps;
    }

    // adjust bitnum and wordnum to bit within word
    while(bitnum > BITS_PER_MAP_ELEMENT) {
      bytenum++;
      bitnum -= BITS_PER_MAP_ELEMENT;
    }

    int mask = (1 << (BITS_PER_MAP_ELEMENT - bitnum));  // generate mask

    return ((mask & maps[bytenum]) != 0);
  }
  //-#endif
}
