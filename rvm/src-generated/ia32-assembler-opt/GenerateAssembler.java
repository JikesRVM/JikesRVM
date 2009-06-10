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
import java.io.*;
import java.util.*;
import java.lang.reflect.*;

/**
 * <P>
 * Generates the assembler that is used by the optimizing compiler, using a
 * combination of the tables describing the low-level instruction formats and
 * operators used by the opt compiler, and the interface of the low-level
 * assembler that understands how to generate IA32 opcodes given specific
 * operands. Essentially, the opt assembler becomes a rather large piece of
 * impedence-matching code that decodes the Instructions and Operators
 * understood by the opt compiler to determine what is the appropriate IA32
 * machine code to emit.
 * </P>
 *
 * <P>
 * In order for this to work, both the optimizing compiler tables and the
 * Assembler must use stylized formats. On the optimizing com[piler side, the
 * major stylization is that the low-level operators that represent assembly
 * code must correspond directly to the official IA32 assembler pneumonics; i.e.
 * since there is an ADD assembler pneumonic in the Intel assembly
 * specification, there must be a correponding IA32_ADD operator in the opt
 * compiler tables. The stylization of the Assembler side is more
 * thoroughgoing, and the reader is referred to the Assembler header comments
 * for a definition.
 * </P>
 *
 * <P>
 * Given these stylizations, GenerateAssembler reads the set of assembler
 * pneumonics supported by the Assembler using reflection to examinme its
 * stylized method signitures. GenerateAssembler also reads the set of IA32
 * operators that the opt compiler defines, using the helper classes
 * InstructionFormatTable and OperatorFormatTable. It then, for each
 * operator, generates a handler method to call the appropriate Assembler
 * emit method given an Instruction. The Assembler will have a family of
 * emit methods named for each opcode, each such emit method takes a specific
 * set of operand addressing modes and sizes. The handler methods that the
 * GenerateAssembler emits examine the operands to an Instruction, and
 * determine which Assembler method to call for the operand addressing modes
 * and sizes that it finds. GenerateAssembler also generates a top-level
 * dispatch method that examines the operator and calls the appropriate handler.
 * </P>
 *
 * <P>
 * GenerateAssembler generates the opt assembler as part of the normal build
 * process; this poses a slight problem in that it needs to examine the
 * Assembler via reflection to generate the Assembler, but that is not
 * possible until the VM sources (including, of course, the Assembler) have
 * been compiled. The current hack to get around this is to compile the
 * Assembler in advance, and read the resulting class file. This utilizies
 * some supporting files to make the Assembler compile in isolation. This is
 * the purpose of the .fake files in the optimizing compiler's assembler
 * directory.
 * </P>
 *
 * <P>
 * Since this is a freestanding program, use the regular Java exit code
 * conventions.
 * </P>
 *
 * @see InstructionFormatTables
 * @see OperatorFormatTables
 * @see org.jikesrvm.compilers.opt.mir2mc.AssemblerBase
 * @see org.jikesrvm.compilers.opt.ir.Instruction
 * @see org.jikesrvm.compilers.opt.Assembler
 * @see Assembler
 */
public class GenerateAssembler {

  /** Global flag controlling printing of debugging information */
  static final boolean DEBUG = false;

  /** Global reference to the assembler being generated */
  static FileWriter out;

  /**
   * Write a single string to the assembler source file.
   *
   * @param String s The string to be written
   */
  private static void emit(String s) {
    try {
      out.write(s, 0, s.length());
    } catch (IOException e) {
      throw new Error(e);
    }
  }

  /**
   * Write tabification to the assembler source file. This is used to make the
   * generates source more readable by identing it.
   *
   * @param int level The level of indentation to generate
   */
  private static void emitTab(int level) {
    for (int i = 0; i < level; i++)
      emit("  ");
  }

  /**
   * Global reference to the InstructionFormatTables class that contains
   * descriptions of each optimizing compiler instruction format that sis
   * visible to the assembler (i.e. the MIR_* instruction formats.
   *
   * @see InstructionFormatTables
   */
  private static Class<InstructionFormatTables> formats = InstructionFormatTables.class;

  /**
   * Global reference to the opcode argument table for the current opcode being
   * processed. This table is null unless some of the operands in the
   * Instruction are to ignored when generating code for the opcode.
   * Ignoring arguments is an ad-hock special case that is controlled by the
   * global opcodeArgTable.
   */
  static int[] currentOpcodeArgTable;

  /**
   * Global reference to the table of symbolic names of the arguments to the
   * current MIR_ instruction format. This information is read from the
   * InstructionFormatTables
   */
  static String[] currentOpcodeSymbolicNames;

  /**
   * The current IA32 opcode being processed. This is the name of IA32
   * instruction. Typically, it is the name of the opt compiler IA32_* opcode as
   * well, but there are exceptions in that multiple IA32_* opcodes can map to
   * the same IA32 instruction
   */
  static String currentOpcode;

  /**
   * The instruction format for the IA32_* opt compiler opcode(s) being
   * processed.
   */
  static String currentFormat;

  /**
   * Global table mapping opt compiler IA32_* opcodes to arrays listing the set
   * of Instruction operands that are to be used as arguments to the IA32
   * architecture instruction. This is used when an instruction has extra
   * operands that are not used in assembly. The array is indexed by the desired
   * argument for the instruction/Assembler method, the value in the array
   * states which operand of the intruction contains the operand for the
   * instruction. For example, an array of "new int{2}" means the instruction
   * has 1 operand and it is read from the 2 operand of the instruction.
   */
  static final Hashtable<String, int[]> opcodeArgTables;

