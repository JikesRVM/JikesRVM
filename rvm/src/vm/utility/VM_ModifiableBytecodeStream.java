/*
 * (C) Copyright IBM Corp. 2001
 */
//VM_ModifiableBytecodeStream.java
//$Id$
/**
 * VM_ModifiableBytecodeStream.java
 *
 * This class provides a modifiable stream of bytecodes
 * 
 * @author Igor Pechtchanski
 * @see VM_BytecodeStream
 * @see OPT_BC2IR
 */

public final class VM_ModifiableBytecodeStream extends VM_BytecodeStream {
  /**
   * Constructor
   * @param m the method containing the bytecodes
   */
  public VM_ModifiableBytecodeStream(VM_Method m) {
    super(m);
  }

  /**
   * Inserts a byte at current position in the stream.
   * Adjusts the exception tables, line number maps, etc. as necessary.
   * Note: the byte does not have to represent a valid bytecode.
   * @param b the byte
   */
  public void insert(byte b) {
    // update bytecodes
    VM_Method method = method();
    int index = index();
    byte[] oldBytecodes = method.getBytecodes();
    byte[] newBytecodes = new byte[oldBytecodes.length + 1];
    for (int k = 0; k < index; k++)
      newBytecodes[k] = oldBytecodes[k];
    newBytecodes[index] = b;
    for (int k = index; k < oldBytecodes.length; k++)
      newBytecodes[k+1] = oldBytecodes[k];
    method.setBytecodes(newBytecodes);
    fixExceptionHandlers(index, 1);
    fixLineNumbers(index, 1);
    // TODO: see if the following works
    //insert(new byte[] { b });
  }

  /**
   * Inserts a byte sequence at current position in the stream.
   * Adjusts the exception tables, line number maps, etc. as necessary.
   * Note: the byte sequence does not have to represent valid bytecodes.
   * @param bytes the byte sequence
   */
  public void insert(byte[] bytes) {
    insert(bytes, 0, bytes.length);
  }

  /**
   * Inserts a byte sequence at current position in the stream.
   * Adjusts the exception tables, line number maps, etc. as necessary.
   * Note: the byte sequence does not have to represent valid bytecodes.
   * @param bytes the byte sequence
   * @param offset index of first byte to insert
   * @param count number of bytes to insert
   */
  public void insert(byte[] bytes, int offset, int count) {
    if (count == 0) return;
    VM_Method method = method();
    int index = index();
    // update bytecodes
    byte[] oldBytecodes = method.getBytecodes();
    byte[] newBytecodes = new byte[oldBytecodes.length + count];
    for (int k = 0; k < index; k++)
      newBytecodes[k] = oldBytecodes[k];
    for (int k = 0; k < count; k++)
      newBytecodes[k+index] = bytes[k+offset];
    for (int k = index; k < oldBytecodes.length; k++)
      newBytecodes[k+count] = oldBytecodes[k];
    method.setBytecodes(newBytecodes);
    fixExceptionHandlers(index, count);
    fixLineNumbers(index, count);
  }

  /**
   * Inserts a bytecode sequence at current position in the stream.
   * Adjusts the exception tables, line number maps, etc. as necessary.
   * @param lw the local words assumed by the sequence
   * @param ow the operand words assumed by the sequence
   * @param bytes the bytecode sequence
   */
  public void insert(int lw, int ow, byte[] bytes) {
    insert(bytes);
    fixSize(lw, ow);
  }

  /**
   * Inserts a bytecode sequence at current position in the stream.
   * Adjusts the exception tables, line number maps, etc. as necessary.
   * @param lw the local words assumed by the sequence
   * @param ow the operand words assumed by the sequence
   * @param bytes the bytecode sequence
   * @param offset index of first byte to insert
   * @param count number of bytes to insert
   */
  public void insert(int lw, int ow, byte[] bytes, int offset, int count) {
    insert(bytes, offset, count);
    fixSize(lw, ow);
  }

  /**
   * Removes a number of bytes from current position in the stream.
   * Adjusts the exception tables, line number maps, etc. as necessary.
   * Note: the byte sequence does not have to represent valid bytecodes.
   * @param len the number of bytes to remove
   */
  public void delete(int len) {
    if (len == 0) return;
    VM_Method method = method();
    int index = index();
    // update bytecodes
    byte[] oldBytecodes = method.getBytecodes();
    if (len > oldBytecodes.length - index) len = oldBytecodes.length - index;
    byte[] newBytecodes = new byte[oldBytecodes.length - len];
    for (int k = 0; k < index; k++)
      newBytecodes[k] = oldBytecodes[k];
    for (int k = index + len; k < oldBytecodes.length; k++)
      newBytecodes[k-len] = oldBytecodes[k];
    method.setBytecodes(newBytecodes);
    fixExceptionHandlers(index, -len);
    fixLineNumbers(index, -len);
  }

  /**
   * Removes the current instruction from the stream.
   * Adjusts the exception tables, line number maps, etc. as necessary.
   * Assumes that it's called right after nextInstruction()
   * @see nextInstruction()
   */
  public void deleteInstruction() {
    int start = index() - 1;
    if (isWide() && getOpcode() != JBC_wide) start--;
    skipInstruction();
    int len = index() - start;
    reset(start);
    delete(len);
  }

