/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author John Barton
 *
 * This class is an extension of the interpreter to map an interpreted class 
 * from the debuggee side to the debugger side
 *
 * (1) The general scheme is:
 * -init is invoked by the interpreter to get the initial TOC indices
 *  These are offset into the JTOC and should stay valid unless the TOC
 *  is rearranged.
 * -As the interpreter encounters an object to be mapped, it wraps it as
 *  a mapVM object before putting it on the interpreter stack.  The wrapped
 *  object contains the necessary context for subsequent bytecode to operate
 *  on the object and to get the values from the JVM side.
 * -Each bytecode that operates on an object or array has an extension to 
 *  deal with these mapVM object on the stack.  For instance, arraylength
 *  will compute the address in the JVM and read the length value for the array.
 *  At first count, some 23 bytecodes will need to have this extension
 *
 * (2) Interfacing to ptrace is done both by the interpreter and jdp, with 
 * jdp controlling the main path of the interface and the interpreter
 * co-accessing at the proper point
 * -Both will initialize the class Platform, but only the first call loads the 
 *  the library.
 * -jdp will create the process and fill in the process id and signal to 
 * -The interpreter reads and caches some addresses to speed up access.
 *  At the beginning, it needs to know when the JVM data structure is valid.
 *  Also, when the jvm returns from execution, these addresses may change 
 *  because of GC, so it needs to know to invalidate the cache.  
    This is done by jdp invoking the refresh() method
 *  in the interpreter, which is a dummy method recognized by the interpreter
 *  as a signal to reread the values.
 * -If the process has exited or been killed, the process id is 0.  During this
 *  time, it is an error if the interpreter comes across any mapped object
 *
 *
 *
 * 
 *  Changes to jdp:
 *
 *
 *
 */

import Platform;
import java.util.*;
import java.io.*;

class mapVM {
  // init flag
  static boolean initialized = false;
  static Class mapClass;
  static VM_Class mapVMClass;
  static final int PointerSize = 4;       // expect a pointer to be 4 bytes in the JVM side
  
  // TOC index for dictionaries
  // these are extracted from RVM.map at the beginning
  // they remain valid as long as the TOC is not unloaded or rearranged
  static int AtomDictionary_keys_index     ;  
  static int AtomDictionary_values_index   ;  
  static int AtomDictionary_nextId_index   ;  
  static int AtomDictionary_chains_index   ;  
  static int MethodDictionary_keys_index   ;  
  static int MethodDictionary_values_index ;  
  static int MethodDictionary_nextId_index ;  
  static int MethodDictionary_chains_index ;    
  static int TypeDictionary_keys_index     ;  
  static int TypeDictionary_values_index   ;  
  static int TypeDictionary_nextId_index   ;  
  static int TypeDictionary_chains_index   ;  
  static int FieldDictionary_keys_index    ;  
  static int FieldDictionary_values_index  ;  
  static int FieldDictionary_nextId_index  ;  
  static int FieldDictionary_chains_index  ;  
  static int ClassLoader_compiledMethods_index          ;  
  static int ClassLoader_currentCompiledMethodId_index  ;  

  // These offset constants are identical for both the mapped JVM and the external 
  // VM_* data structures, so they are precomputed for use later
  static int VMFieldOffset_offset;
  static int VMFieldType_offset;
  static int VMClassConstantPool_offset;
  static int VMClassName_offset;
  static int VMClassSuper_offset;
  static int VMAtomVal_offset;
  static int VMTypeDescriptor_offset;

  // Cached pointers to dictionaries
  // These are the values in the TOC entries at the index above
  // they may change due to GC, so they should be reloaded at the proper interval
  static int AtomDictionary_keys     ;  //  [LVM_Atom;
  static int AtomDictionary_values   ;  //  [LVM_Atom;
  static int AtomDictionary_nextId   ;  //  I
  static int AtomDictionary_chains   ;  //  [[I
  static int MethodDictionary_keys   ;  //  [LVM_Triplet;
  static int MethodDictionary_values ;  //  [LVM_Method;
  static int MethodDictionary_nextId ;  //  I
  static int MethodDictionary_chains ;  //  [[I
  static int TypeDictionary_keys     ;  //  [LVM_Atom;
  static int TypeDictionary_values   ;  //  [LVM_Type;
  static int TypeDictionary_nextId   ;  //  I
  static int TypeDictionary_chains   ;  //  [[I
  static int FieldDictionary_keys    ;  //  [LVM_Triplet;
  static int FieldDictionary_values  ;  //  [LVM_Field;
  static int FieldDictionary_nextId  ;  //  I
  static int FieldDictionary_chains  ;  //  [[I
  static int ClassLoader_compiledMethods          ;  // [LVM_CompiledMethod;
  static int ClassLoader_currentCompiledMethodId  ;  // I