  /**
   * Initialize the opcodeArgTables table
   */
  static {
    opcodeArgTables = new Hashtable<String, int[]>();
    opcodeArgTables.put("CALL", new int[] { 2 });
    opcodeArgTables.put("INT", new int[] { 1 });
    opcodeArgTables.put("CDQ", new int[] { 0 });
    opcodeArgTables.put("CDO", new int[] { 0 });
    opcodeArgTables.put("CDQE", new int[] { 0 });
    opcodeArgTables.put("DIV", new int[] { 1, 2 });
    opcodeArgTables.put("IDIV", new int[] { 1, 2 });
    opcodeArgTables.put("MUL", new int[] { 1, 2 });
    opcodeArgTables.put("IMUL1", new int[] { 1, 2 });
    opcodeArgTables.put("DIV", new int[] { 1, 2 });
    opcodeArgTables.put("IDIV", new int[] { 1, 2 });
    opcodeArgTables.put("SET", new int[] { 1, 0 });
    opcodeArgTables.put("CMPXCHG", new int[] { 1, 2 });
    opcodeArgTables.put("CMPXCHG8B", new int[] { 2 });
    opcodeArgTables.put("FCMOV", new int[] { 2, 0, 1 });
    opcodeArgTables.put("CMOV", new int[] { 2, 0, 1 });
  }

  /**
   * Set the current opcode. This sets four global fields: the currentOpcode,
   * the currentOpcodeArgTable, the currentFormat and the
   * currentOpcodeSymbolicNames.
   *
   * @param opcode The IA32 architecture opcode to make the current opcode
   */
  static void setCurrentOpcode(String opcode) {
    try {
      currentOpcode = opcode;
      currentOpcodeArgTable = (int[]) opcodeArgTables.get(opcode);
      currentFormat = OperatorFormatTables.getFormat(opcode);
      Field f = formats.getDeclaredField(currentFormat + "ParameterNames");
      currentOpcodeSymbolicNames = (String[]) f.get(null);
    } catch (Throwable e) {
      throw new Error("Cannot handle Assembler opcode " + opcode, e);
    }
  }

  enum ArgumentType {
    /**
     * Constant representing immediate arguments to Assembler calls
     */
    Immediate("Imm"),
    /**
     * Constant representing register arguments to Assembler calls. This
     * covers the cases when a general purpose register is encoded into the
     * mod/rm byte; the Assembler handles the detais of generating either the
     * reg bits of the mod/rm byte or encoding a register as mod 11.
     */
    GPRegister("Reg", "GPR_Reg"), FPRegister("Reg", "FPR_Reg"), MMRegister(
        "Reg", "MM_Reg"), XMMRegister("Reg", "XMM_Reg"),
    /**
     * Constant representing condition arguments to Assembler calls. Such
     * operands are not arguments to the ultimate IA32 machine code instruction,
     * but they are used to calculate the opcode that is generated.
     */
    Condition("Cond"),
    /**
     * Constant representing arguments to Assembler calls that use the
     * scaled-index-base (SIB) addressing mode in the special way that uses
     * neither a base not an index to generate an absolute address
     */
    Absolute("Abs"),
    /**
     * Constant representing IA32 memory operands that use register-
     * displacement addressing mode (usually mod bits 01 and 10) arguments to
     * Assembler calls. The Assembler takes care of choosing the right
     * mode for the size of the displacement, so this one mode covers two of the
     * four addressing modes the IA32 has. The Assembler also handles the
     * special cases in which this mode requires weird SIB bytes.
     */
    RegisterDisplacement(2, "RegDisp"),
    /**
     * Constant representing arguments to Assembler calls that use the
     * scaled-index-base (SIB) addressing mode in the special way that does not
     * use a base register. The Assembler simply assumes it has an [index < <
     * scale + disp] addressing mode, and the Assembler takes care of
     * generating the special mod/rm that causes the base register to be
     * ignored.
     */
    RegisterOffset(3, "RegOff"),
    /**
     * Constant representing scaled-index-base (SIB) mode arguments to
     * Assembler calls.
     */
    RegisterIndexed(4, "RegIdx"),
    /**
     * Constant representing register-indirect arguments to Assembler calls.
     * This mode handles what is (usually) mod 00 in the mod/rm byte.
     */
    RegisterIndirect("RegInd"),
    /**
     * Constant representing labels used as branch targets. While code is being
     * generated, the machine code offset for a forward branch cannot, in
     * general, be computed as the target code has not been generated yet. The
     * Assembler uses synthetic code offsets, based upon the order of
     * Instructions in the code being compiled, to communicate forward
     * branch targets to the Assembler. These synthetic offsets are passed to
     * the Assembler where it expected Label arguments.
     */
    Label("Label"),
    /**
     * Constant representing arguments to Assembler calls in which it may be
     * either a backward branch target (resolved to an immediate being the exact
     * branch displacement) or a forward branch (which will be a synthetic
     * Label).
     */
    LabelOrImmediate("ImmOrLabel"),

    /**
     * Constant representing instructions that operate upon bytes
     */
    Byte("Byte", 1),
    /**
     * Constant representing instructions that operate upon words (16 bits)
     */
    Word("Word", 2),
    /**
     * Constant representing instructions that operate upon quad words (64 bits)
     */
    Quad("Quad", 8);

    private final int size;
    private final int parameters;
    private final String assemblerName;
    private final String optName;

    /**
     * Regular constructor - not a size argument, 1 parameter passed to the
     * assembler, opt and assembler names match
     *
     * @param name string used as part of assembler emit method name or for is
     *            and get methods in AssemblerBase
     */
    ArgumentType(String name) {
      this.assemblerName = name;
      this.optName = name;
      this.size = -1;
      this.parameters = 1;
    }

