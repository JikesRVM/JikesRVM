/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * External implementation for BootMap.
 * Load the method map for the JVM boot image from disk
 * (Written by John Barton, 2/4/98)
 * (Modified for jdp, Ton Ngo  2/11/98)
 *
 * @author Ton Ngo
 */

import java.util.*;
import java.io.*;
/* not needed for build in separate RVM.tools directory */
/* import BootImageWriter2; */

class BootMapExternal extends BootMap {
    
  /**
   * Implementation
   */
  static boolean first_time=true;

  /**
   * Cache offset values in JTOC that will be used often
   */
  static int VM_TypeDictionary_values_offset;             // VM_TypeDictionary.values
  static int VM_MethodDictionary_values_offset;           // VM_MethodDictionary.values
  static int VM_Class_declaredMethods_offset;             // VM_Class.declaredMethods
  static int compiledMethodTable_offset;                  // VM_CompiledMethods.compiledMethods
  static int compiledMethod_instructions_offset;          // VM_CompiledMethod.instruction
  static int compiledMethod_method_offset;                // VM_CompiledMethod.method

  /**
   * These tables are intended for reliable debugging of the boot image:
   * the method ID from the stack may be corrupt, so for methods that 
   * are in the boot image, we rely on the code address to look up its
   * method ID instead.  This way, we can still navigate around the boot image
   * even when the stack is corrupted.
   * These tables are filled right after the boot image are loaded
   */

  /** 
   * Start address of the method code: index is the method ID
   */ 
  int startAddress[];
      
  /** 
   * End address of the method code: index is the method ID
   */ 
  int endAddress[];

  /**
   * Constructor: create a boot map loaded from a file
   * @param process    an OsProcess object for access to other objects 
   *                   owned by the process
   * @param in         a Reader character stream input to get the file from disk
   * @param mainClass  the original name of the program
   * @param classesNeededFilename the file name of the list of classes in the boot map
   * @param classpath  class path to ensure the TOC is loaded in the same sequence as
   *                   done by BootImageWriter2
   * @return
   * @see BootMap
   */
  public BootMapExternal(OsProcess process, String mainClass, 
			 String classesNeededFilename, String classpath) 
    throws BootMapCorruptException, Exception   {

    // superclass: BootMap constructor
    super(process);

    // If we are running under the mapping interpreter, it has already
    // set up the JVM data structure in its layer
    if (Debugger.interpretMode) {
      System.out.println("classes NOT preloaded");
      return;
    }

    // If we are running under the JDK (and not the mapping interpreter)
    // set up the JVM data structure for lookup later on
    if (first_time) {
      // The classpath must be identical to the one used by BootImageWriter2
      // so that the TOC will be loaded in the same order;  if not, the
      // offset for the static fields will not match between jdp and the 
      // boot image.
      // System.out.println("BootMapExternal: initializing the VM_ base");
      // System.out.println("BootMapExternal: classpath " + classpath);
      VM.initForTool(classpath);
    
      // load the classes identically to BootImageWriter2
      try {
	preloadClasses(classesNeededFilename);
	first_time = false;
      } catch (Exception e) {
	System.out.println("WARNING: cannot find " +  classesNeededFilename);
	System.out.println("    line number will be incorrect ");
      } 
    }

  }


  /**
   * Create a dummy boot map with nothing in it
   * @param process    an OsProcess object for access to other objects 
   *                   owned by the process
   * @return
   * @see
   */
  public BootMapExternal(OsProcess process) {   

    // superclass: BootMap constructor
    super(process);

  }


  /**
   * Implement abstract method:  find the class.method name for an instruction address
   * @param methodID  an index into either the method dictionary table or the compiled method table
   * @param usingCompiledMethodID true if indexing the VM_CompiledMethods.compiledMethodID
   *                              false if indexing the method dictionary table
   * @return a name of the form class.method for this code
   * @see
   */
  public String findClassMethodName(int methodID, boolean usingCompiledMethodID) {
    VM_Method mth = findVMMethod(methodID, usingCompiledMethodID);
    if (mth!=null)   {
      String name = mth.getDeclaringClass().getName().toString();
      name += "." + mth.getName().toString();
      return name;
    } else
      return "unknown class.method";
  }
  
  /**
   * Implement abstract method: find the class name for an instruction address
   * @param methodID  an index into either the method dictionary table or the compiled method table
   * @param usingCompiledMethodID true if indexing the VM_CompiledMethods.compiledMethodID
   *                              false if indexing the method dictionary table
   * @return the class name for this code
   * @see
   */
  public String findClassName(int methodID, boolean usingCompiledMethodID) {
    VM_Method mth = findVMMethod(methodID, usingCompiledMethodID);
    if (mth!=null) {
      VM_Class cls = mth.getDeclaringClass();
      return cls.getName().toString();
    } else {
      return "unknown class";
    }
  }

