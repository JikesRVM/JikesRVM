/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/*
 * Constants for JNI support
 *
 * @author Ton Ngo
 * @author Steve Smith
 */
interface VM_JNIConstants
{
  // number of JNI function entries
  static final int FUNCTIONCOUNT = 229 ;    

  // byte offset of saved jtoc at end of JNIFunctions array
  static final int JNIFUNCTIONS_JTOC_OFFSET = FUNCTIONCOUNT * 4;

  // index of IP in the AIX linkage triplet
  static final int IP = 0;                    

  // index of TOC in the AIX linage triplet
  static final int TOC = 1;                   

  // index of the named function in the array
  // (these are constant as defined by the JNI specification)
  static final int RESERVED0                     =    0;	
  static final int RESERVED1                     =    1;	
  static final int RESERVED2                     =    2;	
  static final int RESERVED3                     =    3;	
  static final int GETVERSION                    =    4;	
  static final int DEFINECLASS                   =    5;	
  static final int FINDCLASS                     =    6;	
  static final int FROMREFLECTEDMETHOD           =    7;	
  static final int FROMREFLECTEDFIELD            =    8;	
  static final int TOREFLECTEDMETHOD             =    9;	
  static final int GETSUPERCLASS                 =   10;	
  static final int ISASSIGNABLEFROM              =   11;	
  static final int TOREFLECTEDFIELD              =   12;	
  static final int THROW                         =   13;	
  static final int THROWNEW                      =   14;	
  static final int EXCEPTIONOCCURRED             =   15;	
  static final int EXCEPTIONDESCRIBE             =   16;	
  static final int EXCEPTIONCLEAR                =   17;	
  static final int FATALERROR                    =   18;	
  static final int PUSHLOCALFRAME                =   19;	
  static final int POPLOCALFRAME                 =   20;	
  static final int NEWGLOBALREF                  =   21;	
  static final int DELETEGLOBALREF               =   22;	
  static final int DELETELOCALREF                =   23;	
  static final int ISSAMEOBJECT                  =   24;	
  static final int NEWLOCALREF                   =   25;	
  static final int ENSURELOCALCAPACITY           =   26;	
  static final int ALLOCOBJECT                   =   27;	
  static final int NEWOBJECT                     =   28;	
  static final int NEWOBJECTV                    =   29;	
  static final int NEWOBJECTA                    =   30;	
  static final int GETOBJECTCLASS                =   31;	
  static final int ISINSTANCEOF                  =   32;	
  static final int GETMETHODID                   =   33;	
  static final int CALLOBJECTMETHOD              =   34;	
  static final int CALLOBJECTMETHODV             =   35;	
  static final int CALLOBJECTMETHODA             =   36;	
  static final int CALLBOOLEANMETHOD             =   37;	
  static final int CALLBOOLEANMETHODV            =   38;	
  static final int CALLBOOLEANMETHODA            =   39;	
  static final int CALLBYTEMETHOD                =   40;	
  static final int CALLBYTEMETHODV               =   41;	
  static final int CALLBYTEMETHODA               =   42;	
  static final int CALLCHARMETHOD                =   43;	
  static final int CALLCHARMETHODV               =   44;	
  static final int CALLCHARMETHODA               =   45;	
  static final int CALLSHORTMETHOD               =   46;	
  static final int CALLSHORTMETHODV              =   47;	
  static final int CALLSHORTMETHODA              =   48;	
  static final int CALLINTMETHOD                 =   49;	
  static final int CALLINTMETHODV                =   50;	
  static final int CALLINTMETHODA                =   51;	
  static final int CALLLONGMETHOD                =   52;	
  static final int CALLLONGMETHODV               =   53;	
  static final int CALLLONGMETHODA               =   54;	
  static final int CALLFLOATMETHOD               =   55;	
  static final int CALLFLOATMETHODV              =   56;	
  static final int CALLFLOATMETHODA              =   57;	
  static final int CALLDOUBLEMETHOD              =   58;	
  static final int CALLDOUBLEMETHODV             =   59;	
  static final int CALLDOUBLEMETHODA             =   60;	
  static final int CALLVOIDMETHOD                =   61;	
  static final int CALLVOIDMETHODV               =   62;	
  static final int CALLVOIDMETHODA               =   63;	
  static final int CALLNONVIRTUALOBJECTMETHOD    =   64;	
  static final int CALLNONVIRTUALOBJECTMETHODV   =   65;	
  static final int CALLNONVIRTUALOBJECTMETHODA   =   66;	
  static final int CALLNONVIRTUALBOOLEANMETHOD   =   67;	
  static final int CALLNONVIRTUALBOOLEANMETHODV  =   68;	
  static final int CALLNONVIRTUALBOOLEANMETHODA  =   69;	
  static final int CALLNONVIRTUALBYTEMETHOD      =   70;	
  static final int CALLNONVIRTUALBYTEMETHODV     =   71;	
  static final int CALLNONVIRTUALBYTEMETHODA     =   72;	
  static final int CALLNONVIRTUALCHARMETHOD      =   73;	
  static final int CALLNONVIRTUALCHARMETHODV     =   74;	
  static final int CALLNONVIRTUALCHARMETHODA     =   75;	
  static final int CALLNONVIRTUALSHORTMETHOD     =   76;	
  static final int CALLNONVIRTUALSHORTMETHODV    =   77;	
  static final int CALLNONVIRTUALSHORTMETHODA    =   78;	
  static final int CALLNONVIRTUALINTMETHOD       =   79;	
  static final int CALLNONVIRTUALINTMETHODV      =   80;	
  static final int CALLNONVIRTUALINTMETHODA      =   81;	
  static final int CALLNONVIRTUALLONGMETHOD      =   82;	
  static final int CALLNONVIRTUALLONGMETHODV     =   83;	
  static final int CALLNONVIRTUALLONGMETHODA     =   84;	
  static final int CALLNONVIRTUALFLOATMETHOD     =   85;	
  static final int CALLNONVIRTUALFLOATMETHODV    =   86;	
  static final int CALLNONVIRTUALFLOATMETHODA    =   87;	
  static final int CALLNONVIRTUALDOUBLEMETHOD    =   88;	
  static final int CALLNONVIRTUALDOUBLEMETHODV   =   89;	
  static final int CALLNONVIRTUALDOUBLEMETHODA   =   90;	
  static final int CALLNONVIRTUALVOIDMETHOD      =   91;	
  static final int CALLNONVIRTUALVOIDMETHODV     =   92;	
  static final int CALLNONVIRTUALVOIDMETHODA     =   93;	
  static final int GETFIELDID                    =   94;	
  static final int GETOBJECTFIELD                =   95;	
  static final int GETBOOLEANFIELD               =   96;	
  static final int GETBYTEFIELD                  =   97;	
  static final int GETCHARFIELD                  =   98;	
  static final int GETSHORTFIELD                 =   99;	
  static final int GETINTFIELD                   =  100;	
  static final int GETLONGFIELD                  =  101;	
  static final int GETFLOATFIELD                 =  102;	
  static final int GETDOUBLEFIELD                =  103;	
  static final int SETOBJECTFIELD                =  104;	
  static final int SETBOOLEANFIELD               =  105;	
  static final int SETBYTEFIELD                  =  106;	
  static final int SETCHARFIELD                  =  107;	
  static final int SETSHORTFIELD                 =  108;	
  static final int SETINTFIELD                   =  109;	
  static final int SETLONGFIELD                  =  110;	
  static final int SETFLOATFIELD                 =  111;	
  static final int SETDOUBLEFIELD                =  112;	
  static final int GETSTATICMETHODID             =  113;	
  static final int CALLSTATICOBJECTMETHOD        =  114;	
  static final int CALLSTATICOBJECTMETHODV       =  115;	
  static final int CALLSTATICOBJECTMETHODA       =  116;	
  static final int CALLSTATICBOOLEANMETHOD       =  117;	
  static final int CALLSTATICBOOLEANMETHODV      =  118;	
  static final int CALLSTATICBOOLEANMETHODA      =  119;	
  static final int CALLSTATICBYTEMETHOD          =  120;	
  static final int CALLSTATICBYTEMETHODV         =  121;	
  static final int CALLSTATICBYTEMETHODA         =  122;	
  static final int CALLSTATICCHARMETHOD          =  123;	
  static final int CALLSTATICCHARMETHODV         =  124;	
  static final int CALLSTATICCHARMETHODA         =  125;	
  static final int CALLSTATICSHORTMETHOD         =  126;	
  static final int CALLSTATICSHORTMETHODV        =  127;	
  static final int CALLSTATICSHORTMETHODA        =  128;	
  static final int CALLSTATICINTMETHOD           =  129;	
  static final int CALLSTATICINTMETHODV          =  130;	
  static final int CALLSTATICINTMETHODA          =  131;	
  static final int CALLSTATICLONGMETHOD          =  132;	
  static final int CALLSTATICLONGMETHODV         =  133;	
  static final int CALLSTATICLONGMETHODA         =  134;	
  static final int CALLSTATICFLOATMETHOD         =  135;	
  static final int CALLSTATICFLOATMETHODV        =  136;	
  static final int CALLSTATICFLOATMETHODA        =  137;	
  static final int CALLSTATICDOUBLEMETHOD        =  138;	
  static final int CALLSTATICDOUBLEMETHODV       =  139;	
  static final int CALLSTATICDOUBLEMETHODA       =  140;	
  static final int CALLSTATICVOIDMETHOD          =  141;	
  static final int CALLSTATICVOIDMETHODV         =  142;	
  static final int CALLSTATICVOIDMETHODA         =  143;	
  static final int GETSTATICFIELDID              =  144;	
  static final int GETSTATICOBJECTFIELD          =  145;	
  static final int GETSTATICBOOLEANFIELD         =  146;	
  static final int GETSTATICBYTEFIELD            =  147;	
  static final int GETSTATICCHARFIELD            =  148;	
  static final int GETSTATICSHORTFIELD           =  149;	
  static final int GETSTATICINTFIELD             =  150;	
  static final int GETSTATICLONGFIELD            =  151;	
  static final int GETSTATICFLOATFIELD           =  152;	
  static final int GETSTATICDOUBLEFIELD          =  153;	
  static final int SETSTATICOBJECTFIELD          =  154;	
  static final int SETSTATICBOOLEANFIELD         =  155;	
  static final int SETSTATICBYTEFIELD            =  156;	
  static final int SETSTATICCHARFIELD            =  157;	
  static final int SETSTATICSHORTFIELD           =  158;	
  static final int SETSTATICINTFIELD             =  159;	
  static final int SETSTATICLONGFIELD            =  160;	
  static final int SETSTATICFLOATFIELD           =  161;	
  static final int SETSTATICDOUBLEFIELD          =  162;	
  static final int NEWSTRING                     =  163;	
  static final int GETSTRINGLENGTH               =  164;	
  static final int GETSTRINGCHARS                =  165;	
  static final int RELEASESTRINGCHARS            =  166;	
  static final int NEWSTRINGUTF                  =  167;	
  static final int GETSTRINGUTFLENGTH            =  168;	
  static final int GETSTRINGUTFCHARS             =  169;	
  static final int RELEASESTRINGUTFCHARS         =  170;	
  static final int GETARRAYLENGTH                =  171;	
  static final int NEWOBJECTARRAY                =  172;	
  static final int GETOBJECTARRAYELEMENT         =  173;	
  static final int SETOBJECTARRAYELEMENT         =  174;	
  static final int NEWBOOLEANARRAY               =  175;	
  static final int NEWBYTEARRAY                  =  176;	
  static final int NEWCHARARRAY                  =  177;	
  static final int NEWSHORTARRAY                 =  178;	
  static final int NEWINTARRAY                   =  179;	
  static final int NEWLONGARRAY                  =  180;	
  static final int NEWFLOATARRAY                 =  181;	
  static final int NEWDOUBLEARRAY                =  182;	
  static final int GETBOOLEANARRAYELEMENTS       =  183;	
  static final int GETBYTEARRAYELEMENTS          =  184;	
  static final int GETCHARARRAYELEMENTS          =  185;	
  static final int GETSHORTARRAYELEMENTS         =  186;	
  static final int GETINTARRAYELEMENTS           =  187;	
  static final int GETLONGARRAYELEMENTS          =  188;	
  static final int GETFLOATARRAYELEMENTS         =  189;	
  static final int GETDOUBLEARRAYELEMENTS        =  190;	
  static final int RELEASEBOOLEANARRAYELEMENTS   =  191;	
  static final int RELEASEBYTEARRAYELEMENTS      =  192;	
  static final int RELEASECHARARRAYELEMENTS      =  193;	
  static final int RELEASESHORTARRAYELEMENTS     =  194;	
  static final int RELEASEINTARRAYELEMENTS       =  195;	
  static final int RELEASELONGARRAYELEMENTS      =  196;	
  static final int RELEASEFLOATARRAYELEMENTS     =  197;	
  static final int RELEASEDOUBLEARRAYELEMENTS    =  198;	
  static final int GETBOOLEANARRAYREGION         =  199;	
  static final int GETBYTEARRAYREGION            =  200;	
  static final int GETCHARARRAYREGION            =  201;	
  static final int GETSHORTARRAYREGION           =  202;	
  static final int GETINTARRAYREGION             =  203;	
  static final int GETLONGARRAYREGION            =  204;	
  static final int GETFLOATARRAYREGION           =  205;	
  static final int GETDOUBLEARRAYREGION          =  206;	
  static final int SETBOOLEANARRAYREGION         =  207;	
  static final int SETBYTEARRAYREGION            =  208;	
  static final int SETCHARARRAYREGION            =  209;	
  static final int SETSHORTARRAYREGION           =  210;	
  static final int SETINTARRAYREGION             =  211;	
  static final int SETLONGARRAYREGION            =  212;	
  static final int SETFLOATARRAYREGION           =  213;	
  static final int SETDOUBLEARRAYREGION          =  214;	
  static final int REGISTERNATIVES               =  215;	
  static final int UNREGISTERNATIVES             =  216;	
  static final int MONITORENTER                  =  217;	
  static final int MONITOREXIT                   =  218;	
  static final int GETJAVAVM                     =  219;	
  static final int GETSTRINGREGION               =  220;
  static final int GETSTRINGUTFREGION            =  221;
  static final int GETPRIMITIVEARRAYCRITICAL     =  222;	   
  static final int RELEASEPRIMITIVEARRAYCRITICAL =  223;	   
  static final int GETSTRINGCRITICAL             =  224;	   
  static final int RELEASESTRINGCRITICAL         =  225;	   
  static final int NEWWEAKGLOBALREF              =  226;	   
  static final int DELETEWEAKGLOBALREF           =  227;	       
  static final int EXCEPTIONCHECK                =  228;	   

						    
}