    /**
     * As with regular constructor except assembler and opt names don't match to
     * support typing of assembler registers
     *
     * @param asmName string used as part of assembler emit method name
     * @param optName string used for is and get methods in AssemblerBase
     */
    ArgumentType(String asmName, String optName) {
      this.assemblerName = asmName;
      this.optName = optName;
      this.size = -1;
      this.parameters = 1;
    }

    /**
     * As with regular constructor except more parameters are consumed by this
     * argument
     *
     * @param parameters number of parameters consumed by this kind of argument
     * @param name string used as part of assembler emit method name or for is
     *            and get methods in AssemblerBase
     */
    ArgumentType(int parameters, String name) {
      this.assemblerName = name;
      this.optName = name;
      this.size = -1;
      this.parameters = parameters;
    }

    /**
     * Size argument constructor - size as given, 0 parameters passed to the
     * assembler, opt and assembler names match
     *
     * @param name string used as part of assembler emit method name or for is
     *            and get methods in AssemblerBase
     * @param size number of bytes encoded by this size
     */
    ArgumentType(String name, int size) {
      this.assemblerName = name;
      this.optName = name;
      this.size = size;
      this.parameters = 0;
    }

    /**
     * @return does this argument type encode a size information for the
     *         instruction
     */
    boolean isSize() {
      return size != -1;
    }

    /**
     * @return size encoded by argument or -1
     */
    int getSize() {
      return size;
    }

    /** @return number of parameters used for this argument type */
    int getParameters() {
      return parameters;
    }

    /** @return the name used for accessing the opt operand for this type */
    String getOptName() {
      return optName;
    }

    /** @return the name used for accessing the opt operand for this type */
    String getAssemblerName() {
      return assemblerName;
    }
  }

  /**
   * For a given string representing a valid operand encoding for the
   * Assembler, return the corresponding Assembler constant. This
   * function only looks for encodings of operand types, and will not accept
   * strings that correspond to size encodings.
   *
   * @param str A valid Assembler encoding of operand type
   * @return The Assembler constant corresponding to str, or -1 if none
   */
  private static ArgumentType getEncoding(String str, Class<?> type) {
    if (str.equals("Reg")) {
      if (type == null) {
        throw new Error("Unable to encode Reg with no type information");
      }
      String typeName = type.getName();
      str = typeName.substring(typeName.lastIndexOf('$') + 1) + "_Reg";
    }
    for (ArgumentType arg : ArgumentType.values()) {
      if (arg.getOptName().equals(str)) {
        return arg;
      }
    }
    throw new Error("Unable to encode the argument " + str + " of type " + type +
        " as a valid argument type");
  }

  /**
   * For a given operand number, return a string which is a valid Java
   * expression for reading that operand out of the current instruction. This
   * function uses the currentOpcodeSymbolicNames table to determine the
   * appropriate accessor (e.g. getValue if the current name is Value), and it
   * uses the currentOpcodeArgTable (in cases where it has an entry for the kind
   * of instruction being processed) to determine which operand in
   * Instruction corresponds to operand sought.
   *
   * @param op The operand number sought.
   * @return A Java expression for accessing the requested operand.
   */
  private static String getOperand(int op) {
    try {
      if (currentOpcodeArgTable == null)
        return currentFormat + ".get" + currentOpcodeSymbolicNames[op] + "(inst)";
      else
        return currentFormat + ".get" +
            currentOpcodeSymbolicNames[currentOpcodeArgTable[op]] + "(inst)";
    } catch (ArrayIndexOutOfBoundsException e) {
      String error = currentOpcode + ": cannot access operand " + op + ":";
      for (int i = 0; i < currentOpcodeSymbolicNames.length; i++)
        error += currentOpcodeSymbolicNames[i];
      throw new Error(error, e);
    }
  }

  /**
   * Given an operand number and an encoding, generate a test to determine
   * whether the given operand matches the encoding. That is, generate code to
   * the Assembler that examines a given operand of the current
   * Instruction, and determines whether it is of the type encoded by the
   * given encoding. This is used to generate the if statements of the dispatch
   * functions for each opt compiler opcode.
   *
   * @param argNumber The argument to examine
   * @param argEncoding The encoding for which to check
   */
  private static void emitTest(int argNumber, ArgumentType argEncoding) {
    if (argEncoding.isSize())
      emit("is" + argEncoding.getOptName() + "(inst)");
    else
      emit("is" + argEncoding.getOptName() + "(" + getOperand(argNumber) + ")");
  }

  /**
   * Generate code to verify that a given operand matches a given encoding.
   * Since the IA32 architecture is not exactly orthogonal (please note the
   * charitable understatement), there are cases when the opt assembler can
   * determine the Assembler emitter to call without looking at all (or, in
   * some cases, any) of the arguments of the Instruction. An example is the
   * ENTER instruction that only takes one immediate parameter, so the opt
   * assembler could simply call that Assembler emiiter without checking that
   * argument is really an immediate. In such cases, the opt assembler generates
   * guarded tests that verify that Instruction operand actually matches the
   * required encoding. This function emits such tests to the assembler being
   * generated.
   *
   * @param argNumber The argument to examine
   * @param argEncoding The encoding for which to check
   * @param level current level for generating pretty, tabified output
   */
  private static void emitVerify(int argNumber, ArgumentType argEncoding,
      int level) {
    emitTab(level);
    emit("if (VM.VerifyAssertions && !");
    emitTest(argNumber, argEncoding);
    emit(") VM._assert(false, inst.toString());\n");
  }