  // these indicates the address space of the JVM
  // they are needed to support mixed stack frames of Java and native code
  // NOTE:  this assumes that VM_BootRecord is never moved by GC
  static int vmStartAddress;         // address of field VM_BootRecord.startAddress
  static int vmEndAddress;           // address of field VM_BootRecord.startAddress
  static int cachedJTOC;             // last known value of JTOC, to use when in native code


  /**
   * Instance variables to wrap an object to be mapped to the debuggee side 
   */
  private VM_Type type;        // type and javaClass are equivalent, duplicated here for convenience
  private Class javaClass;     
  private int address;         // the address of this field or object in the JVM side
  private int size;            // size of primitive or size of array elements
  

  public mapVM(VM_Type fieldType, int fieldAddress, int fieldSize) {
    type = fieldType;
    javaClass = InterpreterBase.getClassForVMType(fieldType);
    address = fieldAddress;
    size = fieldSize;
  }


  /**
   * Extract the JGOC indices for the dictionaries from RVM.map
   * This can be done at the beginning since we are only reading a file 
   */
  static void init() {
    if (initialized)
      return;
    else
      initialized = true;

    try {
      mapClass = Class.forName("mapVM");       // to test if an object is of this class
      // mapVMClass = InterpreterBase.forName("mapVM");

      // save the offset values for VM_Field object to be used later
      VM_Field field = (VM_Field) VM.getMember("LVM_Field;", "offset", "I");
      VMFieldOffset_offset = field.getOffset();
      field = (VM_Field) VM.getMember("LVM_Field;", "type", "LVM_Type;");
      VMFieldType_offset = field.getOffset();

      // save the offset values for VM_Class object to be used later
      field = (VM_Field) VM.getMember("LVM_Class;", "constantPool", "[I");
      VMClassConstantPool_offset = field.getOffset();
      field = (VM_Field) VM.getMember("LVM_Class;", "superClass", "LVM_Class;");
      VMClassSuper_offset = field.getOffset();
      field = BootMap.findVMField("VM_Class", "descriptor");
      VMClassName_offset = field.getOffset();

      // save the offset values for VM_Atom object to be used later
      field = (VM_Field) VM.getMember("LVM_Atom;", "val", "[B");
      VMAtomVal_offset = field.getOffset();

      // save the offset values for VM_Type object to be used later
      field = (VM_Field) VM.getMember("LVM_Type;", "descriptor", "LVM_Atom;");
      VMTypeDescriptor_offset = field.getOffset();

    // } catch (VM_ResolutionException e) {
    // 	 VM.sysWrite(e + "\n");
    // 	 e.printStackTrace();
    // 	 VM.sysExit(1);      
    } catch (ClassNotFoundException e1) {
      VM.sysWrite(e1 + "\n");
      e1.printStackTrace();
      VM.sysExit(1);      
    } catch (BmapNotFoundException e2) {
      VM.sysWrite(e2 + "\n");
      e2.printStackTrace();
      VM.sysExit(1);            
    }

    /* set to 0 so the compiler won't complain */
    TypeDictionary_keys_index     = 0;  
    TypeDictionary_values_index   = 0;  
    TypeDictionary_nextId_index   = 0;  
    TypeDictionary_chains_index   = 0;  
    AtomDictionary_keys_index     = 0;  
    AtomDictionary_values_index   = 0;  
    AtomDictionary_nextId_index   = 0;  
    AtomDictionary_chains_index   = 0;  
    MethodDictionary_keys_index   = 0;  
    MethodDictionary_values_index = 0;  
    MethodDictionary_nextId_index = 0;  
    MethodDictionary_chains_index = 0;    

    try {
      Reader in = new FileReader(RemoteInterpreter.jbiFileName);      
      LineNumberReader lines = new LineNumberReader(in);
      System.out.println("Scanning "+ RemoteInterpreter.jbiFileName + " for dictionary bases . . . ");
      /* In RVM.map, entries for JTOC map has a number in the first field, format:
       *    slotIndex  hexOffset  textDescription  hexContents  fullAsciiName
       *    decimalIndex == 0x.. hexvalue fieldname type
       *    decimalIndex == 0x.. hexvalue null
       * Scan the table, look for the entries for the dictionaries
       */
      while (true) { //loop over input lines  
      	String line = lines.readLine();
      	if (line == null) break;  // done
      	StringTokenizer z = new StringTokenizer(line);	

      	try {
	  int offset = Integer.parseInt(z.nextToken());  // get the slotIndex
      	  z.nextToken();    // skip the hexOffset field
      	  z.nextToken();    // skip the textDescription field
      	  String hexAddress = z.nextToken();    // get the hexContents field
      	  String fieldName = z.nextToken();     // get the fullAsciiName
	  
          // initialize the JTOC value
      	  if (fieldName.equals("VM_Statics.slots")) {
	    try {
	      cachedJTOC = Integer.parseInt(hexAddress.substring(2), 16);
            } catch (NumberFormatException e) {
              System.out.println("mapVM:  cannot read VM_Statics.slots address");
            }
      	  // look for dictionary entries
      	  } else if (fieldName.equals("VM_AtomDictionary.keys")) {
      	    AtomDictionary_keys_index     = offset;  
      	  } else if (fieldName.equals("VM_AtomDictionary.values")) {
      	    AtomDictionary_values_index   = offset;  
      	  } else if (fieldName.equals("VM_AtomDictionary.nextId")) {
      	    AtomDictionary_nextId_index   = offset;  
      	  } else if (fieldName.equals("VM_AtomDictionary.chains")) {
      	    AtomDictionary_chains_index   = offset;  
      	  } else if (fieldName.equals("VM_MethodDictionary.keys")) {
      	    MethodDictionary_keys_index   = offset;  
      	  } else if (fieldName.equals("VM_MethodDictionary.values")) {
      	    MethodDictionary_values_index = offset;  
      	  } else if (fieldName.equals("VM_MethodDictionary.nextId")) {
      	    MethodDictionary_nextId_index = offset;  
      	  } else if (fieldName.equals("VM_MethodDictionary.chains")) {
      	    MethodDictionary_chains_index = offset;    
      	  } else if (fieldName.equals("VM_TypeDictionary.keys")) {
      	    TypeDictionary_keys_index = offset;
      	  } else if (fieldName.equals("VM_TypeDictionary.values")) {
      	    TypeDictionary_values_index   = offset;  
      	  } else if (fieldName.equals("VM_TypeDictionary.nextId")) {
      	    TypeDictionary_nextId_index   = offset;  
      	  } else if (fieldName.equals("VM_TypeDictionary.chains")) {
      	    TypeDictionary_chains_index   = offset;  
	  } else if (fieldName.equals("VM_FieldDictionary.keys")) {
      	    FieldDictionary_keys_index = offset;
      	  } else if (fieldName.equals("VM_FieldDictionary.values")) {
      	    FieldDictionary_values_index   = offset;  
      	  } else if (fieldName.equals("VM_FieldDictionary.nextId")) {
      	    FieldDictionary_nextId_index   = offset;  
      	  } else if (fieldName.equals("VM_FieldDictionary.chains")) {
      	    FieldDictionary_chains_index   = offset; 
      	  } else if (fieldName.equals("VM_ClassLoader.compiledMethods")) {
	    ClassLoader_compiledMethods_index = offset;
      	  } else if (fieldName.equals("VM_ClassLoader.currentCompiledMethodId")) {
	    ClassLoader_currentCompiledMethodId_index = offset;
	  }

	} catch (NumberFormatException e) {
	  // skip this line if the first field is not a decimal number
	} catch (NoSuchElementException e1) {
	  // this line is blank
	}
	
      }
    } catch (FileNotFoundException e) {
      System.out.println("File " + RemoteInterpreter.jbiFileName + " not found, this is required for the interpreter. ");
      System.exit(1);
      // String sourcePath = System.getProperty("java.class.path");
      // System.out.println("Classpath was: " + sourcePath);
    } catch (IOException e1) {
      System.out.println("Error reading file RemoteInterpreter.jbiFileName");
      System.exit(1);
    }

    // dumpCache();

  }

