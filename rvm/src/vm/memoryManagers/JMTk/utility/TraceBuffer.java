/*
 * (C) Copyright Department of Computer Science,
 *     University of Massachusetts, Amherst. 2003.
 */
package org.mmtk.utility;

import org.mmtk.vm.Constants;
import org.mmtk.vm.VM_Interface;

import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
/**
 * This supports <i>unsynchronized</i> enqueuing and dequeuing of tracing data
 * and bulk processing of the buffer.
 *
 * @author <a href="http://www-ali.cs.umass.edu/~hertz">Matthew Hertz</a>
 * @version $Revision$
 * @date $Date$
 */ 
public class TraceBuffer extends LocalQueue 
  implements Constants, TracingConstants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 
 
  /***********************************************************************
   *
   * Class based constants
   */
  private static final VM_Word TRACE_NEW_RECORD = VM_Word.fromInt(3);
  private static final VM_Word TRACE_ALLOC_SIZE = VM_Word.fromInt(5);
  private static final VM_Word TRACE_ALLOC_NAME = VM_Word.fromInt(6);
  private static final VM_Word TRACE_ALLOC_FP = VM_Word.fromInt(7);
  private static final VM_Word TRACE_ALLOC_THREAD = VM_Word.fromInt(9);
  private static final VM_Word TRACE_TIB_VALUE = VM_Word.fromInt(10);
  private static final VM_Word TRACE_DEATH_TIME = VM_Word.fromInt(11);
  private static final VM_Word TRACE_FIELD_TARGET = VM_Word.fromInt(12);
  private static final VM_Word TRACE_ARRAY_TARGET = VM_Word.fromInt(13);
  private static final VM_Word TRACE_FIELD_SLOT = VM_Word.fromInt(14);
  private static final VM_Word TRACE_ARRAY_ELEMENT = VM_Word.fromInt(15);
  private static final VM_Word TRACE_STATIC_TARGET = VM_Word.fromInt(17);
  private static final VM_Word TRACE_BOOT_ALLOC_SIZE = VM_Word.fromInt(18);

  /***********************************************************************
   *
   * Instance fields
   */
  private SortSharedDeque tracePool;

  /***********************************************************************
   *
   * Public methods
   */

  /**
   * Constructor
   *
   * @param queue The shared queue to which this queue will append
   * its buffers (when full or flushed) and from which it will aquire new
   * buffers when it has exhausted its own.
   */
  TraceBuffer(SharedDeque pool) {
    super(pool);
  }

  /**
   * Push word onto the tracing queue.
   *
   * @param i The data to be pushed onto the tracing queue
   */
  public final void push(VM_Word i) throws VM_PragmaInline {
    checkTailInsert(1);
    uncheckedTailInsert(i.toAddress());
  }

  /**
   * Process the data in the tracing buffer, output information as needed.
   */
  public final void process() {
    VM_Word traceState = TRACE_NEW_RECORD;
    int entriesNotFlushed = 0;
    /* First we must flush any remaining data */
    Log.writeln();
    
    /* Process through the entire buffer. */
    while (checkDequeue(1)) {
      /* For speed and efficiency, we will actually process the data buffer by 
	 buffer and not by dequeue-ing each entry. */
      while (!bufferOffset(head).isZero()) {
	head = head.sub(BYTES_IN_ADDRESS);
	VM_Word val = VM_Magic.getMemoryWord(head);
	if (traceState.EQ(TRACE_NEW_RECORD)) {
	  if (val.EQ(TRACE_GCSTART)) {
	    Log.write('G');
	    Log.write('C');
	    Log.writeln('B', true);
	  } else if (val.EQ(TRACE_GCEND)) {
	    Log.write('G');
	    Log.write('C');
	    Log.writeln('E', true);
	  } else {
	    traceState = val;
	  }
	} else {
	  if (traceState.EQ(TRACE_EXACT_ALLOC) ||
	      traceState.EQ(TRACE_ALLOC)) {
	    Log.write( (traceState.EQ(TRACE_EXACT_ALLOC)) ? 'A' : 'a');
	    Log.write(' ');
	    Log.write(val);
	    traceState = TRACE_ALLOC_SIZE;
	  } else if (traceState.EQ(TRACE_EXACT_IMMORTAL_ALLOC) ||
		     traceState.EQ(TRACE_IMMORTAL_ALLOC)) {
	    Log.write( (traceState.EQ(TRACE_EXACT_IMMORTAL_ALLOC)) ? 'I' : 'i');
	    Log.write(' ');
	    Log.write(val);
	    traceState = TRACE_ALLOC_SIZE;
	  } else if (traceState.EQ(TRACE_BOOT_ALLOC)) {
	    Log.write('B');
	    Log.write(' ');
	    Log.write(val);
	    traceState = TRACE_BOOT_ALLOC_SIZE;
	  } else if (traceState.EQ(TRACE_DEATH)) {
	    Log.write('D');
	    Log.write(' ');
	    Log.write(val);
	    traceState = TRACE_DEATH_TIME;
	  } else if (traceState.EQ(TRACE_BOOT_ALLOC_SIZE)) {
 	    Log.write(val);
	    traceState = TRACE_NEW_RECORD;
	  } else if (traceState.EQ(TRACE_ALLOC_SIZE)) {
	    Log.write(val);
	    traceState = TRACE_ALLOC_FP;
	  } else if (traceState.EQ(TRACE_ALLOC_FP)) {
	    Log.write(val);
	    traceState = TRACE_ALLOC_THREAD;
	  } else if (traceState.EQ(TRACE_ALLOC_THREAD)) {
	    Log.write(val);
	    traceState = TRACE_NEW_RECORD;
	  } else if (traceState.EQ(TRACE_TIB_SET)) {
	    Log.write('T');
	    Log.write(' ');
	    Log.write(val);
	    traceState = TRACE_TIB_VALUE;
	  } else if (traceState.EQ(TRACE_STATIC_SET)) {
	    Log.write('S');
	    Log.write(' ');
	    Log.write(val);
	    traceState = TRACE_STATIC_TARGET;
	  } else if (traceState.EQ(TRACE_TIB_VALUE) ||
		     traceState.EQ(TRACE_DEATH_TIME) ||
		     traceState.EQ(TRACE_STATIC_TARGET)) {
	    Log.write(val);
	    traceState = TRACE_NEW_RECORD;
	  } else if (traceState.EQ(TRACE_FIELD_SET) || 
		     traceState.EQ(TRACE_ARRAY_SET)) {
	    Log.write('U');
	    Log.write(' ');
	    Log.write(val);
	    traceState = TRACE_FIELD_SLOT;
	  } else if (traceState.EQ(TRACE_FIELD_TARGET) || 
		     traceState.EQ(TRACE_ARRAY_TARGET)) {
	    Log.write(val);
	    traceState = TRACE_NEW_RECORD;
	  } else if (traceState.EQ(TRACE_FIELD_SLOT) ||
		     traceState.EQ(TRACE_ARRAY_ELEMENT)) {
	    Log.write(val);
	    traceState = TRACE_FIELD_TARGET;
	  } else
	    VM_Interface.sysFail("Cannot understand directive!\n");
	  if (traceState.EQ(TRACE_NEW_RECORD)) {
	    entriesNotFlushed++;
	    Log.writeln();
	  } else {
	    Log.write(' ');
	  }
	}
	if (entriesNotFlushed == 10) {
	  Log.flush();
	  entriesNotFlushed = 0;
	}
      }
    }
    resetLocal();
  }
}