  /**
   * Generate code to fetch all the arguments needed for a given operand number
   * and encoding. The different argument encodings of the Assembler need
   * different arguments to be passed to the emitter function. For instance, a
   * register-displacement mode operand needs to be given a base register and an
   * immediate displacement. This function generates the appropriate arguments
   * given the operand number and encoding; that is, it generates reads of the
   * appropriate Instruction argument and fetches of the appropriate pieces
   * of information from the operand.
   *
   * @param argNumber The argument being generated.
   * @param argEcoding The encoding to use.
   */
  private static void emitArgs(int argNumber, ArgumentType argEncoding) {
    String op = getOperand(argNumber);
    switch (argEncoding) {
    case LabelOrImmediate:
      emit("getImm(" + op + "), getLabel(" + op + ")");
      break;
    case RegisterDisplacement:
      emit("getBase(" + op + "), getDisp(" + op + ")");
      break;
    case Absolute:
      emit("getDisp(" + op + ").toWord().toAddress()");
      break;
    case RegisterOffset:
      emit("getIndex(" + op + "), getScale(" + op + "), getDisp(" + op + ")");
      break;
    case RegisterIndexed:
      emit("getBase(" + op + "), getIndex(" + op + "), getScale(" + op + "), getDisp(" + op + ")");
      break;
    case RegisterIndirect:
      emit("getBase(" + op + ")");
      break;
    default:
      emit("get" + argEncoding.getOptName() + "(" + op + ")");
    }
  }

  /**
   * An EmitterDescriptor represents a single emit method from the Assembler:
   * it explicitly represents the types of operands the method expects, their
   * number, and the size of the data it uses. When GenerateAssembler encounters
   * an emit* method from the Assembler, it creates an EmitterDescriptor for
   * it. Based upon the stlyized form the method name is required to have, the
   * EmitterDexcriptor represents information about its arguments. This
   * information is stored in terms of the GenerateAssembler constants that
   * represent operand type and size.
   * <P>
   * The EmitterDescriptor class encapsulates the logic for parsing the stylized
   * emit* method names that the Assembler has, and turning them into the
   * explicit representation that GenerateAssembler uses. If parsing a name
   * fails, a {@link GenerateAssembler.BadEmitMethod} runtime exception is
   * thrown and assembler generation is aborted.
   * <P>
   * <HR>
   * <EM>See the descriptions of the GenerateAssembler constants:</EM>
   * <DL>
   * <DT> <EM>Operand types</EM> <DI>
   * <UL>
   * <LI> {@link #Immediate}
   * <LI> {@link #Label}
   * <LI> {@link #LabelOrImmediate}
   * <LI> {@link #Absolute}
   * <LI> {@link #Register}
   * <LI> {@link #RegisterIndirect}
   * <LI> {@link #RegisterOffset}
   * <LI> {@link #RegisterIndexed}
   * </UL>
   * <DT> <EM>Data size</EM>
   * <UL>
   * <LI> {@link #Byte}
   * <LI> {@link #Word}
   * <LI> {@link #Quad}
   * </UL>
   * </DL>
   */
  static class EmitterDescriptor {
    private final ArgumentType size;
    private final int count;
    private final ArgumentType[] args;

    /**
     * Create an EmitterDescriptor for the given methodName. This conmstructor
     * creates a descriptor that represents explicitly the types and size of the
     * operands of the given emit* method. This constructor encapsulate the
     * logic to parse the given method name into the appropriate explicit
     * representation.
     */
    EmitterDescriptor(String methodName, Class<?>[] argTypes) {
      StringTokenizer toks = new StringTokenizer(methodName, "_");
      toks.nextElement(); // first element is emitXXX;
      args = new ArgumentType[toks.countTokens()];
      ArgumentType size = null;
      int count = 0;
      int argTypeNum = 0;
      for (int i = 0; i < args.length; i++) {
        String cs = toks.nextToken();
        ArgumentType code;
        if (argTypeNum < argTypes.length) {
          code = getEncoding(cs, argTypes[argTypeNum]);
        } else {
          code = getEncoding(cs, null);
        }
        argTypeNum += code.getParameters();
        if (DEBUG) {
          System.err.println(methodName + "[" + i + "] is " + code + " for " + cs);
        }

        args[count] = code;
        count++;
        if (code.isSize()) {
          size = code;
          count--;
        }
      }
      this.size = size;
      this.count = count;
    }

    /**
     * This method checks whether the emit* method represented by this
     * EmitterDescriptor expects the argument type represented by enc as its
     * argument'th operand. If enc is an operand type encoding, this method
     * checks wether the given argument is of the appropriate type. If enc is an
     * operand size encoding, the argument parameter is ignored, and this method
     * checks whether the emit* method represented operates upon data of the
     * desired size.
     * <P>
     * <EM>See the descriptions of the GenerateAssembler constants:</EM>
     * <DL>
     * <DT> <EM>Operand types</EM> <DI>
     * <UL>
     * <LI> {@link #Immediate}
     * <LI> {@link #Label}
     * <LI> {@link #LabelOrImmediate}
     * <LI> {@link #Absolute}
     * <LI> {@link #Register}
     * <LI> {@link #RegisterIndirect}
     * <LI> {@link #RegisterOffset}
     * <LI> {@link #RegisterIndexed}
     * </UL>
     * <DT> <EM>Data size</EM>
     * <UL>
     * <LI> {@link #Byte}
     * <LI> {@link #Word}
     * <LI> {@link #Quad}
     * </UL>
     * </DL>
     * <P>
     *
     * @param argument The operand number examined
     * @param enc The argument type queried, as encoded as one of the operand
     *            type constants used throughout GenerateAssembler.
     *
     * @return True if this method expects an argument type encoded by enc as
     *         its argument'th operand, and false otherwise.
     */
    boolean argMatchesEncoding(int argument, ArgumentType enc) {
      if (!enc.isSize())
        return (count > argument) && args[argument] == enc;
      else
        return size == enc;
    }

