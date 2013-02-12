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
package org.mmtk.harness.lang.parser;

import org.mmtk.harness.lang.ast.IntrinsicMethod;
import org.mmtk.harness.lang.runtime.ObjectValue;
import org.mmtk.harness.lang.runtime.PhantomReferenceValue;
import org.mmtk.harness.lang.runtime.SoftReferenceValue;
import org.mmtk.harness.lang.runtime.WeakReferenceValue;
import org.mmtk.harness.lang.type.Type;

/**
 * The global definitions passed around by the parser.
 * <p>
 * The initializer defines the pre-defined types and intrinsic methods.
 */
public class GlobalDefs {

  /**
   * The types - predeclared ones are passed to the constructor
   */
  public final TypeTable types = new TypeTable(Type.INT,Type.STRING,Type.BOOLEAN,
      Type.OBJECT,Type.VOID,Type.WEAKREF,Type.SOFTREF,Type.PHANTOMREF);

  private final String intrinsics = "org.mmtk.harness.lang.Intrinsics";

  /**
   * The methods
   */
  public final MethodTable methods = new MethodTable(
      new IntrinsicMethod("gc",intrinsics,"gc"),
      new IntrinsicMethod("tid",intrinsics,"threadId"),
      new IntrinsicMethod("hash",intrinsics,"hash", new Class<?>[] { ObjectValue.class }),
      new IntrinsicMethod("random",intrinsics,"random",
          new Class<?>[] { int.class, int.class }),
      new IntrinsicMethod("setSeed",intrinsics,"setRandomSeed", new Class<?>[] { int.class }),
      new IntrinsicMethod("heapDump",intrinsics,"heapDump"),
      new IntrinsicMethod("weakRef",intrinsics,"weakRef",
          new Class<?>[] { ObjectValue.class }),
      new IntrinsicMethod("getWeakReferent",intrinsics,"getReferent",
          new Class<?>[] { WeakReferenceValue.class }),
      new IntrinsicMethod("softRef",intrinsics,"softRef",
          new Class<?>[] { ObjectValue.class }),
      new IntrinsicMethod("getSoftReferent",intrinsics,"getReferent",
          new Class<?>[] { SoftReferenceValue.class }),
      new IntrinsicMethod("phantomRef",intrinsics,"phantomRef",
          new Class<?>[] { ObjectValue.class }),
      new IntrinsicMethod("getPhantomReferent",intrinsics,"getReferent",
          new Class<?>[] { PhantomReferenceValue.class }),
      new IntrinsicMethod("setOption",intrinsics,"setOption", new Class[] { String.class }),
      new IntrinsicMethod("barrierWait",intrinsics,"barrierWait",
          new Class[] { String.class, int.class })

  );
}
