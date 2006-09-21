/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * $Id$
 * RVM-specific external structure for JNI
 *
 *
 * @author Ton Ngo
 * @author Steve Smith
 * @date 5/20/00
 */


#define JNI_RESERVED0                            0 
#define JNI_RESERVED1		                 1 
#define JNI_RESERVED2		                 2 
#define JNI_RESERVED3		                 3 
#define JNI_GETVERSION		                 4 
#define JNI_DEFINECLASS 		         5     
#define JNI_FINDCLASS 		                 6 
#define JNI_RESERVED4		                 7 
#define JNI_RESERVED5		                 8 
#define JNI_RESERVED6		                 9 
#define JNI_GETSUPERCLASS 		        10     
#define JNI_ISASSIGNABLEFROM 	                11 
#define JNI_RESERVED7		                12 
#define JNI_THROW 			        13     
#define JNI_THROWNEW  		                14 
#define JNI_EXCEPTIONOCCURRED 	                15 
#define JNI_EXCEPTIONDESCRIBE 	                16 
#define JNI_EXCEPTIONCLEAR 		        17     
#define JNI_FATALERROR 		                18 
#define JNI_RESERVED8		                19 
#define JNI_RESERVED9		                20 
#define JNI_NEWGLOBALREF 		        21     
#define JNI_DELETEGLOBALREF 	                22 
#define JNI_DELETELOCALREF 		        23     
#define JNI_ISSAMEOBJECT 		        24     
#define JNI_RESERVED10		                25 
#define JNI_RESERVED11		                26 
#define JNI_ALLOCOBJECT 		        27     
#define JNI_NEWOBJECT 		                28 
#define JNI_NEWOBJECTV 		                29 
#define JNI_NEWOBJECTA 		                30 
#define JNI_GETOBJECTCLASS 		        31     
#define JNI_ISINSTANCEOF 		        32     
#define JNI_GETMETHODID 		        33     
#define JNI_CALLOBJECTMETHOD 	                34 
#define JNI_CALLOBJECTMETHODV 	                35 
#define JNI_CALLOBJECTMETHODA 	                36 
#define JNI_CALLBOOLEANMETHOD 	                37 
#define JNI_CALLBOOLEANMETHODV 	                38 
#define JNI_CALLBOOLEANMETHODA 	                39 
#define JNI_CALLBYTEMETHOD  	                40 
#define JNI_CALLBYTEMETHODV 	                41 
#define JNI_CALLBYTEMETHODA 	                42 
#define JNI_CALLCHARMETHOD  	                43 
#define JNI_CALLCHARMETHODV 	                44 
#define JNI_CALLCHARMETHODA 	                45 
#define JNI_CALLSHORTMETHOD 	                46 
#define JNI_CALLSHORTMETHODV 	                47 
#define JNI_CALLSHORTMETHODA 	                48 
#define JNI_CALLINTMETHOD  		        49     
#define JNI_CALLINTMETHODV 		        50     
#define JNI_CALLINTMETHODA 		        51     
#define JNI_CALLLONGMETHOD 		        52     
#define JNI_CALLLONGMETHODV 	                53 
#define JNI_CALLLONGMETHODA 	                54 
#define JNI_CALLFLOATMETHOD 	                55 
#define JNI_CALLFLOATMETHODV 	                56 
#define JNI_CALLFLOATMETHODA 	                57 
#define JNI_CALLDOUBLEMETHOD 	                58 
#define JNI_CALLDOUBLEMETHODV 	                59 
#define JNI_CALLDOUBLEMETHODA 	                60 
#define JNI_CALLVOIDMETHOD 		        61     
#define JNI_CALLVOIDMETHODV 	                62 
#define JNI_CALLVOIDMETHODA 	                63 
#define JNI_CALLNONVIRTUALOBJECTMETHOD          64 
#define JNI_CALLNONVIRTUALOBJECTMETHODV         65 
#define JNI_CALLNONVIRTUALOBJECTMETHODA         66 
#define JNI_CALLNONVIRTUALBOOLEANMETHOD         67 
#define JNI_CALLNONVIRTUALBOOLEANMETHODV        68 
#define JNI_CALLNONVIRTUALBOOLEANMETHODA        69 
#define JNI_CALLNONVIRTUALBYTEMETHOD            70 
#define JNI_CALLNONVIRTUALBYTEMETHODV           71 
#define JNI_CALLNONVIRTUALBYTEMETHODA           72 
#define JNI_CALLNONVIRTUALCHARMETHOD            73 
#define JNI_CALLNONVIRTUALCHARMETHODV           74 
#define JNI_CALLNONVIRTUALCHARMETHODA           75 
#define JNI_CALLNONVIRTUALSHORTMETHOD           76 
#define JNI_CALLNONVIRTUALSHORTMETHODV          77 
#define JNI_CALLNONVIRTUALSHORTMETHODA          78 
#define JNI_CALLNONVIRTUALINTMETHOD             79 
#define JNI_CALLNONVIRTUALINTMETHODV            80 
#define JNI_CALLNONVIRTUALINTMETHODA            81 
#define JNI_CALLNONVIRTUALLONGMETHOD            82 
#define JNI_CALLNONVIRTUALLONGMETHODV           83 
#define JNI_CALLNONVIRTUALLONGMETHODA           84 
#define JNI_CALLNONVIRTUALFLOATMETHOD           85 
#define JNI_CALLNONVIRTUALFLOATMETHODV          86 
#define JNI_CALLNONVIRTUALFLOATMETHODA          87 
#define JNI_CALLNONVIRTUALDOUBLEMETHOD          88 
#define JNI_CALLNONVIRTUALDOUBLEMETHODV         89 
#define JNI_CALLNONVIRTUALDOUBLEMETHODA         90 
#define JNI_CALLNONVIRTUALVOIDMETHOD            91 
#define JNI_CALLNONVIRTUALVOIDMETHODV           92 
#define JNI_CALLNONVIRTUALVOIDMETHODA           93 
#define JNI_GETFIELDID 		                94 
#define JNI_GETOBJECTFIELD 		        95     
#define JNI_GETBOOLEANFIELD 	                96 
#define JNI_GETBYTEFIELD 		        97     
#define JNI_GETCHARFIELD 		        98     
#define JNI_GETSHORTFIELD 		        99     
#define JNI_GETINTFIELD 		       100     
#define JNI_GETLONGFIELD 		       101     
#define JNI_GETFLOATFIELD 		       102     
#define JNI_GETDOUBLEFIELD 		       103     
#define JNI_SETOBJECTFIELD 		       104     
#define JNI_SETBOOLEANFIELD 	               105 
#define JNI_SETBYTEFIELD 		       106     
#define JNI_SETCHARFIELD 		       107     
#define JNI_SETSHORTFIELD 		       108     
#define JNI_SETINTFIELD 		       109     
#define JNI_SETLONGFIELD 		       110     
#define JNI_SETFLOATFIELD 		       111     
#define JNI_SETDOUBLEFIELD 		       112     
#define JNI_GETSTATICMETHODID 	               113 
#define JNI_CALLSTATICOBJECTMETHOD 	       114     
#define JNI_CALLSTATICOBJECTMETHODV            115 
#define JNI_CALLSTATICOBJECTMETHODA            116 
#define JNI_CALLSTATICBOOLEANMETHOD            117 
#define JNI_CALLSTATICBOOLEANMETHODV           118 
#define JNI_CALLSTATICBOOLEANMETHODA           119 
#define JNI_CALLSTATICBYTEMETHOD 	       120     
#define JNI_CALLSTATICBYTEMETHODV 	       121     
#define JNI_CALLSTATICBYTEMETHODA 	       122     
#define JNI_CALLSTATICCHARMETHOD 	       123     
#define JNI_CALLSTATICCHARMETHODV 	       124     
#define JNI_CALLSTATICCHARMETHODA 	       125     
#define JNI_CALLSTATICSHORTMETHOD 	       126     
#define JNI_CALLSTATICSHORTMETHODV 	       127     
#define JNI_CALLSTATICSHORTMETHODA 	       128     
#define JNI_CALLSTATICINTMETHOD 	       129     
#define JNI_CALLSTATICINTMETHODV 	       130     
#define JNI_CALLSTATICINTMETHODA 	       131     
#define JNI_CALLSTATICLONGMETHOD 	       132     
#define JNI_CALLSTATICLONGMETHODV 	       133     
#define JNI_CALLSTATICLONGMETHODA 	       134     
#define JNI_CALLSTATICFLOATMETHOD 	       135     
#define JNI_CALLSTATICFLOATMETHODV 	       136     
#define JNI_CALLSTATICFLOATMETHODA 	       137     
#define JNI_CALLSTATICDOUBLEMETHOD 	       138     
#define JNI_CALLSTATICDOUBLEMETHODV            139 
#define JNI_CALLSTATICDOUBLEMETHODA            140 
#define JNI_CALLSTATICVOIDMETHOD 	       141     
#define JNI_CALLSTATICVOIDMETHODV 	       142     
#define JNI_CALLSTATICVOIDMETHODA 	       143     
#define JNI_GETSTATICFIELDID 	               144 
#define JNI_GETSTATICOBJECTFIELD 	       145     
#define JNI_GETSTATICBOOLEANFIELD 	       146     
#define JNI_GETSTATICBYTEFIELD 	               147 
#define JNI_GETSTATICCHARFIELD 	               148 
#define JNI_GETSTATICSHORTFIELD 	       149     
#define JNI_GETSTATICINTFIELD 	               150 
#define JNI_GETSTATICLONGFIELD 	               151 
#define JNI_GETSTATICFLOATFIELD 	       152     
#define JNI_GETSTATICDOUBLEFIELD 	       153     
#define JNI_SETSTATICOBJECTFIELD 	       154     
#define JNI_SETSTATICBOOLEANFIELD 	       155     
#define JNI_SETSTATICBYTEFIELD 	               156 
#define JNI_SETSTATICCHARFIELD 	               157 
#define JNI_SETSTATICSHORTFIELD 	       158     
#define JNI_SETSTATICINTFIELD 	               159 
#define JNI_SETSTATICLONGFIELD 	               160 
#define JNI_SETSTATICFLOATFIELD 	       161     
#define JNI_SETSTATICDOUBLEFIELD 	       162     
#define JNI_NEWSTRING 		               163 
#define JNI_GETSTRINGLENGTH 	               164 
#define JNI_GETSTRINGCHARS 		       165     
#define JNI_RELEASESTRINGCHARS 	               166 
#define JNI_NEWSTRINGUTF 		       167     
#define JNI_GETSTRINGUTFLENGTH 	               168 
#define JNI_GETSTRINGUTFCHARS 	               169 
#define JNI_RELEASESTRINGUTFCHARS 	       170     
#define JNI_GETARRAYLENGTH 		       171     
#define JNI_NEWOBJECTARRAY 		       172     
#define JNI_GETOBJECTARRAYELEMENT 	       173     
#define JNI_SETOBJECTARRAYELEMENT 	       174     
#define JNI_NEWBOOLEANARRAY 	               175 
#define JNI_NEWBYTEARRAY 		       176     
#define JNI_NEWCHARARRAY 		       177     
#define JNI_NEWSHORTARRAY 		       178     
#define JNI_NEWINTARRAY 		       179     
#define JNI_NEWLONGARRAY 		       180     
#define JNI_NEWFLOATARRAY 		       181     
#define JNI_NEWDOUBLEARRAY 		       182     
#define JNI_GETBOOLEANARRAYELEMENTS            183 
#define JNI_GETBYTEARRAYELEMENTS 	       184     
#define JNI_GETCHARARRAYELEMENTS 	       185     
#define JNI_GETSHORTARRAYELEMENTS 	       186     
#define JNI_GETINTARRAYELEMENTS 	       187     
#define JNI_GETLONGARRAYELEMENTS 	       188     
#define JNI_GETFLOATARRAYELEMENTS  	       189     
#define JNI_GETDOUBLEARRAYELEMENTS 	       190     
#define JNI_RELEASEBOOLEANARRAYELEMENTS        191 
#define JNI_RELEASEBYTEARRAYELEMENTS           192 
#define JNI_RELEASECHARARRAYELEMENTS           193 
#define JNI_RELEASESHORTARRAYELEMENTS          194 
#define JNI_RELEASEINTARRAYELEMENTS            195 
#define JNI_RELEASELONGARRAYELEMENTS           196 
#define JNI_RELEASEFLOATARRAYELEMENTS          197 
#define JNI_RELEASEDOUBLEARRAYELEMENTS         198 
#define JNI_GETBOOLEANARRAYREGION 	       199     
#define JNI_GETBYTEARRAYREGION 	               200 
#define JNI_GETCHARARRAYREGION 	               201 
#define JNI_GETSHORTARRAYREGION 	       202     
#define JNI_GETINTARRAYREGION 	               203 
#define JNI_GETLONGARRAYREGION 	               204 
#define JNI_GETFLOATARRAYREGION 	       205     
#define JNI_GETDOUBLEARRAYREGION 	       206     
#define JNI_SETBOOLEANARRAYREGION 	       207     
#define JNI_SETBYTEARRAYREGION 	               208 
#define JNI_SETCHARARRAYREGION 	               209 
#define JNI_SETSHORTARRAYREGION 	       210     
#define JNI_SETINTARRAYREGION 	               211 
#define JNI_SETLONGARRAYREGION 	               212 
#define JNI_SETFLOATARRAYREGION 	       213     
#define JNI_SETDOUBLEARRAYREGION 	       214     
#define JNI_REGISTERNATIVES 	               215 
#define JNI_UNREGISTERNATIVES 	               216 
#define JNI_MONITORENTER 		       217     
#define JNI_MONITOREXIT 		       218     


/*************************************************
 * Synchronization block for the external pthread
 * and internal VM_Thread.  The external JNIEnv
 * contains a pointer to this block
 */

struct JNISynchronization {
  int functionIndex;
  int requestFlag;
  int callerFramePointer;
  int returnValue;
  int exitVM;
} JNISynchronization;
