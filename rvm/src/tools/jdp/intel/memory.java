/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * This is the abstract class for the internal and external implementation of memory
 * This object represents the memory, intended to be associated with an OsProcess
 * We can inspect memory, write value
 * 
 * @author Ton Ngo (2/98)
 */

import java.util.*;
/* not needed for build in separate RVM.tools directory */
/* import PPC_Disassembler; */

abstract class memory implements jdpConstants  
{
  //****************************************************************************
  // In the external implementations, these will be native methods 
  // In the internal implementations, they will use the VM_Magic methods
  //****************************************************************************
  public abstract int read(int address);                   // for word aligned addresses
  public abstract int[] readblock(int address, int count);
  public abstract void write(int address, int value);
  public abstract int readTOC(int offset);
  public abstract int addressTOC(int offset);
  public abstract int branchTarget(int instruction, int address);
  public abstract boolean isTrapInstruction(int instruction);
  public abstract String getNativeProcedureName(int instructionAddress);

  //****************************************************************************
  // These are the common fields and methods for memory
  //****************************************************************************

  /**
   * pointer back to the process that owns this memory
   */
    OsProcess owner;                  	


  /**
   * Constructor
   * @param process the owner of this object, for back reference
   * @return
   * @exception
   * @see
   */
  public memory (OsProcess process) {
    owner = process;         // save the reference to the owning process
  }

  //******************************************************************************
  // Methods for accessing memory
  //******************************************************************************
  
  public int readsafe(int address) throws memoryException {
    int data = read(address);     
    if (data!=-1)
      return data;
    else
      throw new memoryException("bad address");
  }

  /**
   * Read a byte of memory
   * @param address a random byte address
   * @return a byte
   * @exception
   * @see
   */
  public byte readByte(int address) throws memoryException {
    int waddr = address & 0xFFFFFFFC;
    // System.out.println("byte aligned at " + Integer.toHexString(waddr));
    int data = readsafe(waddr);     
    int lobits = address & 0x00000003;

    switch (lobits) {    // pick out the byte requested
    case 1:
      data = data >> 8;
      break;
    case 2:
      data = data >> 16;
      break;
    case 3:
      data = data >> 24;
      break;
    }
    data &= 0x000000FF; 
    return new Integer(data).byteValue();
  }

  /**
   * Read a short word from memory
   * @param address a random address (aligned to 2-bytes)
   * @return
   * @exception
   * @see
   */
  public short readShort(int address) throws memoryException {
    int waddr = address & 0xFFFFFFFC;
    // System.out.println("2 byte aligned at " + Integer.toHexString(waddr));
    int data = readsafe(waddr);     
    int lobits = address & 0x00000002;
    
    if (lobits==2) {
      data = data >> 16;
    }
    data &= 0x0000FFFF; 
    return new Integer(data).shortValue();
  }

  /**
   * Read a word,  breakpoints are transparent
   * @param thread the target thread, so that thread specific breakpoints are made transparent
   * @param address a random address (word aligned)
   * @return
   * @exception
   * @see
   */
  public int readNoBP (int thread, int address) {  
    breakpoint bp;
    int memdata;
    
    memdata = read(address); 

    // check the random breakpoints
    bp = owner.bpset.lookup(address);
    if (bp!=null && memdata==BKPT_INSTRUCTION) {
      if (bp.next_addr==address ) 
	return(bp.next_I);
      else if (bp.branch_addr==address)
	return(bp.branch_addr);
    }
    
    // check the stepping breakpoint;
    for (int i=0; i<owner.threadstep.size(); i++) {   
      bp = (breakpoint) owner.threadstep.elementAt(i);
      if (memdata==BKPT_INSTRUCTION) {
	if (bp.next_addr==address) 
	  return(bp.next_I);
        else if (bp.branch_addr==address) 
	  return(bp.branch_I);
      }
    }

    // check the line stepping breakpoint;
    for (int i=0; i<owner.threadstepLine.size(); i++) {   
      bp = (breakpoint) owner.threadstepLine.elementAt(i);
      if (memdata==BKPT_INSTRUCTION) {
	if (bp.next_addr==address) 
	  return(bp.next_I);
        else if (bp.branch_addr==address) 
	  return(bp.branch_I);
      }
    }

    // not a breakpoint, or breakpoint instruction has been overwritten
    // so read normal memory
    return(memdata);
    
  }


