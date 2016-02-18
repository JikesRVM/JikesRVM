/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.tools.oth;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Vector;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMClassLoader;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.baseline.BaselineCompiler;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilationPlan;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanner;
import org.jikesrvm.compilers.opt.driver.OptimizingCompiler;
import org.jikesrvm.runtime.Callbacks;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Reflection;
import org.jikesrvm.runtime.Time;
import org.vmmagic.unboxed.Address;

/**
 * A test harness for the optimizing compiler.
 * <p>
 * The role of this class is to allow the optimizing compiler
 * to be run as an "application" to enabling selective testing and
 * debugging of the optimizing compiler.
 * For example, the following command line:
 * <br>
 * <pre>rvm -X:h=100 org.jikesrvm.tools.oth.OptTestHarness -oc:O2 -oc:phases=true -class hanoi -er hanoi run  -</pre>
 * <br>
 * invokes the opt compiler at Opt level 2 and phases=true to compile
 * the class hanoi, it then executes the run method of class hanoi.
 * <p>
 * Any command that can be given to the optimizing compiler via -X:irc:&lt;cmd&gt;
 * can be given to the optimizing compiler by org.jikesrvm.tools.oth.OptTestHarness via -oc:&lt;cmd&gt;.
 * In addition, the org.jikesrvm.tools.oth.OptTestHarness supports the following commands:
 * <pre>
 * -useBootOptions           Use the same OptOptions as the bootimage compiler.
 * -longcommandline &lt;filename&gt;    Read commands (one per line) from a file
 * +baseline                      Switch default compiler to baseline
 * -baseline                      Switch default compiler to optimizing
 * -load  &lt;class    &gt;             Load a class
 * -class &lt;class&gt;                 Load a class and compile all its methods
 * -method &lt;class&gt; &lt;method&gt; [-|&lt;descrip&gt;] Compile method with default compiler
 * -methodOpt &lt;class&gt; &lt;method&gt; [-|&lt;descrip&gt;] Compile method with opt compiler
 * -methodBase &lt;class&gt; &lt;method&gt; [-|&lt;descrip&gt;] Compile method with base compiler
 * -er &lt;class&gt; &lt;method&gt; [-|&lt;descrip&gt;] {args} Compile with default compiler and execute a method
 * -performance                   Show performance results
 * </pre>
 */
class OptTestHarness {
  boolean disableClassloading = false;
  boolean executeWithReflection = false;
  boolean executeMainMethod = false;
  /** Default value for for compiling opt/baseline */
  boolean useBaselineCompiler = false;

 /**
   * Should we print the address of compiled methods (useful for
   * debugging)
   */
  boolean printCodeAddress = true;

  boolean addCallbackForPerformancePrintout = true;

  /** Record and show performance of executed methods, if any */
  Performance perf;

  ClassLoader cl;

  // Keep baseline and opt methods separate in list of methods
  // to be compiled
  Vector<RVMMethod> optMethodVector = null;
  Vector<OptOptions> optOptionsVector = null;
  Vector<RVMMethod> baselineMethodVector = null;

  java.lang.reflect.Method reflectoid;
  Object[] reflectMethodArgs;
  Vector<Method> reflectoidVector;
  Vector<RVMMethod> reflectMethodVector;
  Vector<Object[]> reflectMethodArgsVector;

  RVMClass mainClass;
  String[] mainArgs;

  private final OptTestHarnessOutput output;
  private final FileAccess fileAccess;

  OptTestHarness(OptTestHarnessOutput output, OptOptions optOptions, FileAccess fileAccess) {
    this.output = output;
    options = optOptions;
    this.fileAccess = fileAccess;
  }

