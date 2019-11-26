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

package org.jikesrvm.classloader;

import static org.jikesrvm.VM.NOT_REACHED;
import static org.jikesrvm.classloader.ClassLoaderConstants.*;
import static org.jikesrvm.classloader.ConstantPool.CP_CLASS;
import static org.jikesrvm.classloader.ConstantPool.CP_DOUBLE;
import static org.jikesrvm.classloader.ConstantPool.CP_FLOAT;
import static org.jikesrvm.classloader.ConstantPool.CP_INT;
import static org.jikesrvm.classloader.ConstantPool.CP_LONG;
import static org.jikesrvm.classloader.ConstantPool.CP_MEMBER;
import static org.jikesrvm.classloader.ConstantPool.CP_STRING;
import static org.jikesrvm.classloader.ConstantPool.CP_UTF;
import static org.jikesrvm.runtime.JavaSizeConstants.BITS_IN_SHORT;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.jikesrvm.VM;
import org.jikesrvm.classlibrary.ClassLibraryHelpers;
import org.jikesrvm.runtime.Statics;

/**
 * Support code to parse a DataInputStream in the Java classfile format
 * and create the appropriate instance of an RVMClass or UnboxedType.
 */
public class ClassFileReader {

  // Constant pool entry tags.
  //
  public static final byte TAG_UTF = 1;
  public static final byte TAG_UNUSED = 2;
  public static final byte TAG_INT = 3;
  public static final byte TAG_FLOAT = 4;
  public static final byte TAG_LONG = 5;
  public static final byte TAG_DOUBLE = 6;
  public static final byte TAG_TYPEREF = 7;
  public static final byte TAG_STRING = 8;
  public static final byte TAG_FIELDREF = 9;
  public static final byte TAG_METHODREF = 10;
  public static final byte TAG_INTERFACE_METHODREF = 11;
  public static final byte TAG_MEMBERNAME_AND_DESCRIPTOR = 12;

  private InputStream inputStream;
  private LoggingInputStream loggingStream;

  public ClassFileReader(InputStream inputStream) {
    this.inputStream = inputStream;
  }