  /**
   * Check if the current frame pointer point to the C stack frame in the
   * boot image (the next frame pointer is 0)
   * @param fp the current frame pointer
   * @return true if it is, false if it is not
   */
  public boolean isLinkToBoot(int fp) {
    // up to next stack frame
    int nextFp = read(fp+VM_Constants.STACKFRAME_FRAME_POINTER_OFFSET);
    if (nextFp==0)
      return true;
    else 
      return false;
  }

  //******************************************************************************
  // print methods (some customized for JVM)
  //******************************************************************************

  /**
   * Dump contents of memory in both hex and displayable character format
   * @param address a random memory address
   * @param count the number of words to dump
   * @return
   * @exception
   * @see
   */
    public String print(int address, int count) {
      int thread = 0;                    // TODO: place holder for multithread later
      StringBuffer ret = new StringBuffer();
      for (int i=0 ;i<count; i++) {
        String sb0, sb1, sb2, sb3;
        int thisAddress = address+i*4;
        int word = readNoBP(thread, thisAddress);
        int b0 = ((word & 0xFF000000)/0x01000000) & 0x000000FF;
        int b1 = ((word & 0x00FF0000)/0x00010000);
        int b2 = ((word & 0x0000FF00)/0x00000100);
        int b3 =  (word & 0x000000FF);
        sb0 = Integer.toHexString(b0);
        sb1 = Integer.toHexString(b1);
        sb2 = Integer.toHexString(b2);
        sb3 = Integer.toHexString(b3);
        if (sb0.length()==1) sb0 = "0"+sb0;
        if (sb1.length()==1) sb1 = "0"+sb1;
        if (sb2.length()==1) sb2 = "0"+sb2;
        if (sb3.length()==1) sb3 = "0"+sb3;
	
        char c0 = (char) b0;
        char c1 = (char) b1;
        char c2 = (char) b2;
        char c3 = (char) b3;
        if (!Character.isLetterOrDigit(c0))  c0 = '.';
        if (!Character.isLetterOrDigit(c1))  c1 = '.';
        if (!Character.isLetterOrDigit(c2))  c2 = '.';
        if (!Character.isLetterOrDigit(c3))  c3 = '.';
      
        ret.append("  0x" + Integer.toHexString(thisAddress) + ": " +
                   sb0 + " " + sb1 + " " + sb2 + " " + sb3 + "    " +
                   c0 + " " + c1 + " " + c2 + " " + c3 + "\n");
      }    

      return ret.toString();
    }

  /**
   * Dump contents of raw memory in hex (no substition for breakpoints)
   * @param address a random memory address
   * @param count the number of words to dump
   * @return
   * @exception
   * @see
   */
  public String printRaw(int address, int count) {
    int [] memblock;
    StringBuffer ret = new StringBuffer();
    if (count == 0) {              // if no count, print a default number (5)
      memblock = readblock(address, 5);
    } else {
      memblock = readblock(address, count);
    }      

    for (int i=0 ;i<memblock.length; i++) {
      ret.append("  0x" + Integer.toHexString(address+i*4) + ": " +
                 Integer.toHexString(memblock[i]));
      ret.append('\n');
    }
    return ret.toString();

  }

  /**
   * Print the current stack frame in raw form
   * @param width the number of words to display at the top and bottom of 
   *        this frame
   * @return 
   * @exception
   * @see
   */
  public String printJVMstack(int framePointer, int width) {    
    int fpTop;
    int fpBottom;
    boolean isTopFrame;

    fpTop = framePointer;
    if (framePointer==0) {
      fpTop = owner.reg.currentFP();
      isTopFrame = true;
    } else if (framePointer==owner.reg.currentFP()) {
      isTopFrame = true;
    } else {
      isTopFrame = false;
    }

    if (isTopFrame) {
      // Top frame is treated specially
      fpBottom = owner.reg.currentSP();
      if (fpTop == fpBottom) {        // sp has not been incremented yet
	return printstack(fpTop, 0, width);
      } else {
	return printstack(fpTop, fpBottom, width);
      }
      
    } else {      
      // all other frames
      // scan down the frame to look for the bottom of the frame
      for (fpBottom=framePointer;fpBottom>framePointer-400; fpBottom-=4) {
	if (read(fpBottom)==framePointer)  // looking for the back pointer
	  break;
      }
      if (fpBottom==framePointer-400) {
	// can't find bottom after 100 words, assume it's the active frame
	return printstack(framePointer, 0, width);
      } else {
	return printstack(framePointer, fpBottom, width);
      }
    }

  }

