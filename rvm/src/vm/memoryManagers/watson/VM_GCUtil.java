/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Common utility functions used by various garbage collectors
 *
 * @author Stephen Smith
 */  
public class VM_GCUtil
  implements VM_Constants, VM_GCConstants, VM_Uninterruptible {

  private final static boolean TRACE = false;

  static int bootImageStart;
  static int bootImageEnd;
  static int heapStart;
  static int heapEnd;
  static int largeStart;
  static int largeEnd;

  static int minBootRef;
  static int maxBootRef;
  static int minHeapRef;
  static int maxHeapRef;
  static int minLargeRef;
  static int maxLargeRef;

  static Object[] tibForArrayType;
  static Object[] tibForClassType;
  static Object[] tibForPrimitiveType;

  static void boot() {
    bootImageStart = VM_BootRecord.the_boot_record.startAddress;
    bootImageEnd = VM_BootRecord.the_boot_record.freeAddress;
    heapStart = bootImageEnd;
    heapEnd = VM_BootRecord.the_boot_record.endAddress;
    largeStart = VM_BootRecord.the_boot_record.largeStart;
    largeEnd = largeStart + VM_BootRecord.the_boot_record.largeSize;


    minBootRef  = VM_ObjectModel.minimumObjectRef(bootImageStart);
    maxBootRef  = VM_ObjectModel.maximumObjectRef(bootImageEnd);
    minHeapRef  = VM_ObjectModel.minimumObjectRef(heapStart);
    maxHeapRef  = VM_ObjectModel.maximumObjectRef(heapEnd);
    minLargeRef = VM_ObjectModel.minimumObjectRef(largeStart);
    maxLargeRef = VM_ObjectModel.maximumObjectRef(largeEnd);

    // get addresses of TIBs for VM_Array & VM_Class used for testing Type ptrs
    VM_Type t = VM_Array.getPrimitiveArrayType(10);
    tibForArrayType = VM_ObjectModel.getTIB(t);
    tibForPrimitiveType = VM_ObjectModel.getTIB(VM_Type.IntType);
    t = VM_Magic.getObjectType(VM_BootRecord.the_boot_record);
    tibForClassType = VM_ObjectModel.getTIB(t);
    if (TRACE) {
      VM_Scheduler.traceHex("VM_GCUtil.boot","tibForArrayType =", 
                            VM_Magic.objectAsAddress(tibForArrayType));
      VM_Scheduler.traceHex("VM_GCUtil.boot","tibForPrimitiveType =", 
                            VM_Magic.objectAsAddress(tibForPrimitiveType));
      VM_Scheduler.traceHex("VM_GCUtil.boot","tibForClassType =", 
                            VM_Magic.objectAsAddress(tibForClassType));
    }
  }

  static boolean referenceInVM(int ref) {
    if ( (ref >= minBootRef && ref <= maxHeapRef) ||
	 (ref >= minLargeRef && ref <= maxLargeRef) )
      return true;
    else
      return false;
  }

  static boolean referenceInVM (Object o) {
    return referenceInVM(VM_Magic.objectAsAddress(o));
  }

  static boolean referenceInBootImage(int ref) {
    if ( (ref >= minBootRef) && (ref <= maxBootRef) )
      return true;
    else
      return false;
  }

  static boolean referenceInBootImage(Object o) {
    return referenceInBootImage(VM_Magic.objectAsAddress(o));
  }

  static boolean referenceInHeap(int ref) {
    if ( (ref >= minHeapRef) && (ref <= maxHeapRef) )
      return true;
    else
      return false;
  }

  static boolean referenceInHeap(Object o) {
    return referenceInHeap(VM_Magic.objectAsAddress(o));
  }

  // a range test used by the opt compiler. it should probably be
  // calling referenceInVM instead.
  //
  static boolean
  inRange( int address ) {
    if ( (address >= bootImageStart && address < heapEnd) ||
	 (address >= largeStart && address < largeEnd) )
      return true;
    else
      return false;
  }

  static boolean
  addressInVM ( int address ) {
    if ( (address >= bootImageStart && address < heapEnd) ||
	 (address >= largeStart && address < largeEnd) )
      return true;
    else
      return false;
  }

  static boolean
  addressInBootImage ( int address ) {
    if ( (address >= bootImageStart) && (address < bootImageEnd) )
      return true;
    else
      return false;
  }

  static boolean
  addressInHeap ( int address ) {
    if ( (address >= heapStart) && (address < heapEnd) )
      return true;
    else
      return false;
  }

  // check if an address appears to point to a an instance of VM_Type
  static boolean
  validType ( int typeAddress ) {
    if ( ! (referenceInBootImage(typeAddress) || referenceInHeap(typeAddress)) )
      return false;  // type address is outside of heap

    // check if types tib is one of three possible values
    Object[] typeTib = VM_ObjectModel.getTIB(typeAddress);
    if ( (typeTib == tibForClassType) || (typeTib == tibForArrayType) ||
	 (typeTib == tibForPrimitiveType) )
      return true;
    else
      return false;
  }

  /**
   * dump all threads & their stacks starting at the frame identified
   * by the threads saved contextRegisters (ip & fp fields).
   */
  static void 
  dumpAllThreadStacks() {
    int        i, ip, fp;
    VM_Thread  t;
    VM_Scheduler.trace("\ndumpAllThreadStacks","dumping stacks for all threads");
    for ( i=0; i<VM_Scheduler.threads.length; i++ ) {
      t = VM_Scheduler.threads[i];
      if ( t == null ) continue;
      VM.sysWrite("\n Thread "); t.dump(); VM.sysWrite("\n");
      // start stack dump using fp & ip in threads saved context registers
      ip = t.contextRegisters.getInnermostInstructionAddress();
      fp = t.contextRegisters.getInnermostFramePointer();
      VM_Scheduler.dumpStack(ip,fp);
    }
    VM.sysWrite("\ndumpAllThreadStacks: end of thread stacks\n\n");
  }  // dumpAllThreadStacks

  /**
   * check if a ref, its tib pointer & type pointer are all in the heap
   */
  static boolean
  validRef ( int ref ) {
    if (ref == VM_NULL) return true;
    if (!referenceInVM(ref)) {
      VM.sysWrite("validRef: REF outside heap, ref = "); 
      VM.sysWriteHex(ref); VM.sysWrite("\n");
      return false;
    }
    if (VM_Collector.MOVES_OBJECTS) {
      if (VM_AllocatorHeader.isForwarded(VM_Magic.addressAsObject(ref)) ||
	  VM_AllocatorHeader.isBeingForwarded(VM_Magic.addressAsObject(ref))) {
	return true; // TODO: actually follow forwarding pointer (need to bound recursion when things are broken!!)
      }
    }
    
    Object[] tib = VM_ObjectModel.getTIB(ref);
    if (!referenceInVM(tib)) {
      VM.sysWrite("validRef: TIB outside heap, ref = "); VM.sysWriteHex(ref);
      VM.sysWrite(" tib = ");VM.sysWriteHex(VM_Magic.objectAsAddress(tib)); 
      VM.sysWrite("\n");
      return false;
    }
    int type = VM_Magic.objectAsAddress(tib[0]);
    if (!validType(type)) {
      VM.sysWrite("validRef: invalid TYPE, ref = "); VM.sysWriteHex(ref);
      VM.sysWrite(" tib = ");
      VM.sysWriteHex(VM_Magic.objectAsAddress(tib));
      VM.sysWrite(" type = ");VM.sysWriteHex(type); VM.sysWrite("\n");
      return false;
    }
    return true;
  }  // validRef

   public static void dumpRef (Object ref) {
     dumpRef(VM_Magic.objectAsAddress(ref));
   }
   public static void dumpRef (int ref) {
     VM.sysWrite("REF=");
     if (ref==VM_NULL) {
       VM.sysWrite("NULL\n");
       return;
     }
     VM.sysWriteHex(ref);
     if ( !referenceInVM(ref) ) {
       VM.sysWrite(" (REF OUTSIDE OF HEAP)\n");
       return;
     }
     VM_ObjectModel.dumpHeader(ref);
     Object[] tib = VM_ObjectModel.getTIB(ref);
     if (!(referenceInBootImage(tib) || referenceInHeap(tib)) ) {
       VM.sysWrite(" (INVALID TIB: CLASS NOT ACCESSIBLE)\n");
       return;
     }
     VM_Type type = VM_Magic.getObjectType(VM_Magic.addressAsObject(ref));
     int itype = VM_Magic.objectAsAddress(type);
     VM.sysWrite(" TYPE=");
     VM.sysWriteHex(itype);
     if ( ! validType(itype) ) {
       VM.sysWrite(" (INVALID TYPE: CLASS NOT ACCESSIBLE)\n");
       return;
     }
     VM.sysWrite(" CLASS=");
     VM.sysWrite(type.getDescriptor());
     VM.sysWrite("\n");
   }

  static void
  dumpMemoryWords( int start, int count ) {
    int end = start + count * WORDSIZE;
    VM.sysWrite("---- Dumping memory from ");
    VM.sysWriteHex(start);
    VM.sysWrite(" to ");
    VM.sysWriteHex(end);
    VM.sysWrite(" ----\n");
    for ( int loc = start; loc <= end; loc+=WORDSIZE ) {
      VM.sysWriteHex(loc);
      VM.sysWrite(" ");
      int value = VM_Magic.getMemoryWord(loc);
      VM.sysWriteHex(value);
      VM.sysWrite("\n");
    }
    VM.sysWrite("----\n");
  }

  public static void printclass (int ref) {
    if ( validRef(ref) ) {
      VM_Type type = VM_Magic.getObjectType(VM_Magic.addressAsObject(ref));
      if ( validRef( VM_Magic.objectAsAddress(type) ) )
	VM.sysWrite(type.getDescriptor());
    }
  }
  
}   // VM_GCUtil
