/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.*;
import java.io.DataInputStream;
import java.io.IOException;

//-#if RVM_WITH_OPT_COMPILER
import com.ibm.JikesRVM.opt.*;
//-#endif

/**
 * A method of a java class.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public final class VM_Method extends VM_Member {

  /**
   * words needed for local variables (including parameters)
   */
  private final int localWords;          
  /**
   * words needed for operand stack (high water mark)
   */
  //-#if RVM_WITH_OSR
  private int operandWords;        
  //-#else
  private final int operandWords;        
  //-#endif
  /**
   * bytecodes for this method (null --> none)
   */
  private final byte[] bytecodes;           
  /**
   * try/catch/finally blocks for this method (null --> none)
   */
  private final VM_ExceptionHandlerMap exceptionHandlerMap; 
  /**
   * exceptions this method might throw (null --> none)
   */
  private final VM_Type[] exceptionTypes;      
  /**
   * pc to source-line info (null --> none)
   */
  private final VM_LineNumberMap lineNumberMap;       

  // Byte Code Annotations
  private short[]	annotationPC;
  private byte[]	annotationValue;
  public static final byte	annotationNullCheck = 4;
  public static final byte	annotationBoundsCheck = 3;	// Both Upper/Lower
  private int		annotationNum;
  private int		annotationPrior;

  /**
   * current compiled method for this method
   */
  private VM_CompiledMethod currentCompiledMethod;

  /**
   * the name of the native procedure in the native library
   */
  private String nativeProcedureName;                 
  /**
   * the IP of the native p rocedure
   */
  private int nativeIP;                               
  /**
   * the TOC of the native procedure
   */
  private int nativeTOC;                              

  /**
   * NOTE: Only {@link VM_Class} is allowed to create an instance of a VM_Field.
   * 
   * @param declaringClass the VM_Class object of the class that declared this field
   * @param memRef the cannonical memberReference for this member.
   * @param modifiers modifiers associated with this member.
   * @param input the DataInputStream to read the field's attributed from
   */
  VM_Method(VM_Class declaringClass, VM_MemberReference memRef,
	    int modifiers, DataInputStream input) throws IOException {
    super(declaringClass, memRef, modifiers & APPLICABLE_TO_METHODS);
    memRef.asMethodReference().setResolvedMember(this);
    ClassLoader cl = declaringClass.getClassLoader();

    // A number of 'final' fields either have values or not depending
    // on the attributes we read from the bytecodes.  The following kludge
    // lets us declare the fields to be final by making sure that there is 
    // a single definite assignment to the fields in the constructor.
    int tmp_localWords = 0;
    int tmp_operandWords = 0;      
    byte[] tmp_bytecodes = null;       
    VM_ExceptionHandlerMap tmp_exceptionHandlerMap = null;
    VM_Type[] tmp_exceptionTypes = null;
    VM_LineNumberMap tmp_lineNumberMap = null;      

    // Read the attributes
    for (int i = 0, n = input.readUnsignedShort(); i<n; i++) {
      VM_Atom attName   = declaringClass.getUtf(input.readUnsignedShort());
      int     attLength = input.readInt();

      // Only bother to interpret non-boring Method attributes
      if (attName == VM_ClassLoader.codeAttributeName) {
        tmp_operandWords = input.readUnsignedShort();
        tmp_localWords   = input.readUnsignedShort();
        tmp_bytecodes = new byte[input.readInt()];
        input.readFully(tmp_bytecodes);
        int cnt = input.readUnsignedShort();
        if (cnt != 0) {
          tmp_exceptionHandlerMap = new VM_ExceptionHandlerMap(input, declaringClass, cnt);
	}
	// Read the attributes portion of the code attribute
	for (int j = 0, n2 = input.readUnsignedShort(); j<n2; j++) {
	  attName   = declaringClass.getUtf(input.readUnsignedShort());
	  attLength = input.readInt();

	  if (attName == VM_ClassLoader.lineNumberTableAttributeName) {
	    cnt = input.readUnsignedShort();
	    if (cnt != 0) {
	      tmp_lineNumberMap = new VM_LineNumberMap(input, cnt);
	    }
	  } else if (attName == VM_ClassLoader.arrayNullCheckAttributeName) {
	    annotationNum = 0;
	    int attNum = attLength/3;
	    annotationPC = new short[attNum];
	    annotationValue = new byte[attNum];
	    for (int attIndex = 0; attIndex < attNum; attIndex++) {
	      short pc = (short)input.readUnsignedShort();
	      byte value = (byte)input.readUnsignedByte();
	      if (value != 0) {
		// exclude non-interesting 0 values seen coming from soot
		annotationPC[annotationNum] = pc;
		annotationValue[annotationNum++] = value;
	      }
	    }
	    if (annotationNum == 0) {	
	      // no entries of interest allow storage to be reclaimed
	      annotationPC = null;
	      annotationValue = null;
	    } else {
	      annotationPrior = 0;		// 1st probe
	    }
	  } else {
	    // All other entries in the attribute portion of the code attribute are boring.
	    input.skipBytes(attLength);
	  }
	}
      } else if (attName == VM_ClassLoader.exceptionsAttributeName) {
        int cnt = input.readUnsignedShort();
        if (cnt != 0) {
          tmp_exceptionTypes = new VM_Type[cnt];
          for (int j = 0, m = tmp_exceptionTypes.length; j < m; ++j)
            tmp_exceptionTypes[j] = declaringClass.getTypeRef(input.readUnsignedShort());
        }
      } else {
	// all other method attributes are boring
        input.skipBytes(attLength);
      }
    }

    localWords = tmp_localWords;
    operandWords = tmp_operandWords;
    bytecodes = tmp_bytecodes;
    exceptionHandlerMap = tmp_exceptionHandlerMap;
    exceptionTypes = tmp_exceptionTypes;
    lineNumberMap = tmp_lineNumberMap;

    //-#if RVM_WITH_OPT_COMPILER
    if (bytecodes != null) {
      VM_OptMethodSummary.summarizeMethod(this, bytecodes, 
					  (modifiers & ACC_SYNCHRONIZED) != 0);
    }
    //-#endif
  }

  /**
   * Is this method a class initializer?
   */
  public final boolean isClassInitializer() throws VM_PragmaUninterruptible { 
    return getName() == VM_ClassLoader.StandardClassInitializerMethodName;  
  }

  /**
   * Is this method an object initializer?
   */
  public final boolean isObjectInitializer() throws VM_PragmaUninterruptible { 
    return getName() == VM_ClassLoader.StandardObjectInitializerMethodName; 
  }

  /**
   * Is this method a compiler-generated object initializer helper?
   */
  public final boolean isObjectInitializerHelper() throws VM_PragmaUninterruptible { 
    return getName() == VM_ClassLoader.StandardObjectInitializerHelperMethodName; 
  }

  /**
   * Type of this method's return value.
   */
  public final VM_Type getReturnType() throws VM_PragmaUninterruptible {
    return memRef.asMethodReference().getReturnType();
  }

  /**
   * Type of this method's parameters.
   * Note: does *not* include implicit "this" parameter, if any.
   */
  public final VM_Type[] getParameterTypes() throws VM_PragmaUninterruptible {
    return memRef.asMethodReference().getParameterTypes();
  }

  /**
   * Space required by this method for its parameters, in words.
   * Note: does *not* include implicit "this" parameter, if any.
   */
  public final int getParameterWords() throws VM_PragmaUninterruptible {
    return memRef.asMethodReference().getParameterWords();
  }

  /**
   * Has machine code been generated for this method's bytecodes?
   */
  public final boolean isCompiled() {
    return currentCompiledMethod != null;
  }

  /**
   * Get the current compiled method for this method.
   * Will return null if there is no current compiled method!
   * @return compiled method
   */ 
  public final synchronized VM_CompiledMethod getCurrentCompiledMethod() {
    return currentCompiledMethod;
  }

  // Annotations are sparse and sorted in bytecode order. State for the last
  // successful lookup is kept. We first check it to catch repeated quries
  // for the same bytecode. Failing this, we perform a binary search where the
  // first probe is either the next sequential entry or 0 (typical cases).
  public final boolean queryAnnotationForBytecode(int pc, byte mask) {
    if (annotationNum == 0) return false;		// none

    int currPC = annotationPC[annotationPrior];	// prior pc found
    int annotationIndex, Lo, Hi;

    if (currPC == pc)					// same as last match
      annotationIndex = annotationPrior;
    else {
      if ( currPC < pc ) {
	Lo = annotationPrior+1;
	Hi = annotationNum-1;
      }
      else {
	Lo = 0;
	Hi = annotationPrior-1;
      }
      annotationIndex = Lo;		// start with next sequential entry
      while( true ) {
	if ( Lo > Hi ) return false;	// not found
	currPC = annotationPC[ annotationIndex ];
	if ( currPC == pc ) break;	// found
	if ( currPC < pc )
	  Lo = annotationIndex+1;
	else
	  Hi = annotationIndex-1;
	annotationIndex = (Lo+Hi) >> 1;	// split for next probe
      }
    }
    annotationPrior = annotationIndex;
    return (annotationValue[ annotationIndex ] & mask) == mask;
  }

  /**
   * Declared as statically dispatched?
   */
  public final boolean isStatic() throws VM_PragmaUninterruptible {
    return (modifiers & ACC_STATIC) != 0;
  }

  /**
   * Declared as non-overridable by subclasses?
   */
  public final boolean isFinal() throws VM_PragmaUninterruptible {
    return (modifiers & ACC_FINAL) != 0;
  }

  /**
   * Guarded by monitorenter/monitorexit?
   */
  public final boolean isSynchronized() throws VM_PragmaUninterruptible {
    return (modifiers & ACC_SYNCHRONIZED) != 0;
  }

  /**
   * Not implemented in java?
   */
  public final boolean isNative() throws VM_PragmaUninterruptible {
    return (modifiers & ACC_NATIVE) != 0;
  }

  /**
   * Implemented in subclass?
   */
  public final boolean isAbstract() throws VM_PragmaUninterruptible {
    return (modifiers & ACC_ABSTRACT) != 0;
  }

  /**
   * Space required by this method for its local variables, in words.
   * Note: local variables include parameters
   */
  public final int getLocalWords() throws VM_PragmaUninterruptible {
    return localWords;
  }

  /**
   * Space required by this method for its operand stack, in words.
   */
  public final int getOperandWords() throws VM_PragmaUninterruptible {
    return operandWords;
  }

  /**
   * Get a representation of the bytecodes in the code attribute of this method.
   * @return object representing the bytecodes
   */
  public final VM_BytecodeStream getBytecodes() {
    return new VM_BytecodeStream(this, bytecodes);
  }  
  
  /**
   * Fill in DynamicLink object for the invoke at the given bytecode index
   * @param dynamicLink the dynamicLink object to initialize
   * @param bcIndex the bcIndex of the invoke instruction
   */
  public final void getDynamicLink(VM_DynamicLink dynamicLink, int bcIndex) throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(bytecodes != null);
    if (VM.VerifyAssertions) VM._assert(bcIndex + 2 < bytecodes.length);
    int bytecode = bytecodes[bcIndex] & 0xFF;
	if (VM.VerifyAssertions) VM._assert((VM_BytecodeConstants.JBC_invokevirtual <= bytecode)
										&& (bytecode <= VM_BytecodeConstants.JBC_invokeinterface));
    int constantPoolIndex = ((bytecodes[bcIndex + 1] & 0xFF) << 8) | (bytecodes[bcIndex + 2] & 0xFF);
    dynamicLink.set(declaringClass.getMethodRef(constantPoolIndex), bytecode);
  }

  public final int getBytecodeLength() {
    return bytecodes == null ? 0 : bytecodes.length;
  }

  /**
   * Exceptions caught by this method.
   * @return info (null --> method doesn't catch any exceptions)
   */
  public final VM_ExceptionHandlerMap getExceptionHandlerMap() throws VM_PragmaUninterruptible {
    return exceptionHandlerMap;
  }

  /**
   * Exceptions thrown by this method - 
   * something like { "java/lang/IOException", "java/lang/EOFException" }
   * @return info (null --> method doesn't throw any exceptions)
   */
  public final VM_Type[] getExceptionTypes() throws VM_PragmaUninterruptible {
    return exceptionTypes;
  }

  /**
   * Line numbers for this method.
   * @return info (null --> native or abstract: no code, no exception map)
   */
  public final VM_LineNumberMap getLineNumberMap() throws VM_PragmaUninterruptible {
    return lineNumberMap;
  }

  /**
   * Is this method interruptible?
   * In other words, should the compiler insert threadswitch points
   * aka yieldpoints in method prologue, epilogue, and backwards branches.
   * Also, methods that are Uninterruptible do not have stackoverflow checks
   * in the method prologue (since there is no mechanism for handling a stackoverflow
   * that doesn't violate the uninterruptiblity of the method).
   * A method is Uninterruptible if 
   * <ul>
   * <li> It is not a <clinit> or <init> method.
   * <li> It is not the synthetic 'this' method used by jikes to
   *      factor out default initializers for <init> methods.
   * <li> it throws the <CODE>VM_PragmaUninterruptible</CODE> exception.
   * <li> it's declaring class directly implements the <CODE>VM_Uninterruptible</CODE>
   *      interface and the method does not throw the <CODE>VM_PragmaInterruptible</CODE>
   *      exception.
   * </ul>
   */
  public final boolean isInterruptible() {
    if (isClassInitializer() || isObjectInitializer()) return true;
    if (isObjectInitializerHelper()) return true;
    if (VM_PragmaInterruptible.declaredBy(this)) return true;
    if (VM_PragmaUninterruptible.declaredBy(this)) return false;
    VM_Class[] interfaces = getDeclaringClass().getDeclaredInterfaces();
    for (int i = 0, n = interfaces.length; i < n; ++i) {
      if (interfaces[i].isUninterruptibleType()) return false;
    }
    return true;
  }

  /**
   * Has this method been marked as mandatory to inline?
   * ie., it throws the <CODE>VM_PragmaInline</CODE> exception?
   */
  public final boolean hasInlinePragma() {
    return VM_PragmaInline.declaredBy(this);
  }
    
  /**
   * Has this method been marked as forbidden to inline?
   * ie., it throws the <CODE>VM_PragmaNoInline</CODE> or
   * the <CODE>VM_PragmaNoOptCompile</CODE> exception?
   */
  public final boolean hasNoInlinePragma() {
    return VM_PragmaNoInline.declaredBy(this) || VM_PragmaNoOptCompile.declaredBy(this);
  }
    
  /**
   * Has this method been marked as no opt compile?
   * ie., it throws the <CODE>VM_PragmaNoOptCompile</CODE> exception?
   */
  public final boolean hasNoOptCompilePragma() {
    return VM_PragmaNoOptCompile.declaredBy(this);
  }
    
  //------------------------------------------------------------------//
  //                        Section 2.                                //
  // The following are available after the declaring class has been   //
  // "resolved".                                                      //
  //------------------------------------------------------------------//

  /**
   * Get the current instructions for the given method.
   */
  public synchronized INSTRUCTION[] getCurrentInstructions() {
    if (VM.VerifyAssertions) VM._assert(declaringClass.isResolved());
    if (isCompiled()) {
      return currentCompiledMethod.getInstructions();
    } else if (VM.BuildForLazyCompilation && (!VM.writingBootImage || isNative())) {
      return VM_LazyCompilationTrampolineGenerator.getTrampoline();
    } else {
      compile(); 
      return currentCompiledMethod.getInstructions();
    }
  }

  /**
   * Generate machine code for this method if valid
   * machine code doesn't already exist. 
   * Return the resulting VM_CompiledMethod object.
   * 
   * @return VM_CompiledMethod object representing the result of the compilation.
   */
  public final synchronized void compile() {
    if (VM.VerifyAssertions) VM._assert(declaringClass.isResolved());
    if (isCompiled()) return;

    if (VM.VerifyBytecode) {
      VM_Verifier verifier = new VM_Verifier();
      try {
        boolean success = verifier.verifyMethod(this);
        if (!success) {
          VM.sysWrite("Method " + this + " fails bytecode verification!\n");
        }
      } catch(Exception e) {
        VM.sysWrite("Method " + this + " fails bytecode verification!\n");
      }
    }

    if (VM.BuildForEventLogging && VM.EventLoggingEnabled) VM_EventLogger.logCompilationEvent();
    if (VM.TraceClassLoading && VM.runningVM)  VM.sysWrite("VM_Method: (begin) compiling " + this + "\n");

    VM_CompiledMethod cm;
    if (isAbstract()) {
      VM_Entrypoints.unexpectedAbstractMethodCallMethod.compile();
      cm = VM_Entrypoints.unexpectedAbstractMethodCallMethod.getCurrentCompiledMethod();
    } else if (isNative() && !resolveNativeMethod()) {
      // if fail to resolve native, get code to throw unsatifiedLinkError
      VM_Entrypoints.unimplementedNativeMethodMethod.compile();
      cm = VM_Entrypoints.unimplementedNativeMethodMethod.getCurrentCompiledMethod();
    } else {
      // Normal case. Compile the method.
      //
      if (VM.writingBootImage)
        cm = VM_BootImageCompiler.compile(this); // use compiler specified by RVM_BOOT_IMAGE_COMPILER_PATH
      else 
        cm = VM_RuntimeCompiler.compile(this);   // use compiler specified by RVM_RUNTIME_COMPILER_PATH
    }

    // Ensure that cm wasn't invalidated while it was being compiled.
    synchronized(cm) {
      if (cm.isInvalid()) {
	VM_CompiledMethods.setCompiledMethodObsolete(cm);
      } else {
	currentCompiledMethod = cm;
      }
    }

    if (VM.TraceClassLoading && VM.runningVM)  VM.sysWrite("VM_Method: (end)   compiling " + this + "\n");
  }

  //----------------------------------------------------------------//
  //                        Section 3.                              //
  // The following are available after the declaring class has been // 
  // "instantiated".                                                //
  //----------------------------------------------------------------//

  /**
   * Change machine code that will be used by future executions of this method 
   * (ie. optimized <-> non-optimized)
   * @param compiledMethod new machine code
   * Side effect: updates jtoc or method dispatch tables 
   * ("type information blocks")
   *              for this class and its subclasses
   */ 
  public final synchronized void replaceCompiledMethod(VM_CompiledMethod compiledMethod) {
    if (VM.VerifyAssertions) VM._assert(declaringClass.isInstantiated());
    // If we're replacing with a non-null compiledMethod, ensure that is still valid!
    if (compiledMethod != null) {
      synchronized(compiledMethod) {
	if (compiledMethod.isInvalid()) return;
      }
    }
      
    // Grab version that is being replaced
    VM_CompiledMethod oldCompiledMethod = currentCompiledMethod;
    currentCompiledMethod = compiledMethod;

    // Install the new method in jtoc/tib. If virtual, will also replace in
    // all subclasses that inherited the method.
    getDeclaringClass().updateMethod(this);

    // Now that we've updated the jtoc/tib, old version is obsolete
    if (oldCompiledMethod != null) {
      VM_CompiledMethods.setCompiledMethodObsolete(oldCompiledMethod);
    }
  }

  /**
   * If CM is the current compiled code for this, then invaldiate it. 
   */
  public final synchronized void invalidateCompiledMethod(VM_CompiledMethod cm) {
    if (VM.VerifyAssertions) VM._assert(declaringClass.isInstantiated());
    if (currentCompiledMethod == cm) {
      replaceCompiledMethod(null);
    }
  }

  //////////////////////////////////////////////////////////////
  // SUPPORT FOR NATIVE METHODS
  //////////////////////////////////////////////////////////////

  public int getNativeIP() { 
    return nativeIP;
  }

  public int getNativeTOC() { 
    return nativeTOC;
  }

  /**
   * replace a character in a string with a string
   */
  private String replaceCharWithString(String originalString, 
                                       char targetChar, 
                                       String replaceString) {
    String returnString;
    int first = originalString.indexOf(targetChar);
    int next  = originalString.indexOf(targetChar, first+1);
    if (first!=-1) {
      returnString = originalString.substring(0,first) + replaceString;
      while (next!=-1) {
        returnString += originalString.substring(first+1, next) + replaceString;
        first = next;
        next = originalString.indexOf(targetChar, next+1);
      }
      returnString += originalString.substring(first+1);
    } else {
      returnString = originalString;
    }
    return returnString;
  }


  /**
   * Compute the mangled name of the native routine: Java_Class_Method_Sig
   */
  private String getMangledName(boolean sig) {
    String mangledClassName, mangledMethodName;
    String className = declaringClass.getName().toString();
    String methodName = getName().toString();
    int first, next;

    // Mangled Class name
    // Special case: underscore in class name
    mangledClassName = replaceCharWithString(className, '_', "_1");

    // Mangled Method name
    // Special case: underscore in method name
    //   class._underscore  -> class__1underscore
    //   class.with_underscore  -> class_with_1underscore
    mangledMethodName = replaceCharWithString(methodName, '_', "_1");

    if (sig) {
      String sigName = getDescriptor().toString();
      sigName = sigName.substring( sigName.indexOf('(')+1, sigName.indexOf(')') );
      sigName = replaceCharWithString(sigName, '[', "_3");
      sigName = replaceCharWithString(sigName, ';', "_2");
      sigName = sigName.replace( '/', '_');
      mangledMethodName += "__" + sigName;
    }


    String mangledName = "Java_" + mangledClassName + "_" + mangledMethodName;
    mangledName = mangledName.replace( '.', '_' );
    // VM.sysWrite("getMangledName:  " + mangledName + " \n");

    return mangledName;

  }


  private boolean resolveNativeMethod() {
    nativeProcedureName = getMangledName(false);
    String nativeProcedureNameWithSigniture = getMangledName(true);

    // get the library in VM_ClassLoader
    // resolve the native routine in the libraries
    VM_DynamicLibrary libs[] = VM_ClassLoader.getDynamicLibraries();
    VM_Address symbolAddress = VM_Address.zero();
    if (libs!=null) {
      for (int i=1; i<libs.length && symbolAddress.isZero(); i++) {
        VM_DynamicLibrary lib = libs[i];

        if (lib!=null && symbolAddress==VM_Address.zero()) {
          symbolAddress = lib.getSymbol(nativeProcedureNameWithSigniture);
	  if (symbolAddress != VM_Address.zero()) {
	      nativeProcedureName = nativeProcedureNameWithSigniture;
	      break;
	  }
	}

        if (lib != null && symbolAddress==VM_Address.zero()) {
          symbolAddress = lib.getSymbol(nativeProcedureName);
	  if (symbolAddress != VM_Address.zero()) {
	      nativeProcedureName = nativeProcedureName;
	      break;
	  }
	}
      }
    }

    if (symbolAddress.isZero()) {
      // native procedure not found in library
      return false;
    } else {
      //-#if RVM_FOR_IA32
      nativeIP = symbolAddress.toInt();		// Intel use direct branch address
      nativeTOC = 0;                          // not used
      //-#else
      nativeIP  = VM_Magic.getMemoryWord(symbolAddress);     // AIX use a triplet linkage
      nativeTOC = VM_Magic.getMemoryWord(symbolAddress.add(4));
      //-#endif
      // VM.sysWrite("resolveNativeMethod: " + nativeProcedureName + ", IP = " + VM.intAsHexString(nativeIP) + ", TOC = " + VM.intAsHexString(nativeTOC) + "\n");
      return true;
    }

  }

  //-#if RVM_WITH_OSR 
  // Extra fields and methods for on-stack replacement
  // VM_BaselineCompiler and OPT_BC2IR should check if a method is
  // for specialization by calling isForOsrSpecialization, the compiler
  // uses synthesized bytecodes (prologue + original bytecodes) for 
  // OSRing method. Other interfaces of method are not changed, therefore,
  // dynamic linking and gc referring to bytecodes are safe.
  
  /* bytecode array constists of prologue and original bytecodes */
  private byte[] synthesizedBytecodes = null;
  /* record osr prologue */
  private byte[] osrPrologue = null;
  /* prologue may change the maximum stack height, remember the
   * original stack height */
  private int savedOperandWords;
  
  /**
   * Checks if the method is in state for OSR specialization now 
   * @return true, if it is (with prologue)
   */
  public boolean isForOsrSpecialization() {
    return this.synthesizedBytecodes != null;
  }

  /**
   * Sets method in state for OSR specialization, i.e, the subsequent calls
   * of getBytecodes return the stream of specilized bytecodes.
   * NB: between flag and action, it should not allow GC or threadSwitch happen.
   * @param prologue, the bytecode of prologue
   * @param newStackHeight, the prologue may change the default height of 
   *                        stack
   */
  public void setForOsrSpecialization(byte[] prologue, int newStackHeight) {
    if (VM.VerifyAssertions) VM._assert(this.synthesizedBytecodes == null);
	
    byte[] newBytecodes = new byte[prologue.length + bytecodes.length];
    System.arraycopy(prologue, 0, newBytecodes, 0, prologue.length);
    System.arraycopy(bytecodes, 0, newBytecodes, prologue.length, bytecodes.length);
   
	this.osrPrologue = prologue;
    this.synthesizedBytecodes = newBytecodes;
	this.savedOperandWords = operandWords;
	if (newStackHeight > operandWords) 
	  this.operandWords = newStackHeight;
  }
 
  /**
   * Restores the original state of the method.
   */
  public void finalizeOsrSpecialization() {
    if (VM.VerifyAssertions) VM._assert(this.synthesizedBytecodes != null);
    this.synthesizedBytecodes = null;
    this.osrPrologue  = null;
    this.operandWords = savedOperandWords;
  }

  /**
   * Returns the OSR prologue length for adjusting various tables and maps.
   * @return the length of prologue if the method is in state for OSR,
   *         0 otherwise.
   */
  public int getOsrPrologueLength() {
    return isForOsrSpecialization()?this.osrPrologue.length:0;
  }

  /** 
   * Returns a bytecode stream of osr prologue
   * @return osr prologue bytecode stream
   */
  public VM_BytecodeStream getOsrPrologue() {
	if (VM.VerifyAssertions) VM._assert(synthesizedBytecodes != null);
	return new VM_BytecodeStream(this, osrPrologue);
  }
  
  /**
   * Returns the synthesized bytecode stream with osr prologue
   * @return bytecode stream
   */
  public VM_BytecodeStream getOsrSynthesizedBytecodes() {
	if (VM.VerifyAssertions) VM._assert(synthesizedBytecodes != null);
	return new VM_BytecodeStream(this, synthesizedBytecodes);
  }		 
  //-#endif RVM_WITH_OSR
}