  /**
   * This code is duplicated from jdp/registerExternal.getJTOC
   *
   */
  static public int getJTOC() {
    try {
//-#if RVM_FOR_IA32
      /* JTOC register does not exist in Lintel, have to get it through PR */
      int procReg = registerExternal.regGetNum("PR");
      procReg = Platform.readreg(procReg);
      int jtocOffset = ((VM_Field) VM.getMember("LVM_Processor;", "jtoc", "Ljava/lang/Object;")).getOffset();
      int currentJTOC = Platform.readmem(procReg + jtocOffset);
//-#else
      int jtocReg = registerExternal.regGetNum("JT");
      int currentJTOC = Platform.readreg(jtocReg);
//-#endif


      // bootstrap for the first time getJTOC is called,
      // assume we stop in a Java stack frame
      if (vmStartAddress==0) {
        VM_Field field = (VM_Field) VM.getMember("LVM_BootRecord;", "the_boot_record", "LVM_BootRecord;");
        int bootRecordAddress ;
        if (cachedJTOC==0)
          bootRecordAddress = currentJTOC + field.getOffset();
        else
          bootRecordAddress = cachedJTOC + field.getOffset();

        field = (VM_Field) VM.getMember("LVM_BootRecord;", "startAddress", "I");
        vmStartAddress = bootRecordAddress + field.getOffset();

        field = (VM_Field) VM.getMember("LVM_BootRecord;", "endAddress", "I");
        vmEndAddress = bootRecordAddress + field.getOffset();

        // System.out.println("getJTOC: " + Integer.toHexString(vmStartAddress) +
        //                 " - " + Integer.toHexString(vmEndAddress));
      }

      if (cachedJTOC==0) {
	cachedJTOC = currentJTOC;
	
      } else  {
	// for other times, check if the JTOC is within the JVM space
	// if not, just use the last known value
	int start = Platform.readmem(vmStartAddress);
	int end =  Platform.readmem(vmEndAddress);
	if (currentJTOC>=vmStartAddress && currentJTOC<=vmEndAddress)
	  cachedJTOC = currentJTOC;
      }

     } catch (Exception e1) {
       System.out.println("mapVM.getJTOC: cannot read JTOC register");
       e1.printStackTrace();
     }

    return cachedJTOC;


  }


