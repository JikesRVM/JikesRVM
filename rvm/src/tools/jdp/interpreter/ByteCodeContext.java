/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
import com.ibm.JikesRVM.*;

/**
 * @author John Barton
 */
public class ByteCodeContext 
{
  private VM_Method method;
  private byte[] bcodes;
  private int    bindex;  // index to next byte in bcodes
  private int    instruction_index; // index to the byte in bcodes where the current instruction begins
  public static boolean traceByteCodes = false;

  ByteCodeContext(VM_Method method_in)
  {    
    method = method_in;
    bcodes = method.getBytecodes();
  }

  boolean hasMoreElements() { return bindex < bcodes.length; }

   public int getCurrentIndex() 
      {
      return instruction_index;
      }

   VM_Method getMethod()
      {
      return method;
      }

   ByteCode getByteCode(int bytecode_offset)
      {
      // bytecode_offset points to the next byte code to be fetched, 
      // we want the one we already fetched.
      //
      byte second = 0;
      if (bytecode_offset+1 < bcodes.length) second = bcodes[bytecode_offset+1];
      return new ByteCode(bcodes[bytecode_offset], second);
      }

  /** 
    *   Return the bytecode offset (0-based) for the catch clause
    *   that handles exceptions in the method of the type given as 
    *   the argument t
    *
    *    @param t the exception
    *    @return the index into the bytecodes were the handler starts 
    *    or -1 if there is no handler.
    */
  public int getHandlerOffsetForExceptionsMatching(Throwable t)
  {
    // VM_Type exception_type = VM_Magic.getObjectType(t);  
    try {
      VM_Class cls = InterpreterBase.forName(t.getClass().getName());
      VM_Type exception_type = (VM_Type) cls;
      return  method.findCatchBlockForBytecode(bindex, exception_type);
    } catch (VM_ResolutionException e) {
      return -1;
    }
  }

   public String toString()
      {
      // This is only going to be meaning full just before the fetch of a byte code.
      // Otherwise the index may point to some ConstantPool entry or other int.
      //
      return "ByteCodeContext at "+method+":"+getCurrentIndex()+" "+getByteCode(getCurrentIndex()).toString();
      }

    /* reading bytecodes */

   final int fetch1ByteSigned () {   return bcodes[bindex++];  }

   final int getNextCode() 
      {
      instruction_index = bindex;
      return fetch1ByteUnsigned();
      }

   final int fetch1ByteUnsigned () {    return bcodes[bindex++] & 0xFF;  }

   final int fetch2BytesSigned () 
      {
      int i = bcodes[bindex++] << 8;
      i |= (bcodes[bindex++] & 0xFF);
      return i;
      }
   final int fetch2BytesUnsigned () 
      {
      int i = (bcodes[bindex++] & 0xFF) << 8;
      i |= (bcodes[bindex++] & 0xFF);
      return i;
      }

   final int fetch4BytesSigned () 
      {
      int i = bcodes[bindex++] << 24;
      i |= (bcodes[bindex++] & 0xFF) << 16;
      i |= (bcodes[bindex++] & 0xFF) << 8;
      i |= (bcodes[bindex++] & 0xFF);
      return i;
      }
   final int fetch4BytesSigned (int index) 
      {
      int i = bcodes[index++] << 24;
      i |= (bcodes[index++] & 0xFF) << 16;
      i |= (bcodes[index++] & 0xFF) << 8;
      i |= (bcodes[index++] & 0xFF);
      return i;
      }

   final void takeBranch()
      {
      if (traceByteCodes) System.out.println("(branch taken)");
      int offset = fetch2BytesSigned();
      bindex += offset - 3;
      // trace
      }

   final void skipBranch()
      {
      if (traceByteCodes) System.out.println("(branch not taken)");
      bindex += 2;   
      }
   
   final void takeWideBranch()
      {
      if (traceByteCodes) System.out.println("(wide branch taken)");
      int offset = fetch4BytesSigned();
      bindex += offset - 3;
      // trace
      }

   final void tableSwitch(int val)
      {
      int start = bindex - 1;
      int align = bindex & 3;
      if (align != 0) bindex += 4-align; // eat padding
      int defaultoff = this.fetch4BytesSigned();
      int low = this.fetch4BytesSigned();
      if (val < low)  bindex = start + defaultoff;	    // below the lowest value in the table.
      else 
	 {
	 int high = this.fetch4BytesSigned();
	 if (val > high) 
	    {
	    // above the highest value in the table.
	    bindex = start + defaultoff;
	    } else {
	    // in the table.
	    bindex += (val - low) * 4;
	    int offset = this.fetch4BytesSigned();
	    bindex = start + offset;
	    }
	 }
      }
   final void lookupswitch(int val) 
      {
      if (traceByteCodes) System.out.println("lookupswitch");
      int start = bindex - 1;
      int align = bindex & 3;
      if (align != 0) bindex += 4-align; // eat padding
      int defaultoff = this.fetch4BytesSigned();
      int npairs = this.fetch4BytesSigned();
      
      // binary search.
      int first = 0;
      int last = npairs - 1;
      for (;;) 
	 {
	 if (first > last) 
	    {
            //  failure
	    bindex = start + defaultoff;
	    break;  
	    }
         // average of first and last
	 int current = (last + first) / 2;  
	 int match = this.fetch4BytesSigned(bindex + current * 8);
	 if (val < match) 
	    {
	    last = current - 1;
	    } 
	 else if (val > match) 
	    {
	    first = current + 1;
	    } 
	 else 
	    {
	    // we have a match.
	    int offset = this.fetch4BytesSigned(bindex + 4 + current * 8);
	    bindex = start + offset;
	    break;
	    }
	 }
      }

   final JumpSubroutineReturnOffset jumpSubroutine()
      {
      // Compute the index of the jsr itself, one behind the current index
      //
      int jsr_index = bindex - 1; 

      // Decode the new offset to the internal subroutine
      //
      int offset = fetch2BytesSigned();
      
      if (traceByteCodes) System.out.println("jsr from " + jsr_index + " to " + (jsr_index + offset));

      // Save the bytecode offset of the instruction following the jsr for the "ret".
      //
      JumpSubroutineReturnOffset return_offset = new JumpSubroutineReturnOffset(bindex);

      // Jump to the new offset.  
      //
      bindex = jsr_index + offset;

      return return_offset;
      }


   final JumpSubroutineReturnOffset jumpWideSubroutine()
      {
      // Compute the index of the jsr itself, one behind the current index
      //
      int jsr_index = bindex - 1; 

      // Decode the new offset to the internal subroutine
      //
      int offset = fetch4BytesSigned();
      
      if (traceByteCodes) System.out.println("jsr from " + jsr_index + " to " + (jsr_index + offset));

      // Save the bytecode offset of the instruction following the jsr for the "ret".
      //
      JumpSubroutineReturnOffset return_offset = new JumpSubroutineReturnOffset(bindex);

      // Jump to the new offset.  
      //
      bindex = jsr_index + offset;

      return return_offset;
      }

   final void returnSubroutine(Object jump_subroutine_return_offset)
      {
      // The object on the stack had better be a JumpSubroutineReturnOffset we push from jumpSubroutine().
      //
      JumpSubroutineReturnOffset offset = (JumpSubroutineReturnOffset)jump_subroutine_return_offset;
      bindex = offset.getBytecodeOffset();
      }

   final void jumpException(int offset)
      {
      bindex = offset;
      }

    }