  /**
   * Print the current stack frame in raw form, with pointers to LR and SP
   * @param fpTop     the pointer to the top of the frame
   * @param fpBottom  the pointer to the bottom of the frame, or 0 if this is
   *                  the currently active frame
   * @param width the number of words to display at the top and bottom of 
   *        this frame
   * @return
   * @exception
   * @see
   */
  public String printstack(int fpTop, int fpBottom, int width) {    
    int data;
    int[] start, stop;
    StringBuffer ret = new StringBuffer();
    
    // safety check to make sure the display is viewable
    if (fpBottom==0) {
      start = new int[1];
      stop = new int[1];
      start[0] = fpTop;
      stop[0] = fpTop-12;      
    } else if ((fpTop - fpBottom)>(width*4*2)) {
      start = new int[2];
      stop = new int[2];
      start[0] = fpTop;
      stop[0] = fpTop-width*4;
      start[1] = fpBottom+width*4;
      stop[1] = fpBottom;
    } else {
      start = new int[1];
      stop = new int[1];
      start[0] = fpTop;
      stop[0] = fpBottom;
    }


    // print the formatted stack
    for (int r=0; r<start.length; r++) {
      // System.out.println("r=" + r + ", start=" + VM.intAsHexString(start[r]) + 
      //	 ", stop=" + VM.intAsHexString(stop[r]));
      if (r>0)
      {
	ret.append("      . . . . . .");
        ret.append('\n');
      }
      for (int i=start[r]; i>=stop[r]; i-=4) {
	data = read(i);
        String result = "   " + VM.intAsHexString(i) + " : " + VM.intAsHexString(data);
        String blank = "             ";
        String num = String.valueOf(data);
        if (Debugger.stackPreference=='d')
          result += "   " + blank.substring(num.length()) + data;
        if (i==fpTop)
          result += " <- current FP";
        ret.append(result);
        ret.append('\n');
      }
    }

    if (fpBottom==0)
      ret.append("   ...frame bottom unknown... \n");

    return ret.toString();
  }

  /** 
   * Print stack trace from the given Frame Pointer
   * @param fp  the frame pointer to start the stack trace
   *
   */
  public String printJVMstackTrace(int framepointer) {
    int depth = 0;
    int limit = 50;
    int fp = framepointer;
    int linkaddr = read(fp+registerConstants.STACKFRAME_NEXT_INSTRUCTION_OFFSET);
    StringBuffer ret = new StringBuffer();
    while ((linkaddr!=0) && (depth<=limit)) {
      ret.append(printThisFrame(-1,linkaddr,fp));
      fp = read(fp);              // up to next stack frame
      linkaddr = read(fp+registerConstants.STACKFRAME_NEXT_INSTRUCTION_OFFSET);
      if (linkaddr==-1 || fp==-1) {
	return ("ERROR:  stack corrupted at frame " + depth + " or earlier");
      }
    }
    return ret.toString();
  }