    /**
     * Access the array that stores the encodings of the arguments to the emit
     * method represented by this EmitterDescriptor.
     *
     * @return the array of argument encodings
     */
    ArgumentType[] getArgs() {
      return args;
    }

    /**
     * Access the data size operated upon by emit method represented by this
     * EmitterDescriptor.
     *
     * @return data size for this descriptor
     */
    ArgumentType getSize() {
      return size;
    }

    /**
     * Access the number of operands operated upon by emit method represented by
     * this EmitterDescriptor.
     *
     * @return number of operands for this descriptor
     */
    int getCount() {
      return count;
    }

    public String toString() {
      StringBuffer s = new StringBuffer();
      s.append("ed:");
      for (int i = 0; i < count; i++)
        s.append(" " + args[i]);
      if (size != null)
        s.append(" (" + size + ")");
      return s.toString();
    }
  }

  /**
   * An EmitterSet represents a set of emit methods from the Assembler for
   * the same IA32 assembler opcode. These sets are used when generating the do<opcode>
   * method for a given IA32 opcde: first an EmitterSet of all the Assembler
   * emit methods for that opcode is built, and then the do method is
   * recursively generated by emitting operand type and size tests that
   * partition the set of emitters into two smaller sets. This continues until
   * the set is a singleton
   */
  static class EmitterSet {

    /**
     * The Assembler emit methods that this set represents. This is a set of
     * EmitterDescriptor objects.
     */
    private final Set<EmitterDescriptor> emitters = new HashSet<EmitterDescriptor>();

    /**
     * Print this EmitterSet readably.
     *
     * @return a string describing this EmitterSet
     */
    public String toString() {
      StringBuffer s = new StringBuffer();
      s.append("Emitter Set of:\n");
      Iterator<EmitterDescriptor> i = emitters.iterator();
      while (i.hasNext())
        s.append(i.next().toString() + "\n");

      s.append("-------------\n");
      return s.toString();
    }

    /**
     * Test whethe rthis EmitterSet as exactly one element.
     *
     * @return true if this EmitterSet as exactly one element.
     */
    boolean isSingleton() {
      return (emitters.size() == 1);
    }

    /**
     * Insert an EmitterDescriptor into this set
     *
     * @param ed the EmitterDescriptor to insert
     */
    void add(EmitterDescriptor ed) {
      emitters.add(ed);
    }

    /**
     * Count how many of the emit represented by this set match a given operand
     * type and size encoding. This method is used (via getEncodingSplit) while
     * recursively partitioning a given EmitterSet to determine how evenly (or
     * even whether) a given operand type and size splits this set.
     *
     * @see #getEncodingSplit
     *
     * @param n the operand being examined
     * @param code the operand type or size code being considered
     * @return the number of emit methods of which the specified operand type
     *         matches the specified one.
     */
    private int countEncoding(int n, ArgumentType code) {
      Iterator<EmitterDescriptor> i = emitters.iterator();
      int count = 0;
      while (i.hasNext())
        if (((EmitterDescriptor) i.next()).argMatchesEncoding(n, code))
          count++;
      return count;
    }

    /**
     * Return the difference between the number of emit methods in this set that
     * match a given operand type and size for a given operand, and the number
     * of those that do not. This method is used while recursively partitioning
     * a given EmitterSet to determine how evenly (or even whether) a given
     * operand type and size splits this set.
     *
     * @param n the operand being examined
     * @param code the operand type or size code being considered
     * @return the different between matching and non-matching emit method in
     *         this set.
     */
    private int getEncodingSplit(int n, ArgumentType code) {
      int count = countEncoding(n, code);
      return Math.abs((emitters.size() - count) - count);
    }

    /**
     * This class is used just to communicate the two results of searching for
     * the best split for a given set: the chosen operand type or size, and the
     * chosen operand nummber. This class is basically to avoid writing the slew
     * of required type casts that a generic pair would need given Java's
     * primitive type system.
     *
     * @see #makeSplit
     * @see #split
     */
    static class SplitRecord {
      /**
       * The operand number to be split.
       */
      final int argument;

      /**
       * The operand type or size test on which to split.
       */
      final ArgumentType test;

      /**
       * Make s split record to communicate the results of searching for the
       * best operand to split.
       *
       * argument The operand number to be split. test The operand type or size
       * test on which to split.
       */
      SplitRecord(int argument, ArgumentType test) {
        this.argument = argument;
        this.test = test;
      }
    }

    /**
     * This method uses a SplitRecord as the criertion to partition the given
     * EmitterSet into two subsets.
     *
     * @param split the plit record dicatating how to split
     */
    private EmitterSet[] makeSplit(SplitRecord split) {
      int arg = split.argument;
      ArgumentType test = split.test;
      EmitterSet yes = new EmitterSet();
      EmitterSet no = new EmitterSet();
      Iterator<EmitterDescriptor> i = emitters.iterator();
      while (i.hasNext()) {
        EmitterDescriptor ed = (EmitterDescriptor) i.next();
        if (ed.argMatchesEncoding(arg, test)) {
          yes.add(ed);
        } else {
          no.add(ed);
        }
      }

      return new EmitterSet[] { yes, no };
    }

    /**
     * Find the best operand type or size and operand number to partition this
     * EmitterSet. This method searches across all possible ways of splitting
     * this set--all possible operand types and sizes, and all possible
     * operands--to determine which one splits the set most evenly.
     *
     * @return a SplitRecord representing the most-even split
     */
    SplitRecord split() {
      int splitArg = -1;
      ArgumentType splitTest = null;
      int splitDiff = 1000;
      for (int arg = 0; arg < 4; arg++) {
        for (ArgumentType test : ArgumentType.values()) {
          int c = getEncodingSplit(arg, test);
          if (c == 0)
            return new SplitRecord(arg, test);
          else if (c < splitDiff) {
            splitArg = arg;
            splitTest = test;
            splitDiff = c;
          }
        }
      }

      return new SplitRecord(splitArg, splitTest);
    }

