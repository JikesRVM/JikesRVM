/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id:&

/**
 * HPM meta information.
 *
 * The header trace files contents are:
 *  version number(int), number of counters(int), mode(int),
 *  one of three possible record types
 *  1) machine type record
 *    record_format_type(int), length(int), String
 *  2) event record
 *    record_format_type(int), length(int), String
 *  3) thread record
 *    record_format_type(int), length(int), String
 *
 * @author Peter F. Sweeney 
 * @date 2/14/2003
 */

import java.io.*;
import java.util.*;

public final class TraceHeader
{
  public static final int debug = 0;

  /*
   * Must be kept consistent with VM_HardwarePerformanceMonitors
   */
  // trace record formats  
  static public  int MACHINE_TYPE_FORMAT = 1;
  static public  int        EVENT_FORMAT = 2;
  static public  int       THREAD_FORMAT = 3;
  static public  int       METHOD_FORMAT = 4;

  // What endian is used?  Default is big-endian, Intel is little-endian.
  static public  int              DEFAULT_ENDIAN  = 0;
  static public  int              BIG_ENDIAN      = DEFAULT_ENDIAN;
  static public  int              LITTLE_ENDIAN   = 1;
  static public  int              endian          = 0;
  static public  boolean isLittleEndian() {
    return endian == LITTLE_ENDIAN;
  }
  /*
   * Version number
   */
  public int version = 1;
  // trace file name
  public String filename      = null;           // trace file name
  // monitoring mode
  public  int              mode            = 0;
  /*
   * Machine Type
   */
  private String processor_name = null;         // processor name
  public  boolean isPower4() {
    if (processor_name == null) return false;
    return processor_name.compareTo("POWER4") == 0;
  }
  public int groupNumber() {
    if (isPower4()) {
      return ids[1];
    }
    return -1;
  }

  public boolean isPower3() {
    if (processor_name == null) return false;
    return processor_name.compareTo("POWER3") == 0;
  }
  public boolean is604e() {
    if (processor_name == null) return false;
    return processor_name.compareTo("604e") == 0;
  }
  public boolean isRS64_III() {
    if (processor_name == null) return false;
    return processor_name.compareTo("RS64-III") == 0;
  }

  /*
   * Per counter data.  HPM counters start at 1.
   * The 0th entry is for real time taken to execute the thread.
   */
  // number of counters
  public int    n_counters = 0;
  // short name description for event
  public String  []short_event_names;
  // event id
  public int     []ids;
  /*
   * The following arrays are not used.
   */
  // event status; i.e. verified, unverified, ...
  public int     []status;
  // thresholdable event
  public boolean []thresholdable;


  /*
   * Per thread data.
   * off by one can access thread_names only through methods.
   */
  private String  []threads;
  private int     []globalToLocalTIDs;
  public  int     n_threads;
  public String threadName(int i) {
    if (i > n_threads+1) {
      System.out.println("***TraceHeader.threads("+i+") > n_threads "+(n_threads+1)+"!***");
      new Exception().printStackTrace();
      return "NO_THREAD_NAME";
      //      System.exit(-1);
    }
    return threads[i];
  }
  static private TreeMap MID_map = new TreeMap();
  static public  String getFullMIDName(int mid) {
    String full_name = (String)MID_map.get(new Integer(mid));
    if (full_name == null) {
      System.out.println("***MID_map("+mid+") == null!***");
    }
    return full_name;
  }

