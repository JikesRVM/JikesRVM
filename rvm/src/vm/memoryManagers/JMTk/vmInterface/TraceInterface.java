/**
 * (C) Copyright Department of Computer Science,
 * University of Massachusetts, Amherst. 2003.
 */
package com.ibm.JikesRVM.memoryManagers.vmInterface;

import com.ibm.JikesRVM.classloader.VM_Method;
import com.ibm.JikesRVM.classloader.VM_MemberReference;
import com.ibm.JikesRVM.classloader.VM_Type;

//-#if RVM_WITH_OPT_COMPILER
import com.ibm.JikesRVM.opt.VM_OptCompiledMethod;
import com.ibm.JikesRVM.opt.VM_OptMachineCodeMap;
import com.ibm.JikesRVM.opt.VM_OptEncodedCallSiteTree;
//-#endif

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_BaselineCompiledMethod;
import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.VM_CompiledMethods;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_MiscHeader;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_Processor;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_AddressArray;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Extent;
import com.ibm.JikesRVM.VM_Offset;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
/**
 * Class that supports scanning Objects or Arrays for references
 * during tracing, handling those references, and computing death times
 *
 * @author <a href="http://www-ali.cs.umass.edu/~hertz">Matthew Hertz</a>
 * @version $Revision$
 * @date $Date$
 */
public final class TraceInterface implements VM_Constants, VM_Uninterruptible {

  public final static String Id = "$Id$"; 

  /***********************************************************************
   *
   * Class variables
   */
  private static byte allocCallMethods[][];

  static {
    /* Build the list of "own methods" */
    allocCallMethods = new byte[13][];
    allocCallMethods[0] = "postAlloc".getBytes();
    allocCallMethods[1] = "traceAlloc".getBytes();
    allocCallMethods[2] = "allocateScalar".getBytes();
    allocCallMethods[3] = "allocateArray".getBytes();
    allocCallMethods[4] = "clone".getBytes();
    allocCallMethods[5] = "alloc".getBytes();
    allocCallMethods[6] = "buildMultiDimensionalArray".getBytes();
    allocCallMethods[7] = "resolvedNewScalar".getBytes();
    allocCallMethods[8] = "resolvedNewArray".getBytes();
    allocCallMethods[9] = "unresolvedNewScalar".getBytes();
    allocCallMethods[10] = "unresolvedNewArray".getBytes();
    allocCallMethods[11] = "cloneScalar".getBytes();
    allocCallMethods[12] = "cloneArray".getBytes();
  }

  /***********************************************************************
   *
   * Public Methods
   */

  /**
   * Returns if the VM is ready for a garbage collection.
   *
   * @return True if the RVM is ready for GC, false otherwise.
   */
  public static final boolean gcEnabled() {
    /* This test is based upon a review of the code and trial-and-error */
    return VM_Processor.getCurrentProcessor().threadSwitchingEnabled() && 
      VM_Scheduler.allProcessorsInitialized;
  }

  /**
   * Given a method name, determine if it is a "real" method or one
   * used for allocation/tracing.
   *
   * @param name The method name to test as an array of bytes
   * @return True if the method is a "real" method, false otherwise.
   */
  private static final boolean isAllocCall(byte[] name) {    
    for (int i = 0; i < allocCallMethods.length; i++) {
      byte[] funcName = VM_Interface.getArrayNoBarrier(allocCallMethods, i);
      if (VM_Magic.getArrayLength(name) == VM_Magic.getArrayLength(funcName)) {
        /* Compare the letters in the allocCallMethod */
	int j = VM_Magic.getArrayLength(funcName) - 1;
        while (j >= 0) {
	  if (VM_Interface.getArrayNoBarrier(name, j) != 
	      VM_Interface.getArrayNoBarrier(funcName, j))
	    break;
	  j--;
	}
        if (j == -1)
          return true;
      }
    }
    return false;
  }

  /**
   * This adjusts the offset into an object to reflect what it would look like
   * if the fields were laid out in memory space immediately after the object
   * pointer.
   *
   * @param isScalar If this is a pointer store to a scalar object
   * @param offset The offset into the object of the field being updated for 
   * the trace
   * @return The easy to understand offset of the slot
   */
  public static final VM_Offset adjustSlotOffset(boolean isScalar, 
						 int offset) {
    /* Offset scalar objects so that the fields appear to begin at offset 0
       of the object. */
    if (isScalar)
      return VM_Offset.fromInt(getHeaderEndOffset() - offset);
    else
      return VM_Offset.fromInt(offset);
  }