  /**
   * Read and save the addresses of main entry points:  the dictionaries
   * This we are caching values that may change, this should be done 
   *  - Each time the JVM returns from execution, 
   *  - But not before the JVM process has been created since we need to use ptrace
   */
  static void cachePointers() {
    try {      
      int toc = getJTOC();
      // System.out.println("cachePointers: reading Dictionary entriesfrom base " + Integer.toHexString(toc) );
      AtomDictionary_keys     = Platform.readmem(toc + AtomDictionary_keys_index*4);     
      AtomDictionary_values   = Platform.readmem(toc + AtomDictionary_values_index*4);   
      AtomDictionary_nextId   = Platform.readmem(toc + AtomDictionary_nextId_index*4);   
      AtomDictionary_chains   = Platform.readmem(toc + AtomDictionary_chains_index*4);   
      MethodDictionary_keys   = Platform.readmem(toc + MethodDictionary_keys_index*4);   
      MethodDictionary_values = Platform.readmem(toc + MethodDictionary_values_index*4); 
      MethodDictionary_nextId = Platform.readmem(toc + MethodDictionary_nextId_index*4); 
      MethodDictionary_chains = Platform.readmem(toc + MethodDictionary_chains_index*4); 
      TypeDictionary_keys     = Platform.readmem(toc + TypeDictionary_keys_index*4);    
      TypeDictionary_values   = Platform.readmem(toc + TypeDictionary_values_index*4);  
      TypeDictionary_nextId   = Platform.readmem(toc + TypeDictionary_nextId_index*4);   
      TypeDictionary_chains   = Platform.readmem(toc + TypeDictionary_chains_index*4);   
      FieldDictionary_keys    = Platform.readmem(toc + FieldDictionary_keys_index*4);    
      FieldDictionary_values  = Platform.readmem(toc + FieldDictionary_values_index*4);  
      FieldDictionary_nextId  = Platform.readmem(toc + FieldDictionary_nextId_index*4);   
      FieldDictionary_chains  = Platform.readmem(toc + FieldDictionary_chains_index*4);   
      ClassLoader_compiledMethods          = Platform.readmem(toc + ClassLoader_compiledMethods_index*4);   
      ClassLoader_currentCompiledMethodId  = Platform.readmem(toc + ClassLoader_currentCompiledMethodId_index*4);

      // dumpCache();

    } catch (Exception e) {
      System.out.println("mapVM.cachePointer:  cannot find JTOC register using the name JT, has the name been changed in VM_BaselineConstants.java?");
    }
  }


  /**
   * Copy the whole method dictionary
   */
  // static void cacheMethodDictionary() {
  //   int methodAddress, codeStartAddress, codeEndAddress;
  //   System.out.println("Scanning VM_MethodDictionary: " + MethodDictionary_nextId + " entries");
  //   for (int i=1; i<MethodDictionary_nextId; i++) {
  // 	 methodAddress = Platform.readmem(MethodDictionary_values + i*4);
  // 	 if (methodAddress==0)
  // 	   continue;
  // 	 codeStartAddress = Platform.readmem(methodAddress + VMMethodInstructions_offset);
  // 	 if (codeStartAddress!=0) {
  // 	   codeEndAddress = Platform.readmem(codeStartAddress + VM.ARRAY_LENGTH_OFFSET);
  // 	   codeEndAddress = codeStartAddress + codeEndAddress*4;
  // 	 }
  //   }
  //   System.out.println("... done");
  // }