  /**
   * Print stack trace in the desired range
   * (current stack frame index is 0)
   * @param from starting stack frame index
   * @param to ending stack frame index
   * @return
   * @exception
   * @see
   */
  public String printJVMstackTrace(int from, int to) {
    int linkaddr, fp, depth;
    StringBuffer ret = new StringBuffer();
    ret.append("Short stack trace: " );
    ret.append('\n');
    // first traverse to the desired depth
    depth = 0;
    linkaddr = owner.reg.currentIP();
    fp = owner.reg.currentFP();
    while ((linkaddr!=0) && (depth<from)) {
      linkaddr = read(fp+registerConstants.STACKFRAME_NEXT_INSTRUCTION_OFFSET);
      fp = read(fp);                   
      if (linkaddr==-1 || fp==-1) {
	return ("ERROR:  stack corrupted at frame " + depth + " or earlier");
      }
      depth++;
    }
    if (linkaddr==0) {
      if (depth<from) 
	return ("Requested frame " + from + " is beyond current stack depth: " + depth);
      else if (depth==0)
	return ("Frame " + from + ": link to boot C code");
    }

    // then walk the stack to the desired end
    //while ((linkaddr!=0) && (depth<=to)) {
    while (depth<=to) {
      // System.out.println("printJVMstackTrace: depth " + depth + 
      //		    ", fp " + Integer.toHexString(fp) +
      //		    ", ip " + Integer.toHexString(linkaddr));
      ret.append(printThisFrame(depth,linkaddr,fp));
      linkaddr = read(fp+registerConstants.STACKFRAME_NEXT_INSTRUCTION_OFFSET);
      fp = read(fp);              // up to next stack frame
      if (fp==0)
	break;
      if (linkaddr==-1 || fp==-1) {
	return ("ERROR:  stack corrupted at frame " + depth + " or earlier");
      }
      depth++;
      // System.out.println(depth + ": " + Integer.toHexString(fp) + ", " + Integer.toHexString(linkaddr));
    }

    if (fp!=0 && fp!=-1) {
      System.out.println("Finding remaining frame from fp " +
			 Integer.toHexString(fp));
      int remain = findFrameCountFrom(fp);
      if (remain==1000)
      {
	ret.append("    ...(more than 1000 frames remain)...");
        ret.append('\n');
      }
      else
      {
	ret.append("    ...(" + remain + " more frames)...");
        ret.append('\n');
      }
    }
    return ret.toString();

  }

  /**
   * Print a short stack frame:  just the stack depth, address and name
   * @param depth  the stack depth for this frame
   * @param linkaddr  the instruction pointer for this frame
   * @param fp the frame pointer for this frame
   * @return
   * @exception
   * @see printJVMstackTrace
   */
  private String printThisFrame(int depth, int linkaddr, int fp) {
    // System.out.println("methodID " + methodID);
    BootMap bmap = owner.bootmap();
    String depthString;
    StringBuffer ret = new StringBuffer();
    int native_linkaddr = read(fp +4);  // Lintel return address is offset 4 from FP

    if (depth==-1)
      depthString = "?";
    else 
      depthString = String.valueOf(depth);

    // if we are still in the prolog of the current method, the link to the
    // calling stack frame is not set up yet, so indicate that it is missing
    if  (!bmap.isFpReady(linkaddr)) {
      return (depthString + "  0x........   (stack frame being built in prolog)\n");
    }

    // At the end of the stack frame is the link to the C code for booting
    if (isLinkToBoot(fp)) {
      return (depthString + "  " + VM.intAsHexString(native_linkaddr) + 
              "   <- link to boot C code\n");
    }
    // if (!bmap.isInJVMspace(fp)) {
    // 	 return (depthString + "  0x" + Integer.toHexString(native_linkaddr) +
    // 		 "   (native) \n");     
    // }

    int compiledMethodID = bmap.getCompiledMethodID(fp, linkaddr);

    if (compiledMethodID==NATIVE_METHOD_ID) {
      ret.append(depthString + "  " + VM.intAsHexString(native_linkaddr) +
                 "   (native) ");
      //                 "   (native) " + getNativeProcedureName(native_linkaddr));
      ret.append('\n');
    } else {
      VM_Method mth = bmap.findVMMethod(compiledMethodID, true);
      if (mth!=null) {
      	String class_name = mth.getDeclaringClass().getName().toString();
      	String method_name = mth.getName().toString();
      	String method_sig = mth.getDescriptor().toString();
      	String line;
      	if (depth==0)            // for IP, get the current line
      	  line = bmap.findLineNumberAsString(compiledMethodID, linkaddr);   
      	else                     // for LR, get the previous line
      	  line = bmap.findPreviousLineNumberAsString(compiledMethodID, linkaddr);  
      	ret.append(depthString + "  " + VM.intAsHexString(linkaddr) + 
                   "   " + class_name + "." + method_name + 
                   ": " + line + "   " + method_sig);
        ret.append('\n');
      } else {
      	ret.append(depthString + "  " + VM.intAsHexString(linkaddr) + 
                   "   unknown method");
        ret.append('\n');
      }
    }
    return ret.toString();
  }