  /**
   * This skips over the frames added by the tracing algorithm, outputs 
   * information identifying the method the containts the "new" call triggering
   * the allocation, and returns the address of the first non-trace, non-alloc
   * stack frame.
   *
   *@param tib The tib of the object just allocated
   *@return The frame pointer address for the method that allocated the object
   */
  public static final VM_Address skipOwnFramesAndDump(Object[] tib)
    throws VM_PragmaNoInline {
    VM_Method m = null;
    int bci = -1;
    int compiledMethodID = 0;
    VM_Offset ipOffset = VM_Offset.zero();
    VM_Address fp = VM_Magic.getFramePointer();
    VM_Address ip = VM_Magic.getReturnAddress(fp);
    fp = VM_Magic.getCallerFramePointer(fp);
    // This code borrows heavily from VM_Scheduler.dumpStack
    while (VM_Magic.getCallerFramePointer(fp).NE(STACKFRAME_SENTINEL_FP)) {
      compiledMethodID = VM_Magic.getCompiledMethodID(fp);
      if (compiledMethodID != INVISIBLE_METHOD_ID) {
	// normal java frame(s)
        VM_CompiledMethod compiledMethod = 
	  VM_CompiledMethods.getCompiledMethod(compiledMethodID);
	if (compiledMethod.getCompilerType() != VM_CompiledMethod.TRAP) {
	  ipOffset = (ip.diff(VM_Magic.objectAsAddress(compiledMethod.getInstructions())));
	  m = compiledMethod.getMethod();
	  //-#if RVM_WITH_OPT_COMPILER
	  if (compiledMethod.getCompilerType() == VM_CompiledMethod.OPT) {
	    VM_OptCompiledMethod optInfo = (VM_OptCompiledMethod)compiledMethod;
	    /* Opt stack frames may contain multiple inlined methods. */
	    VM_OptMachineCodeMap map = optInfo.getMCMap();
	    int iei = map.getInlineEncodingForMCOffset(ipOffset.toInt());
	    if (iei >= 0) {
	      int[] inlineEncoding = map.inlineEncoding;
	      boolean allocCall = true;
	      bci = map.getBytecodeIndexForMCOffset(ipOffset.toInt());
	      for (int j = iei; j >= 0 && allocCall; 
		   j = VM_OptEncodedCallSiteTree.getParent(j,inlineEncoding)) {
		int mid = VM_OptEncodedCallSiteTree.getMethodID(j, inlineEncoding);
		m = VM_MemberReference.getMemberRef(mid).asMethodReference().getResolvedMember();
 		if (!isAllocCall(m.getName().getBytes()))
		  allocCall = false;
		if (j > 0)
		  bci = VM_OptEncodedCallSiteTree.getByteCodeOffset(j, 
								 inlineEncoding);
	      }
	      if (!allocCall)
		break;
	    }
	  } else 
	  //-#endif
	  {
	    if (!isAllocCall(m.getName().getBytes())) {
	      VM_BaselineCompiledMethod baseInfo = 
		(VM_BaselineCompiledMethod)compiledMethod;
	      bci = baseInfo.findBytecodeIndexForInstruction(ipOffset.toInt());
	      break;
	    }
	  }
	}
      }
      ip = VM_Magic.getReturnAddress(fp);
      fp = VM_Magic.getCallerFramePointer(fp);
    }
    if (m != null) {
      int allocid = (((compiledMethodID & 0x0000ffff) << 15) ^
		     ((compiledMethodID & 0xffff0000) >> 16) ^ 
		     ipOffset.toInt()) & ~0x80000000;
    
      /* Now print the location string. */
      VM.write('\n');
      VM.writeHex(allocid);
      VM.write('-');
      VM.write('>');
      VM.write('[');
      VM.writeHex(compiledMethodID);
      VM.write(']');
      m.getDeclaringClass().getDescriptor().sysWrite();
      VM.write(':');
      m.getName().sysWrite();
      m.getDescriptor().sysWrite();
      VM.write(':');
      VM.writeHex(bci);
      VM.write('\t');
      VM_Type type = VM_Magic.objectAsType(tib[TIB_TYPE_INDEX]);
      type.getDescriptor().sysWrite();
      VM.write('\n');
    }
    return fp;
  }

  /***********************************************************************
   *
   * Wrapper methods
   */

  public static void updateDeathTime(Object obj) throws VM_PragmaInline {
    VM_MiscHeader.updateDeathTime(obj);
  }

  public static void setDeathTime(VM_Address ref, VM_Word time_) 
    throws VM_PragmaInline {
    VM_MiscHeader.setDeathTime(ref, time_);
  }

  public static void setLink(VM_Address ref, VM_Address link) 
    throws VM_PragmaInline {
    VM_MiscHeader.setLink(ref, link);
  }

  public static void updateTime(VM_Word time_) throws VM_PragmaInline {
    VM_MiscHeader.updateTime(time_);
  }

  public static VM_Word getOID(VM_Address obj) throws VM_PragmaInline {
    return VM_MiscHeader.getOID(obj);
  }

  public static VM_Word getDeathTime(VM_Address ref) throws VM_PragmaInline {
    return VM_MiscHeader.getDeathTime(VM_Magic.addressAsObject(ref));
  }

  public static VM_Address getLink(VM_Address ref) throws VM_PragmaInline {
    return VM_MiscHeader.getLink(VM_Magic.addressAsObject(ref));
  }

  public static VM_Address getBootImageLink() throws VM_PragmaInline {
    return VM_MiscHeader.getBootImageLink();
  }

  public static VM_Word getOID() throws VM_PragmaInline {
    return VM_MiscHeader.getOID();
  }

  public static void setOID(VM_Word oid_) throws VM_PragmaInline {
    VM_MiscHeader.setOID(oid_);
  }

  public static final int getHeaderSize() throws VM_PragmaInline {
    return VM_MiscHeader.getHeaderSize();
  }

  public static final int getHeaderEndOffset() throws VM_PragmaInline {
    return VM_ObjectModel.getHeaderEndOffset();
  }
}