  /**
   * Determine whether a field is to be mapped
   * Currently all classes with the name VM_ is mapped
   */
  static boolean isMapped(VM_Field field) {
    String name = field.getDeclaringClass().getName().toString();
    return isMapped(name);
  }

  /**
   * Determine whether a class is to be mapped
   * Currently all classes with the name VM_ is mapped
   */
  static boolean isMapped(VM_Class cls) {
    String name = cls.getName().toString();
    return isMapped(name);
  }

  static boolean isMapped(String name){
    if (name.startsWith("VM_")) 
      return true;
    else
      return false;    
  }

  static int findAddress(VM_Field mappedField){
    return 0;
  }

  /** 
   * Access methods
   */

  public VM_Type getType() {
    return type;
  }

  public Class getJavaClass() {
    return javaClass;
  }

  public int getAddress() {
    return address;
  }

  // intended for jdp program to convert a mapped object to access the address value
  public static int getAddress(Object obj) {
    if (isMappedObject(obj))
      return ((mapVM) obj).getAddress();
    else 
      return 0;
  }
			       
  public int getSize() {
    return size;
  }

  public void setType(VM_Type newType) {
    type = newType;
    javaClass = InterpreterBase.getClassForVMType(newType);   // must be consistent
  }

  public void setClass(Class newClass) {
    javaClass = newClass;
    type = InterpreterBase.getVMTypeForClass(newClass);       // must be consistent
  }

  public void setAddress(int newAddress) {
    address = newAddress;
  }

  /**
   * Check if this object is a mapVM instance (not for checking if this
   * object is to be mapped)
   */
  public static boolean isMappedObject(Object obj) {
    if (obj==null)
      return false;
    if (obj.getClass() == mapClass)
      return true;
    else
      return false;
  }

  /**
   * Check if this class is the actual mapVM class (not for checking if this
   * class is to be mapped)
   */
  public static boolean isMappedClass(Class cls) {
    if (cls == mapClass)
      return true;
    else
      return false;
  }

  /**
   * Check if this VM_Class is the actual mapVM VM_Class (not for checking if this
   * class is to be mapped)
   */
  public static boolean isMappedClass(VM_Class cls) {
    if (cls == mapVMClass)
      return true;
    else
      return false;    
  }
    
  /**
   *  This method provides the entry to the mapped VM.  
   * It intercepts calls to access methods for VM_ object, then
   * substitutes the object being accessed with a mapVM object 
   * that wraps this object for later use.
   *  The following targets for invokestatic are intercepted:
   *   VM_AtomDictionary.getKeysPointer()   -> return mapped array of VM_Atom
   *   VM_AtomDictionary.getChainsPointer() -> return mapped array of array of int
   *   VM_AtomDictionary.getValuesPointer() -> return mapped array of VM_Atom
   *   VM_TypeDictionary.getKeysPointer()   -> return mapped array
   *   VM_ClassLoader.getCompiledMethods()  -> return mapped array of VM_CompiledMethod 
   *  Return null if the invocation target is not intercepted.
   */
  static Object getMapBase(VM_Class cls, VM_Method mth) {
    String clsName = cls.getName().toString();
    String mthName = mth.getName().toString();
    VM_Type returnType = mth.getReturnType();


    /* If a primitive value is to be returned, read it directly and return */
    /* If an object is to be returned, wrap it with the address and return */

    if (clsName.equals("VM_ClassLoader")) {
      if (mthName.equals("getCompiledMethods")) {
	/* expect [LVM_CompiledMethod; */
	return (Object) (new mapVM(returnType, ClassLoader_compiledMethods, PointerSize));
      } 
    }


    if (clsName.equals("VM_AtomDictionary")) {
      if (mthName.equals("getKeysPointer")) {
	/* expect [LVM_Atom; */
	return (Object) (new mapVM(returnType, AtomDictionary_keys, PointerSize));
      } else if (mthName.equals("getChainsPointer")) {
	/* expect [[I */
	return (Object) (new mapVM(returnType, AtomDictionary_chains, PointerSize));
      } else if (mthName.equals("getValuesPointer")) {
	/* expect [LVM_Atom; */
	return (Object) (new mapVM(returnType, AtomDictionary_values, PointerSize));
      }
    } 

    if (clsName.equals("VM_MethodDictionary")) {
      if (mthName.equals("getKeysPointer")) {
	/* expect [VM_Triplet; */
	return (Object) (new mapVM(returnType, MethodDictionary_keys, PointerSize));
      } else if (mthName.equals("getChainsPointer")) {
	/* expect [[I */
	return (Object) (new mapVM(returnType, MethodDictionary_chains, PointerSize));
      } else if (mthName.equals("getValuesPointer")) {
	/* expect [LVM_Method; */
	return (Object) (new mapVM(returnType, MethodDictionary_values, PointerSize));
      }
    }


    if (clsName.equals("VM_TypeDictionary")) {
      if (mthName.equals("getKeysPointer")) {
	/* expect [LVM_Atom; */
	return (Object) (new mapVM(returnType, TypeDictionary_keys, PointerSize));
      } else if (mthName.equals("getChainsPointer")) {
	/* expect [[I */
	return (Object) (new mapVM(returnType, TypeDictionary_chains, PointerSize));
      } else if (mthName.equals("getValuesPointer")) {
	/* expect [LVM_Type; */
	return (Object) (new mapVM(returnType, TypeDictionary_values, PointerSize));
      }
    }

    if (clsName.equals("VM_FieldDictionary")) {
      if (mthName.equals("getKeysPointer")) {
	/* expect [LVM_Triplet; */
	return (Object) (new mapVM(returnType, FieldDictionary_keys, PointerSize));
      } else if (mthName.equals("getChainsPointer")) {
	/* expect [[I */
	return (Object) (new mapVM(returnType, FieldDictionary_chains, PointerSize));
      } else if (mthName.equals("getValuesPointer")) {
	/* expect [LVM_Field; */
	return (Object) (new mapVM(returnType, FieldDictionary_values, PointerSize));
      }
    }

    return null;

  }

