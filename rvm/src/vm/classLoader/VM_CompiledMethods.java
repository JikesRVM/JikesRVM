/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Manage pool of compiled methods. <p>
 * Original extracted from VM_ClassLoader. <p>
 * 
 * @author Bowen Alpern
 * @author Derek Lieber
 * @author Arvin Shepherd
 */
public class VM_CompiledMethods
  {

  // Create an id that will uniquely identify a version of machine code generated for some method.
  // Taken:    nothing
  // Returned: id
  // See also: setCompiledMethod(), getCompiledMethod()
  //
  static int createCompiledMethodId() {
    return ++currentCompiledMethodId;
  }

  // Install a newly compiled method.
  //
  static void setCompiledMethod(int compiledMethodId,
				VM_CompiledMethod compiledMethod) {
    if (compiledMethodId >= compiledMethods.length)
      compiledMethods = growArray(compiledMethods, compiledMethodId << 1); // grow array by 2x in anticipation of more entries being added
    if (VM.VerifyAssertions)
	VM.assert(compiledMethods[compiledMethodId] == null);
			// Slots are never reused even when a slot becomes
			// obsolete. This is because there can be parallel data
    compiledMethods[compiledMethodId] = compiledMethod;
    VM_Magic.sync();  // make sure the update is visible on other procs
  }

  // Fetch a previously compiled method.
  //
  static VM_CompiledMethod getCompiledMethod(int compiledMethodId) {
    VM_Magic.isync();  // see potential update from other procs

    if (VM.VerifyAssertions) {
      VM.assert(0 <= compiledMethodId);
      VM.assert(compiledMethodId <= currentCompiledMethodId);
      VM.assert(compiledMethods[compiledMethodId] != null);
    }

    return compiledMethods[compiledMethodId];
  }

  // Get number of methods compiled so far.
  //
  static int numCompiledMethods() {
    return currentCompiledMethodId + 1;
  }

  // Getter method for the debugger, interpreter.
  //
  static VM_CompiledMethod[] getCompiledMethods() {
    return compiledMethods;
  }

  // Getter method for the debugger, interpreter.
  //
  static int numCompiledMethodsLess1() {
    return currentCompiledMethodId;
  }

   // Find method whose machine code contains specified instruction.
   // Taken:      instruction address
   // Returned:   method (null --> not found)
   // Assumption: caller has disabled gc (otherwise collector could move
   //             objects without fixing up raw "ip" pointer)
   //
   // Usage note: "ip" must point to the instruction *following* the actual instruction
   // whose method is sought. This allows us to properly handle the case where
   // the only address we have to work with is a return address (ie. from a stackframe)
   // or an exception address (ie. from a null pointer dereference, array bounds check,
   // or divide by zero) on a machine architecture with variable length instructions.
   // In such situations we'd have no idea how far to back up the instruction pointer
   // to point to the "call site" or "exception site".
   //
   // Note: this method is highly inefficient. Normally you should use the following instead:
   //   VM_ClassLoader.getCompiledMethod(VM_Magic.getCompiledMethodID(fp))
   //
  static VM_CompiledMethod findMethodForInstruction(int ip) {
    for (int i = 0, n = numCompiledMethods(); i < n; ++i) {
      VM_CompiledMethod compiledMethod = compiledMethods[i];
      if (compiledMethod == null)
	continue; // empty slot

      INSTRUCTION[] instructions = compiledMethod.getInstructions();
      int           beg          = VM_Magic.objectAsAddress(instructions);
      int           end          = beg + (instructions.length << VM.LG_INSTRUCTION_WIDTH);

      // note that "ip" points to a return site (not a call site)
      // so the range check here must be "ip <= beg || ip >  end"
      // and not                         "ip <  beg || ip >= end"
      //
      if (ip <= beg || ip > end)
	continue;

      return compiledMethod;
    }

    return null;
  }

  //----------------//
  // implementation //
  //----------------//

   // Java methods that have been compiled into machine code.
   // Note that there may be more than one compiled versions of the same method
   // (ie. at different levels of optimization).
   //
  private static VM_CompiledMethod[] compiledMethods;

  // Index of most recently allocated slot in compiledMethods[].
  //
  private static int currentCompiledMethodId;

   // Initialize for bootimage.
   //
  static void init() {
    compiledMethods = new VM_CompiledMethod[0];
  }

  // Expand an array.
  //
  private static VM_CompiledMethod[] growArray(VM_CompiledMethod[] array, 
					       int newLength) {
    VM_CompiledMethod[] newarray = new VM_CompiledMethod[newLength];
    for (int i = 0, n = array.length; i < n; ++i)
      newarray[i] = array[i];

    VM_Magic.sync();
    return newarray;
  }

}