  /**
   * Implement abstract method: find the method name for an instruction address
   * @param methodID  an index into either the method dictionary table or the compiled method table
   * @param usingCompiledMethodID true if indexing the VM_CompiledMethods.compiledMethodID
   *                              false if indexing the method dictionary table
   * @return the method name for this code
   * @see
   */
  public String findMethodName(int methodID, boolean usingCompiledMethodID) {
    VM_Method mth = findVMMethod(methodID, usingCompiledMethodID);
    if (mth!=null) 
      return mth.getName().toString();
    else 
      return "unknown method";
  }

  /**
   * Implement abstract method:  find the signature of the method for 
   *                             an instruction address
   * @param methodID  an index into either the method dictionary table or the compiled method table
   * @param usingCompiledMethodID true if indexing the VM_CompiledMethods.compiledMethodID
   *                              false if indexing the method dictionary table
   * @return the signature string for this code
   * @see
   */
  public String findSignature(int methodID, boolean usingCompiledMethodID) {
    VM_Method mth = findVMMethod(methodID, usingCompiledMethodID);
    if (mth!=null) 
      return mth.getDescriptor().toString();
    else 
      return "unknown signature";
  }


  // This is a temporary fix until we can handle breakpoint for multiple compiled code
  // Only the latest compiled instruction is found by this method
  // Either the caller or this routine should should be recoded to 
  // scan the compiled method table to get all codes for this method
  public int temp_instructionAddressForMethod(int methodAddress) {
    try {
      VM_Field field = findVMField("VM_Method", "mostRecentlyGeneratedInstructions");
      return owner.mem.read(methodAddress + field.getOffset());    
    } catch (BmapNotFoundException e) {
      System.out.println("JDP ERROR:  could not find fields VM_Method.mostRecentlyGeneratedInstructions, has it been changed?");
      return 0;      
    }
  }

  /** 
   * Given a compiled method ID, get the address of the code block of this method
   * @param methodID  index into the method array VM_MethodDictionary.values[]
   * @return the address of the instruction array
   */

  public int instructionAddress(int compiledMethodID) {

    // first get the address for the compiled method table in VM_CompiledMethods
    int compiledMethodTable = owner.mem.readTOC(compiledMethodTable_offset);
    int compiledMethodAddress = owner.mem.read(compiledMethodTable + compiledMethodID*4);
    
    // then get the instruction address
    int startAddress = owner.mem.read(compiledMethodAddress + compiledMethod_instructions_offset);
    
    return startAddress;
  }

  /** 
   * Given a starting address of an instruction block and a random address, 
   * check if the address falls within
   * the range of the instruction block for the method
   * @param startAddress the starting address of this instruction block
   * @param address  a random instruction address
   * @return true if the method's instructions span the address
   *         false otherwise.
   */
  public boolean isInstructionAddress(int startAddress, int address) {
    int endAddress = owner.mem.read(startAddress + VM.ARRAY_LENGTH_OFFSET);
    endAddress = startAddress + (endAddress-1)*4;
    if (address>=startAddress && address<=endAddress)
      return true;
    else
      return false;
  }

  /**
   *  Given a class index (into the VM_TypeDictionary) 
   * and a method index (into the method list of the class) for a static or 
   * virtual method, find the starting address of the instruction
   * NOTE:  there may be multiple compiled code for a method, so we need to 
   * scan the entire compiled method table and return an array of addresses.  
   * For now, just get the first match in the table.
   *
   * @param classId  the index into VM_TypeDictionary.values[]
   * @param methodIndex  the index into the method list of this class
   * @param staticMethod a flag indicating whether it's a static or virtual method
   * @return the start address for the instruction for the method
   */
  private int instructionAddressForClass(int classID, int methodIndex, boolean staticMethod) {
    VM_Field field;
    
    try {
      // get the address of the VM_Class
      int classArrayAddress = owner.mem.readTOC(VM_TypeDictionary_values_offset);
      int classAddress = owner.mem.read(classArrayAddress + classID*4);

      // get the address of the static method array
      if (staticMethod)
        field = findVMField("VM_Class", "staticMethods");
      else
        field = findVMField("VM_Class", "virtualMethods");

      int methodArrayAddress = owner.mem.read(classAddress + field.getOffset());
      int methodAddress = owner.mem.read(methodArrayAddress + methodIndex*4);
    
      // get the address of the instruction block
      // TODO:  scan the compiled method table to get all codes for this method
      return temp_instructionAddressForMethod(methodAddress);

    } catch (BmapNotFoundException e) {
      System.out.println("JDP ERROR:  could not find some fields in class VM_ClassDictionary, VM_class or VM_Method, have them been changed?");
      return 0;
    }
  }