  /**
   * This method is used by invokevirtual to find the concrete class implementing
   * an abstract class.  One instance is in VM_TypeDictionary which
   * holds VM_Type objects; these however really are instances of VM_Array, 
   * VM_Class or VM_Primitive.  
   * To do this, we access the TIB from the object pointer, then
   * the VM_Type object and finally the descriptor of the VM_type
   * The type for this mapVM object is updated accordingly
   */
  public void handleAbstractClass() {
    // get the pointer to the real VM_Type on the JVM side
    int typeAddress = Platform.readmem(address + VM_ObjectLayoutConstants.OBJECT_TIB_OFFSET);
    typeAddress = Platform.readmem(typeAddress);           

    int descriptorAddress = Platform.readmem(typeAddress + VMTypeDescriptor_offset);
    String descriptor = getVMAtomString(descriptorAddress);
    String className = descriptor.substring(1, descriptor.length()-1).replace('/','.');
    if (InterpreterBase.traceExtension)
      System.out.println("invokevirtual:  abstract class " + 
			  type.getName().toString() + " resolved to " + className);
    try {
      javaClass = Class.forName(className);
      type = InterpreterBase.forName(className);
    } catch (Exception e) {
      System.out.println("handleAbstractClass:  ERROR finding class " +
			  className);
    }
  }

  static int getMappedPrimitive(VM_Class cls, VM_Method mth) {
    String clsName = cls.getName().toString();
    String mthName = mth.getName().toString();
    if (clsName.equals("VM_AtomDictionary")) {
      if (mthName.equals("getValue")) {
	/* expect I */
	System.out.println("getMappedObject: accessing primitive value");
	return 1;
      }
    }
    return -1;
  }

  /**
   *   Clone a copy of a String from the JVM side to the interpreter side.
   * This is to support the bytecode ldc which may produce a string literal
   * kept in the constant pool of the class
   * @param mappedString a mapVM object for the String
   * @return a cloned string
   */
  static String getMappedString(mapVM mappedString) {
    int address = mappedString.getAddress();
    VM_Field valueField = (VM_Field) VM.getMember("Ljava/lang/String;", "value", "[C");
  
    // get the char array address and size from the JVM side
    address = Platform.readmem(address + valueField.getOffset());
    int count = Platform.readmem(address + VM.ARRAY_LENGTH_OFFSET);

    // copy the char array that constitute the String
    char cloneArray[] = new char[count];
    for (int i=0; i<count; i++) {
      cloneArray[i] = (char) Platform.readShort(address + i*2);
    }

    // create a new String and return
    return (new String(cloneArray)) ;
  }

