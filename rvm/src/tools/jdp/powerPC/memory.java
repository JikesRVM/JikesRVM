/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
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

abstract class memory implements jdpConstants, VM_Constants,
JDPServiceInterface
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
  
  /**
   * Return the contents of a JTOC slot in the debuggee
   *
   * @param slot 
   */
  public int readJTOCSlot(int slot) {
    return readTOC(slot);
  }

  public int readMemory(int address) {
    try {
      return readsafe(address);
    } catch (memoryException e) {
      System.out.println("bad address");
      return 0;
    }
  }
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
    case 2:
      data = data >> 8;
      break;
    case 1:
      data = data >> 16;
      break;
    case 0:
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
    
    if (lobits==0) {
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
    int nextFp = read(fp+STACKFRAME_FRAME_POINTER_OFFSET);
    if (nextFp==-2)
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
    if (framePointer==0)
      return printstack(owner.reg.currentFP(), width);
    else
      return printstack(framePointer, width);
  }

  /* print the stack frame */
  /**
   * Print the current stack frame in raw form, with pointers to LR and SP
   * @param width the number of words to display at the top and bottom of 
   *        this frame
   * @return
   * @exception
   * @see
   */
  public String printstack(int framePointer, int width) {    
    int currfp, prevfp, sp, data;
    int[] start, stop;
    StringBuffer ret = new StringBuffer();
    currfp = framePointer;
    prevfp = read(currfp);
    sp = owner.reg.currentSP();
    if (sp > prevfp || sp < currfp)    // is sp valid?  inside this stack frame
      sp = -1;

    // safety check to make sure the display is viewable
    if ((prevfp - currfp)>(width*4*2)) {
      start = new int[2];
      stop = new int[2];
      start[0] = prevfp;
      stop[0] = prevfp-width*4;
      start[1] = currfp+width*4;
      stop[1] = currfp;
    } else {
      start = new int[1];
      stop = new int[1];
      start[0] = prevfp;
      stop[0] = currfp;
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
        String result = "   " + VM.intAsHexString(i) + " : " + VM.intAsHexString
        (data);
        String blank = "         ";
        String num = String.valueOf(data);
        if (Debugger.stackPreference=='d')
          result += "   " + blank.substring(num.length()) + data;
        if (i==currfp)
          result += " <- current FP";
        else if (i==prevfp)
          result += " <- previous FP";
        else if (i==sp)
          result += " <- SP";
        ret.append(result);
        ret.append('\n');
      }
    }
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
    int linkaddr = read(fp+STACKFRAME_NEXT_INSTRUCTION_OFFSET);
    StringBuffer ret = new StringBuffer();
    while (depth<=limit) {
      ret.append(printThisFrame(-1,linkaddr,fp));
      fp = read(fp);              // up to next stack frame
      if (fp==STACKFRAME_SENTINAL_FP)
	break;      
      linkaddr = read(fp+STACKFRAME_NEXT_INSTRUCTION_OFFSET);
      if (linkaddr==-1 || fp==-1) {
	return ("ERROR 1:  stack corrupted at frame " + depth + " or earlier");
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
    while (depth<from) {
      fp = read(fp);                   
      if (fp==STACKFRAME_SENTINAL_FP)
	break;
      linkaddr = read(fp+STACKFRAME_NEXT_INSTRUCTION_OFFSET);
      if (linkaddr==-1 || fp==-1) {
	return ("ERROR 2:  stack corrupted at frame " + depth + " or earlier");
      }
      depth++;
    }
    if ((fp==STACKFRAME_SENTINAL_FP) && (depth<from)) {
      return ("Requested frame " + from + " is beyond current stack depth: " + depth);
    }

    // then walk the stack to the desired end
    while (depth<=to) {
      ret.append(printThisFrame(depth,linkaddr,fp));
      // System.out.println(depth + "  " + Integer.toHexString(fp));
      fp = read(fp);              // up to next stack frame
      if (fp==STACKFRAME_SENTINAL_FP)
	break;
      linkaddr = read(fp+STACKFRAME_NEXT_INSTRUCTION_OFFSET);
      if (linkaddr==-1 || fp==-1) {
	return ("ERROR 3:  stack corrupted at frame " + depth + " or earlier");
      }
      depth++;
      // System.out.println(depth + ": " + Integer.toHexString(fp) + ", " + Integer.toHexString(linkaddr));
    }

    if (fp!=STACKFRAME_SENTINAL_FP) {
      int remain = findFrameCountFrom(fp);
      if (remain==1000)
      {
	ret.append("    1 ...(more than 1000 frames remain)...");
	ret.append(Integer.toHexString(fp));	
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
    VM_CompiledMethod compMethod;
    // System.out.println("methodID " + methodID);
    BootMap bmap = owner.bootmap();
    String depthString;
    StringBuffer ret = new StringBuffer();
    if (depth==-1)
      depthString = "?";
    else 
      depthString = String.valueOf(depth);

    // if we are still in the prolog of the current method, the link to the
    // calling stack frame is not set up yet, so indicate that it is missing
    if  (!bmap.isFpReady(linkaddr)) {
      return (depthString + "  0x........   (stack frame being built in prolog)");
    }

    // At the end of the stack frame is the link to the C code for booting
    if (isLinkToBoot(fp)) {
      return (depthString + "  0x" + Integer.toHexString(linkaddr) + 
              "   <- link to boot C code");
    }
    
    int compiledMethodID = bmap.getCompiledMethodID(fp, linkaddr);

    if (compiledMethodID==NATIVE_METHOD_ID) {
      ret.append(depthString + "  0x" + Integer.toHexString(linkaddr) +
                 "   (native) " + getNativeProcedureName(linkaddr));
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
      	else {                    // for LR, get the previous line (for baseline)
	  compMethod = bmap.findVMCompiledMethod(compiledMethodID, true);
	  if (compMethod != null && 
	      compMethod.getCompilerType() == VM_CompiledMethod.OPT)
	    line = bmap.findLineNumberAsString(compiledMethodID, linkaddr);
	  else 
	    line = bmap.findPreviousLineNumberAsString(compiledMethodID, linkaddr); 
	}

	String offset = Integer.toHexString(bmap.instructionOffset(compiledMethodID, linkaddr));
      	ret.append(depthString + "  0x" + Integer.toHexString(linkaddr) + 
                   "   " + class_name + "." + method_name + 
		   "(+0x" + offset + ") " +
                   ": " + line + "   " + method_sig);
        ret.append('\n');
      } else {
      	ret.append(depthString + "  0x" + Integer.toHexString(linkaddr) + 
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
    int linkaddr, fp, sp, depth, start_findex, saved_fp;
    int i, findex, methodID;
    Vector fpvect = new Vector(20,20);
    Vector lrvect = new Vector(20,20);
    boolean reach_top;
    StringBuffer ret = new StringBuffer();
    
    // first traverse to the desired depth-1
    reach_top = false;
    depth = 0;
    fp = owner.reg.currentFP();           // start with current frame
    linkaddr = owner.reg.currentIP();
    while (depth<from-1) {
      fp = read(fp);                   
      if (fp==STACKFRAME_SENTINAL_FP)
	break;
      linkaddr = read(fp+STACKFRAME_NEXT_INSTRUCTION_OFFSET);
      if (linkaddr==-1 || fp==-1) {
	return ("ERROR 4:  stack corrupted at frame " + depth + " or earlier");
      }
      depth++;
    }

    // System.out.println("depth " + depth + ", linkaddr " + linkaddr);
    if (depth<=from-1) {
      if (linkaddr==0 || read(fp+STACKFRAME_NEXT_INSTRUCTION_OFFSET)==0) {
	return ("Requested frame " + from + " is beyond current stack depth: " + depth);
      }
    }
    
    // second, traverse for the desired section, saving fp and linkaddr
    while (depth <= to) {      
      fpvect.addElement(new Integer(fp));
      lrvect.addElement(new Integer(linkaddr));
      depth++;
      fp = read(fp);
      if (fp==STACKFRAME_SENTINAL_FP) {
	reach_top = true;
	break;
      }
      linkaddr = read(fp+STACKFRAME_NEXT_INSTRUCTION_OFFSET);
    }
    saved_fp = fp;

    // debug dump
    // for (findex = 0 ; findex<fpvect.size(); findex++) {
    // 	    fp = ((Integer) fpvect.elementAt(findex)).intValue();
    // 	    linkaddr = ((Integer) lrvect.elementAt(findex)).intValue();
    // 	    System.out.println(" " + findex + ": " + Integer.toHexString(fp)+ 
    // 			       " " + Integer.toHexString(linkaddr) );
    // }

    // if the specified range does not include the first frame, 
    // we have saved one extra frame saved to compute the SP value
    if (from==0)
      start_findex = 0;
    else 
      start_findex = 1;   

    // Now walk and print this range of stack frame
    for (findex = start_findex, depth=from ; findex<fpvect.size(); findex++, depth++) {
      VM_Method curr_method;
      int arg_size;

      // get FP and IP for this frame from the saved list
      linkaddr = ((Integer) lrvect.elementAt(findex)).intValue();
      fp = ((Integer) fpvect.elementAt(findex)).intValue();

      // print the frame
      ret.append(printThisFrameFull(depth, linkaddr, fp));
      

    } 

    if (!reach_top) {
      int remain = findFrameCountFrom(saved_fp);
      if (remain==1000)
      {
	ret.append("    2 ...(more than 1000 frames remain)...");
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
    // If it's the current frame 0, pick up IP from the register
    // otherwise, get IP from the saved area
    if (fp==owner.reg.currentFP()) {     
      return printThisFrameFull(0, owner.reg.currentIP(), fp);
    } else {
      int linkaddr = read(fp+STACKFRAME_NEXT_INSTRUCTION_OFFSET);
      return printThisFrameFull(-1, linkaddr, fp);
    }
  }

  /**
   * Print one full strack frame.
   * Java stack frame is labeled as followed:
   *  high addr   saved GPR           (saved SP for base line)
   *              local                 <- FP + getFirstLocalOffset()
   *              local
   *              local
   *              expression stack      <- FP + getEmptyStackOffset() - 4
   *              expression stack
   *              expression stack
   *              spill                 <- FP + getMaxSpillOffset()
   *              spill
   *              spill 
   *              return Address
   *              method ID
   *  low addr    FP this frame         <- FP register
   *
   * Native stack frame is labeled as followed:
   *  high addr   saved FPRs
   *              saved GPRs
   *              local stack
   *              arguments passed
   *              saved TOC
   *              reserved
   *              reserved
   *              return Address
   *              saved CR
   *  low addr    FP this frame         <- FP register
   *
   * @param depth  the stack depth for this frame
   * @param linkaddr  the instruction pointer for this frame
   * @param fp value for the frame pointer to access the frame contents
   * @return
   * @exception
   * @see printJVMstackTraceFull
   */
  private String printThisFrameFull(int depth, int linkaddr, int fp) {
    int addr, data, i;
    int limit=0;            // safeguard in case we get a bad fp
    int stopLimit=50;       // give up if stack has more than 50 entries
    BootMap bmap = owner.bootmap();
    int compiledMethodID = bmap.getCompiledMethodID(fp, linkaddr);
    String depthString;
    StringBuffer ret = new StringBuffer();
    
    if (depth==-1)
      depthString = "?";
    else 
      depthString = String.valueOf(depth);
          
    // check if this is the frame linking to the system code
    // if so, skip it
    if (isLinkToBoot(fp)) {
      return ("frame " + depth + ": " + " <- link to boot C code");
    }

    // For native stack frame
    if (compiledMethodID==NATIVE_METHOD_ID) {
      ret.append("frame " + depth + ": (native) " + getNativeProcedureName(linkaddr) + 
                 " : " + VM.intAsHexString(linkaddr));
      ret.append('\n');
      int top = read(fp);
      for (addr=(top-4); addr>fp+24; addr-=4) {
	ret.append("   " + Integer.toHexString(addr) + 
                   " : " + dataAtEntry(addr));
        ret.append('\n');
      }
      addr -= 4;
      ret.append("   " + Integer.toHexString(addr) + " : " + dataAtEntry(addr) + "    saved TOC");
      ret.append('\n');
      addr -= 4;
      ret.append("   " + Integer.toHexString(addr) + " : " + dataAtEntry(addr) + "    reserved");
      ret.append('\n');
      addr -= 4;
      ret.append("   " + Integer.toHexString(addr) + " : " + dataAtEntry(addr) + "    reserved");
      ret.append('\n');
      addr -= 4;
      ret.append("   " + Integer.toHexString(addr) + " : " + dataAtEntry(addr) + "    return address");
      ret.append('\n');
      addr -= 4;
      ret.append("   " + Integer.toHexString(addr) + " : " + dataAtEntry(addr) + "    saved CR");
      ret.append('\n');
      addr -= 4;
      ret.append("   " + Integer.toHexString(addr) + " : " + dataAtEntry(addr) + "    next FP");
      ret.append('\n');

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
    } else {
      ret.append("frame " + depth + ": " + 
                 "unknown method" + 
                 " : " + VM.intAsHexString(linkaddr));
      ret.append('\n');
    }


    // For a regular JVM frame, try to find the VM_Method to map the 
    // local variables and spill space
    if (mth!=null) {
      int localOffset = VM_Compiler.getFirstLocalOffset(mth);
      int expressionStackOffset = VM_Compiler.getEmptyStackOffset(mth) - 4;
      int spillOffset = VM_Compiler.getMaxSpillOffset(mth);
      //	System.out.println("printThisFrameFull: local at " + localOffset + 
      //			   ", expression at " + expressionStackOffset +
      //			   ", spill at " + spillOffset);

      // label the saved GPR value (for base line compiler, this is the saved SP)
      addr = fp+localOffset+4;
      ret.append("   " + Integer.toHexString(addr) + 
                 " : " + dataAtEntry(addr) + "   saved GPR ");
      ret.append('\n');
      
      // label the local variables
      for (addr=(fp+localOffset), i=0; addr>fp+expressionStackOffset; addr-=4, i++) {
	ret.append("   " + Integer.toHexString(addr) + 
                   " : " + dataAtEntry(addr) + "   local vars " + i);
        ret.append('\n');
	if (limit++>stopLimit) { 
	  return ("   ...aborted, stack may be corrupted...");      
	}
      }

      // label the expression stack
      for (addr=(fp+expressionStackOffset), i=0; 
	   addr>fp+spillOffset;
	   addr-=4, i++) {
	ret.append("   " + Integer.toHexString(addr) + 
                   " : " + dataAtEntry(addr) + "   expression stack " + i);
        ret.append('\n');
	if (limit++>stopLimit) {
	  return ("   ...aborted, stack may be corrupted...");
	  
	}
      }      

      // label the spill area for arguments
      for (addr=(fp+spillOffset), i=0; 
	   addr>fp+STACKFRAME_NEXT_INSTRUCTION_OFFSET; 
	   addr-=4, i++) {
	ret.append("   " + Integer.toHexString(addr) + 
                   " : " + dataAtEntry(addr) + "   spilled args " + i);
        ret.append('\n');
	if (limit++>stopLimit) {
	  return ("   ...aborted, stack may be corrupted...");      
	}
      }
    } 
    // if the method info is not available, just print the stack values without comments
    // catch (BmapNotFoundException e) {
    else {
      int top = read(fp+STACKFRAME_FRAME_POINTER_OFFSET);
      addr = top-8;
      ret.append("   " + Integer.toHexString(addr) + 
                 " : " + dataAtEntry(addr) + "   saved FPR ");
      ret.append('\n');
      for (addr=(top-12); addr>fp+STACKFRAME_NEXT_INSTRUCTION_OFFSET; addr-=4) {
	ret.append("   " + Integer.toHexString(addr) + 
                   " : " + dataAtEntry(addr));
        ret.append('\n');        
	if (limit++>stopLimit) {
	  return ("   ...aborted, stack may be corrupted...");
	}
      }
    } 
    // if stack too large, it may have been corrupted
    // catch (Exception e1) {
    // 	 System.out.println("   ...aborted, stack may be corrupted...");
    // 	 return;
    // }

    addr = fp+STACKFRAME_NEXT_INSTRUCTION_OFFSET;
    ret.append("   " + Integer.toHexString(addr) + 
               " : " + dataAtEntry(addr) + "   return address ");
    ret.append('\n');
    addr = fp+STACKFRAME_METHOD_ID_OFFSET;
    ret.append("   " + Integer.toHexString(addr) + 
               " : " + dataAtEntry(addr) + "   method ID ");
    ret.append('\n');
    addr = fp+STACKFRAME_FRAME_POINTER_OFFSET;
    ret.append("   " + Integer.toHexString(addr) + 
               " : " + dataAtEntry(addr) + "   <- FP for this frame ");
    ret.append('\n');
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
    
    // traverse to the top of stack, or at least 1000 frames
    while (!reach_top && (depth < 1000)) {      
      depth++;
      fp = read(fp);
      if (fp==STACKFRAME_SENTINAL_FP)
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
    
    // traverse to the desired depth
    while (!reach_top && (depth < numframe)) {      
      depth++;
      fp = read(fp);
      if (fp==STACKFRAME_SENTINAL_FP)
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
    return read(fp+STACKFRAME_NEXT_INSTRUCTION_OFFSET);
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
      linkaddr = read(fp+STACKFRAME_NEXT_INSTRUCTION_OFFSET); 
      if (linkaddr==-1 || fp==-1) {
	System.out.println("ERROR 5:  stack corrupted at frame " + depth + " or earlier");
	return false;
      }
      // thisMethod = VM_Magic.findMethodForInstruction(linkaddr);
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
    int IP, instr, branch_addr, thread;
    BootMap bmap = owner.bootmap();
    String branch_target, line;
    StringBuffer ret = new StringBuffer();
    thread = 0;                           // TODO: place holder for multithread later
    if (count < 0) {
      count = -count;
      address = address-(count*4);
      count += 1; // +1 so original address gets listed too
    }

    for (IP=address; IP<address+count*4; IP+=4) {
      instr = readNoBP(thread, IP);
      if (instr==-1)
	return ret.toString();                           // bad address, return
      ret.append(Integer.toHexString(IP) + " : " + 
                 Integer.toHexString(instr) + "  " +  
                 PPC_Disassembler.disasm(instr, IP));
      ret.append('\n');
    }

    return ret.toString();
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
    int IP, FP, compiledMethodID, instr, branch_addr, thread;
    String method_name, branch_target;
    String line, bcnum;
    BootMap bmap = owner.bootmap();

    IP = owner.reg.hardwareIP();
    try {
      FP = owner.reg.read("FP");
      thread = 0;               // TODO: place holder for multithread later
      instr = readNoBP(thread, IP);

      if (isLinkToBoot(FP)) {
        return (Integer.toHexString(IP) + " : " +
                Integer.toHexString(instr) + "  (boot image loader)" +
                PPC_Disassembler.disasm(instr, IP) ); 
      }

      compiledMethodID = bmap.getCompiledMethodID(FP, IP);

      if (compiledMethodID==NATIVE_METHOD_ID) {
        return (Integer.toHexString(IP) + " : " + 
                Integer.toHexString(instr) + 
                "  (native " + getNativeProcedureName(IP) + ")" + 
                PPC_Disassembler.disasm(instr, IP) );
      }

      VM_Method mth;

      if (compiledMethodID == 0)
	mth = null;
      else 
	// look up the class/method for this address
        mth = bmap.findVMMethod(compiledMethodID, true);

      if (mth!=null) {

        method_name = mth.getDeclaringClass().getName().toString();
        method_name += "." + mth.getName().toString();
        
        // look up the symbolic name and line number of the target if this is a branch
        branch_addr = branchTarget(instr, IP);
        if (branch_addr!=-1) {
          line = bmap.findLineNumberAsString(compiledMethodID, branch_addr);
          branch_target = bmap.findClassMethodName(compiledMethodID, true) + ":" + line;
        } else {
          branch_target = "";
        }
        
        // look up the line number
        line = bmap.findLineNumberAsString(compiledMethodID, IP);
        
        return (Integer.toHexString(IP) + " : " + 
                Integer.toHexString(instr) + 
                "  (" + method_name +  ":" + line + ")" +
                PPC_Disassembler.disasm(instr, IP) +
                "    " + branch_target);
      } else {
        return (Integer.toHexString(IP) + " : " + 
                Integer.toHexString(instr) + 
                "  (unknown method)" + 
                PPC_Disassembler.disasm(instr, IP));
      }
      
    } catch (Exception e) {
      return ("ERROR: " + e.getMessage());
    }
  }




}