  int parseMethodArgs(TypeReference[] argDesc, String[] args, int i, Object[] methodArgs) {
    try {
      for (int argNum = 0; argNum < argDesc.length; ++argNum) {
        if (argDesc[argNum].isBooleanType()) {
          methodArgs[argNum] = Boolean.valueOf(args[++i]);
        } else if (argDesc[argNum].isByteType()) {
          methodArgs[argNum] = Byte.valueOf(args[++i]);
        } else if (argDesc[argNum].isShortType()) {
          methodArgs[argNum] = Short.valueOf(args[++i]);
        } else if (argDesc[argNum].isIntType()) {
          methodArgs[argNum] = Integer.valueOf(args[++i]);
        } else if (argDesc[argNum].isLongType()) {
          methodArgs[argNum] = Long.valueOf(args[++i]);
        } else if (argDesc[argNum].isFloatType()) {
          methodArgs[argNum] = Float.valueOf(args[++i]);
        } else if (argDesc[argNum].isDoubleType()) {
          methodArgs[argNum] = Double.valueOf(args[++i]);
        } else if (argDesc[argNum].isCharType()) {
          methodArgs[argNum] = args[++i].charAt(0);
        } else if (argDesc[argNum].isClassType()) {
          // TODO
          output.sysErrPrintln("Parsing args of type " + argDesc[argNum] + " not implemented");
        } else if (argDesc[argNum].isArrayType()) {
          TypeReference element = argDesc[argNum].getArrayElementType();
          if (element.equals(TypeReference.JavaLangString)) {
            String[] array = new String[args.length - i - 1];
            for (int j = 0; j < array.length; j++) {
              array[j] = args[++i];
            }
            methodArgs[argNum] = array;
          } else {
            // TODO implement it
            output.sysErrPrintln("Parsing args of array of " + element + " not implemented");
          }
        }
      }
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new InternalError("Error: not enough method arguments specified on command line after -er");
    }
    return i;
  }


  /**
   * Finds a method, either one with a given descriptor or the first matching
   * one in in the given class.
   * @param klass the class to search
   * @param methname the method's name
   * @param methdesc a descriptor of the method's signature if a specific
   *  method is desired or "-" to find the first method with the given name
   * @return the method or {@code null} if no method was found
   */
  RVMMethod findDeclaredOrFirstMethod(RVMClass klass, String methname, String methdesc) {
    if (klass == null) return null;
    Atom methodName = Atom.findOrCreateAsciiAtom(methname);
    Atom methodDesc = "-".equals(methdesc) ? null : Atom.findOrCreateAsciiAtom(methdesc);

    for (RVMMethod method : klass.getDeclaredMethods()) {
      if (method.getName() == methodName && ((methodDesc == null) || (methodDesc == method.getDescriptor()))) {
        return method;
      }
    }
    if (methodDesc == null) {
      output.sysErrPrintln("No method named " + methodName + " found in class " + klass);
    } else {
      output.sysErrPrintln("No method matching " + methodName + " " + methodDesc + " found in class " + klass);
    }
    return null;
  }

  RVMClass loadClass(String s) throws ClassNotFoundException {
    String className = convertToClassName(s);
    Class<?> clazz = Class.forName(className, true, cl);
    return (RVMClass) java.lang.JikesRVMSupport.getTypeForClass(clazz);
  }

  static String convertToClassName(String s) {
    if (s.startsWith("./")) {
      s = s.substring(2, s.length());
    } else if (s.startsWith("L") && s.endsWith(";")) {
      // parse the class signature
      s = s.substring(1, s.length() - 1);
    }

    if (s.endsWith(".java")) {
      s = s.substring(0, s.length() - 5);
    } else if (s.endsWith(".class")) {
      s = s.substring(0, s.length() - 6);
    }

    return s;
  }

  void printFormatString() {
    output.sysErrPrintln("Format: rvm org.jikesrvm.tools.oth.OptTestHarness { <command> }");
  }

  private void processClass(RVMClass klass, OptOptions opts) {
    RVMMethod[] methods = klass.getDeclaredMethods();
    for (RVMMethod method : methods) {
      if (!method.isAbstract() && !method.isNative()) {
        processMethod(method, opts);
      }
    }
  }