  /**
   * Given a range of stack frame number,
   * print a full stack trace with label for each word
   * @param from starting stack frame (current frame is 0)
   * @param to   ending stack frame
   * @return
   * @exception
   * @see
   */
  public String printJVMstackTraceFull(int from, int to) {
    int linkaddr, fp, fpBottom, sp, depth, saved_fp;
    int i, limit;
    int fpArray[]       = new int[40];
    int fpBottomArray[] = new int[40];
    int lrArray[]       = new int[40];
    boolean reach_top;
    StringBuffer ret = new StringBuffer();

    // limit the number of frame to print to 40
    if ((to-from+1) > 40)
      limit = from + 40;
    else
      limit = to;
    
    // first traverse to the desired depth-1
    reach_top = false;
    depth = 0;
    fpBottom = owner.reg.currentSP();
    fp = owner.reg.currentFP();           // start with current frame
    linkaddr = owner.reg.currentIP();
    while ((linkaddr!=0) && (depth<from)) {
      linkaddr = read(fp+registerConstants.STACKFRAME_NEXT_INSTRUCTION_OFFSET);
      fpBottom = fp;
      fp = read(fp);                   
      if (linkaddr==-1 || fp==-1) {
	return ("ERROR:  stack corrupted at frame " + depth + " or earlier");
      }
      depth++;
    }

    if (linkaddr==0) {
      if (depth<from) 
	return ("Requested frame " + from + " is beyond current stack depth: " + depth);
      else if (depth==from)
	return ("Frame " + from + ": link to boot C code");	
    }

    
    // second, traverse for the desired section, saving fp and linkaddr
    i = 0;
    while (!reach_top && depth <= limit) {      
      fpBottomArray[i] = fpBottom;
      fpArray[i] = fp;
      lrArray[i] = linkaddr;
      depth++; i++;
      linkaddr = read(fp+registerConstants.STACKFRAME_NEXT_INSTRUCTION_OFFSET);
      fpBottom = fp;
      fp = read(fp);
      if (linkaddr==0)
	reach_top = true;
    }
    saved_fp = fp;
    depth = i;

    // debug dump
    // for (findex = 0 ; findex<fpvect.size(); findex++) {
    // 	    fp = ((Integer) fpvect.elementAt(findex)).intValue();
    // 	    linkaddr = ((Integer) lrvect.elementAt(findex)).intValue();
    // 	    System.out.println(" " + findex + ": " + Integer.toHexString(fp)+ 
    // 			       " " + Integer.toHexString(linkaddr) );
    // }

    // Now walk and print this range of stack frame

    for (i=0; i<depth; i++) {
      // print the frame
      ret.append(printThisFrameFull(from+i, lrArray[i], fpArray[i], fpBottomArray[i])); 
    } 

    if (!reach_top) {
      int remain = findFrameCountFrom(saved_fp);
      if (remain==1000)
      {
	ret.append("    ...(more than 1000 frames remain)...");
        ret.append('\n');
      }
      else
      {
	ret.append("    ...(" + remain + " more frames)...");
        ret.append('\n');
      }
    }

    return ret.toString();

  }

  /**
   * Given the frame pointer, print a full stack trace with label for each word
   * @param fp  the frame pointer
   * @return
   * @exception
   * @see
   */
  public String printJVMstackTraceFull(int fp) {
    int fpBottom;
    // If it's the current frame 0, pick up IP from the register
    // otherwise, get IP from the saved area
    if (fp==owner.reg.currentFP()) {     

      return printThisFrameFull(0, owner.reg.currentIP(), fp, owner.reg.currentSP());

    } else {

      int linkaddr = read(fp+registerConstants.STACKFRAME_NEXT_INSTRUCTION_OFFSET);
      // scan down the frame to look for the bottom pointer
      for (fpBottom=fp;fpBottom>fp-400; fpBottom-=4)
	if (read(fpBottom)==fp)
	  break;
      if (fpBottom==fp-400)
	// can't find it after 100 words, assume it's the active frame
	return printThisFrameFull(-1, linkaddr, fp, 0);
      else
	// found it, use it to mark the bottom of this frame
	return printThisFrameFull(-1, linkaddr, fp, fpBottom);
    }
  }