    /**
     * Emit the Java code to call a particular emit method for a particular
     * opcode. This method takes representations of the opcode and operands of a
     * given emit method, and generates the appropriate Java source code to call
     * it. It synthesizes the encoded emit method name, and uses emitArgs to
     * pass all the required arguments.
     *
     * @see #emitArgs
     *
     * @param opcode the IA32 opcode of the emit method
     * @param args the encoding of each operand to the emit method
     * @param count the number of operands
     * @param level the level of tabbing for pretty output
     */
    private void emitEmitCall(String opcode, ArgumentType[] args, int count,
        int level, ArgumentType size) {
      if (DEBUG) {
        System.err.print("Emitting call for " + opcode + " with args: ");
        for (ArgumentType arg : args) {
          System.err.print(arg + " ");
        }
        System.err.println(" count=" + count + " level=" + level + " size=" + size);
      }
      emitTab(level);
      emit("emit" + opcode);
      for (int i = 0; i < count; i++)
        emit("_" + args[i].getAssemblerName());
      if (size != null)
        emit("_" + size.getAssemblerName());

      if (count == 0)
        emit("();\n");
      else {
        emit("(");
        for (int i = 0; i < count; i++) {
          emit("\n");
          emitTab(level + 1);
          emitArgs(i, args[i]);
          if (i == count - 1)
            emit(");\n");
          else
            emit(",");
        }
      }
    }

    /**
     * Write the Java code required for error checking and calling the emit
     * method represented by a singleton EmitterSet. A singleton EmiiterSet will
     * typically be the result of a series of splits of bigger sets, where the
     * splits represent emitted queries of operand types and sizes. (See
     * emitSet) However, there may be cases when some operand has only one
     * possible options, so the splitting will not have generated any tests for
     * it. In this case, we will emit assertions that guarantee the operand is
     * of the expected type. Note that the answers to queries alrrready
     * performed by splitting are known to be fine, so no additional error
     * checking is needed for cases they cover.
     *
     * @see #emitSet
     *
     * @param opcode the IA32 opcode to generate
     * @param testsPerformed the set of queries already performed by splitting.
     * @param level level of indentation for prett printing
     */
    private void emitSingleton(String opcode, boolean[][] testsPerformed,
        int level) {
      EmitterDescriptor ed = (EmitterDescriptor) emitters.iterator().next();

      ArgumentType[] args = ed.getArgs();
      int count = ed.getCount();
      for (int i = 0; i < count; i++)
        if (!testsPerformed[i][args[i].ordinal()])
          emitVerify(i, args[i], level);

      ArgumentType size = ed.getSize();
      if (size != null) {
        boolean needed = true;

        for (int i = 0; i < count; i++)
          if (testsPerformed[i][size.ordinal()])
            needed = false;

        if (needed)
          emitVerify(0, size, level);

        if (size == ArgumentType.Byte)
          for (int i = 0; i < count; i++)
            if (args[i] == ArgumentType.GPRegister)
              if (currentOpcode.indexOf("MOVZX") == -1 &&
                    currentOpcode.indexOf("MOVSX") == -1) {
                emitTab(level);
                emit("if (VM.VerifyAssertions && !(");
                emitArgs(i, ArgumentType.GPRegister);
                emit(".value() < 4)) VM._assert(false, inst.toString());\n");
              }
      }

      emitEmitCall(opcode, args, count, level, ed.getSize());
    }

    /**
     * Emit Java code for deciding which emit method in the given set applies to
     * an Instruction, and then calling the apprpriate method. The method
     * essentially works by recursively parititioning the given set into two
     * smaller pieces until it finds a set with only one element. On each
     * partition, this method generates code for the appropriate operand type or
     * size query, and then calls itself recursively on the two sets resulting
     * from the partition.
     *
     * This method uses split to determine what test to apply, and emitSingleton
     * when it encounteres a singleton set.
     *
     * Note that the testsPerformed parameter is not needed to do the recursive
     * splitting; this is passed to emitSingleton to help it generate
     * appropriate error checking for operands.
     *
     * @see #split
     * @see #emitSingleton
     *
     * @param opcode the IA32 opcode being generated
     * @param testsPerformed the set of tests already performed
     * @param level the indentation level for pretty printing
     */
    private void emitSet(String opcode, boolean[][] testsPerformed, int level) {
      if (emitters.isEmpty()) {
        // do nothing
      } else if (isSingleton())
        emitSingleton(opcode, testsPerformed, level);
      else {
        SplitRecord rec = split();

        if (DEBUG) {
          for (int i = 0; i < level; i++)
            System.err.print("  ");
          System.err.println("split of " + opcode + "[" + rec.argument +
               "] for " + rec.test);
        }

        if (testsPerformed[rec.argument][rec.test.ordinal()]) {
          throw new Error("repeated split of " + opcode + "[" + rec.argument +
               "] for " + rec.test + "\n" + this);
        }

        testsPerformed[rec.argument][rec.test.ordinal()] = true;
        EmitterSet[] splits = makeSplit(rec);
        emitTab(level);
        emit("if (");
        emitTest(rec.argument, rec.test);
        emit(") {\n");
        splits[0].emitSet(opcode, testsPerformed, level + 1);
        emit("\n");
        emitTab(level);
        emit("} else {\n");
        splits[1].emitSet(opcode, testsPerformed, level + 1);
        emitTab(level);
        emit("}\n");
        testsPerformed[rec.argument][rec.test.ordinal()] = false;
      }
    }
  }

