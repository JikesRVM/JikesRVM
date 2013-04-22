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

import java.util.ArrayList;
import java.util.List;

import org.mmtk.harness.lang.Intrinsics;
import org.mmtk.harness.lang.ast.IntrinsicMethod;
import org.mmtk.harness.lang.ast.Method;
import org.mmtk.harness.lang.ast.OptionDef;
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

  private final String INTRINSICS = Intrinsics.class.getName();

  /**
   * The methods.  Initialized with declarations of all the built-in intrinsics.
   */
  public final MethodTable methods = new MethodTable(
      new IntrinsicMethod("gc",INTRINSICS,"gc"),
      new IntrinsicMethod("gcCount",INTRINSICS,"gcCount"),
      new IntrinsicMethod("tid",INTRINSICS,"threadId"),
      new IntrinsicMethod("hash",INTRINSICS,"hash",
          new Class<?>[] { ObjectValue.class }),
      new IntrinsicMethod("random",INTRINSICS,"random",
          new Class<?>[] { int.class, int.class }),
      new IntrinsicMethod("setSeed",INTRINSICS,"setRandomSeed",
          new Class<?>[] { int.class }),
      new IntrinsicMethod("heapDump",INTRINSICS,"heapDump"),
      new IntrinsicMethod("weakRef",INTRINSICS,"weakRef",
          new Class<?>[] { ObjectValue.class }),
      new IntrinsicMethod("getWeakReferent",INTRINSICS,"getReferent",
          new Class<?>[] { WeakReferenceValue.class }),
      new IntrinsicMethod("softRef",INTRINSICS,"softRef",
          new Class<?>[] { ObjectValue.class }),
      new IntrinsicMethod("getSoftReferent",INTRINSICS,"getReferent",
          new Class<?>[] { SoftReferenceValue.class }),
      new IntrinsicMethod("phantomRef",INTRINSICS,"phantomRef",
          new Class<?>[] { ObjectValue.class }),
      new IntrinsicMethod("getPhantomReferent",INTRINSICS,"getReferent",
          new Class<?>[] { PhantomReferenceValue.class }),
      new IntrinsicMethod("setOption",INTRINSICS,"setOption",
          new Class[] { String.class }),
      new IntrinsicMethod("barrierWait",INTRINSICS,"barrierWait",
          new Class[] { String.class, int.class })

  );

  /**
   * The options defined by the script
   */
  private final List<OptionDef> options = new ArrayList<OptionDef>();

  public void declare(OptionDef option) {
    options.add(option);
  }

  public void declare(Method method) {
    methods.add(method);
  }

  public void declare(Type type) {
    types.add(type);
  }

  /**
   * @return The types available in this script
   */
  public TypeTable getTypes() {
    return types;
  }

  /**
   * The methods of this script
   * @return
   */
  public MethodTable getMethods() {
    return methods;
  }

  /**
   * The options defined in this script
   * @return
   */
  public List<OptionDef> getOptions() {
    return options;
  }


}
