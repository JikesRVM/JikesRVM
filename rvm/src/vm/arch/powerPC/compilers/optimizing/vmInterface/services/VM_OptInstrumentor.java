/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;
import java.util.Vector;

/**
 * Run-time data structures for basic block/address trace generation.
 *
 * @author Walter Lee
 */
public class VM_OptInstrumentor
{
  static void init()
  {
    InstrumentBB = true;
    InstrumentAddr = true;
    InstrumentCall = true;
    GeneratePgm = true;
    GenerateStaticCallTrace = false;
    
    PgmFileName = "test.pgm";
    BBBuffer = new VM_CircularBuffer("test.bb");
    AddrBuffer = new VM_CircularBuffer("test.addr");
    CDebug = false;
    RDebug = false;
    NotSilent = false;
    ThreadToInstrument = -1;
    SyncMask = 1023;

    BbMin = 0;
    BbMax = -1;
    
    BbNumInstrument = 1000;
    BbPeriod = 25000;

    MethodMin = 0;
    MethodMax = -1;
    
    Exited = false;
    TracePermits = true;
    TraceLevel = 0;
    
    RecompilePrimordial = true;
    RecompileClassMask = 32;
    RecompileMethodMask = 511;
    IgnoreMethodMask = 15;

    DebugPrintMethods = new Vector();
  }
  
  static void runtimeInit(VM_Thread mainThread)
  {
    resetBbStates();
    
    // By default, we instrument the main application thread
    if (ThreadToInstrument == -1)
      ThreadToInstrument = mainThread.getIndex();
    
    initRecompileClasses();
    initRecompileMethods();
    initIgnoreMethodCalls();
    
    //    if (NotSilent)
    dumpState();
    
    // Recompile some common classes and methods so that they are instrumented.
    doRecompile();
  }

  static void exit()
  {
    if (!Exited)
    {
      Exited=true;
      int threadId = VM_Thread.getCurrentThread().getIndex();
      if (NotSilent)
	VM.sysWrite("VM_OptInstrumentor: " + threadId + " Exiting\n");
      BBBuffer.close();
      AddrBuffer.close();
    }
  }

  static void initRecompileClasses()
  {
    // FIXME: Should use Vector, but am paranoid.
    int ctr = 0;
    RecompileClasses = new VM_Class[7];
    
    try {
      RecompileClasses[ctr++] = VM_Class.forName("java.lang.Integer");
      RecompileClasses[ctr++] = VM_Class.forName("java.lang.Object");
      RecompileClasses[ctr++] = VM_Class.forName("java.lang.String");
      RecompileClasses[ctr++] = VM_Class.forName("java.lang.StringBuffer");
      RecompileClasses[ctr++] = VM_Class.forName("java.util.Hashtable");
      RecompileClasses[ctr++] = VM_Class.forName("java.util.Vector");
      RecompileClasses[ctr++] = VM_Class.forName("OPT_StringBuffer");
    }
    catch (VM_ResolutionException e) {
      VM.sysWrite("ResolutionException for initRecompileClasses\n");
      VM.sysExit(1);
    }
  }