  /**
   * Implement abstract method: find the byte offset of this address 
   *                            from the start of instruction block for this method
   * @param compiledMethodID index into the compiled methods table
   * @param address an address pointing to arbitrary machine instructions
   * @return the byte offset 
   * @see
   */
  public int instructionOffset(int compiledMethodID, int address) {
    int startAddress = instructionAddress(compiledMethodID);
    int endAddress = owner.mem.read(startAddress + VM.ARRAY_LENGTH_OFFSET);
    endAddress = startAddress + endAddress;
    // System.out.println("instructionOffset: code @ " + Integer.toHexString(startAddress) +
      		 // " to " + Integer.toHexString(endAddress));
    if (address<startAddress || address > endAddress) {
      // System.out.println("WARNING:  address " + Integer.toHexString(address) +
      // " is not in the instruction range of this method " + 
      // Integer.toHexString(startAddress) + " - " + Integer.toHexString(endAddress));
      return -1;
    } else {
      return address - startAddress;
    }

  }


  /**
   * Implement abstract method:
   *   check to see if the address falls in the method prolog:
   * @param an address pointing to a machine instruction
   * @return  if in prolog, return the first address after the prolog
   * 	      if not in prolog, just return the given address
   * @see
   */
  public int findAddressSkippingProlog(int address) {
    // don't know yet how to find a method given only the address
    System.out.println("stepping into prolog code for now");
    return address;
  }


  /**
   * Implement abstract method:
   * Search the method table for the closest match for names
   * <ul>
   * <li> for method name only, see if it is unique
   * <li> for not unique, see if any belongs to the current class
   * <li> for class.method name, see if method is overloaded
   * <li> for overloaded, try the signature
   * <li> for class.method:line, look up the line number also
   * </ul>
   * @param  method name with or without classname preceding, delimited by ".",
   *         and with or without line number following, delimited by ":"
   * @param  signature optional, can be null
   * @param  address of current instruction for lookup
   * @return  the corresponding address of the machine instructions 
   * @exception  BmapMultipleException if multiple matches exist
   * @exception	 BmapNotFoundException if no match
   * @see
   */
  public breakpoint findBreakpoint(String name, String sig, int address) throws BmapMultipleException, BmapNotFoundException {

    String methodname;
    String classname;
    int line;

    // the name is of the form:  class.method:line, where class and line may be omitted.
    // separate the class and method names and line number
    classname = CommandLine.breakpointParseClass(name);
    methodname = CommandLine.breakpointParseMethod(name);
    line = CommandLine.breakpointParseLine(name);

    // if no line number given, look for the start of code for the method
    if (line == -1 || line == 0) {

      return breakpointForMethod(classname, methodname, sig, line);

    // given line number with class name, there should be an exact match
    // in this case the method name and signature are not ingored
    } else {

      if (classname.equals("")) {
	throw new BmapNotFoundException("no class specified.");
      } else {

	return breakpointForLine(classname, line);

      }
    }

  }
   