  /**
   * Parse and return the constant pool in a class file
   * @param typeRef the canonical type reference for this type.
   * @param input the data stream from which to read the class's description.
   * @return constant pool as int array
   * @throws IOException if it occurs during reading of the input stream
   */
  private int[] readConstantPool(TypeReference typeRef, DataInputStream input)  throws ClassFormatError, IOException {

    int magic = input.readInt();
    if (magic != 0xCAFEBABE) {
      throw new ClassFormatError("bad magic number " + Integer.toHexString(magic));
    }

    // Get the class file version number and check to see if it is a version
    // that we support.
    int minor = input.readUnsignedShort();
    int major = input.readUnsignedShort();
    switch (major) {
      case 45:
      case 46:
      case 47:
      case 48:
      case 49: // we support all variants of these major versions so the minor number doesn't matter.
        break;
      case 50: // we only support up to 50.0 (ie Java 1.6.0)
        if (minor == 0) break;
      default:
        throw new UnsupportedClassVersionError("unsupported class file version " + major + "." + minor + " for type " + typeRef);
    }

    //
    // pass 1: read constant pool
    //
    int[] constantPool = new int[input.readUnsignedShort()];
    byte[] tmpTags = new byte[constantPool.length];

    // note: slot 0 is unused
    for (int i = 1; i < constantPool.length; i++) {
      tmpTags[i] = input.readByte();
      switch (tmpTags[i]) {
        case TAG_UTF: {
          byte[] utf = new byte[input.readUnsignedShort()];
          input.readFully(utf);
          int atomId = Atom.findOrCreateUtf8Atom(utf).getId();
          constantPool[i] = ConstantPool.packCPEntry(CP_UTF, atomId);
          break;
        }
        case TAG_UNUSED:
          if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
          break;

        case TAG_INT: {
          int literal = input.readInt();
          int offset = Statics.findOrCreateIntSizeLiteral(literal);
          constantPool[i] = ConstantPool.packCPEntry(CP_INT, offset);
          break;
        }
        case TAG_FLOAT: {
          int literal = input.readInt();
          int offset = Statics.findOrCreateIntSizeLiteral(literal);
          constantPool[i] = ConstantPool.packCPEntry(CP_FLOAT, offset);
          break;
        }
        case TAG_LONG: {
          long literal = input.readLong();
          int offset = Statics.findOrCreateLongSizeLiteral(literal);
          constantPool[i] = ConstantPool.packCPEntry(CP_LONG, offset);
          i++;
          break;
        }
        case TAG_DOUBLE: {
          long literal = input.readLong();
          int offset = Statics.findOrCreateLongSizeLiteral(literal);
          constantPool[i] = ConstantPool.packCPEntry(CP_DOUBLE, offset);
          i++;
          break;
        }
        case TAG_TYPEREF:
          constantPool[i] = input.readUnsignedShort();
          break;

        case TAG_STRING:
          constantPool[i] = input.readUnsignedShort();
          break;

        case TAG_FIELDREF:
        case TAG_METHODREF:
        case TAG_INTERFACE_METHODREF: {
          int classDescriptorIndex = input.readUnsignedShort();
          int memberNameAndDescriptorIndex = input.readUnsignedShort();
          constantPool[i] = ConstantPool.packTempCPEntry(classDescriptorIndex, memberNameAndDescriptorIndex);
          break;
        }

        case TAG_MEMBERNAME_AND_DESCRIPTOR: {
          int memberNameIndex = input.readUnsignedShort();
          int descriptorIndex = input.readUnsignedShort();
          constantPool[i] = ConstantPool.packTempCPEntry(memberNameIndex, descriptorIndex);
          break;
        }

        default:
          throw new ClassFormatError("bad constant pool");
      }
    }

    //
    // pass 2: post-process type and string constant pool entries
    // (we must do this in a second pass because of forward references)
    //
    try {
      for (int i = 1; i < constantPool.length; i++) {
        switch (tmpTags[i]) {
          case TAG_LONG:
          case TAG_DOUBLE:
            ++i;
            break;

          case TAG_TYPEREF: { // in: utf index
            Atom typeName = ConstantPool.getUtf(constantPool, constantPool[i]);
            int typeRefId =
                TypeReference.findOrCreate(typeRef.getClassLoader(), typeName.descriptorFromClassName()).getId();
            constantPool[i] = ConstantPool.packCPEntry(CP_CLASS, typeRefId);
            break;
          } // out: type reference id

          case TAG_STRING: { // in: utf index
            Atom literal = ConstantPool.getUtf(constantPool, constantPool[i]);
            int offset = literal.getStringLiteralOffset();
            constantPool[i] = ConstantPool.packCPEntry(CP_STRING, offset);
            break;
          } // out: jtoc slot number
        }
      }
    } catch (java.io.UTFDataFormatException x) {
      ClassFormatError error = new ClassFormatError(x.toString());
      error.initCause(x);
      throw error;
    }

    //
    // pass 3: post-process type field and method constant pool entries
    //
    for (int i = 1; i < constantPool.length; i++) {
      switch (tmpTags[i]) {
        case TAG_LONG:
        case TAG_DOUBLE:
          ++i;
          break;

        case TAG_FIELDREF:
        case TAG_METHODREF:
        case TAG_INTERFACE_METHODREF: { // in: classname+membername+memberdescriptor indices
          int bits = constantPool[i];
          int classNameIndex = ConstantPool.unpackTempCPIndex1(bits);
          int memberNameAndDescriptorIndex = ConstantPool.unpackTempCPIndex2(bits);
          int memberNameAndDescriptorBits = constantPool[memberNameAndDescriptorIndex];
          int memberNameIndex = ConstantPool.unpackTempCPIndex1(memberNameAndDescriptorBits);
          int memberDescriptorIndex = ConstantPool.unpackTempCPIndex2(memberNameAndDescriptorBits);

          TypeReference tref = ConstantPool.getTypeRef(constantPool, classNameIndex);
          Atom memberName = ConstantPool.getUtf(constantPool, memberNameIndex);
          Atom memberDescriptor = ConstantPool.getUtf(constantPool, memberDescriptorIndex);
          MemberReference mr = MemberReference.findOrCreate(tref, memberName, memberDescriptor);
          int mrId = mr.getId();
          constantPool[i] = ConstantPool.packCPEntry(CP_MEMBER, mrId);
          break;
        } // out: MemberReference id
      }
    }
    return constantPool;
  }