  /**
   *   Clone a copy of an array of primitive from the JVM side
   * to the interpreter side.  Currently we only handle 1D array.
   * @param mappedArray a mapVM object for an array
   * @return the array as a Java Object
   *
   */
  static Object getMappedArray(mapVM mappedArray) throws Exception {

    VM_Array arrayType = mappedArray.getType().asArray();
    VM_Type elementType = arrayType.getElementType();
    int dim = arrayType.getDimensionality();
    if (dim>1) {
      throw new Exception("don't know how to create multi dimensional array yet");
    }

    // get the element count from the JVM side
    int count = Platform.readmem(mappedArray.getAddress() + VM.ARRAY_LENGTH_OFFSET);

    // fill up the array with contents from the JVM side
    int address = mappedArray.getAddress();
    int size = mappedArray.getSize();
    int value, value1;
    if (InterpreterBase.traceExtension)
      System.out.println("getMappedArray: count " + count + ", size " + size + ", at " +
		         Integer.toHexString(address));
    if (elementType.isBooleanType()) {
      boolean cloneArray[] = new boolean[count];
      for (int i=0; i<count; i++) {
	value = Platform.readByte(address + i*size);
	cloneArray[i] = (value==0 ? false:true);
      }
      return ((Object) cloneArray);
    } 

    else if (elementType.isByteType()) {
      byte cloneArray[] = new byte[count];
      for (int i=0; i<count; i++) {
	cloneArray[i] = Platform.readByte(address + i*size);
      }
      return ((Object) cloneArray);
    } 

    else if (elementType.isCharType()) {
      char cloneArray[] = new char[count];
      for (int i=0; i<count; i++) {
	cloneArray[i] = (char) Platform.readByte(address + i*size);
      }
      return ((Object) cloneArray);
    } 

    else if (elementType.isDoubleType()) {
      double cloneArray[] = new double[count];
      for (int i=0; i<count; i++) {
	value = Platform.readmem(address + i*size);
	value1 = Platform.readmem(address + i*size + 4);
	cloneArray[i] = Double.longBitsToDouble(InterpreterStack.twoIntsToLong(value, value1));
      }
      return ((Object) cloneArray);
    } 

    else if (elementType.isFloatType()) {
      float cloneArray[] = new float[count];
      for (int i=0; i<count; i++) {
	value = Platform.readmem(address + i*size);
	cloneArray[i] = Float.intBitsToFloat(value);
      }
      return ((Object) cloneArray);
    } 

    else if (elementType.isIntType()) {
      int cloneArray[] = new int[count];
      for (int i=0; i<count; i++) {
	cloneArray[i] = Platform.readmem(address + i*size);
      }
      return ((Object) cloneArray);
    } 

    else if (elementType.isLongType()) {
      long cloneArray[] = new long[count];
      for (int i=0; i<count; i++) {
	value = Platform.readmem(address + i*size);
	value1 = Platform.readmem(address + i*size + 4);
	cloneArray[i] = InterpreterStack.twoIntsToLong(value, value1);
      }
      return ((Object) cloneArray);
    } 

    else if (elementType.isShortType()) {
      short cloneArray[] = new short[count];
      for (int i=0; i<count; i++) {
	cloneArray[i] = (short) Platform.readmem(address + i*size);
      }
      return ((Object) cloneArray);
    } 

    else { 
      System.out.println("getMappedArray: unknown type");
      throw new Exception("get unknown type for mapped array");      
    }

  }


  /**
   * Given the address of an VM_Atom object, read the byte array
   * that makes up the String, and return it as a new String
   * @param VMAtomObjectAddress address of an VM_Atom object
   */
  static String getVMAtomString(int VMAtomObjectAddress) {
    int stringAddr = Platform.readmem(VMAtomObjectAddress + VMAtomVal_offset);
    int size = Platform.readmem(stringAddr + VM.ARRAY_LENGTH_OFFSET);
    byte className[] = new byte[size];
    for (int i=0; i<size; i++) {
      className[i] = Platform.readByte(stringAddr+i);
    }
    return new String(className);
  }