  // Wrapper applying default decision regarding opt/baseline
  private void processMethod(RVMMethod method, OptOptions opts) {
    processMethod(method, opts, useBaselineCompiler);
  }

  private void processMethod(RVMMethod method, OptOptions opts, boolean isBaseline) {
    if (isBaseline) {
      // Method to be baseline compiled
      if (!baselineMethodVector.contains(method)) {
        baselineMethodVector.add(method);
      }
    } else if (!optMethodVector.contains(method)) {
      // Method to be opt compiled
      optMethodVector.add(method);
      optOptionsVector.add(opts);
    }
  }

  // process the command line option
  OptOptions options;

  private void processOptionString(String[] args) {
    for (int i = 0, n = args.length; i < n; i++) {
      try {
        String arg = args[i];
        if (arg.startsWith("-oc:") && options.processAsOption("-X:irc:", arg.substring(4))) {
          // handled in processAsOption
        } else if ("-useBootOptions".equals(arg)) {
          OptimizingCompiler.setBootOptions(options);
        } else if ("-longcommandline".equals(arg)) {
          // the -longcommandline option reads options from a file.
          String fileName = args[++i];
          String[] optionString = fileAccess.readOptionStringFromFile(fileName);
          processOptionString(optionString);
        } else if ("+baseline".equals(arg)) {
          useBaselineCompiler = true;
        } else if ("-baseline".equals(arg)) {
          useBaselineCompiler = false;
        } else if ("-load".equals(arg)) {
          loadClass(args[++i]);
        } else if ("-class".equals(arg)) {
          RVMClass klass = loadClass(args[++i]);
          processClass(klass, options);
          duplicateOptions();
        } else if ("-method".equals(arg) || "-methodOpt".equals(arg) || "-methodBase".equals(arg)) {
          // Default for this method is determined by BASELINE var
          boolean isBaseline = useBaselineCompiler;
          // Unless specified by these options
          if ("-methodOpt".equals(arg)) {
            isBaseline = false;
          }
          if ("-methodBase".equals(arg)) {
            isBaseline = true;
          }

          RVMClass klass = null;
          try {
            klass = loadClass(args[++i]);
          } catch (Exception e) {
            output.sysErrPrintln("WARNING: Skipping method from " + args[i]);
          }
          if (klass == null) continue;
          String name = args[++i];
          String desc = args[++i];
          RVMMethod method = findDeclaredOrFirstMethod(klass, name, desc);
          if (method == null || method.isAbstract() || method.isNative()) {
            output.sysErrPrintln("WARNING: Skipping method " + args[i - 2] + "." + name);
          } else {
            processMethod(method, options, isBaseline);
          }
          duplicateOptions();
        } else if ("-performance".equals(arg)) {
          perf = new Performance(output);
        } else if ("-disableClassLoading".equals(arg)) {
          disableClassloading = true;
        } else if ("-er".equals(arg)) {
          executeWithReflection = true;
          RVMClass klass = loadClass(args[++i]);
          String name = args[++i];
          String desc = args[++i];
          NormalMethod method = (NormalMethod) findDeclaredOrFirstMethod(klass, name, desc);
          CompiledMethod cm = null;
          if (method == null) {
            output.sysErrPrintln("Canceling further option processing to prevent assertion failures.");
            return;
          }
          if (useBaselineCompiler) {
            cm = BaselineCompiler.compile(method);
          } else {
            CompilationPlan cp =
                new CompilationPlan(method, OptimizationPlanner.createOptimizationPlan(options), null, options);
            try {
              cm = OptimizingCompiler.compile(cp);
            } catch (Throwable e) {
              output.sysErrPrintln("SKIPPING method:" + method + "Due to exception: " + e);
            }
          }
          if (cm != null) {
            method.replaceCompiledMethod(cm);
            if (printCodeAddress) {
              output.sysOutPrintln(compiledMethodMessage(method));
            }
          }
          TypeReference[] argDesc = method.getDescriptor().parseForParameterTypes(klass.getClassLoader());
          Object[] reflectMethodArgs = new Object[argDesc.length];
          i = parseMethodArgs(argDesc, args, i, reflectMethodArgs);
          java.lang.reflect.Method reflectoid = java.lang.reflect.JikesRVMSupport.createMethod(method);
          reflectoidVector.add(reflectoid);
          reflectMethodVector.add(method);
          reflectMethodArgsVector.add(reflectMethodArgs);
          duplicateOptions();
        } else if ("-main".equals(arg)) {
          executeMainMethod = true;
          i++;
          mainClass = loadClass(args[i]);
          i++;
          mainArgs = new String[args.length - i];
          for (int j = 0, z = mainArgs.length; j < z; j++) {
            mainArgs[j] = args[i + j];
          }
          break;
        } else {
          output.sysErrPrintln("Unrecognized argument: " + arg + " - ignored");
        }
      } catch (ArrayIndexOutOfBoundsException e) {
        output.sysErrPrintln("Uncaught ArrayIndexOutOfBoundsException, possibly" +
                           " not enough command-line arguments - aborting");
        printFormatString();
        e.printStackTrace(output.getSystemErr());
        break;
      } catch (Exception e) {
        output.sysErrPrintln(e.toString());
        e.printStackTrace(output.getSystemErr());
        break;
      }
    }
  }

