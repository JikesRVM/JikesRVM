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
package org.jikesrvm.tools.bootImageWriter;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.jni.FunctionTable;
import org.jikesrvm.jni.JNIFunctions;

/**
 * This class is responsible for constructing the JNIFuctions
 * array at bootimage writing time.  It uses "internal reflection" to
 * inspect the methods of JNIFunctions and construct
 * a CodeArray[] that contains pointers to the compiled code
 * of the appropriate JNI function implementation.
 * <p>
 * It builds a JNI function table for JNI version 1.2.
 */
public class BuildJNIFunctionTable {

  // index of the named function in the array
  // (these are constant as defined by the JNI specification)
  private static final int RESERVED0                     =    0;
  private static final int RESERVED1                     =    1;
  private static final int RESERVED2                     =    2;
  private static final int RESERVED3                     =    3;
  private static final int GETVERSION                    =    4;
  private static final int DEFINECLASS                   =    5;
  private static final int FINDCLASS                     =    6;
  private static final int FROMREFLECTEDMETHOD           =    7;
  private static final int FROMREFLECTEDFIELD            =    8;
  private static final int TOREFLECTEDMETHOD             =    9;
  private static final int GETSUPERCLASS                 =   10;
  private static final int ISASSIGNABLEFROM              =   11;
  private static final int TOREFLECTEDFIELD              =   12;
  private static final int THROW                         =   13;
  private static final int THROWNEW                      =   14;
  private static final int EXCEPTIONOCCURRED             =   15;
  private static final int EXCEPTIONDESCRIBE             =   16;
  private static final int EXCEPTIONCLEAR                =   17;
  private static final int FATALERROR                    =   18;
  private static final int PUSHLOCALFRAME                =   19;
  private static final int POPLOCALFRAME                 =   20;
  private static final int NEWGLOBALREF                  =   21;
  private static final int DELETEGLOBALREF               =   22;
  private static final int DELETELOCALREF                =   23;
  private static final int ISSAMEOBJECT                  =   24;
  private static final int NEWLOCALREF                   =   25;
  private static final int ENSURELOCALCAPACITY           =   26;
  private static final int ALLOCOBJECT                   =   27;
  private static final int NEWOBJECT                     =   28;
  private static final int NEWOBJECTV                    =   29;
  private static final int NEWOBJECTA                    =   30;
  private static final int GETOBJECTCLASS                =   31;
  private static final int ISINSTANCEOF                  =   32;
  private static final int GETMETHODID                   =   33;
  private static final int CALLOBJECTMETHOD              =   34;
  private static final int CALLOBJECTMETHODV             =   35;
  private static final int CALLOBJECTMETHODA             =   36;
  private static final int CALLBOOLEANMETHOD             =   37;
  private static final int CALLBOOLEANMETHODV            =   38;
  private static final int CALLBOOLEANMETHODA            =   39;
  private static final int CALLBYTEMETHOD                =   40;
  private static final int CALLBYTEMETHODV               =   41;
  private static final int CALLBYTEMETHODA               =   42;
  private static final int CALLCHARMETHOD                =   43;
  private static final int CALLCHARMETHODV               =   44;
  private static final int CALLCHARMETHODA               =   45;
  private static final int CALLSHORTMETHOD               =   46;
  private static final int CALLSHORTMETHODV              =   47;
  private static final int CALLSHORTMETHODA              =   48;
  private static final int CALLINTMETHOD                 =   49;
  private static final int CALLINTMETHODV                =   50;
  private static final int CALLINTMETHODA                =   51;
  private static final int CALLLONGMETHOD                =   52;
  private static final int CALLLONGMETHODV               =   53;
  private static final int CALLLONGMETHODA               =   54;
  private static final int CALLFLOATMETHOD               =   55;
  private static final int CALLFLOATMETHODV              =   56;
  private static final int CALLFLOATMETHODA              =   57;
  private static final int CALLDOUBLEMETHOD              =   58;
  private static final int CALLDOUBLEMETHODV             =   59;
  private static final int CALLDOUBLEMETHODA             =   60;
  private static final int CALLVOIDMETHOD                =   61;
  private static final int CALLVOIDMETHODV               =   62;
  private static final int CALLVOIDMETHODA               =   63;
  private static final int CALLNONVIRTUALOBJECTMETHOD    =   64;
  private static final int CALLNONVIRTUALOBJECTMETHODV   =   65;
  private static final int CALLNONVIRTUALOBJECTMETHODA   =   66;
  private static final int CALLNONVIRTUALBOOLEANMETHOD   =   67;
  private static final int CALLNONVIRTUALBOOLEANMETHODV  =   68;
  private static final int CALLNONVIRTUALBOOLEANMETHODA  =   69;
  private static final int CALLNONVIRTUALBYTEMETHOD      =   70;
  private static final int CALLNONVIRTUALBYTEMETHODV     =   71;
  private static final int CALLNONVIRTUALBYTEMETHODA     =   72;
  private static final int CALLNONVIRTUALCHARMETHOD      =   73;
  private static final int CALLNONVIRTUALCHARMETHODV     =   74;
  private static final int CALLNONVIRTUALCHARMETHODA     =   75;
  private static final int CALLNONVIRTUALSHORTMETHOD     =   76;
  private static final int CALLNONVIRTUALSHORTMETHODV    =   77;
  private static final int CALLNONVIRTUALSHORTMETHODA    =   78;
  private static final int CALLNONVIRTUALINTMETHOD       =   79;
  private static final int CALLNONVIRTUALINTMETHODV      =   80;
  private static final int CALLNONVIRTUALINTMETHODA      =   81;
  private static final int CALLNONVIRTUALLONGMETHOD      =   82;
  private static final int CALLNONVIRTUALLONGMETHODV     =   83;
  private static final int CALLNONVIRTUALLONGMETHODA     =   84;
  private static final int CALLNONVIRTUALFLOATMETHOD     =   85;
  private static final int CALLNONVIRTUALFLOATMETHODV    =   86;
  private static final int CALLNONVIRTUALFLOATMETHODA    =   87;
  private static final int CALLNONVIRTUALDOUBLEMETHOD    =   88;
  private static final int CALLNONVIRTUALDOUBLEMETHODV   =   89;
  private static final int CALLNONVIRTUALDOUBLEMETHODA   =   90;
  private static final int CALLNONVIRTUALVOIDMETHOD      =   91;
  private static final int CALLNONVIRTUALVOIDMETHODV     =   92;
  private static final int CALLNONVIRTUALVOIDMETHODA     =   93;
  private static final int GETFIELDID                    =   94;
  private static final int GETOBJECTFIELD                =   95;
  private static final int GETBOOLEANFIELD               =   96;
  private static final int GETBYTEFIELD                  =   97;
  private static final int GETCHARFIELD                  =   98;
  private static final int GETSHORTFIELD                 =   99;
  private static final int GETINTFIELD                   =  100;
  private static final int GETLONGFIELD                  =  101;
  private static final int GETFLOATFIELD                 =  102;
  private static final int GETDOUBLEFIELD                =  103;
  private static final int SETOBJECTFIELD                =  104;
  private static final int SETBOOLEANFIELD               =  105;
  private static final int SETBYTEFIELD                  =  106;
  private static final int SETCHARFIELD                  =  107;
  private static final int SETSHORTFIELD                 =  108;
  private static final int SETINTFIELD                   =  109;
  private static final int SETLONGFIELD                  =  110;
  private static final int SETFLOATFIELD                 =  111;
  private static final int SETDOUBLEFIELD                =  112;
  private static final int GETSTATICMETHODID             =  113;
  private static final int CALLSTATICOBJECTMETHOD        =  114;
  private static final int CALLSTATICOBJECTMETHODV       =  115;
  private static final int CALLSTATICOBJECTMETHODA       =  116;
  private static final int CALLSTATICBOOLEANMETHOD       =  117;
  private static final int CALLSTATICBOOLEANMETHODV      =  118;
  private static final int CALLSTATICBOOLEANMETHODA      =  119;
  private static final int CALLSTATICBYTEMETHOD          =  120;
  private static final int CALLSTATICBYTEMETHODV         =  121;
  private static final int CALLSTATICBYTEMETHODA         =  122;
  private static final int CALLSTATICCHARMETHOD          =  123;
  private static final int CALLSTATICCHARMETHODV         =  124;
  private static final int CALLSTATICCHARMETHODA         =  125;
  private static final int CALLSTATICSHORTMETHOD         =  126;
  private static final int CALLSTATICSHORTMETHODV        =  127;
  private static final int CALLSTATICSHORTMETHODA        =  128;
  private static final int CALLSTATICINTMETHOD           =  129;
  private static final int CALLSTATICINTMETHODV          =  130;
  private static final int CALLSTATICINTMETHODA          =  131;
  private static final int CALLSTATICLONGMETHOD          =  132;
  private static final int CALLSTATICLONGMETHODV         =  133;
  private static final int CALLSTATICLONGMETHODA         =  134;
  private static final int CALLSTATICFLOATMETHOD         =  135;
  private static final int CALLSTATICFLOATMETHODV        =  136;
  private static final int CALLSTATICFLOATMETHODA        =  137;
  private static final int CALLSTATICDOUBLEMETHOD        =  138;
  private static final int CALLSTATICDOUBLEMETHODV       =  139;
  private static final int CALLSTATICDOUBLEMETHODA       =  140;
  private static final int CALLSTATICVOIDMETHOD          =  141;
  private static final int CALLSTATICVOIDMETHODV         =  142;
  private static final int CALLSTATICVOIDMETHODA         =  143;
  private static final int GETSTATICFIELDID              =  144;
  private static final int GETSTATICOBJECTFIELD          =  145;
  private static final int GETSTATICBOOLEANFIELD         =  146;
  private static final int GETSTATICBYTEFIELD            =  147;
  private static final int GETSTATICCHARFIELD            =  148;
  private static final int GETSTATICSHORTFIELD           =  149;
  private static final int GETSTATICINTFIELD             =  150;
  private static final int GETSTATICLONGFIELD            =  151;
  private static final int GETSTATICFLOATFIELD           =  152;
  private static final int GETSTATICDOUBLEFIELD          =  153;
  private static final int SETSTATICOBJECTFIELD          =  154;
  private static final int SETSTATICBOOLEANFIELD         =  155;
  private static final int SETSTATICBYTEFIELD            =  156;
  private static final int SETSTATICCHARFIELD            =  157;
  private static final int SETSTATICSHORTFIELD           =  158;
  private static final int SETSTATICINTFIELD             =  159;
  private static final int SETSTATICLONGFIELD            =  160;
  private static final int SETSTATICFLOATFIELD           =  161;
  private static final int SETSTATICDOUBLEFIELD          =  162;
  private static final int NEWSTRING                     =  163;
  private static final int GETSTRINGLENGTH               =  164;
  private static final int GETSTRINGCHARS                =  165;
  private static final int RELEASESTRINGCHARS            =  166;
  private static final int NEWSTRINGUTF                  =  167;
  private static final int GETSTRINGUTFLENGTH            =  168;
  private static final int GETSTRINGUTFCHARS             =  169;
  private static final int RELEASESTRINGUTFCHARS         =  170;
  private static final int GETARRAYLENGTH                =  171;
  private static final int NEWOBJECTARRAY                =  172;
  private static final int GETOBJECTARRAYELEMENT         =  173;
  private static final int SETOBJECTARRAYELEMENT         =  174;
  private static final int NEWBOOLEANARRAY               =  175;
  private static final int NEWBYTEARRAY                  =  176;
  private static final int NEWCHARARRAY                  =  177;
  private static final int NEWSHORTARRAY                 =  178;
  private static final int NEWINTARRAY                   =  179;
  private static final int NEWLONGARRAY                  =  180;
  private static final int NEWFLOATARRAY                 =  181;
  private static final int NEWDOUBLEARRAY                =  182;
  private static final int GETBOOLEANARRAYELEMENTS       =  183;
  private static final int GETBYTEARRAYELEMENTS          =  184;
  private static final int GETCHARARRAYELEMENTS          =  185;
  private static final int GETSHORTARRAYELEMENTS         =  186;
  private static final int GETINTARRAYELEMENTS           =  187;
  private static final int GETLONGARRAYELEMENTS          =  188;
  private static final int GETFLOATARRAYELEMENTS         =  189;
  private static final int GETDOUBLEARRAYELEMENTS        =  190;
  private static final int RELEASEBOOLEANARRAYELEMENTS   =  191;
  private static final int RELEASEBYTEARRAYELEMENTS      =  192;
  private static final int RELEASECHARARRAYELEMENTS      =  193;
  private static final int RELEASESHORTARRAYELEMENTS     =  194;
  private static final int RELEASEINTARRAYELEMENTS       =  195;
  private static final int RELEASELONGARRAYELEMENTS      =  196;
  private static final int RELEASEFLOATARRAYELEMENTS     =  197;
  private static final int RELEASEDOUBLEARRAYELEMENTS    =  198;
  private static final int GETBOOLEANARRAYREGION         =  199;
  private static final int GETBYTEARRAYREGION            =  200;
  private static final int GETCHARARRAYREGION            =  201;
  private static final int GETSHORTARRAYREGION           =  202;
  private static final int GETINTARRAYREGION             =  203;
  private static final int GETLONGARRAYREGION            =  204;
  private static final int GETFLOATARRAYREGION           =  205;
  private static final int GETDOUBLEARRAYREGION          =  206;
  private static final int SETBOOLEANARRAYREGION         =  207;
  private static final int SETBYTEARRAYREGION            =  208;
  private static final int SETCHARARRAYREGION            =  209;
  private static final int SETSHORTARRAYREGION           =  210;
  private static final int SETINTARRAYREGION             =  211;
  private static final int SETLONGARRAYREGION            =  212;
  private static final int SETFLOATARRAYREGION           =  213;
  private static final int SETDOUBLEARRAYREGION          =  214;
  private static final int REGISTERNATIVES               =  215;
  private static final int UNREGISTERNATIVES             =  216;
  private static final int MONITORENTER                  =  217;
  private static final int MONITOREXIT                   =  218;
  private static final int GETJAVAVM                     =  219;
  private static final int GETSTRINGREGION               =  220;
  private static final int GETSTRINGUTFREGION            =  221;
  private static final int GETPRIMITIVEARRAYCRITICAL     =  222;
  private static final int RELEASEPRIMITIVEARRAYCRITICAL =  223;
  private static final int GETSTRINGCRITICAL             =  224;
  private static final int RELEASESTRINGCRITICAL         =  225;
  private static final int NEWWEAKGLOBALREF              =  226;
  private static final int DELETEWEAKGLOBALREF           =  227;
  private static final int EXCEPTIONCHECK                =  228;
  /* Added in JNI 1.4: */
  private static final int NEWDIRECTBYTEBUFFER           =  229;
  private static final int GETDIRECTBUFFERADDRESS        =  230;
  private static final int GETDIRECTBUFFERCAPACITY       =  231;