  /**
   * Read the class' TypeReference
   * @param typeRef the type reference that we're expecting to read
   * @param input input stream
   * @param constantPool constant pool
   * @return the constantPool index of the typeRef of the class we are reading
   * @throws IOException when a problems occurs while reading the input stream
   * @throws ClassFormatError when the read type ref does not match up with the expected type ref
   */
  private int readTypeRef(TypeReference typeRef, DataInputStream input, int[] constantPool) throws IOException, ClassFormatError {
    int myTypeIndex = input.readUnsignedShort();
    TypeReference myTypeRef = ConstantPool.getTypeRef(constantPool, myTypeIndex);
    if (myTypeRef != typeRef) {
      // eg. file contains a different class than would be
      // expected from its .class file name
      if (!VM.VerifyAssertions) {
        throw new ClassFormatError("expected class \"" +
                                   typeRef.getName() +
                                   "\" but found \"" +
                                   myTypeRef.getName() +
                                   "\"");
      } else {
        throw new ClassFormatError("expected class \"" +
                                   typeRef.getName() +
                                   "\" but found \"" +
                                   myTypeRef.getName() +
                                   "\"\n" + typeRef + " != " + myTypeRef);
      }
    }
    return myTypeIndex;
  }

  private RVMClass readSuperClass(DataInputStream input, int[] constantPool,
      short modifiers) throws IOException, NoClassDefFoundError {
    TypeReference superType = ConstantPool.getTypeRef(constantPool, input.readUnsignedShort()); // possibly null
    RVMClass superClass = null;
    if (((modifiers & ACC_INTERFACE) == 0) && (superType != null)) {
      superClass = superType.resolve().asClass();
    }
    return superClass;
  }

  private RVMClass[] readDeclaredInterfaces(DataInputStream input, int[] constantPool) throws IOException, NoClassDefFoundError {
    int numInterfaces = input.readUnsignedShort();
    RVMClass[] declaredInterfaces;
    if (numInterfaces == 0) {
      declaredInterfaces = RVMType.emptyVMClass;
    } else {
      declaredInterfaces = new RVMClass[numInterfaces];
      for (int i = 0; i < numInterfaces; ++i) {
        TypeReference inTR = ConstantPool.getTypeRef(constantPool, input.readUnsignedShort());
        declaredInterfaces[i] = inTR.resolve().asClass();
      }
    }
    return declaredInterfaces;
  }

  private RVMField[] readDeclaredFields(TypeReference typeRef, DataInputStream input, int[] constantPool) throws IOException {
    int numFields = input.readUnsignedShort();
    RVMField[] declaredFields;
    if (numFields == 0) {
      declaredFields = RVMType.emptyVMField;
    } else {
      declaredFields = new RVMField[numFields];
      for (int i = 0; i < numFields; i++) {
        short fmodifiers = input.readShort();
        Atom fieldName = ConstantPool.getUtf(constantPool, input.readUnsignedShort());
        Atom fieldDescriptor = ConstantPool.getUtf(constantPool, input.readUnsignedShort());
        if (typeRef == TypeReference.JavaLangSystem &&
            (fmodifiers & (ACC_STATIC | ACC_FINAL | ACC_PUBLIC)) == (ACC_STATIC | ACC_FINAL | ACC_PUBLIC)) {
          /* We have to stop System.in .out and .err fields from being final! */
          fmodifiers -= ACC_FINAL;
        }
        MemberReference memRef = MemberReference.findOrCreate(typeRef, fieldName, fieldDescriptor);
        declaredFields[i] = RVMField.readField(typeRef, constantPool, memRef, fmodifiers, input, loggingStream);
      }
    }
    declaredFields = ClassLibraryHelpers.modifyDeclaredFields(declaredFields, typeRef);
    return declaredFields;
  }

  private RVMMethod[] readDeclaredMethods(TypeReference typeRef, DataInputStream input, int[] constantPool) throws IOException {
    int numMethods = input.readUnsignedShort();
    RVMMethod[] declaredMethods;
    if (numMethods == 0) {
      declaredMethods = RVMType.emptyVMMethod;
    } else {
      declaredMethods = new RVMMethod[numMethods];
      for (int i = 0; i < numMethods; i++) {
        short mmodifiers = input.readShort();
        Atom methodName = ConstantPool.getUtf(constantPool, input.readUnsignedShort());
        Atom methodDescriptor = ConstantPool.getUtf(constantPool, input.readUnsignedShort());
        MemberReference memRef = MemberReference.findOrCreate(typeRef, methodName, methodDescriptor);
        RVMMethod method = readMethod(typeRef, constantPool, memRef, mmodifiers, input);
        declaredMethods[i] = method;
      }
    }
    return declaredMethods;
  }

