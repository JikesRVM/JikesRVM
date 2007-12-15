/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.tools.oth;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.StringTokenizer;
import java.util.Vector;
import org.jikesrvm.VM;
import org.jikesrvm.VM_Callbacks;
import org.jikesrvm.classloader.VM_Atom;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_ClassLoader;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.baseline.VM_BaselineCompiler;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.opt.CompilationPlan;
import org.jikesrvm.compilers.opt.Compiler;
import org.jikesrvm.compilers.opt.OptimizationPlanner;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.Options;
import org.jikesrvm.runtime.VM_Reflection;
import org.jikesrvm.runtime.VM_Time;

/**
 * A test harness for the optimizing compiler.
 * <p>
 * The role of this class is to allow the optimizing compiler
 * to be run as an "application" to enabling selective testing and
 * debugging of the optimizing compiler.
 * For example, the following command line:
 * <br>
 *   rvm -X:h=100 org.jikesrvm.tools.oth.OptTestHarness -oc:O2 -oc:phases=true
 *      -class hanoi -er hanoi run  -
 * <br>
 * invokes the opt compiler at Opt level 2 and phases=true to compile
 * the class hanoi, it then executes the run method of class hanoi.
 * <p>
 * Any command that can be given to the optimizing compiler via -X:irc:<cmd>
 * can be given to the optimizing compiler by org.jikesrvm.tools.oth.OptTestHarness via -oc:<cmd>.
 * In addition, the org.jikesrvm.tools.oth.OptTestHarness supports the following commands:
 * -useBootOptions           Use the same OptOptions as the bootimage compiler.
 * -longcommandline <filename>    Read commands (one per line) from a file
 * +baseline                      Switch default compiler to baseline
 * -baseline                      Switch default compiler to optimizing
 * -load  <class    >             Load a class
 * -class <class>                 Load a class and compile all its methods
 * -method <class> <method> [-|<descrip>] Compile method with default compiler
 * -methodOpt <class> <method> [-|<descrip>] Compile method with opt compiler
 * -methodBase <class> <method> [-|<descrip>] Compile method with base compiler
 * -er <class> <method> [-|<descrip>] {args} Compile with default compiler and execute a method
 * -performance                   Show performance results
 */
class OptTestHarness {
  static boolean DISABLE_CLASS_LOADING = false;
  static boolean EXECUTE_WITH_REFLECTION = false;
  static boolean EXECUTE_MAIN = false;
  // Default value for for compiling opt/baseline
  static boolean BASELINE = false;

  // Record and show performance of executed methods, if any
  static Performance perf;

  static ClassLoader cl;

  // Keep baseline and opt methods separate in list of methods
  // to be compiled
  static Vector<VM_Method> optMethodVector = null;
  static Vector<Options> optOptionsVector = null;
  static Vector<VM_Method> baselineMethodVector = null;

  static java.lang.reflect.Method reflectoid;
  static Object[] reflectMethodArgs;
  static Vector<Method> reflectoidVector;
  static Vector<VM_Method> reflectMethodVector;
  static Vector<Object[]> reflectMethodArgsVector;

  static VM_Class mainClass;
  static String[] mainArgs;