  /**
   * Merges the given exception table with the existing exception table.
   * @param em additions to the exception table
   */
  public void addExceptionHandlers(VM_ExceptionHandlerMap em) {
    if (em == null) return;
    VM_Method method = method();
    VM_ExceptionHandlerMap ehm = method.getExceptionHandlerMap();
    if (ehm == null) {
      method.setExceptionHandlerMap(em);
      return;
    }
    int len = em.startPCs.length;
    int oldlen = ehm.startPCs.length;
    int[] startPCs   = new int[oldlen + len];
    int[] endPCs     = new int[oldlen + len];
    int[] handlerPCs = new int[oldlen + len];
    int[] cpiIndex   = new int[oldlen + len];
    VM_Type[] exceptionTypes = new VM_Type[oldlen + len];
    // prepend em to ehm
    for (int i = 0; i < len; i++) {
      startPCs[i] = em.startPCs[i];
      endPCs[i] = em.endPCs[i];
      handlerPCs[i] = em.handlerPCs[i];
      cpiIndex[i] = em.cpiIndex[i];
      exceptionTypes[i] = em.exceptionTypes[i];
    }
    for (int i = 0; i < oldlen; i++) {
      startPCs[i+len] = ehm.startPCs[i];
      endPCs[i+len] = ehm.endPCs[i];
      handlerPCs[i+len] = ehm.handlerPCs[i];
      cpiIndex[i+len] = ehm.cpiIndex[i];
      exceptionTypes[i+len] = ehm.exceptionTypes[i];
    }
    ehm.startPCs = startPCs;
    ehm.endPCs = endPCs;
    ehm.handlerPCs = handlerPCs;
    ehm.cpiIndex = cpiIndex;
    ehm.exceptionTypes = exceptionTypes;
  }

  /**
   * Merges the given line number map with the existing line number map.
   * @param lm additions to the line number map
   */
  public void addLineNumbers(VM_LineNumberMap lm) {
    if (lm == null) return;
    VM_Method method = method();
    VM_LineNumberMap lnm = method.getLineNumberMap();
    // If we didn't have line numbers before, we don't want them now
    // TODO: find out if this is correct
    if (lnm == null) return;
    int len = lm.startPCs.length;
    int oldlen = lnm.startPCs.length;
    int[] startPCs    = new int[oldlen + len];
    int[] lineNumbers = new int[oldlen + len];
    // invariant: both lnm and lm are sorted by startPCs
    int i = 0;
    int j = 0;
    // merge (straight out of the algorithms book)
    // invariant: k == i + j
    for (int k = 0; i < oldlen && j < len; k++) {
      if (lnm.startPCs[i] < lm.startPCs[j]) {
        startPCs[k] = lnm.startPCs[i];
        lineNumbers[k] = lnm.lineNumbers[i];
        i++;
      } else {
        startPCs[k] = lm.startPCs[j];
        lineNumbers[k] = lm.lineNumbers[j];
        j++;
      }
    }
    // only one of the following 2 loops will execute
    for (; i < oldlen; i++) {
      startPCs[i+j] = lnm.startPCs[i];
      lineNumbers[i+j] = lnm.lineNumbers[i];
    }
    for (; j < len; j++) {
      startPCs[i+j] = lm.startPCs[j];
      lineNumbers[i+j] = lm.lineNumbers[j];
    }
    lnm.startPCs = startPCs;
    lnm.lineNumbers = lineNumbers;
  }

  /**
   * Adjusts the local and stack sizes of the method as necessary.
   * @param lwords the number of local words
   * @param owords the number of operand words
   */
  private void fixSize(int lwords, int owords) {
    VM_Method method = method();
    // update stack and local sizes
    if (lwords > method.getLocalWords())
      method.setLocalWords(lwords);
    if (owords > method.getOperandWords())
      method.setOperandWords(owords);
  }

  /**
   * Adjusts the exception table.
   * @param index the index of bytecode insertion
   * @param len the length of inserted sequence
   * @param em additions to the exception table
   */
  private void fixExceptionHandlers(int index, int len) {
    if (len == 0) return;
    VM_Method method = method();
    VM_ExceptionHandlerMap ehm = method.getExceptionHandlerMap();
    if (ehm == null) return;
    int oldlen = ehm.startPCs.length;
    for (int i = 0; i < oldlen; i++)
      if (ehm.startPCs[i] >= index) ehm.startPCs[i] += len;
    for (int i = 0; i < oldlen; i++)
      if (ehm.endPCs[i] >= index) ehm.endPCs[i] += len;
    for (int i = 0; i < oldlen; i++)
      if (ehm.handlerPCs[i] >= index) ehm.handlerPCs[i] += len;
  }

  /**
   * Adjusts the line number map.
   * @param index the index of bytecode insertion
   * @param len the length of inserted sequence
   * @param lm additions to the line number map
   */
  private void fixLineNumbers(int index, int len) {
    if (len == 0) return;
    VM_Method method = method();
    VM_LineNumberMap lnm = method.getLineNumberMap();
    if (lnm == null) return;
    int i = 0;
    for (; i < lnm.startPCs.length && lnm.startPCs[i] < index; i++)
      ;
    for (; i < lnm.startPCs.length; i++)
      lnm.startPCs[i] += len;
  }
}