  static void initRecompileMethods()
  {
    int ctr = 0;
    RecompileMethods = new VM_Method[18];

    RecompileMethods[ctr++] = (VM_Method)VM.getMember("Ljava/lang/Object;",       "equals",      "(Ljava/lang/Object;)Z");
    RecompileMethods[ctr++] = (VM_Method)VM.getMember("Ljava/lang/Integer;",      "<init>",      "(I)V");
    RecompileMethods[ctr++] = (VM_Method)VM.getMember("Ljava/util/HashMapEntry;", "<init>",      "(Ljava/lang/Object;Ljava/lang/Object;)V");
    RecompileMethods[ctr++] = (VM_Method)VM.getMember("Ljava/util/MapEntry;",     "<init>",      "(Ljava/lang/Object;Ljava/lang/Object;)V");
    RecompileMethods[ctr++] = (VM_Method)VM.getMember("Ljava/util/Hashtable;",    "put",         "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    RecompileMethods[ctr++] = (VM_Method)VM.getMember("Ljava/util/Hashtable;",    "get",         "(Ljava/lang/Object;)Ljava/lang/Object;");
    RecompileMethods[ctr++] = (VM_Method)VM.getMember("Ljava/util/Hashtable;",    "keys",        "()Ljava/util/Enumeration;");
    RecompileMethods[ctr++] = (VM_Method)VM.getMember("Ljava/util/Hashtable;",    "containsKey", "(Ljava/lang/Object;)Z");
    // 8

    {
      // for db
      RecompileMethods[ctr++] = (VM_Method)VM.getMember("Ljava/lang/String;",       "compareTo",   "(Ljava/lang/String;)I");
    }

    {
      // for jack
      RecompileMethods[ctr++] = (VM_Method)VM.getMember("Ljava/lang/String;",       "getChars",    "(II[CI)V");
      RecompileMethods[ctr++] = (VM_Method)VM.getMember("Ljava/lang/String;",       "charAt",      "(I)C");
      RecompileMethods[ctr++] = (VM_Method)VM.getMember("Ljava/lang/System;",       "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V");
      // 12
      
      RecompileMethods[ctr++] = (VM_Method)VM.getMember("Ljava/lang/Object;",       "hashCode", "()I");
      RecompileMethods[ctr++] = (VM_Method)VM.getMember("Ljava/lang/Character;",    "isWhitespace", "(C)Z");
      RecompileMethods[ctr++] = (VM_Method)VM.getMember("Ljava/lang/String;",       "valueOf", "(C)Ljava/lang/String;");
      RecompileMethods[ctr++] = (VM_Method)VM.getMember("Ljava/lang/String;",       "<init>", "(II[C)V");
      RecompileMethods[ctr++] = (VM_Method)VM.getMember("Ljava/lang/Character;",    "getType", "(C)I");
    }
    
    // the following are frequently used but found to be uninstrumentable
    RecompileMethods[ctr++] = (VM_Method)VM.getMember("Ljava/lang/Object;",       "<init>", "()V");
      // The above chokes because it contains a single branch instruction that directly 
      // references the link register.  The instrumentation code would overwrite this 
      // register.
  }
  
  static void initIgnoreMethodCalls()
  {
    int ctr = 0;
    
    IgnoreMethodIds = new int[11];
    IgnoreMethodIds[ctr++] = VM.getMember("LVM_Linker;",    "invokeinterface", "(Ljava/lang/Object;I)[I").getDictionaryId();
    IgnoreMethodIds[ctr++] = VM.getMember("LVM_Allocator;", "allocateScalar",  "(I[Ljava/lang/Object;Z)Ljava/lang/Object;").getDictionaryId();
    IgnoreMethodIds[ctr++] = VM.getMember("LVM_Allocator;", "allocateArray",   "(II[Ljava/lang/Object;)Ljava/lang/Object;").getDictionaryId();
    
    // The following methods should be recompiled instead of ignored,
    // but the instrumentation code chokes on them.  See
    // initRecompileMethods() for reason.  Since they do nothing we
    // simply ignore them.
    IgnoreMethodIds[ctr++] = VM.getMember("Ljava/lang/Object;",       "<init>", "()V").getDictionaryId();
    
    {
      // for jack
      IgnoreMethodIds[ctr++] = VM.getMember("Ljava/lang/System;",       "currentTimeMillis", "()J").getDictionaryId();
      IgnoreMethodIds[ctr++] = VM.getMember("LVM_Runtime;",             "newScalar", "(I)Ljava/lang/Object;").getDictionaryId();

      // HACK for jack.  Obviously you can't ignore these, but want to see how much it helps.
      IgnoreMethodIds[ctr++] = VM.getMember("LVM_Array;",              "arraycopy", "([CI[CII)V").getDictionaryId();
      IgnoreMethodIds[ctr++] = VM.getMember("LVM_Array;",              "arraycopy", "([II[III)V").getDictionaryId();
      IgnoreMethodIds[ctr++] = VM.getMember("LVM_Array;",              "arraycopy", "([JI[JII)V").getDictionaryId();
    }
      
    // The following causes problems when I try to ignore them...
    IgnoreMethodIds[ctr++] = VM.getMember("LVM_Lock;",      "lock",            "(Ljava/lang/Object;)V").getDictionaryId();
    IgnoreMethodIds[ctr++] = VM.getMember("LVM_Lock;",      "unlock",          "(Ljava/lang/Object;)V").getDictionaryId();
  }

  static void dumpState()
  {
    VM.sysWrite("VM_OptInstrumentor state:\n");
    VM.sysWrite(" InstrumentBB=" + InstrumentBB +
		" InstrumentAddr=" + InstrumentAddr +
		" InstrumentCall=" + InstrumentCall + 
		" GenerateStaticCallTrace=" + GenerateStaticCallTrace + '\n' +
		" RecompilePrimordial=" + RecompilePrimordial + 
		" RecompileClassMask=" + RecompileClassMask + 
		" RecompileMethodMask=" + RecompileMethodMask + 
		" IgnoreMethodMask=" + IgnoreMethodMask + '\n' +
		" PgmFileName=" + PgmFileName + 
		" BBBufferFile=" + BBBuffer.getFileName() + 
		" AddrBufferFile=" + AddrBuffer.getFileName() + '\n' +
		" CDebug=" + CDebug +
		" RDebug=" + RDebug +
		" NotSilent=" + NotSilent +
		" ThreadToInstrument=" + ThreadToInstrument + 
		" SyncMask=" + SyncMask + '\n' +
		" BbMin=" + BbMin +
		" BbMax=" + BbMax + 
		" BbNumInstrument=" + BbNumInstrument +
		" BbPeriod=" + BbPeriod + '\n' +
		" MethodMin=" + MethodMin +
		" MethodMax=" + MethodMax + '\n' +
		" BbCtr=" + BbCtr +
		" BbNextInstrument=" + BbNextInstrument +
		" BbNextNoInstrument=" + BbNextNoInstrument +
		" SamplingPermits=" + SamplingPermits + '\n' +
		" TracePermits=" + TracePermits +
		" TraceLevel=" + TraceLevel + '\n' +
		'\n');
    
    if (DebugPrintMethods.size() > 0)
    {
      VM.sysWrite(" DebugPrintMethods:\n");
      for (int i=0; i<DebugPrintMethods.size(); i+=2)
      {
	VM.sysWrite("   " + (String)DebugPrintMethods.get(i));
	VM.sysWrite("   " + (String)DebugPrintMethods.get(i+1) + '\n');
      }
      VM.sysWrite('\n');
    }
  }

  static void printHelp()
  {
    VM.sysWrite("Instrumentation options (-X:rc:inst:<option>=<value>)\n");
    VM.sysWrite("Main Options         Type    Default  Description\n");
    VM.sysWrite("help                 bool    false    List instrumentation options\n");
    VM.sysWrite("do_bb_only           bool    false    Only instrument to output basic block trace.\n");
    VM.sysWrite("bb_range             int,int 0,-1     Set the range of basic blocks to instrumentation. (-1 => infinity)\n");
    VM.sysWrite("bb_ratio             int,int 1k,25k   Set the ratio of basic block to instrument.\n");
    VM.sysWrite("                                      1st number = number of bbs to instrument; \n");
    VM.sysWrite("                                      2nd number = period; \n");
    VM.sysWrite("thread               int     -1       Set the thread to instrument (default: main application encountered).\n");
    VM.sysWrite("fileroot             string  test     Set the root of the output files.\n");
    VM.sysWrite('\n');
    VM.sysWrite("Hacker Options       Type    Default  Description\n");
    VM.sysWrite("recompile            bool    true     Recompile a static list of frequently used classes and methods.\n");
    VM.sysWrite("recompile_cmask      int     32       Mask to determine what classes to recompile.\n");
    VM.sysWrite("recompile_mmask      int     255      Mask to determine what methods to recompile.\n");
    VM.sysWrite("ignore_mmask         int     15       Mask to determine what methods to ignore.\n");
    VM.sysWrite('\n');
    VM.sysWrite("Debugging Options    Type    Default  Description\n");
    VM.sysWrite("do_none              bool    false    Instrument nothing (for debugging).\n");
    VM.sysWrite("method_range         int,int 0,-1     Range of methods to instrument (-1 = infinity); mainly for debugging\n");
    VM.sysWrite("silent               bool    true     Turn off all instrumentation-related print statements \n");
    VM.sysWrite("rdebug               bool    false    Turn on debugging statements in run-time instrumentation procedures.\n");
    VM.sysWrite("cdebug               bool    false    Turn on debugging statements in instrumentation portion of compiler.\n");
    VM.sysWrite("sync_interval        int     1024     Set the number of basic blocks between insertion\n");
    VM.sysWrite("                                      of synchronization values in the bb/addr streams.  must be a\n");
    VM.sysWrite("                                      power of 2.\n");
    VM.sysWrite("gen_static_calltrace bool    false    Generate static call trace for debugging.\n");
    VM.sysWrite("debug_print_method   str,str --       Dump the ir of the specified method before PreRegallocInstrumentation.\n");
    VM.sysWrite("                                      1st str = class; 2nd str = method\n");
  }

  /* See printHelp() for argument list */
  static boolean processCommandLineArg(String name, String value)
  {
    boolean retval = false;
    if (name.equals("inst:help"))
    {
      printHelp();
      retval = true;
    }
    else if (name.equals("inst:do_none"))
    {
      if (value.equals("true"))
      {
	InstrumentBB = false;
	InstrumentAddr = false;
	InstrumentCall = false;
	GeneratePgm = false;
	retval = true;
      }
      else
	retval = true;
    }
    else if (name.equals("inst:do_bb_only"))
    {
      if (value.equals("true"))
      {
	InstrumentBB = true;
	InstrumentAddr = false;
	InstrumentCall = false;
	GeneratePgm = false;
	retval = true;
      }
      else
	retval = true;
    }
    else if (name.equals("inst:gen_static_calltrace"))
    {
      if (value.equals("true"))
      {
	GenerateStaticCallTrace = true;
	retval = true;
      }
      else if (value.equals("false"))
      {
	GenerateStaticCallTrace = false;
	retval = true;
      }
    }
    else if (name.equals("inst:recompile"))
    {
      if (value.equals("true"))
      {
	RecompilePrimordial = true;
	retval = true;
      }
      else if (value.equals("false"))
      {
	RecompilePrimordial = false;
	retval = true;
      }
    }
    else if (name.equals("inst:recompile_cmask"))
    {
      RecompileClassMask = Integer.parseInt(value);
      retval = true;
    }
    else if (name.equals("inst:recompile_mmask"))
    {
      RecompileMethodMask = Integer.parseInt(value);
      retval = true;
    }
    else if (name.equals("inst:ignore_mmask"))
    {
      IgnoreMethodMask = Integer.parseInt(value);
      retval = true;
    }
    else if (name.equals("inst:bb_range"))
    {
      int split = value.indexOf(',');
      if (split == -1)
	retval = false;
      else
      {
	BbMin = Integer.parseInt(value.substring(0,split));
	BbMax = Integer.parseInt(value.substring(split+1));
	//	resetBbStates();
	retval = true;
      }
    }
    else if (name.equals("inst:bb_ratio"))
    {
      int split = value.indexOf(',');
      if (split == -1)
	retval = false;
      else
      {
	BbNumInstrument = Integer.parseInt(value.substring(0,split));
	BbPeriod = Integer.parseInt(value.substring(split+1));
	//	resetBbStates();
	retval = true;
      }
    }
    else if (name.equals("inst:method_range"))
    {
      int split = value.indexOf(',');
      if (split == -1)
	retval = false;
      else
      {
	MethodMin = Integer.parseInt(value.substring(0,split));
	MethodMax = Integer.parseInt(value.substring(split+1));
	retval = true;
      }
    }
    else if (name.equals("inst:silent"))
    {
      if (value.equals("true"))
      {
	NotSilent = false;
	retval = true;
      }
      else if (value.equals("false"))
      {
	NotSilent = true;
	retval = true;
      }
    }
    else if (name.equals("inst:rdebug"))
    {
      if (value.equals("true"))
      {
	RDebug = true;
	retval = true;
      }
      else if (value.equals("false"))
      {
	RDebug = false;
	retval = true;
      }
    }
    else if (name.equals("inst:cdebug"))
    {
      if (value.equals("true"))
      {
	CDebug = true;
	retval = true;
      }
      else if (value.equals("false"))
      {
	CDebug = false;
	retval = true;
      }
    }
    else if (name.equals("inst:thread"))
    {
      ThreadToInstrument = Integer.parseInt(value);
      retval = true;
    }
    else if (name.equals("inst:fileroot"))
    {
      PgmFileName = value + ".pgm";
      BBBuffer.setFileName(value + ".bb");
      AddrBuffer.setFileName(value + ".addr");
      retval = true;
    }
    else if (name.equals("inst:sync_interval"))
    {
      SyncMask = Integer.parseInt(value) - 1;
      retval = true;
    }
    else if (name.equals("inst:debug_print_method"))
    {
      int split = value.indexOf(',');
      if (split == -1)
	retval = false;
      else
      {
	String the_class = value.substring(0,split);
	String the_method = value.substring(split+1);
	DebugPrintMethods.add(the_class);
	DebugPrintMethods.add(the_method);
	retval = true;
      }
    }

    return retval;
  }
  
  static void doRecompile()
  {
    if (NotSilent)
      VM.sysWrite("Begin recompilation\n");
    if (RecompilePrimordial)
    {
      VM_Class[] classes = RecompileClasses;
      for (int i=0; i<classes.length; i++)
      {
	if ((RecompileClassMask & (1 << i)) != 0)
	{
	  VM_Class cls = classes[i];
	  VM_Method[] ms = cls.getDeclaredMethods();
	  for (int j=0; j<ms.length; j++)
	  {
	    VM_Method m = ms[j];
	    if (m.isCompiled())
	    {
	      if (NotSilent)
		VM.sysWrite("Recompiling " + cls.getName() + " . " + m.getName() + '\n');
	      VM_RuntimeOptCompilerInfrastructure.recompileWithOpt(m);
	    }
	  } 
	}
      }
      
      VM_Method[] methods = RecompileMethods;
      for (int i=0; i<methods.length; i++)
      {
	if ((RecompileMethodMask & ( 1 << i)) != 0)
	{
	  VM_Method m = methods[i];
	  if (m.isCompiled())
	  {
	    if (NotSilent)
	      VM.sysWrite("Recompiling " + m.getDeclaringClass().getName() + " . " + m.getName() + '\n');
	    VM_RuntimeOptCompilerInfrastructure.recompileWithOpt(m);
	  }
	}
      }
    }
    if (NotSilent)
      VM.sysWrite("End recompilation\n");
  }
  
  /*****************************************************************
   * Public routines
   *****************************************************************/

  final static public String getPgmFileName() { return PgmFileName; }
  
  final static public boolean skipMethodCall(OPT_Instruction inst)
  {
    if (MIR_Call.conforms(inst))
    {
      if (MIR_Call.hasMethod(inst))
      {
	int id = MIR_Call.getMethod(inst).method.getDictionaryId();
	int[] ignoreIds = IgnoreMethodIds;
	for (int i=0; i<ignoreIds.length; i++)
	{
	  if (((IgnoreMethodMask & (1 << i)) != 0) && id == ignoreIds[i])
	    return true;
	}
      }
    }
    return false;
  }

  /*****************************************************************
   * Public trace routines
   *****************************************************************/

  final static public void incrementTraceLevel()
  {
    VM_Thread thread = VM_Thread.getCurrentThread();
    if (!ShouldInstrument(thread)) return;
    
    TraceLevel++;
    TracePermits = false;

    {
      // Print debugging statements
      int threadId = thread.getIndex();
      //p//      debug("Thread " + threadId + " increment TraceLevel =" + TraceLevel + '\n');
    }
  }

  final static public void decrementTraceLevel()
  {
    VM_Thread thread = VM_Thread.getCurrentThread();
    if (!ShouldInstrument(thread)) return;
    
    TraceLevel--;
    if (TraceLevel==0)
      TracePermits = true;
    else if (TraceLevel<0)
    {
      //p//      VM.sysWrite("Error: TraceLevel=" + TraceLevel + " is negative!!\n");
      VM.sysExit(1);
    }
    
    {
      // Print debugging statements
      int threadId = thread.getIndex();
      //p//      debug("Thread " + threadId + " decrement TraceLevel =" + TraceLevel + '\n');
    }
  }

  // set instrumentationExitId of current thread to exit.
  final static public void setExit(int exit)
  {
    if (!TracePermits) return;
    if (!SamplingPermits) return;
    
    VM_Thread thread = VM_Thread.getCurrentThread();
    if (!ShouldInstrument(thread)) return;
    
    thread.instrumentationExitId = exit;
    
    {
      // Print debugging statements
      int threadId = thread.getIndex();
      //p//      debug("Thread " + threadId + " Setting exit = " + Integer.toHexString(exit) + '\n');
    }
  }

  // 1. update instrumentation condition.
  // 2. write out instrumentationExitId of current thread to BBBuffer.
  // 3. write sync value to bb/addr streams.
  final static public void writeExit()
  {
    if (!TracePermits) return;
    
    VM_Thread thread = VM_Thread.getCurrentThread();
    if (!ShouldInstrument(thread)) return;

    // 1. Update instrumentation condition.
    UpdateInstrumentationCondition();
    
    if (!SamplingPermits) return;
    
    // 2. write out instrumentationExitId of current thread to BBBuffer.
    int threadId = thread.getIndex();
    int exitId = thread.instrumentationExitId;

    int packId = (threadId << Thread_Id_Shift) + exitId;
    BBBuffer.writeInt(packId);

    // 3. write sync value to bb/addr streams.
    if (((BbCtr-1) & SyncMask) == 0)
    {
      BBBuffer.writeInt(VAL_SYNC);
      AddrBuffer.writeInt(VAL_SYNC);
    }

    {
      // Print debugging statements
      //p//      debug("Thread " + threadId + " Ctr " + (BbCtr-1) + " Writing exit = " + Integer.toHexString(exitId) + "; " + Integer.toHexString(packId) + '\n');

      if (NotSilent && (((BbCtr-1) & SyncMask) == 0))
      {
	//p//	VM.sysWrite("Sync: Thread " + threadId + " Ctr " + (BbCtr-1) + " Writing exit = " + Integer.toHexString(exitId) + "; " + Integer.toHexString(packId) + '\n');
      }
    }
  }
  
  // Write a+b to AddrBuffer.
  final static public void writeAddr(int a, int b)
  {
    if (!TracePermits) return;
    if (!SamplingPermits) return;
    
    VM_Thread thread = VM_Thread.getCurrentThread();
    if (!ShouldInstrument(thread)) return;

    int threadId = thread.getIndex();
    int addr = a+b;
    AddrBuffer.writeInt(addr);

    {
      // Print debugging statements
      //p//      debug("Thread " + threadId + 
      //p//	    " Writing " + Integer.toHexString(a) + "+" + Integer.toHexString(b) + " = " + 
      //p//	    Integer.toHexString(addr) + '\n');
    }
  }
  
  // Write id to AddrBuffer.
  final static public void writeId(int id)
  {
    if (!TracePermits) return;
    if (!SamplingPermits) return;
    
    VM_Thread thread = VM_Thread.getCurrentThread();
    if (!ShouldInstrument(thread)) return;

    int threadId = thread.getIndex();
    AddrBuffer.writeInt(id);

    {
      // Print debugging statements
      //p//      debug("Thread " + threadId + " Writing MethodId = " + Integer.toHexString(id) + '\n');
    }
  }
  
  // Write gc begin marker to bb stream
  // Note that this is written even if (!TracePermits).  This marker
  // is needed to indicate whether the dynamic dependence graph needs
  // to be broken when TracePermits becomes true.
  final static public void writeGcBegin()
  {
    if (!SamplingPermits) return;
    
    BBBuffer.writeInt(VAL_GC_BEGIN);
    
    {
      // Print debugging statements
      //p//      debug("GC Begin\n");
    }
  }

  // Write gc end marker to bb stream
  final static public void writeGcEnd()
  {
    if (!SamplingPermits) return;
    
    BBBuffer.writeInt(VAL_GC_END);
    
    {
      // Print debugging statements
      //p//      debug("GC End\n");
    }
  }

  /*****************************************************************
   * Private trace helpers
   *****************************************************************/
  
  final static private void UpdateInstrumentationCondition()
  {
    if (Exited)
    {
      SamplingPermits = false;
      return;
    }
    
    if (BbCtr == BbNextInstrument)
    {
      //p//      if (NotSilent)
      //p//	VM.sysWrite("Instrumentation start at Bb " + BbCtr + '\n');
      SamplingPermits = true;
      updateNextInstrument();
    }
    
    if (BbCtr == BbNextNoInstrument)
    {
      //p//      if (NotSilent)
      //p//	VM.sysWrite("Instrumentation stop at Bb " + BbCtr + '\n');

      if (SamplingPermits)
      {
	// Write gc marker to indicate termination of trace.  FIXME: if
	// we need to distinguish this event from gc, we'd need a new
	// marker.
	writeGcBegin();
	writeGcEnd();
	
	SamplingPermits = false;
	updateNextNoInstrument();

	BBBuffer.flushAndReset();
	AddrBuffer.flushAndReset();
      }
    }
    
    if (BbCtr == BbMax)
    {
      BBBuffer.close();
      AddrBuffer.close();
    }
    
    BbCtr++;
  }

  // First time it sees a user thread, sets ThreadToInstrument to its
  // id and returns true.  Subsequently, returns true if thread id
  // matches ThreadToInstrument.
  final static private boolean ShouldInstrument(VM_Thread thread)
  {
    return ThreadToInstrument == thread.getIndex();
  }
  
  final static private void debug(String s)
  {
    if (RDebug)
      VM.sysWrite(s);
  }
  
  /*****************************************************************
   * Private bb state methods
   *****************************************************************/
  
  final static private int min(int a,int b)
  {
    return (a<b) ? a : b;
  }
  
  final static private int max(int a,int b)
  {
    return (a>b) ? a : b;
  }

  final static private boolean greaterThan(int a, int b)
  {
    if (b == -1)
      return false;
    else if (a == -1)
      return true;
    else
      return a>b;
  }
  
  static private void resetBbStates()
  {
    BbCtr = 0;
    
    if (BbNumInstrument == BbPeriod)
    {
      BbNextInstrument = BbMin;
      BbNextNoInstrument = BbMax;
    }
    else
    {
      BbNextInstrument = BbMin;
      BbNextNoInstrument = BbMin + BbNumInstrument;
    }
    
    if (greaterThan(BbNextNoInstrument, BbMax))
      BbNextNoInstrument = BbMax;
    SamplingPermits = false;
  }
  
  final static private void updateNextInstrument()
  {
    BbNextInstrument = BbNextInstrument + BbPeriod;
    if (greaterThan(BbNextInstrument, BbMax) ||
	BbNumInstrument == BbPeriod)
      BbNextInstrument = -1;
  }

  final static private void updateNextNoInstrument()
  {
    BbNextNoInstrument = BbNextNoInstrument + BbPeriod;
    if (greaterThan(BbNextNoInstrument, BbMax))
    {
      BbNextInstrument = -1;
      BbNextNoInstrument = BbMax;
    }
  }

  /*****************************************************************
   * Private fields
   *****************************************************************/
  
  static boolean InstrumentBB;
  static boolean InstrumentAddr;
  static boolean InstrumentCall;
  static boolean GeneratePgm;
  static boolean GenerateStaticCallTrace;
  static boolean RecompilePrimordial;
  static int RecompileClassMask;
  static int RecompileMethodMask;
  static int IgnoreMethodMask;
  
  static String PgmFileName;
  static VM_CircularBuffer BBBuffer;
  static VM_CircularBuffer AddrBuffer;
  static boolean CDebug;			// Compiler debug
  static boolean RDebug;			// Run-time debug
  static boolean NotSilent;
  static int ThreadToInstrument;
  static int SyncMask;

  static int BbMin;			/* Range of counters to instrument */
  static int BbMax;
  static int BbNumInstrument;		/* Defines the periodic behavior when counters are in range */
  static int BbPeriod;
  
  static int BbCtr;			/* A counter to artificially limit the
					 * size of the output trace
					 */
  static int BbNextInstrument;
  static int BbNextNoInstrument;
  static boolean SamplingPermits;
  
  static int TraceLevel;
  static boolean TracePermits;
  static boolean Exited;

  static int MethodMin;
  static int MethodMax;

  static Vector DebugPrintMethods;
  static VM_Class[] RecompileClasses;
  static VM_Method[] RecompileMethods;
  
  static int[] IgnoreMethodIds;
  
  /*
   * FIXME: these constants must be consistent with dddg_builder.h
   */
  
  // special bb_info values
  static final int VAL_GC_BEGIN = 0xffff0000;
  static final int VAL_GC_END = 0xffff0001;
  static final int VAL_SYNC = 0xffff0002;

  // encoding for BASIC_BLOCKs
  static final int Thread_Id_Mask  = 0x7;
  static final int Thread_Id_Shift = 29;
  static final int Exit_Id_Mask    = 0x1fffffff;
  static final int Exit_Id_Shift   = 0;
}