  static int parseMethodArgs(VM_TypeReference[] argDesc, String[] args, int i, Object[] methodArgs) {
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
          System.err.println("Parsing args of type " + argDesc[argNum] + " not implemented");
        } else if (argDesc[argNum].isArrayType()) {
          VM_TypeReference element = argDesc[argNum].getArrayElementType();
          if (element.equals(VM_TypeReference.JavaLangString)) {
            String[] array = new String[args.length - i - 1];
            for (int j = 0; j < array.length; j++) {
              array[j] = args[++i];
            }
            methodArgs[argNum] = array;
          } else {// TODO
            System.err.println("Parsing args of array of " + element + " not implemented");
          }
        }
      }
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new InternalError("Error: not enough method arguments specified on command line after -er");
    }
    return i;
  }

  // if "methdesc" is "-", find the first method with "methname" in "klass",
  // otherwise, find the method whose signature matches "methdesc"
  static VM_Method findDeclaredOrFirstMethod(VM_Class klass, String methname, String methdesc) {
    if (klass == null) return null;
    VM_Atom methodName = VM_Atom.findOrCreateAsciiAtom(methname);
    VM_Atom methodDesc = methdesc.equals("-") ? null : VM_Atom.findOrCreateAsciiAtom(methdesc);

    for (VM_Method method : klass.getDeclaredMethods()) {
      if (method.getName() == methodName && ((methodDesc == null) || (methodDesc == method.getDescriptor()))) {
        return method;
      }
    }
    if (methodDesc == null) {
      System.err.println("No method named " + methodName + " found in class " + klass);
    } else {
      System.err.println("No method matching " + methodName + " " + methodDesc + " found in class " + klass);
    }
    return null;
  }

  static VM_Class loadClass(String s) throws ClassNotFoundException {
    if (s.startsWith("./")) s = s.substring(2, s.length());
    if (s.endsWith(".java")) s = s.substring(0, s.length() - 5);
    if (s.endsWith(".class")) s = s.substring(0, s.length() - 6);

    // parse the class signature
    if (s.startsWith("L") && s.endsWith(";")) {
      s = s.substring(1, s.length() - 1);
    }

    s = s.replace('.', '/');

    return (VM_Class) java.lang.JikesRVMSupport.getTypeForClass(Class.forName(s, true, cl));
  }

  static void printFormatString() {
    System.err.println("Format: rvm org.jikesrvm.tools.oth.OptTestHarness { <command> }");
  }

  private static void processClass(VM_Class klass, Options opts) {
    VM_Method[] methods = klass.getDeclaredMethods();
    for (VM_Method method : methods) {
      if (!method.isAbstract() && !method.isNative()) {
        processMethod(method, opts);
      }
    }
  }

  // Wrapper applying default decision regarding opt/baseline
  private static void processMethod(VM_Method method, Options opts) {
    processMethod(method, opts, BASELINE);
  }

  private static void processMethod(VM_Method method, Options opts, boolean isBaseline) {
    if (isBaseline) {
      // Method to be baseline compiled
      if (!baselineMethodVector.contains(method)) {
        baselineMethodVector.addElement(method);
      }
    } else if (!optMethodVector.contains(method)) {
      // Method to be opt compiled
      optMethodVector.addElement(method);
      optOptionsVector.addElement(opts);
    }
  }

  // process the command line option
  static Options options = new Options();

  private static void processOptionString(String[] args) {
    for (int i = 0, n = args.length; i < n; i++) {
      try {
        String arg = args[i];
        if (arg.startsWith("-oc:") && options.processAsOption("-X:irc:", arg.substring(4))) {
          // handled in processAsOption
        } else if (arg.equals("-useBootOptions")) {
          Compiler.setBootOptions(options);
        } else if (arg.equals("-longcommandline")) {
          // the -longcommandline option reads options from a file.
          // use for cases when the command line is too long for AIX
          i++;
          BufferedReader in = new BufferedReader(new FileReader(args[i]));
          StringBuilder s = new StringBuilder("");
          while (in.ready()) {
            String line = in.readLine().trim();
            if (!line.startsWith("#")) {
              s.append(line);
              s.append(" ");
            }
          }
          in.close();
          StringTokenizer t = new StringTokenizer(s.toString());
          String[] av = new String[t.countTokens()];
          for (int j = 0; j < av.length; j++) {
            av[j] = t.nextToken();
          }
          processOptionString(av);
        } else if (arg.equals("+baseline")) {
          BASELINE = true;
        } else if (arg.equals("-baseline")) {
          BASELINE = false;
        } else if (arg.equals("-load")) {
          loadClass(args[++i]);
        } else if (arg.equals("-class")) {
          VM_Class klass = loadClass(args[++i]);
          processClass(klass, options);
          options = options.dup();
        } else if (arg.equals("-method") || arg.equals("-methodOpt") || arg.equals("-methodBase")) {
          // Default for this method is determined by BASELINE var
          boolean isBaseline = BASELINE;
          // Unless specified by these options
          if (arg.equals("-methodOpt")) {
            isBaseline = false;
          }
          if (arg.equals("-methodBase")) {
            isBaseline = true;
          }

          VM_Class klass = null;
          try {
            klass = loadClass(args[++i]);
          } catch (Exception e) {
            System.err.println("WARNING: Skipping method from " + args[i - 1]);
          }
          if (klass == null) continue;
          String name = args[++i];
          String desc = args[++i];
          VM_Method method = findDeclaredOrFirstMethod(klass, name, desc);
          if (method == null || method.isAbstract() || method.isNative()) {
            System.err.println("WARNING: Skipping method " + args[i - 2] + "." + name);
          } else {
            processMethod(method, options, isBaseline);
          }
          options = options.dup();
        } else if (arg.equals("-performance")) {
          perf = new Performance();
        } else if (arg.equals("-disableClassLoading")) {
          DISABLE_CLASS_LOADING = true;
        } else if (arg.equals("-er")) {
          EXECUTE_WITH_REFLECTION = true;
          VM_Class klass = loadClass(args[++i]);
          String name = args[++i];
          String desc = args[++i];
          VM_NormalMethod method = (VM_NormalMethod) findDeclaredOrFirstMethod(klass, name, desc);
          VM_CompiledMethod cm = null;
          if (BASELINE) {
            cm = VM_BaselineCompiler.compile(method);
          } else {
            CompilationPlan cp =
                new CompilationPlan(method, OptimizationPlanner.createOptimizationPlan(options), null, options);
            try {
              cm = Compiler.compile(cp);
            } catch (Throwable e) {
              System.err.println("SKIPPING method:" + method + "Due to exception: " + e);
            }
          }
          if (cm != null) method.replaceCompiledMethod(cm);
          VM_TypeReference[] argDesc = method.getDescriptor().parseForParameterTypes(klass.getClassLoader());
          Object[] reflectMethodArgs = new Object[argDesc.length];
          i = parseMethodArgs(argDesc, args, i, reflectMethodArgs);
          java.lang.reflect.Method reflectoid = java.lang.reflect.JikesRVMSupport.createMethod(method);
          reflectoidVector.addElement(reflectoid);
          reflectMethodVector.addElement(method);
          reflectMethodArgsVector.addElement(reflectMethodArgs);
          options = options.dup();
        } else if (arg.equals("-main")) {
          EXECUTE_MAIN = true;
          i++;
          mainClass = loadClass(args[i]);
          i++;
          mainArgs = new String[args.length - i];
          for (int j = 0, z = mainArgs.length; j < z; j++) {
            mainArgs[j] = args[i+j];
          }
          break;
        } else {
          System.err.println("Unrecognized argument: " + arg + " - ignored");
        }
      } catch (ArrayIndexOutOfBoundsException e) {
        System.err.println("Uncaught ArrayIndexOutOfBoundsException, possibly" +
                           " not enough command-line arguments - aborting");
        printFormatString();
        e.printStackTrace(System.err);
        break;
      } catch (Exception e) {
        System.err.println(e);
        e.printStackTrace(System.err);
        break;
      }
    }
  }

  private static void compileMethodsInVector() {
    // Compile all baseline methods first
    int size = baselineMethodVector.size();
    VM.sysWrite("Compiling " + size + " methods baseline\n");
    // Compile all methods in baseline vector
    for (int i = 0; i < size; i++) {
      VM_NormalMethod method = (VM_NormalMethod) baselineMethodVector.elementAt(i);
      VM_CompiledMethod cm = null;
      cm = VM_BaselineCompiler.compile(method);
      method.replaceCompiledMethod(cm);
    }

    // Now compile all methods in opt vector
    size = optMethodVector.size();
    VM.sysWrite("Compiling " + size + " methods opt\n");
    for (int i = 0; i < size; i++) {
      VM_NormalMethod method = (VM_NormalMethod) optMethodVector.elementAt(i);
      Options opts = optOptionsVector.elementAt(i);
      try {
        VM_CompiledMethod cm = null;
        CompilationPlan cp =
            new CompilationPlan(method, OptimizationPlanner.createOptimizationPlan(opts), null, opts);
        cm = Compiler.compile(cp);
        method.replaceCompiledMethod(cm);
      } catch (OptimizingCompilerException e) {
        if (e.isFatal && VM.ErrorsFatal) {
          e.printStackTrace();
          VM.sysFail("Internal vm error: " + e.toString());
        } else {
          System.err.println("SKIPPING opt-compilation of " + method + ":\n  " + e.getMessage());
          if (opts.PRINT_METHOD) {
            e.printStackTrace();
          }
        }
      }
    }
  }

  private static void executeCommand() throws InvocationTargetException, IllegalAccessException {
    compileMethodsInVector();

    if (EXECUTE_WITH_REFLECTION) {

      if (DISABLE_CLASS_LOADING) {
        VM_Class.classLoadingDisabled = true;
      }

      int size = reflectoidVector.size();
      for (int i = 0; i < size; i++) {
        reflectoid = reflectoidVector.elementAt(i);
        reflectMethodArgs = reflectMethodArgsVector.elementAt(i);
        VM_Method method = reflectMethodVector.elementAt(i);
        VM.sysWrite("**** START OF EXECUTION of " + method + " ****.\n");
        Object result = null;
        if (perf != null) perf.reset();
        result = reflectoid.invoke(null, reflectMethodArgs);
        if (perf != null) perf.stop();
        VM.sysWrite("**** END OF EXECUTION of " + method + " ****.\n");
        VM.sysWrite("**** RESULT: " + result + "\n");
      }
      EXECUTE_WITH_REFLECTION = false;
    }

    if (EXECUTE_MAIN) {
      VM_Method mainMethod = mainClass.findMainMethod();
      if (mainMethod == null) {
        // no such method
        System.err.println(mainClass + " doesn't have a \"public static void main(String[])\" method to execute\n");
        return;
      }
      VM.sysWrite("**** START OF EXECUTION of " + mainMethod + " ****.\n");
      VM_Reflection.invoke(mainMethod, null, new Object[]{mainArgs}, false);
      VM.sysWrite("**** END OF EXECUTION of " + mainMethod + " ****.\n");
    }
  }

  public static void main(String[] args) throws InvocationTargetException, IllegalAccessException {
    cl = VM_ClassLoader.getApplicationClassLoader();
    optMethodVector = new Vector<VM_Method>(50);
    optOptionsVector = new Vector<Options>(50);
    baselineMethodVector = new Vector<VM_Method>(50);
    reflectoidVector = new Vector<Method>(10);
    reflectMethodVector = new Vector<VM_Method>(10);
    reflectMethodArgsVector = new Vector<Object[]>(10);
    if (!Compiler.isInitialized()) {
      Compiler.init(options);
    }
    processOptionString(args);
    if (perf != null) {
      VM_Callbacks.addExitMonitor(perf);
    }
    executeCommand();
    if (perf != null) {
      perf.show();
    }
  }

  private static class Performance implements VM_Callbacks.ExitMonitor {
    private long start = 0;
    private long end = 0;

    void reset() { start = VM_Time.nanoTime(); }

    void stop() { if (end == 0) end = VM_Time.nanoTime(); }

    void show() {
      stop();  // In case we got here due to a System.exit
      System.out.println("");
      System.out.println("Performance of executed method");
      System.out.println("------------------------------");
      System.out.print("Elapsed wallclock time: ");
      System.out.print(VM_Time.nanosToMillis(end - start));
      System.out.println(" msec");
    }

    public void notifyExit(int discard) { show(); }
  }
}