  /**
   * the Class object of the Assembler. This is used for reflective inquiries
   * about emit methods.
   *
   * @see #main
   */
  static final Class<org.jikesrvm.compilers.common.assembler.ia32.Assembler> lowLevelAsm = org.jikesrvm.compilers.common.assembler.ia32.Assembler.class;

  /**
   * Computes the set of emit methods in the Assembler for a given IA32
   * opcode.
   *
   * @param emitters the set of all emit methods
   * @param opcode the opcode being examined
   */
  private static EmitterSet buildSetForOpcode(Method[] emitters, String opcode) {
    EmitterSet s = new EmitterSet();
    for (int i = 0; i < emitters.length; i++) {
      Method m = emitters[i];
      if (m.getName().startsWith("emit" + opcode + "_") ||
           m.getName().equals("emit" + opcode)) {
        s.add(new EmitterDescriptor(m.getName(), m.getParameterTypes()));
      }
    }

    return s;
  }

  /**
   * the set of IA32 opcodes to ignore. Some opcode are not used by the opt
   * compiler (NOP is a good example) but may be present in the Assembler if
   * other compilers use them. We keep an explicit list of such opcodes to
   * ignore.
   */
  private static Set<String> excludedOpcodes;

  /**
   * Initialize the set of opcodes to ignore
   *
   * @see #excludedOpcodes
   */
  static {
    excludedOpcodes = new HashSet<String>();
    excludedOpcodes.add("FSAVE");
    excludedOpcodes.add("FNSTSW");
    excludedOpcodes.add("FUCOMPP");
    excludedOpcodes.add("SAHF");
    excludedOpcodes.add("NOP");
    excludedOpcodes.add("ENTER");
    excludedOpcodes.add("JMP");
    excludedOpcodes.add("JCC");
    excludedOpcodes.add("EMMS");
  }

  /**
   * Compute the set of all IA32 opcodes that have emit methods in the
   * Assembler. This method uses the stylized form of all emit method names
   * in the Assembler to extract the opcode of each one. It returns a set of
   * all such distinct names, as a set of Strings.
   *
   * @param emitters the set of all emit methods in the Assembler
   * @return the set of all opcodes handled by the Assembler
   */
  private static Set<String> getOpcodes(Method[] emitters) {
    Set<String> s = new HashSet<String>();
    for (int i = 0; i < emitters.length; i++) {
      String name = emitters[i].getName();
      if (DEBUG)
        System.err.println(name);
      if (name.startsWith("emit")) {
        int posOf_ = name.indexOf('_');
        if (posOf_ != -1) {
          String opcode = name.substring(4, posOf_);
          if (!excludedOpcodes.contains(opcode)) {
            s.add(opcode);
          }
        } else {
          String opcode = name.substring(4);
          // make sure it is an opcode
          if (opcode.equals(opcode.toUpperCase(Locale.getDefault()))) {
            if (!excludedOpcodes.contains(opcode)) {
              s.add(opcode);
            }
          }
        }
      }
    }

    return s;
  }

  /**
   * returns a list of all IA32_ opt compiler operators that do not correspond
   * to real IA32 opcodes handled by the assembler. These are all supposed to
   * have been removed by the time the assembler is called, so the assembler
   * actually seeing such an opcode is an internal compiler error. This set is
   * used during generating of error checking code.
   *
   * @param emittedOpcodes the set of IA32 opcodes the assembler understands.
   * @return the set of IA32 opt operators that the assembler does not
   *         understand.
   */
  private static Set<String> getErrorOpcodes(Set<String> emittedOpcodes) {
    Iterator<String> e = OperatorFormatTables.getOpcodes();
    Set<String> errorOpcodes = new HashSet<String>();
    while (e.hasNext()) {
      String opcode = (String) e.next();
      if (!emittedOpcodes.contains(opcode))
        errorOpcodes.add(opcode);
    }

    return errorOpcodes;
  }

  /**
   * Given an IA32 opcode, return the set of opt compiler IA32_ operators that
   * translate to it. There is, by and large, a one-to-one mapping in each each
   * IA332_ opt operator represents an IA32 opcde, so this method might seem
   * useless. However, there are some special cases, notably for operand size.
   * In this case, an opt operator of the form ADD__B would mean use the ADD
   * IA32 opcode with a byte operand size.
   */
  private static Set<String> getMatchingOperators(String lowLevelOpcode) {
    Iterator<String> e = OperatorFormatTables.getOpcodes();
    Set<String> matchingOperators = new HashSet<String>();
    while (e.hasNext()) {
      String o = (String) e.next();
      if (o.equals(lowLevelOpcode) || o.startsWith(lowLevelOpcode + "__"))
        matchingOperators.add(o);
    }

    return matchingOperators;
  }