  private breakpoint breakpointForMethod(String classname, String methodname, String sig, int line) throws BmapMultipleException, BmapNotFoundException {
    int clsID=0;
    boolean usingClassList;
    int candidateID = -1;
    int start, end;
    VM_Method[] mthList;
    VM_Method mth;
    
    
    // Accomodate wild cards
    if (!classname.equals("")) {
      // class name given, look for this class specifically
      String classDescriptor = "L" + classname.replace('.','/') + ";";
      clsID = dictionaryExtension.findTypeID(classDescriptor);
      if (clsID==0) 
        throw new BmapNotFoundException("class "+classDescriptor+" not found");
      VM_Type[] val = VM_TypeDictionary.getValuesPointer();
      VM_Class cls = val[clsID].asClass();
      if (!cls.isLoaded())
      {
        throw new BmapNotFoundException("class not loaded");
      }
      mthList = cls.getDeclaredMethods();
      start = 0;
      end = mthList.length;
      usingClassList = true;

    }
    
    // only method name given, first check the current class
    // (currently not available, need to pass in the current class)
    
    else {
      // only the method name given, walk through the method dictionary 
      // to see if the method name is unique
      mthList = VM_MethodDictionary.getValuesPointer();      
      start = 1;
      end = VM_MethodDictionary.getNumValues();
      usingClassList = false;
    }
    
    // now scan through the list chosen to find the method
    // (we can't use the VM_MethodDictionary lookup method because it
    // needs an exact triplet of class.method.sig, while we have wild cards)
    for (int i=start; i<end; i++) {
      mth = mthList[i];
      if (mth.getName().toString().equals(methodname)) {
        // sig wildcard: match any sig
        if (sig==null) {
          if (candidateID==-1)
            candidateID = i;
          else 
            throw new BmapMultipleException("method " + classname + "." + methodname + " overloaded");
        } else if (mth.getDescriptor().toString().equals(sig)) {
          candidateID = i;
          break;
        }
      }
    }
    
    // done scanning method list, did we get any match?
    if (candidateID!=-1) {
      // compute the address of the VM_Method so we can search the compiled method table
      int methodAddress;
      if (usingClassList) {
	methodAddress = getAddressForClassMethod(clsID, candidateID);
      } else {
	methodAddress = owner.mem.readTOC(VM_MethodDictionary_values_offset);
	methodAddress = owner.mem.read(methodAddress + candidateID*4);
      }

      // TODO:  we should get a list of compiled ID for this method
      int compiledMethodID = getCompiledMethodIDForVMMethod(methodAddress);
      if (compiledMethodID == 0)
      {
        throw new BmapNotFoundException("compiled method not found");
      }
      int codeAddress = instructionAddress(compiledMethodID);
    
      if (line==0) {
	// System.out.println("breakpointForMethod: " + compiledMethodID + ", 0 at " + 
	// Integer.toHexString(codeAddress));
        return new breakpoint(compiledMethodID, 0, codeAddress);
      } else {
	VM_CompilerInfo compInfo = findVMCompilerInfo(compiledMethodID, true);
        int offset = getPrologSize(compInfo, codeAddress);
	// System.out.println("breakpointForMethod: " + compiledMethodID + ", " + offset +  
	// " at " + Integer.toHexString(codeAddress + offset));
        return new breakpoint(compiledMethodID, offset, codeAddress + offset);
      }
    
    } else {
      throw new BmapNotFoundException(classname + "." + methodname + " not found in dictionary.");
    }	


  }

  /**
   * Get the prolog size 
   * If the compilerinfo indicates this is an opt compiled method, then
   * the compilerInfo has the end of prologue offset. If the method
   * is baseline compiled then use the scan prolog approach
   * @param compInfo VM_CompilerInfo of the method of interest
   * @param startAddress starting address of the method
   * @return size of the prologue (ie offset of instruction after the prologue)
   */
  public int getPrologSize(VM_CompilerInfo compInfo, int startAddress) {
    if (compInfo == null)
      return 0;
    else {
      //-#if RVM_WITH_OPT_COMPILER
      if (compInfo.getCompilerType() == VM_CompilerInfo.OPT) {
	return ((VM_OptCompilerInfo)compInfo).getEndPrologueOffset();
      } else
      //-#endif
        {
	return scanPrologSize(startAddress);
        }
    }
  }
      

  /**
   * The prolog from the base compiler contains this pattern to mark the start of
   * the method instruction:
   *     0x90    NOP
   * Given the starting address of the instruction block, scan for this
   * pattern and return the byte offset from the starting address
   * Return 0 if the pattern is not found (code may be from the optimizing compiler)
   */
  public int scanPrologSize(int startAddress) {
    int byteLength = owner.mem.read(startAddress + VM.ARRAY_LENGTH_OFFSET);
    // limit: 60 bytes should be enough to capture the prolog
    if (byteLength > 60)
      byteLength = 60;
    
    int wordLength = byteLength/4+1;
    byte instrByte[] = new byte[byteLength];
    byte instrLength[] = new byte[byteLength];
    int instrWord[] = new int[wordLength];
    
    // read the block of instruction
    for (int i = 0; i<wordLength; i++) {
      instrWord[i] = owner.mem.read(startAddress + i*4);
    }

    instrByte = IntelDisassembler.intToByteArray(instrWord);

    instrLength = IntelDisassembler.instructionLength(instrByte);

    int offset = 0;   // offset into the instruction array 
    for (int i=0; i<instrLength.length; i++) {
      if (instrLength[i]==0)  // no more
	return 0;
      if (instrLength[i]==1) {
	if (instrByte[offset]==((byte)0x90)) {
	  return offset+1;    // point to the instruction after the NOP
	}
      }
      offset += instrLength[i];
    }

    return 0;
  }




