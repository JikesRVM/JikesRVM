/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id:

/**
 * A java method's local variable information (for use by debuggers).
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
class VM_LocalVariable {
  VM_Atom name;               // name of this variable
  VM_Atom descriptor;         // its type descriptor

  int stackSlot;              // slot where it resides (in "locals" part of stackframe)

  int startPC;                // range of bytecodes for which it resides in that stack slot,
  int endPC;                  // indexed from start of methods' bytecodes[] (inclusive)

  VM_LocalVariable(VM_Class cls, VM_BinaryData input) {
    startPC    = input.readUnsignedShort();
    endPC      = startPC + input.readUnsignedShort();
    name       = cls.getUtf(input.readUnsignedShort());
    descriptor = cls.getUtf(input.readUnsignedShort());
    stackSlot  = input.readUnsignedShort();
  }

  final VM_Type getType() {
    return VM_ClassLoader.findOrCreateType(descriptor);
  }

  // Is this local variable currently in scope of specified bytecode?
  // Taken:    offset of bytecode from start of method
  // Returned: true  --> in scope
  //           false --> not in scope
  //
  public final boolean inScope(int pc) {
    return pc >= startPC && pc <= endPC;
  }

  public final String getName() {
    return name.toString();
  }

  public final int getStackOffset() {
    return stackSlot;
  }

  public final VM_Atom getDescriptor() {
    return descriptor;
  }
}