  /**
   * Generate an assembler for the opt compiler
   */
  public static void main(String[] args) {
    try {
      out = new FileWriter(System.getProperty("generateToDir") + "/AssemblerOpt.java");
    } catch (IOException e) {
      throw new Error(e);
    }

    emit("package org.jikesrvm.compilers.opt.mir2mc.ia32;\n\n");
    emit("import org.jikesrvm.*;\n\n");
    emit("import org.jikesrvm.compilers.opt.*;\n\n");
    emit("import org.jikesrvm.compilers.opt.ir.*;\n\n");
    emit("\n\n");

    emit("/**\n");
    emit(" *  This class is the automatically-generated assembler for\n");
    emit(" * the optimizing compiler.  It consists of methods that\n");
    emit(" * understand the possible operand combinations of each\n");
    emit(" * instruction type, and how to translate those operands to\n");
    emit(" * calls to the Assember low-level emit method\n");
    emit(" *\n");
    emit(" * It is generated by GenerateAssembler.java\n");
    emit(" *\n");
    emit(" */\n");
    emit("public abstract class AssemblerOpt extends AssemblerBase {\n\n");

    emitTab(1);
    emit("/**\n");
    emitTab(1);
    emit(" * @see org.jikesrvm.ArchitectureSpecific.Assembler\n");
    emitTab(1);
    emit(" */\n");
    emitTab(1);
    emit("public AssemblerOpt(int bcSize, boolean print, IR ir) {\n");
    emitTab(2);
    emit("super(bcSize, print, ir);\n");
    emitTab(1);
    emit("}");
    emit("\n\n");

    Method[] emitters = lowLevelAsm.getDeclaredMethods();
    Set<String> opcodes = getOpcodes(emitters);

    Iterator<String> i = opcodes.iterator();
    while (i.hasNext()) {
      String opcode = (String) i.next();
      setCurrentOpcode(opcode);
      emitTab(1);
      emit("/**\n");
      emitTab(1);
      emit(" *  Emit the given instruction, assuming that\n");
      emitTab(1);
      emit(" * it is a " + currentFormat + " instruction\n");
      emitTab(1);
      emit(" * and has a " + currentOpcode + " operator\n");
      emitTab(1);
      emit(" *\n");
      emitTab(1);
      emit(" * @param inst the instruction to assemble\n");
      emitTab(1);
      emit(" */\n");
      emitTab(1);
      emit("private void do" + opcode + "(Instruction inst) {\n");
      EmitterSet emitter = buildSetForOpcode(emitters, opcode);
      boolean[][] tp = new boolean[4][ArgumentType.values().length];
      emitter.emitSet(opcode, tp, 2);
      emitTab(1);
      emit("}\n\n");
    }

    emitTab(1);
    emit("/**\n");
    emitTab(1);
    emit(" *  The number of instructions emitted so far\n");
    emitTab(1);
    emit(" */\n");
    emitTab(1);
    emit("private int instructionCount = 0;\n\n");

    emitTab(1);
    emit("/**\n");
    emitTab(1);
    emit(" *  Assemble the given instruction\n");
    emitTab(1);
    emit(" *\n");
    emitTab(1);
    emit(" * @param inst the instruction to assemble\n");
    emitTab(1);
    emit(" */\n");
    emitTab(1);
    emit("public void doInst(Instruction inst) {\n");
    emitTab(2);
    emit("instructionCount++;\n");
    emitTab(2);
    emit("resolveForwardReferences(instructionCount);\n");
    emitTab(2);
    emit("switch (inst.getOpcode()) {\n");

    Set<String> emittedOpcodes = new HashSet<String>();

    i = opcodes.iterator();
    while (i.hasNext()) {
      String opcode = i.next();
      Iterator<String> operators = getMatchingOperators(opcode).iterator();
      while (operators.hasNext()) {
        String operator = operators.next();
        emitTab(3);
        emittedOpcodes.add(operator);
        emit("case IA32_" + operator + "_opcode:\n");
      }
      emitTab(4);
      emit("do" + opcode + "(inst);\n");
      emitTab(4);
      emit("break;\n");
    }

    // Special case because doJCC is handwritten to add
    // logic for short-forward branches
    emittedOpcodes.add("JCC");
    emitTab(3);
    emit("case IA32_JCC_opcode:\n");
    emitTab(4);
    emit("doJCC(inst);\n");
    emitTab(4);
    emit("break;\n");

    // Special case because doJMP is handwritten to add
    // logic for short-forward branches
    emittedOpcodes.add("JMP");
    emitTab(3);
    emit("case IA32_JMP_opcode:\n");
    emitTab(4);
    emit("doJMP(inst);\n");
    emitTab(4);
    emit("break;\n");

    // Kludge for IA32_LOCK which needs to call emitLockNextInstruction
    emittedOpcodes.add("LOCK");
    emitTab(3);
    emit("case IA32_LOCK_opcode:\n");
    emitTab(4);
    emit("emitLockNextInstruction();\n");
    emitTab(4);
    emit("break;\n");

    // Kludge for PATCH_POINT
    emitTab(3);
    emit("case IG_PATCH_POINT_opcode:\n");
    emitTab(4);
    emit("emitPatchPoint();\n");
    emitTab(4);
    emit("break;\n");

    // Kludge for LOWTABLESWITCH
    emitTab(3);
    emit("case MIR_LOWTABLESWITCH_opcode:\n");
    emitTab(4);
    emit("doLOWTABLESWITCH(inst);\n");
    emitTab(4);
    emit("// kludge table switches that are unusually long instructions\n");
    emitTab(4);
    emit("instructionCount += MIR_LowTableSwitch.getNumberOfTargets(inst);\n");
    emitTab(4);
    emit("break;\n");

    Set<String> errorOpcodes = getErrorOpcodes(emittedOpcodes);
    if (!errorOpcodes.isEmpty()) {
      i = errorOpcodes.iterator();
      while (i.hasNext()) {
        emitTab(3);
        emit("case IA32_" + i.next() + "_opcode:\n");
      }
      emitTab(4);
      emit("throw new OptimizingCompilerException(inst + \" has unimplemented IA32 opcode (check excludedOpcodes)\");\n");
    }

    emitTab(2);
    emit("}\n");
    emitTab(2);
    emit("inst.setmcOffset( mi );\n");
    emitTab(1);
    emit("}\n\n");

    emit("\n}\n");

    try {
      out.close();
    } catch (IOException e) {
      throw new Error(e);
    }
  }
}