  /**
   * Creates an instance of a RVMMethod by reading the relevant data from the
   * argument bytecode stream.
   *
   * @param declaringClass the TypeReference of the class being loaded
   * @param constantPool the constantPool of the RVMClass object that's being constructed
   * @param memRef the canonical memberReference for this member.
   * @param modifiers modifiers associated with this member.
   * @param input the DataInputStream to read the method's attributes from
   * @throws IOException when the underlying stream throws an IOException or when
   *  the skipping of attributes does not work as expected
   * @return the newly created method
   */
  private RVMMethod readMethod(TypeReference declaringClass, int[] constantPool, MemberReference memRef,
                              short modifiers, DataInputStream input) throws IOException {
    short tmp_localWords = 0;
    short tmp_operandWords = 0;
    byte[] tmp_bytecodes = null;
    ExceptionHandlerMap tmp_exceptionHandlerMap = null;
    TypeReference[] tmp_exceptionTypes = null;
    int[] tmp_lineNumberMap = null;
    LocalVariableTable tmp_localVariableTable = null;
    Atom tmp_signature = null;
    RVMAnnotation[] annotations = null;
    RVMAnnotation[][] parameterAnnotations = null;
    Object tmp_annotationDefault = null;
    byte[] rawAnnotations = null;
    byte[] rawParameterAnnotations = null;
    byte[] rawAnnotationDefault = null;

    // Read the attributes
    for (int i = 0, n = input.readUnsignedShort(); i < n; i++) {
      Atom attName = ConstantPool.getUtf(constantPool, input.readUnsignedShort());
      int attLength = input.readInt();

      // Only bother to interpret non-boring Method attributes
      if (attName == RVMClassLoader.codeAttributeName) {
        tmp_operandWords = input.readShort();
        tmp_localWords = input.readShort();
        tmp_bytecodes = new byte[input.readInt()];
        input.readFully(tmp_bytecodes);
        tmp_exceptionHandlerMap = ExceptionHandlerMap.readExceptionHandlerMap(input, constantPool);

        // Read the attributes portion of the code attribute
        for (int j = 0, n2 = input.readUnsignedShort(); j < n2; j++) {
          attName = ConstantPool.getUtf(constantPool, input.readUnsignedShort());
          attLength = input.readInt();

          if (attName == RVMClassLoader.lineNumberTableAttributeName) {
            int cnt = input.readUnsignedShort();
            if (cnt != 0) {
              tmp_lineNumberMap = new int[cnt];
              for (int k = 0; k < cnt; k++) {
                int startPC = input.readUnsignedShort();
                int lineNumber = input.readUnsignedShort();
                tmp_lineNumberMap[k] = (lineNumber << BITS_IN_SHORT) | startPC;
              }
            }
          } else if (attName == RVMClassLoader.localVariableTableAttributeName) {
            tmp_localVariableTable = LocalVariableTable.readLocalVariableTable(input, constantPool);
          } else {
            // All other entries in the attribute portion of the code attribute are boring.
            int skippedAmount = input.skipBytes(attLength);
            if (skippedAmount != attLength) {
              throw new IOException("Unexpected short skip");
            }
          }
        }
      } else if (attName == RVMClassLoader.exceptionsAttributeName) {
        int cnt = input.readUnsignedShort();
        if (cnt != 0) {
          tmp_exceptionTypes = new TypeReference[cnt];
          for (int j = 0, m = tmp_exceptionTypes.length; j < m; ++j) {
            tmp_exceptionTypes[j] = ConstantPool.getTypeRef(constantPool, input.readUnsignedShort());
          }
        }
      } else if (attName == RVMClassLoader.syntheticAttributeName) {
        modifiers |= ACC_SYNTHETIC;
      } else if (attName == RVMClassLoader.signatureAttributeName) {
        tmp_signature = ConstantPool.getUtf(constantPool, input.readUnsignedShort());
      } else if (attName == RVMClassLoader.runtimeVisibleAnnotationsAttributeName) {
        loggingStream.startLogging();
        annotations = AnnotatedElement.readAnnotations(constantPool, input, declaringClass.getClassLoader());
        loggingStream.stopLogging();
        rawAnnotations = loggingStream.getLoggedBytes();
        loggingStream.clearLoggedBytes();
      } else if (attName == RVMClassLoader.runtimeVisibleParameterAnnotationsAttributeName) {
        loggingStream.startLogging();
        int numParameters = input.readByte() & 0xFF;
        parameterAnnotations = new RVMAnnotation[numParameters][];
        for (int a = 0; a < numParameters; ++a) {
          parameterAnnotations[a] = AnnotatedElement.readAnnotations(constantPool, input, declaringClass.getClassLoader());
        }
        loggingStream.stopLogging();
        rawParameterAnnotations = loggingStream.getLoggedBytes();
        loggingStream.clearLoggedBytes();
      } else if (attName == RVMClassLoader.annotationDefaultAttributeName) {
        try {
          loggingStream.startLogging();
          tmp_annotationDefault = RVMAnnotation.readValue(memRef.asMethodReference().getReturnType(), constantPool, input, declaringClass.getClassLoader());
          loggingStream.stopLogging();
          rawAnnotationDefault = loggingStream.getLoggedBytes();
          loggingStream.clearLoggedBytes();
        } catch (ClassNotFoundException e) {
          loggingStream.stopLogging();
          loggingStream.clearLoggedBytes();
          throw new Error(e);
        }
      } else {
        // all other method attributes are boring
        int skippedAmount = input.skipBytes(attLength);
        if (skippedAmount != attLength) {
          throw new IOException("Unexpected short skip");
        }
      }
    }
    MethodAnnotations methodAnnotations = new MethodAnnotations(annotations, parameterAnnotations, tmp_annotationDefault);
    methodAnnotations.setRawAnnotations(rawAnnotations);
    methodAnnotations.setRawParameterAnnotations(rawParameterAnnotations);
    methodAnnotations.setRawAnnotationDefault(rawAnnotationDefault);
    RVMMethod method;
    if ((modifiers & ACC_NATIVE) != 0) {
      method =
          new NativeMethod(declaringClass,
                              memRef,
                              modifiers,
                              tmp_exceptionTypes,
                              tmp_signature,
                              methodAnnotations);
    } else if ((modifiers & ACC_ABSTRACT) != 0) {
      method =
          new AbstractMethod(declaringClass,
                                memRef,
                                modifiers,
                                tmp_exceptionTypes,
                                tmp_signature,
                                methodAnnotations);

    } else {
      method =
          new NormalMethod(declaringClass,
                              memRef,
                              modifiers,
                              tmp_exceptionTypes,
                              tmp_localWords,
                              tmp_operandWords,
                              tmp_bytecodes,
                              tmp_exceptionHandlerMap,
                              tmp_lineNumberMap,
                              tmp_localVariableTable,
                              constantPool,
                              tmp_signature,
                              methodAnnotations);
    }
    return method;
  }