  /**
   * Print one full strack frame.
   * Java stack frame is labeled as followed:
   *  high addr   FP this frame        
   *              method ID
   *              (temp entry, no content)
   *              saved JTOC
   *              ...
   *              local and spill 
   *              return Address
   *  low addr    
   *
   * Native stack frame is labeled as followed:
   *  high addr   
   *              ...
   *  low addr    FP this frame         <- FP register
   *
   * @param depth  the stack depth for this frame
   * @param linkaddr  the instruction pointer for this frame
   * @param fpTop pointer to this frame, or 0 if it's the booter frame
   * @param fpBottom pointer to the callee's frame, or 0 if there is no callee 
   *        below the current frame
   * @return
   * @exception
   * @see printJVMstackTraceFull
   */
  private String printThisFrameFull(int depth, int linkaddr, int fpTop, int fpBottom) {
    int addr, i;
    int limit=0;            // safeguard in case we get a bad fp
    int stopLimit=50;       // give up if stack has more than 50 entries
    BootMap bmap = owner.bootmap();
    int compiledMethodID = bmap.getCompiledMethodID(fpTop, linkaddr);
    String depthString;
    StringBuffer ret = new StringBuffer();
    
    if (depth==-1)
      depthString = "?";
    else 
      depthString = String.valueOf(depth);
          
    // check if this is the frame linking to the system code
    // if so, skip it
    if (isLinkToBoot(fpTop)) {
      return ("frame " + depth + ": " + " <- link to boot C code");
    }


    // For native stack frame
    if (compiledMethodID==NATIVE_METHOD_ID) {
      // Note: getNativeProcedureName() not available on Lintel
      // ret.append("frame " + depth + ": (native) " + getNativeProcedureName(linkaddr) + 
      ret.append("frame " + depth + ": (native) " + 
                 " : " + VM.intAsHexString(linkaddr));
      ret.append('\n');
      for (addr=(fpTop-4); addr>fpBottom; addr-=4) {
	ret.append("   " + Integer.toHexString(addr) + 
                   " : " + dataAtEntry(addr));
        ret.append('\n');
      }

      return ret.toString();
    }


    // For Java stack frame
    VM_Method mth = bmap.findVMMethod(compiledMethodID, true);
    if (mth!=null) {
      String class_name = mth.getDeclaringClass().getName().toString();
      String method_name = mth.getName().toString();
      String method_sig = mth.getDescriptor().toString();
      String line;
      if (depth==0)            // for IP, get the current line
	line = bmap.findLineNumberAsString(compiledMethodID, linkaddr);   
      else                     // for LR, get the previous line
	line = bmap.findPreviousLineNumberAsString(compiledMethodID, linkaddr);  
      ret.append("frame " + depth + ": " + 
                 class_name + "." + method_name + ":" + line + " " + 
                 method_sig + " : " + VM.intAsHexString(linkaddr));
      ret.append('\n');
      // System.out.println("printThisFrameFull: found " + method_name +
      // 			 " for method ID " + compiledMethodID + 
      // 			 " from fp " + VM.intAsHexString(fpTop));
    } else {
      ret.append("frame " + depth + ": " + 
                 "unknown method" + 
                 " : " + VM.intAsHexString(linkaddr));
      ret.append('\n');
    }
    
    
    // label the method ID, return address, JTOC
    ret.append("   " + Integer.toHexString(fpTop) + 
	       " : " + dataAtEntry(fpTop)  + "   <- FP for this frame \n");
    ret.append("   " + Integer.toHexString(fpTop-4) + 
	       " : " + dataAtEntry(fpTop-4)  + "   method ID " + 
	       read(fpTop-4) + "\n");
    ret.append("   " + Integer.toHexString(fpTop-8) + 
	       " : " + dataAtEntry(fpTop-8)  + "   (temp entry)\n");
    ret.append("   " + Integer.toHexString(fpTop-12) + 
	       " : " + dataAtEntry(fpTop-12) + "   saved JTOC \n");
    
    if (fpBottom==0) {
      ret.append("   ...frame bottom unknown... \n");
      return ret.toString();
    }
  
    // the rest is space for paramaters and local vars
    // For a regular JVM frame, try to find the VM_Method to map the 
    // local variables and spill space
    if (mth!=null) {

      for (addr=(fpTop-16); addr>fpBottom+4; addr-=4) {
	ret.append("   " + Integer.toHexString(addr) + 
                   " : " + dataAtEntry(addr));
        ret.append('\n');
      }

    } 

    // if the method info is not available, just print the stack values without comments
    // catch (BmapNotFoundException e) {
    else {

      for (addr=(fpTop-16); addr>fpBottom+4; addr-=4) {
	ret.append("   " + Integer.toHexString(addr) + 
                   " : " + dataAtEntry(addr));
        ret.append('\n');        
	if (limit++>stopLimit) {
	  ret.append("   ...abort after " +  stopLimit + " frames...\n");
	  return ret.toString();
	}
      }
    } 
    // if stack too large, it may have been corrupted
    // catch (Exception e1) {
    // 	 System.out.println("   ...aborted, stack may be corrupted...");
    // 	 return;
    // }

    if (depth!=0)
      ret.append("   " + Integer.toHexString(addr) + 
		 " : " + dataAtEntry(addr) + "   return address \n");

    return ret.toString();
  } 



