/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.io.DataInputStream;
import java.io.IOException;

/**
 * A method of a java class.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public class VM_Method extends VM_Member implements VM_ClassLoaderConstants {
  //-----------//
  // Interface //
  //-----------//

  //----------------------------------------------------------------//
  //                      Section 0.                                //
  // The following are always available.                            //
  //----------------------------------------------------------------//

  /**
   * Classifiers.
   */
  public final boolean isClassInitializer()   
  { return name == VM_ClassLoader.StandardClassInitializerMethodName;  }
  public final boolean isObjectInitializer()  
  { return name == VM_ClassLoader.StandardObjectInitializerMethodName; }

  /**
   * Type of this method's return value.
   */
  public final VM_Type getReturnType() {
    return returnType;
  }

  /**
   * Type of this method's parameters.
   * Note: does *not* include implicit "this" parameter, if any.
   */
  public final VM_Type[] getParameterTypes() {
    return parameterTypes;
  }

  /**
   * Space required by this method for its parameters, in words.
   * Note: does *not* include implicit "this" parameter, if any.
   */
  final int getParameterWords() {
    return parameterWords;
  }

  /**
   * Has machine code been generated for this method's bytecodes?
   */
  final boolean isCompiled() {
    if (VM.VerifyAssertions) 
      VM.assert(!declaringClass.isLoaded() || isLoaded());
    return mostRecentlyGeneratedInstructions != null;
  }

  /**
   * Find source line number corresponding to one of this method's bytecodes.
   * @param pc offset of bytecode from start of this method, in bytes
   * @return source line number 
   * (0 == no line info available, 1 == first line of source file)
   *
   * Note: this method is for use by VM_Interpreter. 
   * It is not needed for the core vm.
   */
  final int findLineNumberForBytecode(int pc) {
    if (VM.VerifyAssertions) VM.assert(isLoaded());

    if (lineNumberMap == null)
      return 0; // javac didn't provide any info

    // since "pc" points just beyond the desired instruction,
    // we scan for the line whose "pc" most-closely-preceeds
    // the desired instruction
    //
    int[] startPCs       = lineNumberMap.startPCs;
    int[] lineNumbers    = lineNumberMap.lineNumbers;
    int   candidateIndex = -1;
    for (int i = 0, n = startPCs.length; i < n; ++i)
    {
      if (startPCs[i] >= pc)
        break;
      candidateIndex = i;
    }

    if (candidateIndex == -1)
      return 0; // not found

    return lineNumbers[candidateIndex];
  }

  // Annotations are sparse and sorted in bytecode order. State for the last
  // successful lookup is kept. We first check it to catch repeated quries
  // for the same bytecode. Failing this, we perform a binary search where the
  // first probe is either the next sequential entry or 0 (typical cases).
  final boolean queryAnnotationForBytecode( int pc, byte mask ) {

    if ( annotationNum == 0 ) return false;		// none

    int currPC = annotationPC[ annotationPrior ];	// prior pc found
    int annotationIndex, Lo, Hi;

    if ( currPC == pc )					// same as last match
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

  //-------------------------------------------------------------------//
  //                             Section 1.                            //
  // The following are available after the declaring class has been    //
  // "loaded".                                                         //
  //-------------------------------------------------------------------//

  //
  // Attributes.
  //

  /**
   * Declared as statically dispatched?
   */
  public final boolean isStatic() {
    if (VM.VerifyAssertions) VM.assert(declaringClass.isLoaded());
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return (modifiers & ACC_STATIC) != 0;
  }

  /**
   * Declared as non-overridable by subclasses?
   */
  final boolean isFinal() {
    if (VM.VerifyAssertions) VM.assert(declaringClass.isLoaded());
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return (modifiers & ACC_FINAL) != 0;
  }

  /**
   * Guarded by monitorenter/monitorexit?
   */
  final boolean isSynchronized() {
    if (VM.VerifyAssertions) VM.assert(declaringClass.isLoaded());
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return (modifiers & ACC_SYNCHRONIZED) != 0;
  }

  /**
   * Not implemented in java?
   */
  final boolean isNative() {
    if (VM.VerifyAssertions) VM.assert(declaringClass.isLoaded());
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return (modifiers & ACC_NATIVE) != 0;
  }

  /**
   * Implemented in subclass?
   */
  final boolean isAbstract() {
    if (VM.VerifyAssertions) VM.assert(declaringClass.isLoaded());
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return (modifiers & ACC_ABSTRACT) != 0;
  }

  /**
   * Space required by this method for its local variables, in words.
   * Note: local variables include parameters
   */
  final int getLocalWords() {
    if (VM.VerifyAssertions) VM.assert(declaringClass.isLoaded());
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return localWords;
  }

  /**
   * setter for localWords
   */
  final void setLocalWords(int lwords) {
    localWords = lwords;
  }

  /**
   * Space required by this method for its operand stack, in words.
   */
  final int getOperandWords() {
    if (VM.VerifyAssertions) VM.assert(declaringClass.isLoaded());
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return operandWords;
  }

  /**
   * setter for operandWords
   */
  final void setOperandWords(int owords) {
    operandWords = owords;
  }

  /**
   * Bytecodes to be executed by this method.
   * @return bytecodes (null --> native or abstract: no code)
   */
  final byte[] getBytecodes() {
    if (VM.VerifyAssertions) VM.assert(declaringClass.isLoaded());
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return bytecodes;
  }

  /**
   * setter for bytecodes
   */
  final void setBytecodes(byte[] bcodes) {
    bytecodes = bcodes;
  }


  /**
   * Local variables defined by this method.
   * @return info (null --> no locals or method wasn't compiled with "-g")
   */
  final VM_LocalVariable[] getLocalVariables() {
    if (VM.VerifyAssertions) VM.assert(declaringClass.isLoaded());
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return localVariables;
  }

  /**
   * Exceptions caught by this method.
   * @return info (null --> method doesn't catch any exceptions)
   */
  final VM_ExceptionHandlerMap getExceptionHandlerMap() {
    if (VM.VerifyAssertions) VM.assert(declaringClass.isLoaded());
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return exceptionHandlerMap;
  }

  /**
   * setter for exception handler map
   */
  final void
    setExceptionHandlerMap(VM_ExceptionHandlerMap ehm)
    {
      exceptionHandlerMap = ehm;
    }

  /**
   * Exceptions thrown by this method - 
   * something like { "java/lang/IOException", "java/lang/EOFException" }
   * @return info (null --> method doesn't throw any exceptions)
   */
  public final VM_Type[] getExceptionTypes() {
    if (VM.VerifyAssertions) VM.assert(declaringClass.isLoaded());
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return exceptionTypes;
  }

  /**
   * Line numbers for this method.
   * @return info (null --> native or abstract: no code, no exception map)
   */
  final VM_LineNumberMap getLineNumberMap() {
    if (VM.VerifyAssertions) VM.assert(declaringClass.isLoaded());
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return lineNumberMap;
  }

  /**
   * setter for line number map
   */
  final void setLineNumberMap(VM_LineNumberMap lnm) {
    lineNumberMap = lnm;
  }


  //------------------------------------------------------------------//
  //                        Section 2.                                //
  // The following are available after the declaring class has been   //
  // "resolved".                                                      //
  //------------------------------------------------------------------//

  /**
   * The actual method that this object represents
   */
  public final VM_Method resolve() {
    if (VM.VerifyAssertions) VM.assert(declaringClass.isResolved());
    if (!isLoaded()) return VM_ClassLoader.repairMethod(this);
    return this;
  }

  /**
   * NB: Different semantics than resolve.
   *     If canLoad == false, this method may return null to signify that
   *     the method could not be resolved.
   */
  public final VM_Method resolveInterfaceMethod(boolean canLoad) throws VM_ResolutionException {
    if (!isLoaded()) return VM_ClassLoader.repairInterfaceMethod(this, canLoad);
    return this;
  }

  /**
   * Get offset of method pointer (for standard method dispatching), in bytes.
   *
   * <p> For static method, offset is with respect to
   * virtual machine's "table of contents" (jtoc).
   *
   * <p> For non-static method, offset is with respect to
   * object's type information block.
   */
  public final int getOffset() {
    if (VM.VerifyAssertions) VM.assert(declaringClass.isResolved());
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    return offset;
  }

  /**
   * Generate machine code for this method's bytecodes if we 
   * haven't already done so.
   * @return copy of machine code that was generated
   */
  final synchronized INSTRUCTION[] compile() {
    if (VM.VerifyAssertions) VM.assert(isLoaded());

    if (isCompiled())
      return mostRecentlyGeneratedInstructions;

    if (VM.BuildForEventLogging && VM.EventLoggingEnabled) VM_EventLogger.logCompilationEvent();
    if (VM.VerifyAssertions)   VM.assert(declaringClass.isResolved());
    if (VM.TraceClassLoading && VM.runningVM)  VM.sysWrite("VM_Method: (begin) compiling " + this + "\n");

    if (isAbstract())
      mostRecentlyGeneratedInstructions = getUnexpectedAbstractMethodInstructions();
    else if (isNative()
             && !resolveNativeMethod()
            )
      // if fail to resolve native, get code to throw unsatifiedLinkError
      mostRecentlyGeneratedInstructions = getUnexpectedNativeMethodInstructions();
    else
    {
      // generate machine code
      //
      VM_CompiledMethod compiledMethod;
      if (VM.writingBootImage)
        compiledMethod = VM_BootImageCompiler.compile(this); // use compiler specified by RVM_BOOT_IMAGE_COMPILER_PATH
      else if (VM.runningVM)
        compiledMethod = VM_RuntimeCompiler.compile(this);   // use compiler specified by RVM_RUNTIME_COMPILER_PATH
      else
        compiledMethod = VM_Compiler.compile(this);          // use baseline compiler (assumption: we're producing information for debugger)

      // save it away
      //
      VM_CompiledMethods.setCompiledMethod(compiledMethod.getId(), compiledMethod);
      mostRecentlyGeneratedInstructions = compiledMethod.getInstructions();
      mostRecentlyGeneratedCompiledMethod = compiledMethod;
    }

    if (VM.TraceClassLoading && VM.runningVM)  VM.sysWrite("VM_Method: (end)   compiling " + this + "\n");
    return mostRecentlyGeneratedInstructions;
  }

  //----------------------------------------------------------------//
  //                        Section 3.                              //
  // The following are available after the declaring class has been // 
  // "instantiated".                                                //
  //----------------------------------------------------------------//

  /**
   * Get machine code for most recently compiled version of this method.
   * @return machine instructions
   */ 
  public final INSTRUCTION[] getMostRecentlyGeneratedInstructions() {
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    if (VM.VerifyAssertions) VM.assert(isCompiled());
    return mostRecentlyGeneratedInstructions;
  }

  /**
   * Get compiled method for most recently compiled version of this method.
   * @return compiled method
   */ 
  public final VM_CompiledMethod getMostRecentlyGeneratedCompiledMethod() {
    if (VM.VerifyAssertions) VM.assert(isLoaded());
    if (VM.VerifyAssertions) VM.assert(isCompiled());
    return mostRecentlyGeneratedCompiledMethod;
  }

  /**
   * Side-effect: Erases information about most recent compilation.
   */
  final void clearMostRecentCompilation() {
    if (VM.VerifyAssertions) VM.assert(isLoaded());

    VM_CompiledMethods.setCompiledMethodObsolete(mostRecentlyGeneratedCompiledMethod);
    mostRecentlyGeneratedInstructions = null;
    mostRecentlyGeneratedCompiledMethod = null;
  }

  /**
   * Find "catch" block for a bytecode of this method that might be guarded
   * against specified class of exceptions by a "try" block .
   * 
   * @param pc offset of bytecode from start of this method, in bytes
   * @param exceptionType type of exception being thrown - 
   * something like "NullPointerException"
   * @return offset of bytecode for catch block (-1 --> no catch block)
   * 
   * Note: this method is for use by VM_Interpreter. 
   * It is not needed for the core vm.
   */ 
  final int findCatchBlockForBytecode(int pc, VM_Type exceptionType) {
    if (VM.VerifyAssertions) VM.assert(isLoaded());

    if (VM.VerifyAssertions) VM.assert(declaringClass.isInstantiated());
    if (VM.VerifyAssertions) VM.assert(exceptionType.isInstantiated());

    if (exceptionHandlerMap == null)
      return -1;

    int[] startPCs = exceptionHandlerMap.startPCs;
    int[] endPCs   = exceptionHandlerMap.endPCs;

    for (int i = 0, n = startPCs.length; i < n; ++i)
    {
      // note that "pc" points to a return site (not a call site)
      // so the range check here must be "pc <= beg || pc >  end"
      // and not                         "pc <  beg || pc >= end"
      //
      if (pc <= startPCs[i] ||
          pc >  endPCs[i])
        continue;

      if (exceptionHandlerMap.exceptionTypes[i] == null)
      { // catch block handles any exception
        return exceptionHandlerMap.handlerPCs[i];
      }

      try
      {
        VM_Type lhs = exceptionHandlerMap.exceptionTypes[i];
        if ((lhs == exceptionType) ||
            (lhs.isInstantiated() &&
             VM_Runtime.isAssignableWith(lhs, exceptionType)))
        { // catch block handles specified exception
          return exceptionHandlerMap.handlerPCs[i];
        }
      }
      catch (VM_ResolutionException e)
      { // "exceptionTypes[i]" (or one of its superclasses) doesn't exist
        // so it couldn't possibly be a match for "exceptionType"
      }
    }

    return -1;
  }

  /**
   * Change machine code that will be used by future executions of this method 
   * (ie. optimized <-> non-optimized)
   * @param compiledMethod new machine code
   * Side effect: updates jtoc or method dispatch tables 
   * ("type information blocks")
   *              for this class and its subclasses
   */ 
  public final void replaceCompiledMethod(VM_CompiledMethod compiledMethod) throws VM_ResolutionException {
    if (VM.VerifyAssertions) VM.assert(isLoaded());

    VM_CompiledMethods.setCompiledMethod(compiledMethod.getId(), compiledMethod);

    // Grab ahold of version that is being replaced
    VM_CompiledMethod	previouslyGeneratedCompiledMethod =
				this.mostRecentlyGeneratedCompiledMethod;
    this.mostRecentlyGeneratedInstructions = compiledMethod.getInstructions();
    this.mostRecentlyGeneratedCompiledMethod = compiledMethod;


    // Install the new method in jtoc/tib. If virtual, will also replace in
    // all subclasses that inherited the method.
    this.getDeclaringClass().resetMethod(this, true);

    // Now that we've updated the jtoc/tib, old version is now obsolete
    VM_CompiledMethods.setCompiledMethodObsolete( previouslyGeneratedCompiledMethod );

  }

  //----------------//
  // Implementation //
  //----------------//

  // machine instructions for methods that have no bodies
  private static INSTRUCTION[] lazyMethodInvokerInstructions;
  private static INSTRUCTION[] unexpectedAbstractMethodInstructions;
  private static INSTRUCTION[] unexpectedNativeMethodInstructions;
  private static INSTRUCTION[] nativeMethodInvokerInstructions;

  //
  // The following are set during "creation".
  //
  /**
   * type of return value
   */
  private VM_Type                returnType;          
  /**
   * types of parameters (not including "this", if virtual)
   */
  private VM_Type[]              parameterTypes;      
  /**
   * words needed for parameters (not including "this", if virtual)
   */
  private int                    parameterWords;      

  //
  // The following are set during "loading".
  //
  /**
   * words needed for local variables (including parameters)
   */
  private int                    localWords;          
  /**
   * words needed for operand stack (high water mark)
   */
  private int                    operandWords;        
  /**
   * bytecodes for this method
   */
  private byte[]                 bytecodes;           
  /**
   * try/catch/finally blocks for this method (null --> none)
   */
  private VM_ExceptionHandlerMap exceptionHandlerMap; 
  /**
   * exceptions this method might throw (null --> none)
   */
  private VM_Type[]              exceptionTypes;      
  /**
   * pc to source-line info (null --> none)
   */
  private VM_LineNumberMap       lineNumberMap;       
  /**
   * info for use by debugger (null --> none)
   */
  private VM_LocalVariable[]     localVariables;      

  // Byte Code Annotations
  private short[]	annotationPC;
  private byte[]	annotationValue;
  static final byte	annotationNullCheck = 4;
  static final byte	annotationBoundsCheck = 3;	// Both Upper/Lower
  private int		annotationNum;
  private int		annotationPrior;

  //
  // The following is set during "resolution".
  //
  /**
   * jtoc/tib offset for standard method dispatch, in bytes
   */
  int                            offset;              

  //
  // The following are set during "compilation".
  //
  /**
   * machine code most recently generated for this method
   */
  private INSTRUCTION[]          mostRecentlyGeneratedInstructions; 
  /**
   * compiled method associated with those machine instructions
   */
  private VM_CompiledMethod      mostRecentlyGeneratedCompiledMethod; 

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
   * To guarantee uniqueness, only the VM_ClassLoader class may construct 
   * VM_Method instances.
   * All VM_Method creation should be performed by calling 
   * "VM_ClassLoader.findOrCreate" methods.
   */ 
  private VM_Method() {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
  }

  /**
   * Standard initialization.
   */
  VM_Method(VM_Class declaringClass, VM_Atom name, VM_Atom descriptor, 
            int dictionaryId, ClassLoader classloader) {
    super(declaringClass, name, descriptor, dictionaryId);
    returnType = descriptor.parseForReturnType(classloader);
    parameterTypes = descriptor.parseForParameterTypes(classloader);
    for (int i = 0, n = parameterTypes.length; i < n; ++i)
      parameterWords += parameterTypes[i].getStackWords();
    offset = VM_Member.UNINITIALIZED_OFFSET;
  }

  final void load(DataInputStream input, int modifiers) throws IOException {
    this.modifiers = modifiers;
    readAttributes(input);
    this.modifiers |= ACC_LOADED;
  }

  private void readAttributes(DataInputStream input) throws IOException {
    for (int i = 0, n = input.readUnsignedShort(); i < n; ++i)
    {
      VM_Atom attName   = declaringClass.getUtf(input.readUnsignedShort());
      int     attLength = input.readInt();

      // Method attributes
      if (attName == VM_ClassLoader.codeAttributeName)
      {
        operandWords = input.readUnsignedShort();
        localWords   = input.readUnsignedShort();

        bytecodes = new byte[input.readInt()];
        input.readFully(bytecodes);

        //-#if RVM_WITH_OPT_COMPILER
	VM_OptMethodSummary.summarizeMethod(this, bytecodes, 
					    (modifiers & ACC_SYNCHRONIZED) 
					    != 0);
        //-#endif

        int cnt = input.readUnsignedShort();
        if (cnt != 0)
          exceptionHandlerMap = new VM_ExceptionHandlerMap(input, 
                                                           declaringClass, 
							   cnt);

        readAttributes(input);
        continue;
      }

      if (attName == VM_ClassLoader.exceptionsAttributeName)
      {
        int cnt = input.readUnsignedShort();
        if (cnt != 0)
        {
          exceptionTypes = new VM_Type[cnt];
          for (int j = 0, m = exceptionTypes.length; j < m; ++j)
            exceptionTypes[j] = declaringClass.getTypeRef(input.readUnsignedShort());
        }
        continue;
      }

      if (attName == VM_ClassLoader.deprecatedAttributeName)
      { // boring
        input.skipBytes(attLength);
        continue;
      }

      if (attName == VM_ClassLoader.syntheticAttributeName)
      { // boring
        input.skipBytes(attLength);
        continue;
      }

      // Code attributes
      if (attName == VM_ClassLoader.lineNumberTableAttributeName)
      {
        int cnt = input.readUnsignedShort();
        if (cnt != 0)
          lineNumberMap = new VM_LineNumberMap(input, cnt);
        continue;
      }

      if (attName == VM_ClassLoader.localVariableTableAttributeName)
      {
        if (VM.LoadLocalVariableTables)
        { // load extra info for use by debugger
          int cnt = input.readUnsignedShort();
          if (cnt != 0)
          {
            localVariables = new VM_LocalVariable[cnt];
            for (int j = 0, m = localVariables.length; j < m; ++j)
              localVariables[j] = new VM_LocalVariable(declaringClass, input);
          }
        }
        else
          input.skipBytes(attLength);
        continue;
      }

      annotationNum = 0;
      if (attName == VM_ClassLoader.arrayNullCheckAttributeName)
      {
	int attNum = attLength/3;
	annotationPC = new short[ attNum ];
	annotationValue = new byte[ attNum ];
	for ( int attIndex = 0; attIndex < attNum; attIndex++ )
	{
	  short pc	= (short) input.readUnsignedShort();
	  byte  value	= (byte)  input.readUnsignedByte();
	  if ( value != 0 ) {		// exclude non-interesting 0 values
					// seen coming from soot
	    annotationPC[ annotationNum ] = pc;
	    annotationValue[ annotationNum++ ] = value;
	  }
	}
	if ( annotationNum == 0 ) {	// no entries of interest
	  annotationPC = null;		// allow storage to be reclaimed
	  annotationValue = null;
	}
	else {
	  annotationPrior = 0;		// 1st probe
	}
	continue;
      }

      input.skipBytes(attLength);
    }
  }

  static INSTRUCTION[] getLazyMethodInvokerInstructions() {
    if (lazyMethodInvokerInstructions == null) {
      lazyMethodInvokerInstructions = VM_Entrypoints.lazyMethodInvokerMethod.compile();
    }
    return VM_LazyCompilationTrampolineGenerator.getTrampoline();
  }

  private static INSTRUCTION[] getUnexpectedAbstractMethodInstructions() {
    if (unexpectedAbstractMethodInstructions == null) {
      unexpectedAbstractMethodInstructions = 
	VM_Entrypoints.unexpectedAbstractMethodCallMethod.compile();
    }
    return unexpectedAbstractMethodInstructions;
  }

  static INSTRUCTION[] getNativeMethodInvokerInstructions() {
    if (nativeMethodInvokerInstructions == null) {
      nativeMethodInvokerInstructions = VM_Entrypoints.lazyMethodInvokerMethod.compile();
    }
    return nativeMethodInvokerInstructions;
  }


  //////////////////////////////////////////////////////////////
  // TODO: fix the following to work with dummy methods! (IP)
  //////////////////////////////////////////////////////////////

  int getNativeIP() { 
    return nativeIP;
  }

  int getNativeTOC() { 
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
    String methodName = name.toString();
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
        if (lib!=null)
          symbolAddress = lib.getSymbol(nativeProcedureNameWithSigniture);
        if (lib != null && symbolAddress == VM_Address.zero())
          symbolAddress = lib.getSymbol(nativeProcedureName);
	else if (symbolAddress != VM_Address.zero())
	  nativeProcedureName = nativeProcedureNameWithSigniture;
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

  private static INSTRUCTION[] getUnexpectedNativeMethodInstructions() {
    if (unexpectedNativeMethodInstructions == null) {
      unexpectedNativeMethodInstructions = VM_Entrypoints.unimplementedNativeMethodMethod.compile();
    }
    return unexpectedNativeMethodInstructions;
  }
}