  /**
   * Scan the methods of a class to find the method that contains the desired line
   * Find the starting address for this method and the byte offset for the code
   * implementing this line.  Create and return a breakpoint object that holds this
   * information.
   * @param classname a class name
   * @param linenum a line number
   * @return a breakpoint
   * @exception BmapNotFoundException if this source line does not contain
   *            valid codes or the class does not exist
   * @see
   */
  private breakpoint breakpointForLine (String classname, int linenum) throws BmapNotFoundException {
    int offset, startAddr, methodID; 

    // we use this round-about look up because later we have to compute
    // the real address from these references:  the typeID makes it easier
    String classDescriptor = "L" + classname.replace('.','/') + ";";
    int id = dictionaryExtension.findTypeID(classDescriptor);
    if (id==0)
      throw new BmapNotFoundException("class not found");
    VM_Type[] val = VM_TypeDictionary.getValuesPointer();
    VM_Class cls = val[id].asClass();
    if (!cls.isLoaded())
    {
      throw new BmapNotFoundException("class not loaded");
    }
    //System.out.println("breakpointForLine: search class " + cls.getName().toString());

    // scan through the static methods for this line number
    VM_Method mth;
    VM_Method[] methods = cls.getStaticMethods();
    int  mthIndex;
    //System.out.println("breakpointForLine: found " + methods.length + " static methods");
    for (int i=0; i<methods.length; i++) {
      mth = methods[i];
      // TODO:   scan the compiled method table to get all codes for this method
      if (mth.isCompiled())
      {
        offset = mth.getMostRecentlyGeneratedCompilerInfo().findInstructionForLineNumber(linenum);
        if (offset!=-1) {
          startAddr = instructionAddressForClass(id, i, true);
          methodID = getCompiledMethodIDForInstruction(startAddr);
          // System.out.println("breakpointForLine: " + methodID + ", " + offset + " at " +
          // 		      Integer.toHexString(startAddr + offset));
          return new breakpoint(methodID, offset, startAddr + offset);
        }
      }
    }

    // no luck, scan through the virtual methods for the line number
    methods = cls.getVirtualMethods();
    for (int i=0; i<methods.length; i++) {
      mth = methods[i];
      // TODO:   scan the compiled method table to get all codes for this method
      if (mth.isCompiled())
      {
        offset = mth.getMostRecentlyGeneratedCompilerInfo().findInstructionForLineNumber(linenum);
        //System.out.println("... checking virtual method " + methods[i].getName().toString() +
        // 			    ": offset " + offset);
        if (offset!=-1) {
          startAddr = instructionAddressForClass(id, i, false);
          methodID = getCompiledMethodIDForInstruction(startAddr);
          // System.out.println("breakpointForLine: " + methodID + ", " + offset + " at " +
          // 		      Integer.toHexString(startAddr + offset));
          return new breakpoint(methodID, offset, startAddr + offset);
        }
      }
    }
   
    throw new BmapNotFoundException("no compiled code at line " + linenum);

  }

  /**
   * Given a class ID and a method index for the method list of this class,
   * get the address of the code block of this method
   * @param classID  index into VM_TypeDictionary.values[]
   * @param methodIndex  index into the method array
   * @return the address of the VM_Method
   */

  private int getAddressForClassMethod(int classID, int methodIndex) {
    // first get the class address
    int classAddress = owner.mem.readTOC(VM_TypeDictionary_values_offset);
    classAddress     = owner.mem.read(classAddress + classID*4);
   
    // then get the method address
    int methodAddress = owner.mem.read(classAddress + VM_Class_declaredMethods_offset);
    methodAddress = owner.mem.read(methodAddress + methodIndex*4);
   
    return methodAddress;
  }



  /**
   * Implement abstract method: find the VM_Class for an instruction address
   * @param methodID  an index into either the method dictionary table or the compiled method table
   * @param usingCompiledMethodID true if indexing the VM_CompiledMethods.compiledMethodID
   *                              false if indexing the method dictionary table
   * @return the runtime VM_Class 
   * @exception BmapNotFoundException if the name is not in the map
   * @see findVMClassByIndex
   */
  public VM_Class  findVMClass(int methodID,boolean usingCompiledMethodID) {
    VM_Method mth = findVMMethod(methodID, usingCompiledMethodID);
    if (mth!=null)
      return mth.getDeclaringClass();
    else
      return null;
  }


  /**
   * Implement abstract method: find the VM_Method given a method ID
   * @param methodID  an index into either the method dictionary table or the compiled method table
   * @param usingCompiledMethodID true if indexing the VM_CompiledMethods.compiledMethodID
   *                              false if indexing the method dictionary table
   * @return the runtime VM_Method containing this code
   * @exception BmapNotFoundException if the name is not in the map
   * @see findVMMethodByIndex
   */
  public VM_Method findVMMethod(int methodID, boolean usingCompiledMethodID){
    if (usingCompiledMethodID) {
      // the methodID is an index into the compiled method table VM_CompiledMethods.compiledMethods[]
      VM_CompiledMethod[] compiledMths = VM_CompiledMethods.getCompiledMethods();
      // System.out.println("findVMMethod: looking for compiled method ID " + methodID +
      // 	 " out of " + compiledMths.length);
      // System.out.println("findVMMethod: compiled " + VM_CompiledMethods.numCompiledMethods());
      
      VM_CompiledMethod compiledMethod = compiledMths[methodID];
      if (compiledMethod !=null) {
	VM_Method mth = compiledMethod.getMethod();
	// System.out.println("findVMMethod: found " + mth.getName().toString() + 
	// 		   " at compiled method ID " + methodID);
	return mth;
      } else {
	return null;
      }
    } else {
      // the methodID is an index into the method dictionary VM_MethodDictionary.values[]
      VM_Method[] mthArray = VM_MethodDictionary.getValuesPointer();    
      int limit = VM_MethodDictionary.getNumValues();
      if (methodID > 0 && methodID < limit)
	return mthArray[methodID];
      else
	return null;
    }
  }