  /**
   * Constructor
   *
   * @param header_filename  name of header file
   * @param trace_filename   name of trace file that contained header file name
   */
  public TraceHeader(String header_filename, String trace_filename) 
  {
    filename = trace_filename;

    DataInputStream input_file = openTraceMetaFile(header_filename, trace_filename);
    version    = Utilities.getIntFromDataInputStream(input_file);
    endian     = Utilities.getIntFromDataInputStream(input_file);
    n_counters = Utilities.getIntFromDataInputStream(input_file);
    mode       = Utilities.getIntFromDataInputStream(input_file);
    
    if(debug>=1) System.out.println(" version "+version+", n_counters "+n_counters+", mode "+mode+", endian "+endian);

    int n_elements      = n_counters+1;
    // allocate and initialize arrays
    short_event_names   = new String[ n_elements];
    ids                 = new int[    n_elements];
    status              = new int[    n_elements];
    thresholdable       = new boolean[n_elements];
    for (int i=0; i<n_elements; i++) {
      ids[                i] = 0;
      short_event_names[  i] = null;
      status[       i] = -1;
      thresholdable[i] = false;
    }
    // read records from trace file
    HashMap map     = new HashMap();    // Global TID (Integer) X thread name(String)
    HashMap mapg2l  = new HashMap();    // Global TID (Integer) X TID (Integer)
    int trips = 0;
    while(true) {
      int record_type = Utilities.getIntFromDataInputStream(input_file);

      if (record_type == Utilities.EOF_int) {
        break;
      } else if (record_type == MACHINE_TYPE_FORMAT) {
        processor_name = Utilities.getStringFromDataInputStream(input_file);
        if (debug>=3) System.out.println(trips+": TraceHeader() MACHINE_TYPE_RECORD "+processor_name);
      } else if (record_type == EVENT_FORMAT) {
        int counter_number = Utilities.getIntFromDataInputStream(input_file);
        if (counter_number >= n_elements) {
          System.out.println(trips+": TraceHeader("+trace_filename+") counter number "+
                             counter_number+" > n_elements "+n_elements);
          System.exit(-1);
        }
        ids[counter_number] =  Utilities.getIntFromDataInputStream(input_file);
        short_event_names[counter_number] = Utilities.getStringFromDataInputStream(input_file);
        if (debug>=3) System.out.println("TraceHeader() EVENT_RECORD "+counter_number+" = "+
                                         ids[counter_number]+" : "+short_event_names[counter_number]);
      } else if (record_type == THREAD_FORMAT) {
        int tid       = Utilities.getIntFromDataInputStream(input_file);
        int local_tid = Utilities.getIntFromDataInputStream(input_file);
        if (n_threads < tid) n_threads = tid;
        String thread_name = Utilities.getStringFromDataInputStream(input_file);
        Integer TID = new Integer(tid);
        map.put(TID, thread_name);
        if (debug>=3) System.out.println(trips+": TraceHeader() THREAD_RECORD global tid "+tid+" : "+
                                         local_tid+" : "+thread_name);
        Integer LOCAL_TID = new Integer(local_tid);
        mapg2l.put(TID,LOCAL_TID);
      } else if (record_type == METHOD_FORMAT) {
        int    mid               = Utilities.getIntFromDataInputStream(input_file);
        String  class_name       = Utilities.getStringFromDataInputStream(input_file);
        String method_name       = Utilities.getStringFromDataInputStream(input_file);
        String method_descriptor = Utilities.getStringFromDataInputStream(input_file);
        String full_name = class_name+"."+method_name+method_descriptor;
        if (debug>=3) System.out.println(trips+": TraceHeader() METHOD_RECORD mid "+mid+" : "+full_name);
        MID_map.put(new Integer(mid), full_name);
      }
      trips++;
    }
    // allocate thread array.
    threads = new String[n_threads+1];
    for (int i = 0; i<=n_threads; i++) {
      threads[i] = null;
    }
    if (debug>=3)
      System.out.println("\nDump global thread id to thread name map:");
    Set keys = map.keySet();
    for (Iterator i = keys.iterator(); i.hasNext();) {
      Integer Key = (Integer)i.next();
      String  thread_name = (String)map.get(Key);
      int key = Key.intValue();
      if (key >=0) {
        threads[key] = thread_name;
        if(debug>=3)System.out.println(" threads["+key+"] = "+thread_name);
      } else {
        if(debug>=3)System.out.println(" threads["+key+"] = "+thread_name+" discarded!" );
      }
    }
    // allocate thread array.
    globalToLocalTIDs = new int[n_threads+1];
    if (debug>=3)
      System.out.println("\nDump global to local thread id map:");
    keys = mapg2l.keySet();
    for (Iterator i = keys.iterator(); i.hasNext();) {
      Integer Key = (Integer)i.next();
      Integer Local_Tid = (Integer)mapg2l.get(Key);
      int tid = Key.intValue();
      if (tid >= 0) {
        globalToLocalTIDs[tid] = Local_Tid.intValue();
        if(debug>=3)System.out.println(" globalToLocalTIDs["+tid+"] = "+Local_Tid);
      } else {
        if(debug>=3)System.out.println(" globalToLocalTIDs["+tid+"] = "+Local_Tid+" discarded!");
      }
    }

    if (debug>=2) print();
  }
  /**
   * Open trace meta file
   * Use path from trace_filename to prepend to header_filename.
   * Strip header_filename of trailing spaces.
   *
   * @param header_filename  name of trace meta file
   * @param trace_filename   name of trace file
   */
  private DataInputStream openTraceMetaFile(String header_filename, String trace_filename)
  {
    if(debug>=2)System.out.println("TraceHeader.openTraceMetaFile("+header_filename+","+trace_filename+")");
    String path = null;
    int index = trace_filename.lastIndexOf('/');
    if (index == -1) {
      path = "";
    } else {
      //      System.out.println("TH.openTraceMetaFile("+header_filename+", "+trace_filename+") index "+index);
      // temporary hack to get rid of trailing "data".
      //      index = trace_filename.lastIndexOf('/',index-1);
      //      System.out.println("trace_filename.lastIndexOf('/',"+(index)+") == "+index);
      
      path = trace_filename.substring(0,index);
      path += "/";
      System.out.println("TraceHeader.openTraceMetaFile() path \""+path+"\"");
    }
    index = header_filename.lastIndexOf(' ');
    if (index != -1) 
      header_filename = header_filename.substring(0,index);
    if(debug>=2)
      System.out.println("openTraceMetaFile() calls Utilities.openDataInputStream("+(path + header_filename)+")");
    return Utilities.openDataInputStream(path + header_filename);
  }
  /**
   * print trace header.
   */
  public void print(){
    printEvents();
    printThreads();
  }
  /** 
   * print names for all events
   */
  public void print_short_event_names(){
    for (int i=0; i<=n_counters; i++) {
      System.out.println(short_event_names[i]+" ");
    }
  }
  /**
   * return short name of the ith event
   */
  public String short_event_name(int i){
    if (i>n_counters) {
      System.err.println("***TraceHeader.short_event_name("+i+") "+i+" > number of counters "+
                         n_counters+"!***");
      System.exit(-1);
    }
    return short_event_names[i];
  }
  /**
   * print events
   */
  public void printEvents() 
  {
    System.out.println("\n"+processor_name+" has "+n_counters+" counters and mode = "+mode+", events:");
    for (int i=0; i<=n_counters; i++) {
      //      System.out.println(i+": "+short_event_names[i]+": "+ids[i]+", "+status[i]+", "+thresholdable[i]+";");
      System.out.println(i+": "+short_event_names[i]+": "+ids[i]);
    }
  }
  /**
   * print threads
   */
  public void printThreads(){
    System.out.println("Threads:");
    for (int i=0; i<=n_threads; i++) {
      if (threads[i] == null) {
        //      System.out.println(i+" (0): null");
      } else {
        System.out.println(i+" ("+globalToLocalTIDs[i]+"): "+threads[i]);
      }
    }
  }
  /**
   * print MIDs
   */
  public void printMIDs() 
  {
    int size = MID_map.size();
    System.out.println("MIDs: "+size);
    
    Iterator iterator = MID_map.keySet().iterator();
    Integer MID = null;
    for ( ; iterator.hasNext(); MID = (Integer)iterator.next()) {
      if (MID != null) {
        String full_name = (String)MID_map.get(MID);
        System.out.println(MID+": \""+full_name+"\"");
      }
    }
  }
  /*
   * Combine this trace header with the argument.
   * CONSTRAINT: current.n_threads > 0
   *
   * @param current  current trace header to be combined
   *
  public void combine(TraceHeader current)
  {
    if(debug>=2) {
      System.out.println("TraceHeader.combine() print current");
      current.print();
      System.out.println("TraceHeader.combine() print this");
      print();
    }
    if (processor_name == null) {
      System.out.println("TraceHeader.combine() super trace header is null");
      processor_name = current.processor_name;
      mode = current.mode;
      n_counters = current.n_counters;
      short_event_names = current.short_event_names;
      ids = current.ids;
      status = current.status;
      thresholdable = current.thresholdable;
      threads = current.threads;
      n_threads = current.n_threads;
    } else {
      if (processor_name.compareTo(current.processor_name) != 0) {
        System.out.println("***TraceHeader.combine() processor name mismatch \""+processor_name+
                           "\" != \""+current.processor_name+"\"!***");
        System.exit(-1);
      }
      if (mode != current.mode) {
        System.out.println("***TraceHeader.combine() mode mismatch "+mode+" != "+current.mode+"!***");
        System.exit(-1);
      }
      if (n_counters != current.n_counters) {
        System.out.println("***TraceHeader.combine() number of counters mismatch "+n_counters+
                           " != "+current.n_counters+"!***");
        System.exit(-1);
      }
      for (int i=1; i<=n_counters; i++) {
        if (short_event_names[i].compareTo(current.short_event_names[i]) != 0) {
          System.out.println("***TraceHeader.combine() "+i+"th event name mismatch \""+
                             short_event_names[i]+"\" != \""+current.short_event_names[i]+"\"!***");
          System.exit(-1);
        }
      }
      combineTraceThreads(current);
    }
  }

  /**
   * Combine current trace header into super.
   *
   * @param super   combination of all trace headers
   * @param current current trace header   
   *
  public void combineTraceThreads(TraceHeader current)
  {
    if (threads == null) {
      threads   = current.threads;
      n_threads = current.n_threads;
      return;
    }
    int min_threads = java.lang.Math.min(n_threads, current.n_threads);
    for (int i=0; i<min_threads; i++) {
      if (threads[i].compareTo(current.threads[i]) != 0) {
        System.out.println("***TraceHeader.combine() "+i+"th thread name mismatch \""+
                           threads[i]+"\" != \""+current.threads[i]+"\"!***");
        System.exit(-1);
      }
    }
    if (current.n_threads > n_threads) {
      threads   = current.threads;
      n_threads = current.n_threads;
    }
  }
*/
}

