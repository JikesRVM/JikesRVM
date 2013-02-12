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

import org.jikesrvm.VM;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public abstract class Chunk extends RawChunk {

    private static final int MAGIC_WORD_1 = 0xdeadbeef;
    private static final int MAGIC_WORD_2 = 0xcafebabe;
    private static final int LENGTH_OFFSET = 8;
    private static final int CHUNK_TYPE_OFFSET = 12;
    protected static final int DATA_OFFSET = 16;

    protected final static int DEFAULT_CHUNK_SIZE = 16 * 1024;

    protected Chunk(int chunkType, byte[] buffer) {
	super(buffer);
	addInt(MAGIC_WORD_1);
	addInt(MAGIC_WORD_2);
	seek(CHUNK_TYPE_OFFSET);
	addInt(chunkType);
	seek(DATA_OFFSET);
    }

    protected Chunk(int chunkType, int capacity) {
	this(chunkType, new byte[capacity]);
    }

    protected Chunk(int chunkType) {
	this(chunkType, new byte[DEFAULT_CHUNK_SIZE]);
    }

    @Override
    public void close() {
	int pos = getPosition();
	int bodyLength = pos - DATA_OFFSET;
	putIntAt(LENGTH_OFFSET, bodyLength);
	super.close();
    }

    @Override
    protected void resetImpl() {
      super.resetImpl();
      seek(DATA_OFFSET);
    }

    @Interruptible
    protected char[] getChars(String s) {
      if (VM.runningVM) {
        return JikesRVMSupport.getBackingCharArray(s);
      } else {
        return s.toCharArray();
      }
    }
}