  /**
   * Implement abstract method: find the VM_CompilerInfo given a method ID
   * @param methodID  an index into either the method dictionary table or the compiled method table
   * @param usingCompiledMethodID true if indexing the VM_CompiledMethods.compiledMethodID
   *                              false if indexing the method dictionary table
   * @return the runtime VM_CompilerInfo for this method
   * @exception BmapNotFoundException if the name is not in the map
   * @see findVMMethodByIndex
   */
  public VM_CompilerInfo findVMCompilerInfo(int methodID, boolean usingCompiledMethodID){
    if (usingCompiledMethodID) {
      // the methodID is an index into the compiled method table VM_CompiledMethods.compiledMethods[]
      VM_CompiledMethod[] compiledMths = VM_CompiledMethods.getCompiledMethods();
      // System.out.println("findVMMethod: looking for compiled method ID " + methodID +
      // 	 " out of " + compiledMths.length);
      // System.out.println("findVMMethod: compiled " + VM_CompiledMethods.numCompiledMethods());
      
      VM_CompiledMethod compiledMethod = compiledMths[methodID];
      if (compiledMethod !=null) {
	VM_CompilerInfo compInfo = compiledMethod.getCompilerInfo();
	// System.out.println("findVMCompilerInfo: found " + compInfo.getCompilerType() + 
	// 		   " at compiled method ID " + methodID);
	return compInfo;
      } else {
	return null;
      }
    } else {
      // the methodID is an index into the method dictionary VM_MethodDictionary.values[]
      VM_Method[] mthArray = VM_MethodDictionary.getValuesPointer();    
      int limit = VM_MethodDictionary.getNumValues();
      if (methodID > 0 && methodID < limit) {
	VM_Method mth = mthArray[methodID];
	return mth.getMostRecentlyGeneratedCompilerInfo();
      }
      else
	return null;
    }
  }

  /**
   * Compute the compiled method ID given a stack frame and an instruction address
   * this is difficult because we may be in the 
   * prolog code where the compiled methodID is not set yet
   * Look in the following order:
   *  -If the address is in the boot image, search the boot table
   *  -Check the breakpoint list.
   *  -otherwise, read at the right offset from FP
   * Ideally, if the address is in the prolog, we should pick up from the first prolog 
   * instruction but we don't have a way to compute the start of the prolog without
   * the method ID.
   *
   * This should give the correct method ID in most cases, 
   * if none found for some reason, return 0 so that indexing into the method
   * table would give null as VM_Method
   * @param fp  the Frame Pointer for this stack frame
   * @param address the Instruction Pointer for this stack frame
   * @return the compiled method ID, or -1 if this is a non-Java stack frame (C or C++)
   */
  public int getCompiledMethodID(int fp, int address) {
    
    if (!isInRVMspace(address)) {
      return NATIVE_METHOD_ID;
    }

    // First attempt:  if the address in the boot image, use the table
    if (address>=bootStart && address<=bootEnd) {
      int methodIdInBoot = getMethodIdFromBootAddress(address);
      if (methodIdInBoot!=-1)
	return methodIdInBoot;
      else
	return 0;
    } 
      
    // Second attempt: check if the address is within the instruction range of 
    // a breakpoint which has its methodID saved
    breakpoint bp = owner.bpset.lookupByRange(address);
    if (bp==null)  {
      bp = owner.threadstep.lookupByRange(address);
    }
    if (bp!=null)  {
      // System.out.println("getCompiledMethodID: found in breakpoint, " + bp);
      if (bp.methodID!=0)
        return bp.methodID;
    }

    // last resort:  
    // (1) read method ID from the stack frame
    // (2) check if the address is within the instruction range of this method ID
    //     if yes, use it
    // (3) if no, scan the entire VM_MethodDictionary (heavy weight)
    int compiledMethodID = owner.mem.read(fp+VM_Constants.STACKFRAME_METHOD_ID_OFFSET);
    int startAddress = instructionAddress(compiledMethodID);
    if (isInstructionAddress(startAddress, address)) {
      return compiledMethodID;
    } else {
      return getCompiledMethodIdForAddress(address);
    }

  }