  /**
   * Returns the class initializer method among the declared methods of the class
   * @param declaredMethods the methods declared by the class
   * @return the class initializer method {@code <clinit>} of the class or {@code null}
   *  if none was found
   */
  private RVMMethod getClassInitializerMethod(RVMMethod[] declaredMethods) {
    for (RVMMethod method : declaredMethods) {
      if (method.isClassInitializer()) return method;
    }
    return null;
  }

  /**
   * Creates an instance of a RVMClass.
   * @param typeRef the canonical type reference for this type.
   * @param input the data stream from which to read the class's description.
   * @return a newly created class
   * @throws IOException when data cannot be read from the input stream or
   *  skipping during class construction reads less data than expected
   * @throws ClassFormatError when the class data is corrupt
   */
  private RVMClass readClass(TypeReference typeRef, DataInputStream input) throws ClassFormatError, IOException {

    if (RVMClass.isClassLoadingDisabled()) {
      throw new RuntimeException("ClassLoading Disabled : " + typeRef);
    }

    if (VM.TraceClassLoading) {
      VM.sysWriteln("RVMClass (ClassFileReader): (begin) load file " + typeRef.getName());
    }

    int[] constantPool = readConstantPool(typeRef, input);
    short modifiers = input.readShort();
    short originalModifiers = modifiers;
    int myTypeIndex = readTypeRef(typeRef, input, constantPool);
    RVMClass superClass = readSuperClass(input, constantPool, modifiers);
    RVMClass[] declaredInterfaces = readDeclaredInterfaces(input, constantPool);
    RVMField[] declaredFields = readDeclaredFields(typeRef, input, constantPool);
    RVMMethod[] declaredMethods = readDeclaredMethods(typeRef, input, constantPool);
    RVMMethod classInitializerMethod = getClassInitializerMethod(declaredMethods);

    TypeReference[] declaredClasses = null;
    Atom sourceName = null;
    TypeReference declaringClass = null;
    Atom signature = null;
    RVMAnnotation[] annotations = null;
    byte[] rawAnnotations = null;
    TypeReference enclosingClass = null;
    MethodReference enclosingMethod = null;
    // Read attributes.
    for (int i = 0, n = input.readUnsignedShort(); i < n; ++i) {
      Atom attName = ConstantPool.getUtf(constantPool, input.readUnsignedShort());
      int attLength = input.readInt();

      // Class attributes
      if (attName == RVMClassLoader.sourceFileAttributeName && attLength == 2) {
        sourceName = ConstantPool.getUtf(constantPool, input.readUnsignedShort());
      } else if (attName == RVMClassLoader.innerClassesAttributeName) {
        // Parse InnerClasses attribute, and use the information to populate
        // the list of declared member classes.  We do this so we can
        // support the java.lang.Class.getDeclaredClasses()
        // and java.lang.Class.getDeclaredClass methods.

        int numberOfClasses = input.readUnsignedShort();
        declaredClasses = new TypeReference[numberOfClasses];

        for (int j = 0; j < numberOfClasses; ++j) {
          int innerClassInfoIndex = input.readUnsignedShort();
          int outerClassInfoIndex = input.readUnsignedShort();
          int innerNameIndex = input.readUnsignedShort();
          int innerClassAccessFlags = input.readUnsignedShort();

          if (innerClassInfoIndex != 0 && outerClassInfoIndex == myTypeIndex && innerNameIndex != 0) {
            // This looks like a declared inner class.
            declaredClasses[j] = ConstantPool.getTypeRef(constantPool, innerClassInfoIndex);
          }

          if (innerClassInfoIndex == myTypeIndex) {
            if (outerClassInfoIndex != 0) {
              declaringClass = ConstantPool.getTypeRef(constantPool, outerClassInfoIndex);
              if (enclosingClass == null) {
                // TODO: is this the null test necessary?
                enclosingClass = declaringClass;
              }
            }
            if ((innerClassAccessFlags & (ACC_PRIVATE | ACC_PROTECTED)) != 0) {
              modifiers &= ~(ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED);
            }
            modifiers |= innerClassAccessFlags;
            originalModifiers = (short) innerClassAccessFlags;
          }
        }
      } else if (attName == RVMClassLoader.syntheticAttributeName) {
        modifiers |= ACC_SYNTHETIC;
      } else if (attName == RVMClassLoader.enclosingMethodAttributeName) {
        int enclosingClassIndex = input.readUnsignedShort();
        enclosingClass = ConstantPool.getTypeRef(constantPool, enclosingClassIndex);

        int enclosingMethodIndex = input.readUnsignedShort();
        if (enclosingMethodIndex != 0) {
          int memberNameIndex = constantPool[enclosingMethodIndex] >>> BITS_IN_SHORT;
          int memberDescriptorIndex = constantPool[enclosingMethodIndex] & ((1 << BITS_IN_SHORT) - 1);
          Atom memberName = ConstantPool.getUtf(constantPool, memberNameIndex);
          Atom memberDescriptor = ConstantPool.getUtf(constantPool, memberDescriptorIndex);
          enclosingMethod =
              MemberReference.findOrCreate(enclosingClass, memberName, memberDescriptor).asMethodReference();
        }
      } else if (attName == RVMClassLoader.signatureAttributeName) {
        signature = ConstantPool.getUtf(constantPool, input.readUnsignedShort());
      } else if (attName == RVMClassLoader.runtimeVisibleAnnotationsAttributeName) {
        loggingStream.startLogging();
        annotations = AnnotatedElement.readAnnotations(constantPool, input, typeRef.getClassLoader());
        loggingStream.stopLogging();
        rawAnnotations = loggingStream.getLoggedBytes();
        loggingStream.clearLoggedBytes();
      } else {
        int skippedAmount = input.skipBytes(attLength);
        if (skippedAmount != attLength) {
          throw new IOException("Unexpected short skip");
        }
      }
    }

    Annotations encapsulatedAnnotations = new Annotations(annotations);
    encapsulatedAnnotations.setRawAnnotations(rawAnnotations);
    return new RVMClass(typeRef,
                        constantPool,
                        modifiers,
                        originalModifiers,
                        superClass,
                        declaredInterfaces,
                        declaredFields,
                        declaredMethods,
                        declaredClasses,
                        declaringClass,
                        enclosingClass,
                        enclosingMethod,
                        sourceName,
                        classInitializerMethod,
                        signature,
                        encapsulatedAnnotations);
  }