  /**
   *  Manually search the VM_TypeDictionary to find a VM_Class by name
   * (1) hash the class name to get the index to the right chain
   * (2) retrieve each VM_Atom in the chain and compare the string name
   * (3) match: values[index] is the VM_Class pointer, return a mapVM object 
   *     wrapping the mapped VM_Class
   * 
   *  This method is similar to dictionaryExtension.findIdByString() except 
   * that it accesses the JVM side directly instead of through the mapVM scheme.
   */
  static int manualTypeSearch(String keyString) {

    // First compute the hash code and get the right hash chain
    int    len   = keyString.length();
    byte[] keyByte = new byte[len];
    keyString.getBytes(0, len, keyByte, 0);
    int hash = dictionaryExtension.hashName(keyByte);
    int chainLength = Platform.readmem(TypeDictionary_chains + VM.ARRAY_LENGTH_OFFSET);
    int chainIndex = (hash & 0x7fffffff) % chainLength;
    int chainAddr = Platform.readmem(TypeDictionary_chains + chainIndex*4);
    // System.out.println("manualTypeSearch: chain index " + chainIndex +
    // 		       ", address " + Integer.toHexString(chainAddr));
    
    // if no chain, key not present in dictionary
    if (chainAddr == 0)
      return 0;

    // Then walk through the index in the chain and check each VM_Atom    
    chainLength = Platform.readmem(chainAddr + VM.ARRAY_LENGTH_OFFSET);
    int candidateId = -1;

    for (int i=0; i<chainLength; i++) {
      candidateId  = Platform.readmem(chainAddr + i*4);
      int candidateAtomAddr = Platform.readmem(TypeDictionary_keys + candidateId*4);

      // if end of chain sentinal (null/zero) - key not present
      if (candidateAtomAddr==0)
	return 0;

      int candidateStringAddr = Platform.readmem(candidateAtomAddr + VMAtomVal_offset);
      int candidateLength = Platform.readmem(candidateStringAddr + VM.ARRAY_LENGTH_OFFSET);
      // System.out.println("manualTypeSearch: VM_Atom @" + Integer.toHexString(candidateAtomAddr));

      if (candidateLength==len) {
	boolean match = true;
	for (int j=0; j<candidateLength; j++) {
	  byte candidateByte = Platform.readByte(candidateStringAddr+j);
	  if (keyByte[j]!=candidateByte) {
	    match = false;
	    break;
	  }
	}
	// found a match, create the mapVM object to point to this VM_Class
	if (match) {
	  return Platform.readmem(TypeDictionary_values + candidateId*4);
	}
      }
    }

    // not found
    return 0;
  }


  /**
   *  Display the current cache of pointers into the JVM space
   * This cache should be refreshed whenever the debugger regains
   * control of the JVM.
   */
  static void dumpCache() {
    System.out.println("JTOC indices for dictionaries: ");
    System.out.println("                  keys  values chains  nextId ");
    System.out.println("AtomDictionary    " +  
		       AtomDictionary_keys_index + "   " + AtomDictionary_values_index + "   " + 
		       AtomDictionary_chains_index + "   " + AtomDictionary_nextId_index);
    System.out.println("MethodDictionary  " + 
		       MethodDictionary_keys_index + "   " + MethodDictionary_values_index + "   " + 
		       MethodDictionary_chains_index + "   " + MethodDictionary_nextId_index);
    System.out.println("TypeDictionary    " + 
		       TypeDictionary_keys_index + "   " + TypeDictionary_values_index + "   " +   
		       TypeDictionary_chains_index + "   " + TypeDictionary_nextId_index);

    System.out.println("Pointers to dictionaries: ");
    System.out.println("                  keys       values     chains     nextId ");
    System.out.println("AtomDictionary    " +  
		       Integer.toHexString(AtomDictionary_keys)   + "   " + 
		       Integer.toHexString(AtomDictionary_values) + "   " + 
		       Integer.toHexString(AtomDictionary_chains) + "   " + 
		       Integer.toHexString(AtomDictionary_nextId));
    System.out.println("MethodDictionary  " + 
		       Integer.toHexString(MethodDictionary_keys)   + "   " + 
		       Integer.toHexString(MethodDictionary_values) + "   " + 
		       Integer.toHexString(MethodDictionary_chains) + "   " + 
		       Integer.toHexString(MethodDictionary_nextId));
    System.out.println("TypeDictionary    " + 
		       Integer.toHexString(TypeDictionary_keys)   + "   " + 
		       Integer.toHexString(TypeDictionary_values) + "   " +   
		       Integer.toHexString(TypeDictionary_chains) + "   " + 
		       Integer.toHexString(TypeDictionary_nextId));
    
    System.out.println("Offsets:  ");
    System.out.println("VM_Field offset = " + VMFieldOffset_offset + 
		       ", type = " + VMFieldType_offset);
    System.out.println("VM_Class constantPool = " + VMClassConstantPool_offset +
		       ", name = " + VMClassName_offset +
		       ", superclass = " + VMClassSuper_offset);
    System.out.println("VM_Atom val = " + VMAtomVal_offset);
    System.out.println("VM_Type descriptor = " + VMTypeDescriptor_offset);
    System.out.println("VM_ClassLoader compiledMethods = " + ClassLoader_compiledMethods);
    System.out.println("VM_ClassLoader currentCompiledMethodId  = " +   
		       ClassLoader_currentCompiledMethodId);
  }

  /**
   * print this mapped object
   *
   */
  public String toString() {
    return "mapVM " + type.getName() + " @ " + Integer.toHexString(address);
  }

}