  private void duplicateOptions() {
    if (VM.BuildForOptCompiler) {
      options = options.dup();
    }
  }

  static String compiledMethodMessage(NormalMethod method) {
    CompiledMethod cm = method.getCurrentCompiledMethod();
    Address addr = Magic.objectAsAddress(cm.getEntryCodeArray());
    return "Method: " + method + " compiled code: " + addrToString(addr);
  }

  private void compileMethodsInVector() {
    // Compile all baseline methods first
    int size = baselineMethodVector.size();
    output.sysOutPrintln("Compiling " + size + " methods baseline");
    // Compile all methods in baseline vector
    for (int i = 0; i < size; i++) {
      NormalMethod method = (NormalMethod) baselineMethodVector.get(i);
      CompiledMethod cm = null;
      cm = BaselineCompiler.compile(method);
      method.replaceCompiledMethod(cm);
      if (printCodeAddress) {
        output.sysOutPrintln(compiledMethodMessage(method));
      }
    }

    // Now compile all methods in opt vector
    size = optMethodVector.size();
    output.sysOutPrintln("Compiling " + size + " methods opt");
    for (int i = 0; i < size; i++) {
      NormalMethod method = (NormalMethod) optMethodVector.get(i);
      OptOptions opts = optOptionsVector.get(i);
      try {
        CompiledMethod cm = null;
        CompilationPlan cp =
            new CompilationPlan(method, OptimizationPlanner.createOptimizationPlan(opts), null, opts);
        cm = OptimizingCompiler.compile(cp);
        method.replaceCompiledMethod(cm);
        if (printCodeAddress) {
          output.sysOutPrintln(compiledMethodMessage(method));
        }
      } catch (OptimizingCompilerException e) {
        if (e.isFatal && VM.ErrorsFatal) {
          e.printStackTrace();
          VM.sysFail("Internal vm error: " + e);
        } else {
          output.sysErrPrintln("SKIPPING opt-compilation of " + method + ":\n  " + e.getMessage());
          if (opts.PRINT_METHOD) {
            e.printStackTrace();
          }
        }
      }
    }
  }