  /**
   * Read off the entire VM_MethodDictionary, save the ID and the start/end
   * address of each method code.
   *
   */
  public void fillBootMethodTable() {
    try {
      // while we are at this, cache a few offset values that will be used often
      // (OK to cache since they are static and will stay in the TOC)
      VM_Field field = findVMField("VM_TypeDictionary", "values");
      VM_TypeDictionary_values_offset = field.getOffset();

      field = findVMField("VM_MethodDictionary", "values");
      VM_MethodDictionary_values_offset = field.getOffset();

      field = findVMField("VM_Class", "declaredMethods");      
      VM_Class_declaredMethods_offset = field.getOffset();

      field = findVMField("VM_CompiledMethods", "compiledMethods");
      compiledMethodTable_offset = field.getOffset();

      field = findVMField("VM_CompiledMethod", "instructions");
      compiledMethod_instructions_offset = field.getOffset();

      field = findVMField("VM_CompiledMethod", "method");
      compiledMethod_method_offset = field.getOffset();

      // first get the address for the method array
      int methodArrayAddress = owner.mem.readTOC(compiledMethodTable_offset);

      // get the number of compiled methods when booting is done
      field = findVMField("VM_CompiledMethods", "currentCompiledMethodId");
      int methodArraySize = owner.mem.readTOC(field.getOffset()) + 1;
      System.out.println("There are " + methodArraySize + 
			 " compiled methods in the boot image.");

      // allocate the address arrays
      startAddress = new int[methodArraySize];
      endAddress = new int[methodArraySize];

      // the first entry is null
      startAddress[0] = -1;     
      endAddress[0] = -1;

      // fill the rest of the table
      for (int i=1; i<methodArraySize; i++) { 
	int compiledMethodAddress = owner.mem.read(methodArrayAddress + i*4);
	if (compiledMethodAddress==0)    // can be null with opt compiler
	  continue;
	startAddress[i] = owner.mem.read(compiledMethodAddress + compiledMethod_instructions_offset);

	if (startAddress[i]==0) {
	  // some 150 entries will be native or else and have no instructions
	  endAddress[i] = 0;
	} else {
	  int codeSize = owner.mem.read(startAddress[i] + VM.ARRAY_LENGTH_OFFSET);
	  // For AIX, code is word size (4 bytes), for Lintel, code is byte size
	  // endAddress[i] = startAddress[i] + codeSize*4;
	  endAddress[i] = startAddress[i] + codeSize;
	}
      }

      System.out.println("Done filling address table"); 

      // now get the address range of the boot image
      field = findVMField("VM_BootRecord", "the_boot_record");
      int bootRecordAddress = owner.mem.readTOC(field.getOffset());
      field = findVMField("VM_BootRecord", "startAddress");
      bootStart = owner.mem.read(bootRecordAddress + field.getOffset()); 
      field = findVMField("VM_BootRecord", "freeAddress");
      bootEnd = owner.mem.read(bootRecordAddress + field.getOffset());
      field = findVMField("VM_BootRecord", "endAddress");
      vmEndAddress = bootRecordAddress + field.getOffset();
      field = findVMField("VM_BootRecord", "largeStart");
      largeStartAddress = bootRecordAddress + field.getOffset();
      field = findVMField("VM_BootRecord", "largeSize");
      largeSizeAddress = bootRecordAddress + field.getOffset();

        System.out.println("Method table: " + methodArraySize + " entries, boot address " +
       		    Integer.toHexString(bootStart) + " : " + 
       		    Integer.toHexString(bootEnd) + " : @" +
       		    Integer.toHexString(vmEndAddress));



    } catch (BmapNotFoundException e) {
      System.out.println("JDP ERROR: fillBootMethodTable , could not find class VM_BootRecord, VM_MethodDictionary or VM_Method, or one of their fields.");
      return;
    }

  }


  /**
   * Given an address, scan the start/end address table to
   * get the matching compiled method ID
   *
   */
  private int getMethodIdFromBootAddress(int instructionAddress) {
    int numMethod = startAddress.length;
    for (int i=1; i<numMethod; i++) {
      if (instructionAddress>=startAddress[i] && instructionAddress<=endAddress[i]) {
	return i;
      }
    }
    return -1;
  }


  /**
   * Given an address, scan the entire table of compiled methods to
   * find the method with instructions spanning this address
   * @param address  a random instruction address
   * @return the compiled method ID for the method with instructions spanning this address
   */
  private int getCompiledMethodIdForAddress(int address) {
    return scanCompiledMethodIDTable(address, compiledMethod_instructions_offset, true);
  }