  // Shamelessly cloned & owned from ClassFileReader.readClass constructor....
  private TypeReference getClassTypeRef(DataInputStream input, ClassLoader cl)
      throws IOException, ClassFormatError {
    int magic = input.readInt();
    if (magic != 0xCAFEBABE) {
      throw new ClassFormatError("bad magic number " + Integer.toHexString(magic));
    }

    // Drop class file version number on floor. readClass will do the check later.
    input.readUnsignedShort(); // minor ID
    input.readUnsignedShort(); // major ID

    //
    // pass 1: read constant pool
    //
    int[] constantPool = new int[input.readUnsignedShort()];
    byte[] tmpTags = new byte[constantPool.length];

    // note: slot 0 is unused
    for (int i = 1; i < constantPool.length; i++) {
      tmpTags[i] = input.readByte();
      switch (tmpTags[i]) {
        case TAG_UTF: {
          byte[] utf = new byte[input.readUnsignedShort()];
          input.readFully(utf);
          constantPool[i] = Atom.findOrCreateUtf8Atom(utf).getId();
          break;
        }

        case TAG_UNUSED:
          break;

        case TAG_INT:
        case TAG_FLOAT:
        case TAG_FIELDREF:
        case TAG_METHODREF:
        case TAG_INTERFACE_METHODREF:
        case TAG_MEMBERNAME_AND_DESCRIPTOR:
          input.readInt(); // drop on floor
          break;

        case TAG_LONG:
        case TAG_DOUBLE:
          i++;
          input.readLong(); // drop on floor
          break;

        case TAG_TYPEREF:
          constantPool[i] = input.readUnsignedShort();
          break;

        case TAG_STRING:
          input.readUnsignedShort(); // drop on floor
          break;

        default:
          throw new ClassFormatError("bad constant pool entry: " + tmpTags[i]);
      }
    }

    //
    // pass 2: post-process type constant pool entries
    // (we must do this in a second pass because of forward references)
    //
    for (int i = 1; i < constantPool.length; i++) {
      switch (tmpTags[i]) {
        case TAG_LONG:
        case TAG_DOUBLE:
          ++i;
          break;

        case TAG_TYPEREF: { // in: utf index
          Atom typeName = Atom.getAtom(constantPool[constantPool[i]]);
          constantPool[i] = TypeReference.findOrCreate(cl, typeName.descriptorFromClassName()).getId();
          break;
        } // out: type reference id
      }
    }

    // drop modifiers on floor.
    input.readUnsignedShort();

    int myTypeIndex = input.readUnsignedShort();
    return TypeReference.getTypeRef(constantPool[myTypeIndex]);
  }