  private void executeCommand() throws InvocationTargetException, IllegalAccessException {
    compileMethodsInVector();

    if (executeWithReflection) {

      if (disableClassloading) {
        RVMClass.setClassLoadingDisabled(true);
      }

      int size = reflectoidVector.size();
      for (int i = 0; i < size; i++) {
        reflectoid = reflectoidVector.get(i);
        reflectMethodArgs = reflectMethodArgsVector.get(i);
        RVMMethod method = reflectMethodVector.get(i);
        output.sysOutPrintln(startOfExecutionString(method));
        Object result = null;
        if (perf != null) perf.reset();
        Object receiver = null;
        if (!method.isStatic()) {
          receiver = attemptToInvokeDefaultConstructor(method);
          if (receiver == null) {
            continue;
          }
        }
        result = reflectoid.invoke(receiver, reflectMethodArgs);
        if (perf != null) perf.stop();
        output.sysOutPrintln(endOfExecutionString(method));
        output.sysOutPrintln(resultString(result));
      }
      executeWithReflection = false;
    }

    if (executeMainMethod) {
      RVMMethod mainMethod = mainClass.findMainMethod();
      if (mainMethod == null) {
        // no such method
        output.sysErrPrintln(mainClass + " doesn't have a \"public static void main(String[])\" method to execute\n");
        return;
      }
      output.sysOutPrintln(startOfExecutionString(mainMethod));
      Reflection.invoke(mainMethod, null, null, new Object[]{mainArgs}, true);
      output.sysOutPrintln(endOfExecutionString(mainMethod));
    }
  }

  private Object attemptToInvokeDefaultConstructor(RVMMethod method) {
    Object receiver = null;
    try {
      receiver = method.getDeclaringClass().getClassForType().newInstance();
    } catch (Exception e) {
      output.sysErrPrintln("Invocation of default constructor failed for method " + method);
      e.printStackTrace(output.getSystemErr());
    }
    return receiver;
  }

  static String resultString(Object result) {
    return "**** RESULT: " + result;
  }

  static String endOfExecutionString(RVMMethod method) {
    return "**** END OF EXECUTION of " + method + " ****.";
  }

  static String startOfExecutionString(RVMMethod method) {
    return "**** START OF EXECUTION of " + method + " ****.";
  }

  public static void main(String[] args) throws InvocationTargetException, IllegalAccessException {
    OptTestHarness oth = new OptTestHarness(new DefaultOutput(), new OptOptions(), new DefaultFileAccess());
    oth.mainMethod(args);
  }

  public void mainMethod(String[] args) throws InvocationTargetException, IllegalAccessException {
    cl = RVMClassLoader.getApplicationClassLoader();
    optMethodVector = new Vector<RVMMethod>(50);
    optOptionsVector = new Vector<OptOptions>(50);
    baselineMethodVector = new Vector<RVMMethod>(50);
    reflectoidVector = new Vector<Method>(10);
    reflectMethodVector = new Vector<RVMMethod>(10);
    reflectMethodArgsVector = new Vector<Object[]>(10);
    if (VM.BuildForOptCompiler && !OptimizingCompiler.isInitialized()) {
      OptimizingCompiler.init(options);
    } else if (!VM.BuildForOptCompiler) {
      useBaselineCompiler = true;
    }
    processOptionString(args);
    if (perf != null && addCallbackForPerformancePrintout) {
      Callbacks.addExitMonitor(perf);
    }
    executeCommand();
    if (perf != null) {
      perf.show();
    }
  }

  private static String addrToString(Address addr) {
    if (VM.BuildFor32Addr) {
      return Integer.toHexString(addr.toInt());
    } else if (VM.BuildFor64Addr) {
      return Long.toHexString(addr.toLong());
    }
    return null;
  }


  private static class Performance implements Callbacks.ExitMonitor {
    private long start = 0;
    private long end = 0;

    private final OptTestHarnessOutput output;

    Performance(OptTestHarnessOutput output) {
      this.output = output;
    }

    void reset() {
      start = Time.nanoTime();
    }

    void stop() {
      if (end == 0)
        end = Time.nanoTime();
    }

    void show() {
      stop();  // In case we got here due to a System.exit
      output.sysOutPrintln("");
      output.sysOutPrintln("Performance of executed method");
      output.sysOutPrintln("------------------------------");
      output.sysOutPrint("Elapsed wallclock time: ");
      output.sysOutPrint(Double.toString(Time.nanosToMillis(end - start)));
      output.sysOutPrintln(" msec");
    }

    @Override
    public void notifyExit(int discard) {
      show();
    }
  }

}