  /**
   * Construct the JNIFuntionTable.
   * This is not very efficient, but is done at bootImageWriting time,
   * so we just don't worry about it.
   */
  public static FunctionTable buildTable() {
    String[] names = initNames();
    FunctionTable functions = FunctionTable.allocate(JNIFunctions.FUNCTIONCOUNT);

    RVMClass cls = TypeReference.JNIFunctions.peekType().asClass();
    if (VM.VerifyAssertions) VM._assert(cls.isInstantiated());
    for (RVMMethod mth : cls.getDeclaredMethods()) {
      String methodName = mth.getName().toString();
      int jniIndex = indexOf(names, methodName);
      if (jniIndex != -1) {
        functions.set(jniIndex, mth.getCurrentEntryCodeArray());
      }
    }
    return functions;
  }

  /**
   * Get the JNI index for a function name.
   * @param functionName a JNI function name
   * @return the index for this function, -1 if not found
   */
  private static int indexOf(String[] names, String functionName) {
    for (int i=0; i<names.length; i++) {
      if (names[i].equals(functionName))
        return i;
    }
    return -1;
  }

  private static String[] initNames() {
    String[] names = new String[JNIFunctions.FUNCTIONCOUNT];
    names[0]                             = "undefined";
    names[RESERVED0]                     = "reserved0";
    names[RESERVED1]                     = "reserved1";
    names[RESERVED2]                     = "reserved2";
    names[RESERVED3]                     = "reserved3";
    names[GETVERSION]                    = "GetVersion";
    names[DEFINECLASS]                   = "DefineClass";
    names[FINDCLASS]                     = "FindClass";
    names[FROMREFLECTEDMETHOD]           = "FromReflectedMethod"; //  JDK1.2, #7
    names[FROMREFLECTEDFIELD]            = "FromReflectedField";  //  JDK1.2, #8
    names[TOREFLECTEDMETHOD]             = "ToReflectedMethod";   //  JDK1.2, #9
    names[GETSUPERCLASS]                 = "GetSuperclass";
    names[ISASSIGNABLEFROM]              = "IsAssignableFrom";
    names[TOREFLECTEDFIELD]              = "ToReflectedField";    //  JDK1.2, #12
    names[THROW]                         = "Throw";
    names[THROWNEW]                      = "ThrowNew";
    names[EXCEPTIONOCCURRED]             = "ExceptionOccurred";
    names[EXCEPTIONDESCRIBE]             = "ExceptionDescribe";
    names[EXCEPTIONCLEAR]                = "ExceptionClear";
    names[FATALERROR]                    = "FatalError";
    names[PUSHLOCALFRAME]                = "PushLocalFrame";      //  JDK1.2, #19
    names[POPLOCALFRAME]                 = "PopLocalFrame";       //  JDK1.2, #20
    names[NEWGLOBALREF]                  = "NewGlobalRef";
    names[DELETEGLOBALREF]               = "DeleteGlobalRef";
    names[DELETELOCALREF]                = "DeleteLocalRef";
    names[ISSAMEOBJECT]                  = "IsSameObject";
    names[NEWLOCALREF]                   = "NewLocalRef";         //  JDK1.2, #25
    names[ENSURELOCALCAPACITY]           = "EnsureLocalCapacity"; //  JDK1.2, #26
    names[ALLOCOBJECT]                   = "AllocObject";
    names[NEWOBJECT]                     = "NewObject";
    names[NEWOBJECTV]                    = "NewObjectV";
    names[NEWOBJECTA]                    = "NewObjectA";
    names[GETOBJECTCLASS]                = "GetObjectClass";
    names[ISINSTANCEOF]                  = "IsInstanceOf";
    names[GETMETHODID]                   = "GetMethodID";
    names[CALLOBJECTMETHOD]              = "CallObjectMethod";
    names[CALLOBJECTMETHODV]             = "CallObjectMethodV";
    names[CALLOBJECTMETHODA]             = "CallObjectMethodA";
    names[CALLBOOLEANMETHOD]             = "CallBooleanMethod";
    names[CALLBOOLEANMETHODV]            = "CallBooleanMethodV";
    names[CALLBOOLEANMETHODA]            = "CallBooleanMethodA";
    names[CALLBYTEMETHOD]                = "CallByteMethod";
    names[CALLBYTEMETHODV]               = "CallByteMethodV";
    names[CALLBYTEMETHODA]               = "CallByteMethodA";
    names[CALLCHARMETHOD]                = "CallCharMethod";
    names[CALLCHARMETHODV]               = "CallCharMethodV";
    names[CALLCHARMETHODA]               = "CallCharMethodA";
    names[CALLSHORTMETHOD]               = "CallShortMethod";
    names[CALLSHORTMETHODV]              = "CallShortMethodV";
    names[CALLSHORTMETHODA]              = "CallShortMethodA";
    names[CALLINTMETHOD]                 = "CallIntMethod";
    names[CALLINTMETHODV]                = "CallIntMethodV";
    names[CALLINTMETHODA]                = "CallIntMethodA";
    names[CALLLONGMETHOD]                = "CallLongMethod";
    names[CALLLONGMETHODV]               = "CallLongMethodV";
    names[CALLLONGMETHODA]               = "CallLongMethodA";
    names[CALLFLOATMETHOD]               = "CallFloatMethod";
    names[CALLFLOATMETHODV]              = "CallFloatMethodV";
    names[CALLFLOATMETHODA]              = "CallFloatMethodA";
    names[CALLDOUBLEMETHOD]              = "CallDoubleMethod";
    names[CALLDOUBLEMETHODV]             = "CallDoubleMethodV";
    names[CALLDOUBLEMETHODA]             = "CallDoubleMethodA";
    names[CALLVOIDMETHOD]                = "CallVoidMethod";
    names[CALLVOIDMETHODV]               = "CallVoidMethodV";
    names[CALLVOIDMETHODA]               = "CallVoidMethodA";
    names[CALLNONVIRTUALOBJECTMETHOD]    = "CallNonvirtualObjectMethod";
    names[CALLNONVIRTUALOBJECTMETHODV]   = "CallNonvirtualObjectMethodV";
    names[CALLNONVIRTUALOBJECTMETHODA]   = "CallNonvirtualObjectMethodA";
    names[CALLNONVIRTUALBOOLEANMETHOD]   = "CallNonvirtualBooleanMethod";
    names[CALLNONVIRTUALBOOLEANMETHODV]  = "CallNonvirtualBooleanMethodV";
    names[CALLNONVIRTUALBOOLEANMETHODA]  = "CallNonvirtualBooleanMethodA";
    names[CALLNONVIRTUALBYTEMETHOD]      = "CallNonvirtualByteMethod";
    names[CALLNONVIRTUALBYTEMETHODV]     = "CallNonvirtualByteMethodV";
    names[CALLNONVIRTUALBYTEMETHODA]     = "CallNonvirtualByteMethodA";
    names[CALLNONVIRTUALCHARMETHOD]      = "CallNonvirtualCharMethod";
    names[CALLNONVIRTUALCHARMETHODV]     = "CallNonvirtualCharMethodV";
    names[CALLNONVIRTUALCHARMETHODA]     = "CallNonvirtualCharMethodA";
    names[CALLNONVIRTUALSHORTMETHOD]     = "CallNonvirtualShortMethod";
    names[CALLNONVIRTUALSHORTMETHODV]    = "CallNonvirtualShortMethodV";
    names[CALLNONVIRTUALSHORTMETHODA]    = "CallNonvirtualShortMethodA";
    names[CALLNONVIRTUALINTMETHOD]       = "CallNonvirtualIntMethod";
    names[CALLNONVIRTUALINTMETHODV]      = "CallNonvirtualIntMethodV";
    names[CALLNONVIRTUALINTMETHODA]      = "CallNonvirtualIntMethodA";
    names[CALLNONVIRTUALLONGMETHOD]      = "CallNonvirtualLongMethod";
    names[CALLNONVIRTUALLONGMETHODV]     = "CallNonvirtualLongMethodV";
    names[CALLNONVIRTUALLONGMETHODA]     = "CallNonvirtualLongMethodA";
    names[CALLNONVIRTUALFLOATMETHOD]     = "CallNonvirtualFloatMethod";
    names[CALLNONVIRTUALFLOATMETHODV]    = "CallNonvirtualFloatMethodV";
    names[CALLNONVIRTUALFLOATMETHODA]    = "CallNonvirtualFloatMethodA";
    names[CALLNONVIRTUALDOUBLEMETHOD]    = "CallNonvirtualDoubleMethod";
    names[CALLNONVIRTUALDOUBLEMETHODV]   = "CallNonvirtualDoubleMethodV";
    names[CALLNONVIRTUALDOUBLEMETHODA]   = "CallNonvirtualDoubleMethodA";
    names[CALLNONVIRTUALVOIDMETHOD]      = "CallNonvirtualVoidMethod";
    names[CALLNONVIRTUALVOIDMETHODV]     = "CallNonvirtualVoidMethodV";
    names[CALLNONVIRTUALVOIDMETHODA]     = "CallNonvirtualVoidMethodA";
    names[GETFIELDID]                    = "GetFieldID";
    names[GETOBJECTFIELD]                = "GetObjectField";
    names[GETBOOLEANFIELD]               = "GetBooleanField";
    names[GETBYTEFIELD]                  = "GetByteField";
    names[GETCHARFIELD]                  = "GetCharField";
    names[GETSHORTFIELD]                 = "GetShortField";
    names[GETINTFIELD]                   = "GetIntField";
    names[GETLONGFIELD]                  = "GetLongField";
    names[GETFLOATFIELD]                 = "GetFloatField";
    names[GETDOUBLEFIELD]                = "GetDoubleField";
    names[SETOBJECTFIELD]                = "SetObjectField";
    names[SETBOOLEANFIELD]               = "SetBooleanField";
    names[SETBYTEFIELD]                  = "SetByteField";
    names[SETCHARFIELD]                  = "SetCharField";
    names[SETSHORTFIELD]                 = "SetShortField";
    names[SETINTFIELD]                   = "SetIntField";
    names[SETLONGFIELD]                  = "SetLongField";
    names[SETFLOATFIELD]                 = "SetFloatField";
    names[SETDOUBLEFIELD]                = "SetDoubleField";
    names[GETSTATICMETHODID]             = "GetStaticMethodID";
    names[CALLSTATICOBJECTMETHOD]        = "CallStaticObjectMethod";
    names[CALLSTATICOBJECTMETHODV]       = "CallStaticObjectMethodV";
    names[CALLSTATICOBJECTMETHODA]       = "CallStaticObjectMethodA";
    names[CALLSTATICBOOLEANMETHOD]       = "CallStaticBooleanMethod";
    names[CALLSTATICBOOLEANMETHODV]      = "CallStaticBooleanMethodV";
    names[CALLSTATICBOOLEANMETHODA]      = "CallStaticBooleanMethodA";
    names[CALLSTATICBYTEMETHOD]          = "CallStaticByteMethod";
    names[CALLSTATICBYTEMETHODV]         = "CallStaticByteMethodV";
    names[CALLSTATICBYTEMETHODA]         = "CallStaticByteMethodA";
    names[CALLSTATICCHARMETHOD]          = "CallStaticCharMethod";
    names[CALLSTATICCHARMETHODV]         = "CallStaticCharMethodV";
    names[CALLSTATICCHARMETHODA]         = "CallStaticCharMethodA";
    names[CALLSTATICSHORTMETHOD]         = "CallStaticShortMethod";
    names[CALLSTATICSHORTMETHODV]        = "CallStaticShortMethodV";
    names[CALLSTATICSHORTMETHODA]        = "CallStaticShortMethodA";
    names[CALLSTATICINTMETHOD]           = "CallStaticIntMethod";
    names[CALLSTATICINTMETHODV]          = "CallStaticIntMethodV";
    names[CALLSTATICINTMETHODA]          = "CallStaticIntMethodA";
    names[CALLSTATICLONGMETHOD]          = "CallStaticLongMethod";
    names[CALLSTATICLONGMETHODV]         = "CallStaticLongMethodV";
    names[CALLSTATICLONGMETHODA]         = "CallStaticLongMethodA";
    names[CALLSTATICFLOATMETHOD]         = "CallStaticFloatMethod";
    names[CALLSTATICFLOATMETHODV]        = "CallStaticFloatMethodV";
    names[CALLSTATICFLOATMETHODA]        = "CallStaticFloatMethodA";
    names[CALLSTATICDOUBLEMETHOD]        = "CallStaticDoubleMethod";
    names[CALLSTATICDOUBLEMETHODV]       = "CallStaticDoubleMethodV";
    names[CALLSTATICDOUBLEMETHODA]       = "CallStaticDoubleMethodA";
    names[CALLSTATICVOIDMETHOD]          = "CallStaticVoidMethod";
    names[CALLSTATICVOIDMETHODV]         = "CallStaticVoidMethodV";
    names[CALLSTATICVOIDMETHODA]         = "CallStaticVoidMethodA";
    names[GETSTATICFIELDID]              = "GetStaticFieldID";
    names[GETSTATICOBJECTFIELD]          = "GetStaticObjectField";
    names[GETSTATICBOOLEANFIELD]         = "GetStaticBooleanField";
    names[GETSTATICBYTEFIELD]            = "GetStaticByteField";
    names[GETSTATICCHARFIELD]            = "GetStaticCharField";
    names[GETSTATICSHORTFIELD]           = "GetStaticShortField";
    names[GETSTATICINTFIELD]             = "GetStaticIntField";
    names[GETSTATICLONGFIELD]            = "GetStaticLongField";
    names[GETSTATICFLOATFIELD]           = "GetStaticFloatField";
    names[GETSTATICDOUBLEFIELD]          = "GetStaticDoubleField";
    names[SETSTATICOBJECTFIELD]          = "SetStaticObjectField";
    names[SETSTATICBOOLEANFIELD]         = "SetStaticBooleanField";
    names[SETSTATICBYTEFIELD]            = "SetStaticByteField";
    names[SETSTATICCHARFIELD]            = "SetStaticCharField";
    names[SETSTATICSHORTFIELD]           = "SetStaticShortField";
    names[SETSTATICINTFIELD]             = "SetStaticIntField";
    names[SETSTATICLONGFIELD]            = "SetStaticLongField";
    names[SETSTATICFLOATFIELD]           = "SetStaticFloatField";
    names[SETSTATICDOUBLEFIELD]          = "SetStaticDoubleField";
    names[NEWSTRING]                     = "NewString";
    names[GETSTRINGLENGTH]               = "GetStringLength";
    names[GETSTRINGCHARS]                = "GetStringChars";
    names[RELEASESTRINGCHARS]            = "ReleaseStringChars";
    names[NEWSTRINGUTF]                  = "NewStringUTF";
    names[GETSTRINGUTFLENGTH]            = "GetStringUTFLength";
    names[GETSTRINGUTFCHARS]             = "GetStringUTFChars";
    names[RELEASESTRINGUTFCHARS]         = "ReleaseStringUTFChars";
    names[GETARRAYLENGTH]                = "GetArrayLength";
    names[NEWOBJECTARRAY]                = "NewObjectArray";
    names[GETOBJECTARRAYELEMENT]         = "GetObjectArrayElement";
    names[SETOBJECTARRAYELEMENT]         = "SetObjectArrayElement";
    names[NEWBOOLEANARRAY]               = "NewBooleanArray";
    names[NEWBYTEARRAY]                  = "NewByteArray";
    names[NEWCHARARRAY]                  = "NewCharArray";
    names[NEWSHORTARRAY]                 = "NewShortArray";
    names[NEWINTARRAY]                   = "NewIntArray";
    names[NEWLONGARRAY]                  = "NewLongArray";
    names[NEWFLOATARRAY]                 = "NewFloatArray";
    names[NEWDOUBLEARRAY]                = "NewDoubleArray";
    names[GETBOOLEANARRAYELEMENTS]       = "GetBooleanArrayElements";
    names[GETBYTEARRAYELEMENTS]          = "GetByteArrayElements";
    names[GETCHARARRAYELEMENTS]          = "GetCharArrayElements";
    names[GETSHORTARRAYELEMENTS]         = "GetShortArrayElements";
    names[GETINTARRAYELEMENTS]           = "GetIntArrayElements";
    names[GETLONGARRAYELEMENTS]          = "GetLongArrayElements";
    names[GETFLOATARRAYELEMENTS]         = "GetFloatArrayElements";
    names[GETDOUBLEARRAYELEMENTS]        = "GetDoubleArrayElements";
    names[RELEASEBOOLEANARRAYELEMENTS]   = "ReleaseBooleanArrayElements";
    names[RELEASEBYTEARRAYELEMENTS]      = "ReleaseByteArrayElements";
    names[RELEASECHARARRAYELEMENTS]      = "ReleaseCharArrayElements";
    names[RELEASESHORTARRAYELEMENTS]     = "ReleaseShortArrayElements";
    names[RELEASEINTARRAYELEMENTS]       = "ReleaseIntArrayElements";
    names[RELEASELONGARRAYELEMENTS]      = "ReleaseLongArrayElements";
    names[RELEASEFLOATARRAYELEMENTS]     = "ReleaseFloatArrayElements";
    names[RELEASEDOUBLEARRAYELEMENTS]    = "ReleaseDoubleArrayElements";
    names[GETBOOLEANARRAYREGION]         = "GetBooleanArrayRegion";
    names[GETBYTEARRAYREGION]            = "GetByteArrayRegion";
    names[GETCHARARRAYREGION]            = "GetCharArrayRegion";
    names[GETSHORTARRAYREGION]           = "GetShortArrayRegion";
    names[GETINTARRAYREGION]             = "GetIntArrayRegion";
    names[GETLONGARRAYREGION]            = "GetLongArrayRegion";
    names[GETFLOATARRAYREGION]           = "GetFloatArrayRegion";
    names[GETDOUBLEARRAYREGION]          = "GetDoubleArrayRegion";
    names[SETBOOLEANARRAYREGION]         = "SetBooleanArrayRegion";
    names[SETBYTEARRAYREGION]            = "SetByteArrayRegion";
    names[SETCHARARRAYREGION]            = "SetCharArrayRegion";
    names[SETSHORTARRAYREGION]           = "SetShortArrayRegion";
    names[SETINTARRAYREGION]             = "SetIntArrayRegion";
    names[SETLONGARRAYREGION]            = "SetLongArrayRegion";
    names[SETFLOATARRAYREGION]           = "SetFloatArrayRegion";
    names[SETDOUBLEARRAYREGION]          = "SetDoubleArrayRegion";
    names[REGISTERNATIVES]               = "RegisterNatives";
    names[UNREGISTERNATIVES]             = "UnregisterNatives";
    names[MONITORENTER]                  = "MonitorEnter";
    names[MONITOREXIT]                   = "MonitorExit";
    names[GETJAVAVM]                     = "GetJavaVM";
    names[GETSTRINGREGION]               = "GetStringRegion";               // JDK 1.2, #220
    names[GETSTRINGUTFREGION]            = "GetStringUTFRegion";            // JDK 1.2, #221
    names[GETPRIMITIVEARRAYCRITICAL]     = "GetPrimitiveArrayCritical";     // JDK 1.2, #222
    names[RELEASEPRIMITIVEARRAYCRITICAL] = "ReleasePrimitiveArrayCritical"; // JDK 1.2, #223
    names[GETSTRINGCRITICAL]             = "GetStringCritical";             // JDK 1.2, # 224
    names[RELEASESTRINGCRITICAL]         = "ReleaseStringCritical";         // JDK 1.2, #225
    names[NEWWEAKGLOBALREF]              = "NewWeakGlobalRef";              // JDK 1.2, #226
    names[DELETEWEAKGLOBALREF]           = "DeleteWeakGlobalRef";           // JDK 1.2, #227
    names[EXCEPTIONCHECK]                = "ExceptionCheck";                // JDK 1.2, #228
    names[NEWDIRECTBYTEBUFFER]           = "NewDirectByteBuffer";           // JDK 1.4, #229
    names[GETDIRECTBUFFERADDRESS]        = "GetDirectBufferAddress";        // JDK 1.4, #230
    names[GETDIRECTBUFFERCAPACITY]       = "GetDirectBufferCapacity";       // JDK 1.4, #231

    return names;
  }
}