  // read a stack entry, check the switch to print the decimal column
  private String dataAtEntry(int address) {
    int data = read(address);
    String blank = "         ";
    String num = String.valueOf(data);
    if (Debugger.stackPreference=='d')
      return VM.intAsHexString(data) + "  " + blank.substring(num.length()) + data;
    else
      return VM.intAsHexString(data);
  }


  /**
   * Traverse to the top of the stack to see how deep it is from 
   * the current instruction.
   * We have to start from one frame up because the next_instruction slot
   * for the current frame is not filled with a valid address
   * @return the total number of stack frame
   * @exception
   * @see
   */
  public int findFrameCount() {
    int fp = read(owner.reg.currentFP());
    int count = findFrameCountFrom(fp); // not including current frame
    return count + 1;
  }
  
  /**
   * Traverse to the top of the stack to see how deep it is from 
   * the current frame.
   * @param startFP a valid frame pointer
   * @return the number of stack frames starting from this frame pointer
   * @exception
   * @see findFrameCount
   */
  public int findFrameCountFrom(int startFP) {
    boolean reach_top = false;
    int depth = 0;
    int fp = startFP;
    // int linkaddr = frameIP(fp);
    
    // traverse to the top of stack, or at least 1000 frames
    while (!reach_top && (depth < 1000)) {      
      depth++;
      fp = read(fp);
      // linkaddr = frameIP(fp);
      if (fp==0)
	reach_top = true;
    } 
    return depth;
  }

  /**
   * Traverse up n frame starting from a particular frame
   * @param numframe   the number of frames to traverse upward
   * @param startFP    a valid frame pointer
   * @return the frame pointer at this frame
   */
  public int upNumberOfFrame(int numframe, int startFP) throws Exception {
    boolean reach_top = false;
    int depth = 0;
    int fp = startFP;
    // int linkaddr = frameIP(fp);
    
    // traverse to the desired depth
    while (!reach_top && (depth < numframe)) {      
      depth++;
      //  System.out.println(depth + ": " + VM.intAsHexString(fp) + ", " + 
      // 			    VM.intAsHexString(linkaddr));
      fp = read(fp);
      // linkaddr = frameIP(fp);
      if (fp==0)
	reach_top = true;
    } 
    if (reach_top)
      throw new Exception("frame number out of bound");
    else
      return fp;
  }

  /**
   * Get the instruction pointer of this stack frame
   * @param fp a valid frame pointer
   * @return the instruction pointer
   */
  public int frameIP(int fp) {
    return read(fp+registerConstants.STACKFRAME_NEXT_INSTRUCTION_OFFSET);
  }

  