  public RVMType readClass(String className, ClassLoader classloader) throws ClassFormatError {
    TypeReference tRef;
    if (className == null) {
      // NUTS: Our caller hasn't bothered to tell us what this class is supposed
      //       to be called, so we must read the input stream and discover it ourselves
      //       before we actually can create the RVMClass instance.
      try {
        inputStream.mark(inputStream.available());
        tRef = getClassTypeRef(new DataInputStream(inputStream), classloader);
        inputStream.reset();
      } catch (IOException e) {
        ClassFormatError cfe = new ClassFormatError(e.getMessage());
        cfe.initCause(e);
        throw cfe;
      }
    } else {
      Atom classDescriptor = Atom.findOrCreateAsciiAtom(className).descriptorFromClassName();
      tRef = TypeReference.findOrCreate(classloader, classDescriptor);
    }

    try {
      if (VM.VerifyAssertions) VM._assert(tRef.isClassType());
      if (VM.TraceClassLoading) {
        VM.sysWriteln("(defineClassInternal) loading \"" + tRef.getName() + "\" with " + classloader);
      }

      loggingStream = new LoggingInputStream(inputStream);
      DataInputStream stream = new DataInputStream(loggingStream);
      RVMClass ans = readClass(tRef, stream);
      tRef.setType(ans);
      return ans;
    } catch (IOException e) {
      ClassFormatError cfe = new ClassFormatError(e.getMessage());
      cfe.initCause(e);
      throw cfe;
    }

  }

}