  /**
   *  Given the address for a VM_Method object, search the table
   * in VM_CompiledMethods to find all the compiled method ID for this VM_Method
   * NOTE:  should return an array of int, but for now just return the first one
   * @param methodAddress  the address of a VM_Method object
   * @return the compiled method ID
   * @see
   */
  public int getCompiledMethodIDForVMMethod(int methodAddress) {
    return scanCompiledMethodIDTable(methodAddress, compiledMethod_method_offset, false);
  }

  /**
   *  Given the starting address for an instruction array, search the table
   * in VM_CompiledMethods to find all the compiled method ID for this instruction array
   * NOTE:  should return an array of int, but for now just return the first one
   * @param instructionAddress  the starting address of an instruction array
   * @return the compiled method ID
   * @see
   */
  public int getCompiledMethodIDForInstruction(int instructionAddress) {
    return scanCompiledMethodIDTable(instructionAddress, compiledMethod_instructions_offset, false);
  }

  /**
   * Common code to scan the table of VM_CompiledMethods.currentCompiledMethodId to look for 
   * all compiled method IDs for a certain VM_Method or a certain instruction address
   *
   * @param address  the address to look for
   * @param fieldOffset  offset into the field of the VM_CompiledMethod object,
   *                     expect to be either for method or instructions
   * @param checkAddressRange   if true for instructions, check if the address falls in the range
   *                            if false for instructions, the address is the starting address
   *                            should be false for method 
   * @return the matching compiled method ID, 0 if no match, NATIVE_METHOD_ID if the address is native
   * @see getCompiledMethodIDForInstruction, getCompiledMethodIDForVMMethod, getCompiledMethodIdForAddress
   */

  private int scanCompiledMethodIDTable(int address, int fieldOffset, boolean checkAddressRange) {
    int methodArrayAddress = owner.mem.readTOC(compiledMethodTable_offset);
    int numMethods;

    if (!isInRVMspace(address)) {
      return NATIVE_METHOD_ID;
    }

    // get the number of methods
    try {
      VM_Field field = findVMField("VM_CompiledMethods", "currentCompiledMethodId");
      numMethods = owner.mem.readTOC(field.getOffset()) + 1;
    } catch (BmapNotFoundException e) {
      System.out.println("JDP ERROR: Field VM_CompiledMethods.currentCompiledMethodId not found, has it been changed?");
      return 0;
    }

    // System.out.println("scan compiledMethodID table: " + numMethods + " entries ...");

    // searching backward should be faster since the methods in the front are
    // in the boot image and should have been found through getMethodIdFromBootAddress
    for (int compiledMethodID=numMethods-1; compiledMethodID>0; compiledMethodID--) {
      int methodAddress = owner.mem.read(methodArrayAddress + compiledMethodID*4);
      if (methodAddress==0)              // can be null with opt compiler
	continue;
      int fieldContent  = owner.mem.read(methodAddress + fieldOffset);
      // there may not be any code if the method is native
      if (checkAddressRange) {
	if (fieldContent!=0) {
	  if (isInstructionAddress(fieldContent, address)) 
	    return compiledMethodID;
	}
      } else {
	if (fieldContent == address)
	  return compiledMethodID;
      }
    }

    // not found in dictionary, the address must not be an instruction address
    System.out.println("scanCompiledMethodIDTable:  not found");
    return 0;
  }


  /**
   * The method ID is stored at the end of the instruction array
   * @param methodAddress the starting address of the instruction block
   * @return the method ID for this method
   */
  // This no longer works, the compiled method ID is not being saved with the instruction block
  // public int methodIDForAddress(int methodAddress) {    
  //   int length = owner.mem.read(methodAddress + VM.ARRAY_LENGTH_OFFSET);
  //   return owner.mem.read(methodAddress + (length-1)*4);
  // }

  /**
   * For the external implementation running without the interpreter, 
   * we must do this to get the same set 
   * of classes preloaded as for BootImageWriters so that the compilation 
   * later will give bytecode map that match the boot image
   * @param testname the name of the boot image file
   * @param classesNeededFilename the file listing the classes included in the boot image
   * @return
   * @see BootImageWriter2
   */
  private void preloadClasses(String classesNeededFilename) 
    throws Exception, VM_ResolutionException
  {
    System.out.println("loading classes needed: " + classesNeededFilename);
    
    // old boot image writer
    // BootImageWriter.loadClassesIntoJDKObjects(classesNeededFilename);
    
    // new boot image writer -- work in progress [DL]
    try {
      VM.writingBootImage = true;   // disable lazy compilation so preloading can occur
      Vector typeNames = BootImageWriter2.readTypeNames(classesNeededFilename);
      BootImageWriter2.createBootImageObjects(typeNames, "dummyName");
    } catch (Exception e) {
      System.out.println("preloadClasses fails");
      e.printStackTrace();
      throw e;
    }
  }


}