  /**
   * Walk the stack to see if we are in a particular class and method
   * This is used in the internal debugger to determine if an exception
   * is from the debugger's normal execution rather than from the program.
   * Done by walking back the stack to check each stack frame
   * @param className the class to test
   * @param methodName the method to test
   * @return true if a stack frame is from this class and method
   * @see Debugger
   */
  public boolean calledFrom(String className, String methodName) {
    int linkaddr, fp;
    int depth = 0;

    // first traverse to the desired depth
    linkaddr = owner.reg.currentIP();
    fp = owner.reg.currentFP();
    while (linkaddr!=0) {
      fp = read(fp);                   
      linkaddr = read(fp+registerConstants.STACKFRAME_NEXT_INSTRUCTION_OFFSET); 
      if (linkaddr==-1 || fp==-1) {
	System.out.println("ERROR:  stack corrupted at frame " + depth + " or earlier");
	return false;
      }
      depth++;
    }

    return false;
  }


  /**
   * Disassemble a range of address
   * @param address a random instruction address
   * @param count the number of words to disassemble
   * @return
   * @exception
   * @see
   */
  public String listInstruction(int address, int count) {
    int IP, i, thread;
    int instr[] = new int[count*MAX_INSTRUCTION_SIZE];
    thread = 0;                   // TODO: place holder for multithread later

    for (IP=address, i=0; IP<address+count*4; IP+=4, i++) {
      instr[i] = readNoBP(thread, IP);
      if (instr[i]==-1)
	return "invalid address";
    }

    // System.out.println("listInstruction: address = " + Integer.toHexString(address));
    return IntelDisassembler.disasm(instr, count, address);

  }


  /**
   *
   * Print the current instruction with:
   *   machine instruction,
   *   class + method name,
   *   line number
   * This is for the hardware state, NOT for the context thread
   * @param
   * @return
   * @exception
   * @see
   */
  public String printCurrentInstr() {
    int IP, FP, compiledMethodID, branch_addr, thread;
    int instr[] = new int[MAX_INSTRUCTION_SIZE];
    String method_name, branch_target;
    String line, bcnum;
    BootMap bmap = owner.bootmap();
    
    IP = owner.reg.hardwareIP();

    try {
      FP = owner.reg.read("FP");
      thread = 0;               // TODO: place holder for multithread later
      
      /* read enough from memory for 1 instruction */
      for (int i=0; i<MAX_INSTRUCTION_SIZE; i++)
	instr[i] = readNoBP(thread, IP+(i*4));

       if (isLinkToBoot(FP)) {
         return (
		 //Integer.toHexString(IP) + " : " +
		 // Integer.toHexString(instr) + 
		 IntelDisassembler.disasm(instr, 1, IP) +
		 "  (boot image loader)"); 
       }
       
       compiledMethodID = bmap.getCompiledMethodID(FP, IP);
       
       if (compiledMethodID==NATIVE_METHOD_ID) {
         return (
		 // Integer.toHexString(IP) + " : " + 
		 // Integer.toHexString(instr) + 
		 IntelDisassembler.disasm(instr, 1, IP) +
		 "  (native " + getNativeProcedureName(IP) + ")");
       }
       
       // look up the class/method for this address
       VM_Method mth = bmap.findVMMethod(compiledMethodID, true);
       
       if (mth!=null) {
       
         method_name = mth.getDeclaringClass().getName().toString();
         method_name += "." + mth.getName().toString();
         
         // look up the symbolic name and line number of the target if this is a branch
         // INTEL_TEMP: add test for branch
         // branch_addr = branchTarget(instr, IP);
         branch_addr = -1;
         if (branch_addr!=-1) {
           line = bmap.findLineNumberAsString(compiledMethodID, branch_addr);
           branch_target = bmap.findClassMethodName(compiledMethodID, true) + ":" + line;
         } else {
           branch_target = "";
         }
         
         // look up the line number
         line = bmap.findLineNumberAsString(compiledMethodID, IP);
         
         return (
		 // Integer.toHexString(IP) + " : " + 
		 // Integer.toHexString(instr) + 
		 IntelDisassembler.disasm(instr, 1, IP) +
		 "  (" + method_name +  ":" + line + ")" );
		 // "    " + branch_target);
       } else {
         return (
		 // Integer.toHexString(IP) + " : " + 
		 // Integer.toHexString(instr) + 
		 IntelDisassembler.disasm(instr, 1, IP) + 
		 "  (unknown method)");
       }
       // return (IntelDisassembler.disasm(instr, 1, IP));
      
    } catch (Exception e) {
      return ("ERROR: " + e.getMessage());
    }
  }




}

