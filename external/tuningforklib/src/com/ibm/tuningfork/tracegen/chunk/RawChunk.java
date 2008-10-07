/*
 * This file is part of the Tuning Fork Visualization Platform
 *  (http://sourceforge.net/projects/tuningforkvp)
 *
 * Copyright (c) 2005 - 2008 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 */

package com.ibm.tuningfork.tracegen.chunk;

import java.io.IOException;
import java.io.OutputStream;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;

/*
 * There are 3 basic operations: (1) Closing a chunk which involves fixing up
 * data that cannot be determined initially such as the length field. Subclasses
 * should override this at each level and call the superclass's close(). The
 * base implementation will limit and reset the buffer position so overrides
 * need to put the position at the end of data. (2) Writing the data out. This
 * method is not overrideable as it simply means to write out the contents of
 * the buffer. The cursor position will be reset to zero. The entire chunk is
 * written out and the position is undefined on return. (3) Reset which allows
 * the buffer object to be reused. Subclasses that can actually be reset should
 * define a reset method and use the resetImpl method here.
 */
@Uninterruptible
public abstract class RawChunk {

    public final static int ENCODING_SPACE_INT = 4;
    public final static int ENCODING_SPACE_LONG = 8;
    public final static int ENCODING_SPACE_DOUBLE = 8;

    private final byte[] data;
    private int cursor = 0;
    private boolean open = true;

    protected RawChunk(byte[] buffer) {
	data = buffer;
    }

    protected RawChunk(int capacity) {
	this(new byte[capacity]);
    }

    public void close() {
	if (!open) {
	    VM.sysWriteln("RawChunk: Cannot close a closed chunk.");
	}
	open = false;
    }

    /* Synchronous */
    @Interruptible
    public final void write(OutputStream outputStream) throws IOException {
	outputStream.write(data, 0, cursor);
    }

    protected void resetImpl() {
	cursor = 0;
	open = true;
    }

    protected int getPosition() {
	return cursor;
    }

    protected void seek(int pos) {
	cursor = pos;
    }

    protected final boolean hasRoom(int bytes) {
	int remaining = data.length - cursor;
	return remaining >= bytes;
    }

    protected final boolean addLong(long l) {
	if (!hasRoom(ENCODING_SPACE_LONG)) {
	    return false;
	}
	putLong(l);
	return true;
    }

    protected final void addLongUnchecked(long l) {
      putLong(l);
    }

    protected final boolean addDouble(double d) {
	if (!hasRoom(ENCODING_SPACE_DOUBLE)) {
	    return false;
	}
	putLong(Magic.doubleAsLongBits(d));
	return true;
    }

    protected final void addDoubleUnchecked(double d) {
      putLong(Magic.doubleAsLongBits(d));
    }

    protected final boolean addInt(int i) {
	if (!hasRoom(ENCODING_SPACE_INT)) {
	    return false;
	}
	putInt(i);
	return true;
    }

    protected final void addIntUnchecked(int i) {
      putInt(i);
    }

    protected final boolean addByte(byte b) {
	if (!hasRoom(1)) {
	    return false;
	}
	data[cursor++] = b;
	return true;
    }

    protected final boolean addString(String str) {
	int strLen = JikesRVMSupport.getStringLength(str);
	int minimalSize = ENCODING_SPACE_INT + strLen;
	if (!hasRoom(minimalSize)) {
	    return false;
	}
        char[] backingChars = JikesRVMSupport.getBackingCharArray(str);
	return addStringInternal(backingChars);
    }

    /*
     * Write String's char[] encoded as UTF-8.
     * Table from http://tools.ietf.org/html/rfc3629
     *
     *    Char. number range  |        UTF-8 octet sequence
     *       (hexadecimal)    |              (binary)
     *    --------------------+---------------------------------------------
     *    0000 0000-0000 007F | 0xxxxxxx
     *    0000 0080-0000 07FF | 110xxxxx 10xxxxxx
     *    0000 0800-0000 FFFF | 1110xxxx 10xxxxxx 10xxxxxx
     *    0001 0000-0010 FFFF | 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
     */
    protected boolean addStringInternal(char[] backingChars) {
      int startCursor = cursor;
      int strLen = backingChars.length;
      cursor += ENCODING_SPACE_INT;

      for (int i=0; i<strLen; i++) {
          char c = backingChars[i];
          if (c <= 0x7f) {
      	data[cursor++] = (byte)(c);
          } else if (c <= 0x7ff) {
      	if (!hasRoom(1 + (strLen-i))) {
      	    cursor = startCursor;
      	    return false;
      	}
      	data[cursor++] = (byte)(0xc0 | (c >> 6));
      	data[cursor++] = (byte)(0x80 | (c & 0x3f));
          } else {
      	if (!hasRoom(2 + (strLen-i))) {
      	    cursor = startCursor;
      	    return false;
      	}
      	data[cursor++] = (byte)(0xe0 | (c >> 12));
      	data[cursor++] = (byte)(0x80 | ((c & 0xfc0) >> 6));
      	data[cursor++] = (byte)(0x80 | (c & 0x3f));
          }
      }
      int endCursor = cursor;
      int finalLen = endCursor-startCursor-ENCODING_SPACE_INT;
      cursor = startCursor;
      putInt(finalLen);
      cursor = endCursor;
      return true;
    }

    @Inline(value=Inline.When.AllArgumentsAreConstant)
    private void putLong(long value) {
	data[cursor + 0] = (byte) ((value >> 56) & 0xff);
	data[cursor + 1] = (byte) ((value >> 48) & 0xff);
	data[cursor + 2] = (byte) ((value >> 40) & 0xff);
	data[cursor + 3] = (byte) ((value >> 32) & 0xff);
	data[cursor + 4] = (byte) ((value >> 24) & 0xff);
	data[cursor + 5] = (byte) ((value >> 16) & 0xff);
	data[cursor + 6] = (byte) ((value >> 8) & 0xff);
	data[cursor + 7] = (byte) ((value >> 0) & 0xff);
	cursor += 8;
    }

    @Inline(value=Inline.When.AllArgumentsAreConstant)
    private void putInt(int value) {
	data[cursor + 0] = (byte) ((value >> 24) & 0xff);
	data[cursor + 1] = (byte) ((value >> 16) & 0xff);
	data[cursor + 2] = (byte) ((value >> 8) & 0xff);
	data[cursor + 3] = (byte) ((value >> 0) & 0xff);
	cursor += 4;
    }

    @Inline(value=Inline.When.AllArgumentsAreConstant)
    protected void putIntAt(int index, int value) {
        data[index + 0] = (byte) ((value >> 24) & 0xff);
        data[index + 1] = (byte) ((value >> 16) & 0xff);
        data[index + 2] = (byte) ((value >> 8) & 0xff);
        data[index + 3] = (byte) ((value >> 0) & 0xff);
    }

}
