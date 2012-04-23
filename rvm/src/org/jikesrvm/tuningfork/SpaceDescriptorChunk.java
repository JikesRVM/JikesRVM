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

package org.jikesrvm.tuningfork;

import org.mmtk.policy.Space;
import org.mmtk.policy.Space.SpaceVisitor;
import org.mmtk.utility.Constants;

import com.ibm.tuningfork.tracegen.chunk.Chunk;

/**
 * A chunk to encode Space index/Space name mappings.
 */
public class SpaceDescriptorChunk extends Chunk {
  private static final int SPACE_DESCRIPTOR_CHUNK_ID = (64 * 1024) + 100;

  public static final int SPACE_DESCRIPTOR_COUNT_OFFSET = DATA_OFFSET;
  public static final int SPACE_DESCRIPTOR_DATA_OFFSET = SPACE_DESCRIPTOR_COUNT_OFFSET + ENCODING_SPACE_INT;

  private int numSpaces;

  SpaceDescriptorChunk() {
    super(SPACE_DESCRIPTOR_CHUNK_ID);

    /* Write mapping of space ids to logical space names */
    seek(SPACE_DESCRIPTOR_DATA_OFFSET);
    numSpaces = 0;
    Space.visitSpaces(new SpaceVisitor() {
      @Override
      public void visit(Space s) {
        numSpaces++;
        addInt(s.getIndex());
        addString(s.getName());
      }
    });
    int pos = getPosition();
    seek(SPACE_DESCRIPTOR_COUNT_OFFSET);
    addInt(numSpaces);
    seek(pos);

    /* Pages per byte */
    addInt(Constants.BYTES_IN_PAGE);

    /* Possible heap range */
    /* TODO: Horrific 32 bit assumption.
     *       Matches equally bogus assumption in event definitions that use
     *       an int to pass addresses.
     *       MUST FIX BEFORE MERGING TO TRUNK!
     */
    addInt(org.mmtk.vm.VM.HEAP_START.toInt());
    addInt(org.mmtk.vm.VM.HEAP_END.toInt());

    close();
  }

}
