/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id:&

/**
 * Given one or more trace files of values, extract trace information.
 *
 * More than one trace file can be specified on the command line, and if so
 * all the trace files are combined into a super in-memory trace file.
 * 
 * All trace files contain the name of a header file that contains the 
 * events and threads that were active when the program executed.

 *
 * @author Peter F. Sweeney
 * @date 1/28/2003
 */

import java.io.*;
import java.util.*;

class TraceFileReader
{
  static private int debug = 2;
  
  /*
   * constants
   */
  static private int    SIZE_OF_INT    = 4;
  static private int    SIZE_OF_LONG   = 8;
  static private int    SIZE_OF_HEADER = 24;    // VP, tid, start_wall_time + end_wall_time
  /*
    * For all processors, accumulate values
    */
  static private HashMap vp_trs      = new HashMap();  // Integer:VPID X TraceRecord
  static private HashMap tid_trs     = new HashMap();  // Integer:TID X TraceRecord
  static private HashMap vp_tids_trs = new HashMap();  // Integer:VPID X HashMap        

  // trace header 
  static private TraceHeader traceHeader = null;
  // number of value (a subset of which is HPM events)
  static private int n_values = 0;
  // version using
  static private int version = 3;
  // name of header file
  static private String header_filename = null;

  // subset of records we are interested in
  static private boolean subsetOnly = false;
  static private int start_record_number = -1;
  static private int end_record_number   = -1;

  // keep track of start wall clock times for each trace file.
  static private long[] start_wall_times = null;
  static public  long   start_wall_time  = 0;

  /*
    * Manage trace records for the start and completion of applications and application runs.
    */
  static private HashMap startApplication    = new HashMap(); // String:application X Integer:trace record number
  static private HashMap completeApplication = new HashMap(); // String:application X Integer:trace record number
  static private HashMap startApplicationRunIndex    = new HashMap(); // String:application X Integer:run number
  static private HashMap startApplicationRunMap      = new HashMap(); // String:application X int[run number] trace record number
  static private HashMap completeApplicationRunIndex = new HashMap(); // String:application X Integer: run number
  static private HashMap completeApplicationRunMap   = new HashMap(); // String:application X int[run number] trace record number

  static public CommandLineOptions options = null;
  /*
    * Main routine
    *
    * Process command line options. Open trace file.  Write contents.
    */
  static public void main(String args[]) 
  {
    options = new CommandLineOptions();
    if (args.length < 1) {
      options.usage();
    }
    options.processArgs(args, false);

    // looking for a subset of trace records?
    if (options.application != null || options.run != -1) {
      subsetOnly = true;
    }

    // At this point we know how many trace files there are
    //    Trace[] traces = new Trace[options.trace_file_index];
    // allocate array for start wall time.
    start_wall_times         = new long[options.trace_file_index];

    // contains the information from all traces
    Trace super_trace = null;

    // read in data for all the trace files
    for (int i=0; i<options.trace_file_index; i++) {

      TraceCounterRecord.start_time = 0;
      //      options.resetStatistics();
      String trace_filename = options.trace_filenames[i];
      if(options.debug>=1)System.out.println("main() start of iteration "+i+" with trace file \""+
                                             trace_filename+"\"");

      DataInputStream input_file = Utilities.openDataInputStream(trace_filename);

      // read version 
      readVersion(input_file, trace_filename);

      // read meta trace file name and process it
      readMetaFile(input_file, trace_filename);

      // read trace records
      TraceRecord[] records = readTraceRecords(input_file, trace_filename);

      start_wall_times[i] = trace_file_start_wall_time;

      Trace trace = new Trace(traceHeader, records);
      //      traces[i] = trace;
      if (super_trace == null) {
        super_trace = trace;
      } else {
        super_trace.merge(trace);
      }

      if(options.print_local) {
        //      if (options.generate_statistics) {
        //        options.reportGeneratedStatistics(traceHeader);
        //      }
        if (options.print_aggregate){
          aggregate(false);
        }
        if (options.print_aggregate_by_thread){
          aggregate(true);
        }
      }

      if(options.debug>=2)System.out.println("main() end of iteration "+i);
    } // end for i<trace_file_index

    if (options.print_structure) {
      build_structure(super_trace);
      print_structure();
    }

    //    if(options.generate_statistics){
    //      options.reportGeneratedStatistics(super_trace.header);
    //    }

    // process trace records
    if (options.print_trace || options.print_aggregate || options.print_aggregate_by_thread) {
      // determine what the start and end trace records should be.
      int start = find_start_record_number();
      int end   = find_end_record_number(super_trace.records.length);

      if (options.print_trace) {
        // find least start wall time and use from which to adjust all start wall times.
        start_wall_time = Long.MAX_VALUE;
        for (int i=0; i<options.trace_file_index; i++) {
          if (start_wall_times[i] < start_wall_time) {
            start_wall_time = start_wall_times[i];
          }
        }
        TraceCounterRecord.start_time = start_wall_time;
        System.out.println("Trace:");
      }

      for (int i=start; i<end; i++) {
        TraceRecord tr = super_trace.records[i];
        if (options.print_trace)
          printTraceRecord(tr, i);
        updateCummulativeValues(tr);
      }
      if (options.print_aggregate){
        aggregate(false);
      }
      if (options.print_aggregate_by_thread){
        aggregate(true);
      }
    }
  }
  
  /*
   * Find the start record number
   */
  static private int find_start_record_number()
  {
    if (options.application != null) {
      if (options.run == -1) {
        Integer Start = (Integer)startApplication.get(options.application);
        if (Start == null) {
          System.out.println("***find_start_record_number() Specified application \""+options.application+
                             "\" on command line; however, no trace record found for startApplication!***");
          System.out.println("\tTry the command line argument -structure to determine valid application values.\n");
          System.exit(1);
        }
        if(debug>=1)System.out.println("First trace record for application \""+options.application+
                                         "\": "+Start+".");
        return Start.intValue();
      } else { // if (options.run != -1)
        int[] start_map = (int[])startApplicationRunMap.get(options.application);
        if (start_map == null) {
          System.out.println("***Start Run Map for \""+options.application+"\" is null!***");
          System.exit(1);
        } 
        if (options.run >= start_map.length) {
          System.out.println("***Specified a run \""+options.run+"\" for application "+
                             options.application+" that was never recorded!  -run must be specfied less than "+start_map.length+".***");
          System.out.println("\tTry the command line argument -structure to determine valid application values.\n");
          System.exit(1);
        }
        if(debug>=1)System.out.println("First trace record for run "+options.run+" of application "+
                                       options.application+": "+start_map[options.run]+".");
        return start_map[options.run];
      } // end else (if options.run != -1)
    } // endif (options.application != null)
    // start at the beginning
    if(debug>=1)System.out.println("First trace record: 0");
    return 0;
  }
  /*
   * Find the end record number
   * @param default_last   if all else fails, return this
   */
  static private int find_end_record_number(int default_last)
  {
    if (options.application != null) {
      if (options.run == -1) {
        Integer Complete = (Integer)completeApplication.get(options.application);
        if (Complete == null) {
          System.out.println("***find_complete_record_number() Specified application "+options.application+
                             " on command line; however, no trace record found for its completion!***");
          System.out.println("\tTry the command line argument -structure to determine valid application values.\n");
          System.exit(1);
        }
        if(debug>=1)System.out.println("Last trace record for application "+options.application+
                                         ": "+Complete+".");
        return Complete.intValue();
      } else { // if (options.run != -1)
        int[] complete_map = (int[])completeApplicationRunMap.get(options.application);
        if (complete_map == null) {
          System.out.println("***Complete Run Map for \""+options.application+"\" is null!***");
          System.exit(1);
        } 
        if (options.run >= complete_map.length) {
          System.out.println("***Specified a run "+options.run+" for application "+
                             options.application+" that was never recorded!  -run must be specfied less than "+complete_map.length+".***");
          System.out.println("\tTry the command line argument -structure to determine valid application values.\n");
          System.exit(1);
        }
        if(debug>=1)System.out.println("Last trace record for run "+options.run+" of application "+
                                       options.application+": "+complete_map[options.run]+".");
        return complete_map[options.run];
      } // end else if (options.run != -1)
    } // endif (options.application != null)
    // end at the end
    if(debug>=1)System.out.println("Last trace record to consider "+default_last);
    return default_last;
  }
  /*
   * Factored out functionality.
    *
    * @param input_file     opened file
    * @param trace_filename name of opened file
   */
  static private void readVersion(DataInputStream input_file, String trace_filename)
  {
    if(options.debug>=2)System.out.print("readVersion("+trace_filename+") ");
    int local_version = Utilities.getIntFromDataInputStream(input_file);
    if(options.debug>=2)System.out.println(" reads "+local_version);
    if (version != local_version) {
      System.out.println("***"+trace_filename+"'s version "+local_version+" != "+version+"!***");
      System.exit(1);
    }
  }
  /*
    * read meta file and process it.
    * If more than one trace file is specified, read meta file only once.
    * Factored out functionality.
    *
    * @param input_file     opened file
    * @param trace_filename name of opened file
    */
  static private void readMetaFile(DataInputStream input_file, String trace_filename)
  {
    String local_header_filename = Utilities.getStringFromDataInputStream(input_file);
    if(options.debug>=2)System.out.println("readMetaFile("+trace_filename+") "+header_filename+"\n");
    if (header_filename == null) {
      traceHeader = new TraceHeader(local_header_filename, trace_filename);
      n_values = traceHeader.n_values;
      if (options.print_events)  traceHeader.printEvents();
      if (options.print_threads) traceHeader.printThreads();
      if (options.print_mids)    traceHeader.printMIDs();
      header_filename = local_header_filename;
    } else if (header_filename.compareTo(local_header_filename) != 0) {
      System.out.println("***"+trace_filename+"'s header filename \""+
                         local_header_filename+"\" != \""+header_filename+"\"!***");
      System.exit(1);
    }
  }
   
  /*
   * print structure of non-trace format records
   */
  static private void print_structure() 
  {
    System.out.println("print_structure()");
    Set keys = startApplication.keySet();
    for (Iterator i = keys.iterator(); i.hasNext(); ) {
      String app_name = (String)i.next();
      Integer n_trace_record = (Integer)startApplication.get(app_name);
      System.out.println("Start application \""+app_name+"\" at trace record "+n_trace_record);
    }
    keys = completeApplication.keySet();
    for (Iterator i = keys.iterator(); i.hasNext(); ) {
      String app_name = (String)i.next();
      Integer n_trace_record = (Integer)startApplication.get(app_name);
      System.out.println("Complete application \""+app_name+"\" at trace record "+n_trace_record);
    }
    keys = startApplicationRunIndex.keySet();
    for (Iterator i = keys.iterator(); i.hasNext(); ) {
      String app_name = (String)i.next();
      Integer max_index = (Integer)startApplicationRunIndex.get(app_name);
      System.out.println("start application \""+app_name+"\" total runs "+max_index);
      int [] map = (int []) startApplicationRunMap.get(app_name);
      for (int j = 0; j<max_index.intValue(); j++) {
        int trace_record = map[j];
        System.out.println("  run "+j+" at index "+trace_record);
      }
    }
    keys = completeApplicationRunIndex.keySet();
    for (Iterator i = keys.iterator(); i.hasNext(); ) {
      String app_name = (String)i.next();
      Integer max_index = (Integer)completeApplicationRunIndex.get(app_name);
      System.out.println("complete application \""+app_name+"\" runs "+max_index);
      int [] map = (int []) completeApplicationRunMap.get(app_name);
      for (int j = 0; j<max_index.intValue(); j++) {
        int trace_record = map[j];
        System.out.println(" run "+j+" at index "+trace_record);
      }
    }
  }
  /*
   * Read trace file records.
   * For each record read, update thread and processor cumulative values.
   * CONSTRAINT: the length of the returned array is the number of trace records!
   *
   * @param input_file      opened trace file
   * @param trace_filename  name of opened trace file (for error message)
   * @return array of trace records
   */
  static private int  trace_file_vpid = -1;
  static private long trace_file_start_wall_time = 0;

  static private TraceRecord[] readTraceRecords(DataInputStream input_file, String trace_filename)
  {
    TraceRecord[] records = new TraceRecord[2];
    int records_index = 0;
    TraceRecord tr;
    trace_file_vpid = -1;
    trace_file_start_wall_time = 0;
    while (true) {
      if (records.length <= records_index) {
        // grow trace record array
        int old_length = records.length;
        TraceRecord[] trs = new TraceRecord[records.length*2];
        for (int i=0; i<old_length; i++) {
          trs[i] = records[i];
        }
        records = trs;
      }

      // read record's fields
      tr = readTraceRecord(input_file);
      if (tr == null) break;
      records[records_index] = tr;

      // generate statistics
      //      if (options.generate_statistics && (tr instanceof TraceCounterRecord)) {
      //        options.generateStatistics((TraceCounterRecord)tr, records_index);
      //      }
      // write record's fields
      if (options.print_trace && options.print_local) {
        printTraceRecord(tr, records_index);
      }
      records_index++;

      //      updateCummulativeValues(tr);

    } // end while
    if(options.verbose>=1) System.out.println("readTraceRecords() read "+records_index+" trace records from "+trace_filename);

    // Create array with length is number of trace records
    TraceRecord[] r_records = new TraceRecord[records_index];
    for (int i=0; i<records_index; i++) { 
      r_records[i] = records[i];
    }
    return r_records;
  }
  /*
   * Update accumulators.
   * Factored code
   *
   * @param tr trace record
   */
  static private void updateCummulativeValues(TraceRecord tr)
  {
    if (! (tr instanceof TraceCounterRecord)) return;
    TraceCounterRecord tcr = (TraceCounterRecord)tr;
    
    // VPID cummulative values
    Integer VPID = new Integer(tcr.vpid);
    TraceCounterRecord vp_tr = (TraceCounterRecord)vp_trs.get(VPID);
    if (vp_tr == null) {        // add new HashMap
      vp_tr = new TraceCounterRecord(n_values);
      vp_tr.vpid = tcr.vpid;
      if(options.debug>=2)System.out.println("  vp_trs.put("+VPID+",vp_tr)");
      vp_trs.put(VPID,vp_tr);
    }
    vp_tr.accumulate(tcr);
    
    // get processor specific threads
    HashMap vp_tid_trs = (HashMap)vp_tids_trs.get(VPID);
    if (vp_tid_trs == null) {
      vp_tid_trs = new HashMap();
      if(options.debug>=2)System.out.println("  vp_tids_trs.put(    "+VPID+",vp_tid_trs)");
      vp_tids_trs.put(VPID,vp_tid_trs);
    }
    int tid = tcr.tid;
    if (tid < 0) tid = -tid;
    Integer TID = new Integer(tid);     // TID key
    // accumulate by TID and VPID
    TraceCounterRecord tid_tr = (TraceCounterRecord)vp_tid_trs.get(TID);
    if (tid_tr == null) {       // add new HashMap
      tid_tr = new TraceCounterRecord(n_values);
      tid_tr.tid = tcr.tid;
      if(options.debug>=2)System.out.println("  vp_tid_trs.put(    "+TID+",tid_trs)");
      vp_tid_trs.put(TID,tid_tr);
    }
    tid_tr.accumulate(tcr);
    
    // accumulate by TID
    tid_tr = (TraceCounterRecord)tid_trs.get(TID);
    if (tid_tr == null) {
      tid_tr = new TraceCounterRecord(n_values);
      tid_tr.tid = tcr.tid;
      if(options.debug>=2)System.out.println("  tid_trs.put(    "+TID+",tid_tr)");
      tid_trs.put(TID,tid_tr);
    }
    tid_tr.accumulate(tcr);
  }

  /*
   * Read the next record from the trace file.
   *
   * @param tr     where to put trace record data
   * @return boolean  true if read completed successfully, otherwise return false
   */
  static private TraceRecord readTraceRecord(DataInputStream input_file)
  {
    TraceRecord tr = null;
    
    try {
      int encoding = Utilities.getIntFromDataInputStream(input_file);
      if (encoding == -1) {        // EOF
        return tr;
      }
      if(options.debug>=5){System.out.print("encoding "+encoding);}
      int record_format = encoding & 0x0000000F;        // last 4 bits
      if (record_format == TraceRecord.COUNTER_TYPE) {
        TraceCounterRecord tcr = new TraceCounterRecord(n_values);
        tcr.local_tid     =  encoding >> 16;
        tcr.buffer_code   = (encoding >> 15) & 0x00000001;
        int bit           = (encoding >> 14) & 0x00000001;
        tcr.vpid          = (encoding >>  4) & 0x000003FF;
        tcr.thread_switch = (bit==1?true:false);
        tcr.tid           = Utilities.getIntFromDataInputStream(input_file);
        if(options.debug>=5){
          if (tcr.thread_switch) {
            System.out.print(tcr.buffer_code+" VPID "+tcr.vpid);
          } else {
            System.out.print(tcr.buffer_code+"*VPID "+tcr.vpid);
          }
          System.out.print(" TID "+tcr.tid+" LTID "+tcr.local_tid);
        }
        tcr.start_wall_time   = Utilities.getLongFromDataInputStream(input_file);
        // translate end wall time to relative wall time.
        long end_wall_time    = Utilities.getLongFromDataInputStream(input_file);
          tcr.values[0]         = end_wall_time - tcr.start_wall_time;
          //    }

        if(options.debug>=5){System.out.print(" EWT "+end_wall_time+" duration "+tcr.values[0]);}
        tcr.callee_MID        = Utilities.getIntFromDataInputStream(input_file);
        tcr.caller_MID        = Utilities.getIntFromDataInputStream(input_file);
        if(options.debug>=5){System.out.print(" mids: callee "+tcr.callee_MID+", caller "+tcr.caller_MID);}

        for (int i=1; i<tcr.n_values; i++) {
          tcr.values[i] = Utilities.getLongFromDataInputStream(input_file);
          if(options.debug>=5)System.out.print(" "+i+": "+tcr.values[i]);
        }
        if(options.debug>=5)System.out.println();
        // first Trace Counter Record read 
        if (trace_file_vpid == -1) {
          trace_file_vpid = tcr.vpid;                           // cache vpid
          trace_file_start_wall_time = tcr.start_wall_time;
          TraceCounterRecord.start_time = tcr.start_wall_time;
          if (debug>=3) {
            System.out.println(trace_file_vpid+" start wall clock time "+trace_file_start_wall_time);
          }
        } else { // check that all vpid in a trace file are the same!
          if (trace_file_vpid != tcr.vpid) {
            System.out.println("***readTraceRecord: trace_file_vpid "+trace_file_vpid+" != record vpid "+tcr.vpid+"!***");
            System.exit(1);
          }
        }
        tr = tcr;
      } else if (record_format == TraceRecord.START_APP_TYPE) {
        String app_name = Utilities.getStringFromDataInputStream(input_file);
        if(options.debug>=1)System.out.println("  VPID "+trace_file_vpid+" START_APP_TYPE: app "+app_name);
        tr = new TraceStartAppRecord(trace_file_vpid, app_name);
      } else if (record_format == TraceRecord.COMPLETE_APP_TYPE) {
        String app_name = Utilities.getStringFromDataInputStream(input_file);
        if(options.debug>=4)System.out.println("  VPID "+trace_file_vpid+" COMPLETE_APP_TYPE: app "+app_name);
        tr = new TraceCompleteAppRecord(trace_file_vpid, app_name);
        
      } else if (record_format == TraceRecord.START_APP_RUN_TYPE) {
        int  run = Utilities.getIntFromDataInputStream(input_file);
        String app_name = Utilities.getStringFromDataInputStream(input_file);
        if(options.debug>=4)System.out.println("  VPID "+trace_file_vpid+" START_APP_RUN_TYPE: app "+app_name+", run "+run);
        tr = new TraceStartAppRunRecord(trace_file_vpid, app_name, run);

      } else if (record_format == TraceRecord.COMPLETE_APP_RUN_TYPE) {
        int  run = Utilities.getIntFromDataInputStream(input_file);
        String app_name = Utilities.getStringFromDataInputStream(input_file);
        if(options.debug>=4)System.out.println("  VPID "+trace_file_vpid+" COMPLETE_APP_RUN_TYPE: app "+app_name+", run "+run);
        Integer Index = (Integer)completeApplicationRunIndex.get(app_name);
        tr = new TraceCompleteAppRunRecord(trace_file_vpid, app_name, run);

      } else if (record_format == TraceRecord.EXIT_TYPE) {
        int  value = Utilities.getIntFromDataInputStream(input_file);      // value
        if(options.debug>=4)System.out.println("  VPID "+trace_file_vpid+" EXIT_TYPE: value "+value);
        tr = new TraceExitRecord(trace_file_vpid, value);

      } else if (record_format == TraceRecord.PADDING_TYPE) {
        int  length = Utilities.getIntFromDataInputStream(input_file);     // value
        if(options.debug>=4)System.out.println("  VPID "+trace_file_vpid+" PADDING_TYPE: length "+length);
        // gobble up padding
        for (int i=0; i<length; i++) {
          input_file.readByte();
        }

        tr = new TracePaddingRecord(trace_file_vpid, length);

      } else {
        System.out.println("***TraceFileReader.readTraceRecord() record format "+record_format+
                           " unknown with encoding "+encoding+"!***");
        int value;
        int BOUND = 100;
        System.out.print("***"+BOUND+" additional values: ");
        for (int i = 0; i<BOUND; i++) {
          value = Utilities.getIntFromDataInputStream(input_file);
          System.out.print(" "+value);
        }
        System.out.println("***");
        //      System.exit(1);
      }

    } catch (EOFException e) {
      if(options.debug>=4)System.out.println("!!!EOF in readTraceRecord!!!");
      return null;
    } catch (IOException e) {
      System.out.println("*** IO Exception in readTraceRecord!***");
      System.exit(1);
    }

    return tr;
  }

  /*
   * Read the next record from the trace file.
   *
   * @param records   array of trace records
   * @return boolean  true if read completed successfully, otherwise return false
   */
  static private void build_structure(Trace trace)
  {
    TraceRecord[] records = trace.records;
    if(options.debug>=3)System.out.println("build_structure() n_records = "+records.length);
    for (int n_records=0; n_records<records.length; n_records++) {
      TraceRecord tr = records[n_records];
      if (tr == null) return;
    
      if (tr instanceof TraceCounterRecord || 
          tr instanceof TracePaddingRecord ||
          tr instanceof TraceExitRecord   ) {
        // do nothing
      } else if (tr instanceof TraceStartAppRecord) {
        TraceStartAppRecord tsar = (TraceStartAppRecord)tr;
        if(options.debug>=3)
          System.out.println("Start    application "+tsar.app_name+" at record "+n_records);
        startApplication.put(tsar.app_name, new Integer(n_records));
        
      } else if (tr instanceof TraceCompleteAppRecord) {
        TraceCompleteAppRecord tcar = (TraceCompleteAppRecord)tr;
        if(options.debug>=3)
          System.out.println("Complete application "+tcar.app_name+" at record "+n_records);
        completeApplication.put(tcar.app_name, new Integer(n_records));
        
      } else if (tr instanceof TraceStartAppRunRecord) {
        TraceStartAppRunRecord tsarr = (TraceStartAppRunRecord)tr;
        if(options.debug>=3)
          System.out.print("Start    application "+tsarr.app_name+" run "+tsarr.run+" at record "+n_records);
        Integer Index = (Integer)startApplicationRunIndex.get(tsarr.app_name);
        int [] map = null;
        if (Index == null) {
          // first time run found for this application
          if(options.debug>=3)System.out.println(" first run ");
          Index = new Integer(tsarr.run+1);
          map = new int[tsarr.run+1];
          map[tsarr.run] = n_records;
        } else {
          if(options.debug>=3)System.out.println();
          Index = new Integer(tsarr.run+1);
          map = (int[])startApplicationRunMap.get(tsarr.app_name);
          map = Utilities.addIntArrayElement(map, n_records, tsarr.run);
        }
        startApplicationRunIndex.put(tsarr.app_name, Index);
        startApplicationRunMap.put(  tsarr.app_name, map);

      } else if (tr instanceof TraceCompleteAppRunRecord) {
        TraceCompleteAppRunRecord tcarr = (TraceCompleteAppRunRecord)tr;
        if(options.debug>=3)
          System.out.print("Complete application "+tcarr.app_name+" run "+tcarr.run+" at record "+n_records);
        Integer Index = (Integer)completeApplicationRunIndex.get(tcarr.app_name);
        int [] map = null;
        if (Index == null) {
          // first time run found for this application
          if(options.debug>=3)System.out.println(" first run found!");
          Index = new Integer(tcarr.run+1);
          map = new int[tcarr.run+1];
          map[tcarr.run] = n_records;
        } else {
          if(options.debug>=3)System.out.println();
          Index = new Integer(tcarr.run+1);
          map = (int[])completeApplicationRunMap.get(tcarr.app_name);
          map = Utilities.addIntArrayElement(map, n_records, tcarr.run);
        }
        completeApplicationRunIndex.put(tcarr.app_name, Index);
        completeApplicationRunMap.put(  tcarr.app_name, map);
      } else {
        System.out.println("***TraceFileReader.build_structure() unknown trace record format!***");
        //      System.exit(1);
      }
    } // end for
  }

  /*
   * Write the trace file's record
   * @param tr    trace record to be printed
   * @param index index of record printed
   */
  static private void printTraceRecord(TraceRecord tr, int index)
  {
    if (tr instanceof TraceCounterRecord) {
      TraceCounterRecord tcr = (TraceCounterRecord)tr;
      if (options.tid != CommandLineOptions.UNINITIALIZED && options.tid != Math.abs(tcr.tid)) return;
      if (options.no_yields && tcr.tid < 0) return;
      if(options.verbose>=1) {
        System.out.print(index);
      }
      if(options.verbose>=3) System.out.print(tcr.buffer_code+" ");
      tcr.print();
    } else {
      if(options.verbose>=1) System.out.print(index+" ");
      tr.print();
    }
  }

  /*
   * Dump processor and processor specific thread values.
   *
   * @param by_thread     aggregate by thread (without differentiating by processor)
   */
  static private void aggregate(boolean by_thread)
  {
    if(options.debug>=1)System.out.println("TraceFileReader.aggregate("+by_thread+","+options.tid+")");
    TraceCounterRecord  vp_sum_tr = new TraceCounterRecord(traceHeader.n_values);
    TraceCounterRecord tid_sum_tr = new TraceCounterRecord(traceHeader.n_values);

    if (traceHeader.groupNumber() != -1) {
      // find totals across all threads.
      TraceCounterRecord total_tr = new TraceCounterRecord(traceHeader.n_values);
      for (Iterator keys = vp_trs.keySet().iterator(); keys.hasNext(); ) {
        Integer VPID = (Integer)keys.next();
        TraceCounterRecord p_tr = (TraceCounterRecord)vp_trs.get(VPID);
        total_tr.accumulate(p_tr);
      }
      if(options.debug>=1) {
        System.out.println("TraceFileReader.aggregate() total across all threads ");
        total_tr.print();
      }
    }

    if (by_thread) {
      System.out.println("\nDump HPM counter values for VPIDs (aggregate threads)");
      int p_trips = 0;
      for (Iterator keys = vp_trs.keySet().iterator(); keys.hasNext(); ) {
        Integer VPID = (Integer)keys.next();
        System.out.print(" VPID: "+VPID+"     ");
        TraceCounterRecord p_tr = (TraceCounterRecord)vp_trs.get(VPID);
        if (p_tr == null) {
          System.err.println("***aggregate() vp_trs.get("+VPID+") == null!***");
          continue;
        }
        p_tr.printCPI(traceHeader);
        System.out.println();
        p_tr.print(traceHeader);
        System.out.println();
        vp_sum_tr.accumulate(p_tr);
        p_trips++;
      }
      if (p_trips > 1) {
        System.out.print("Dump aggregate HPM counter values for VPIDs");
        vp_sum_tr.printCPI(traceHeader);
        System.out.println();
        vp_sum_tr.print(traceHeader);
        System.out.println();
      }
    
      System.out.println("\nDump aggregate HPM counter values per thread");
      tid_sum_tr.reset();
      int t_trips = 0;
      for (Iterator keys = tid_trs.keySet().iterator(); keys.hasNext(); ) {
        Integer TID = (Integer)keys.next();
        System.out.println(" TID "+TID);        
        TraceCounterRecord tcr = (TraceCounterRecord)tid_trs.get(TID);
        if (tcr == null) {
          System.out.println("***tid_trs.get("+TID+") == null!***");
          System.exit(1);
        }
        int tid = TID.intValue();
        if(options.debug>=1)System.out.println("TraceFileReader.aggregate() tid "+tid);
        if (options.tid == CommandLineOptions.UNINITIALIZED || options.tid == Math.abs(tid)) {
          String thread_name = traceHeader.threadName(tid);
          System.out.print(" ThreadIndex: " + tid + " "+ thread_name + "     ");
          tcr.printCPI(traceHeader);
          System.out.println();
          tcr.print(traceHeader);
          System.out.println();
          tid_sum_tr.accumulate(tcr);
          t_trips++;
        }
      }
      if(t_trips > 1) {
        System.out.print("\nAggregate HPM counter values across threads     ");
        tid_sum_tr.printCPI(traceHeader);
        System.out.println();
        tid_sum_tr.print(traceHeader);
        System.out.println();
      }
    } else {
      // System.out.println("\nDump HPM counter values for VPIDs");
      tid_sum_tr.reset();
      int p_trips = 0;
      for (Iterator keys = vp_trs.keySet().iterator(); keys.hasNext(); ) {
        Integer VPID = (Integer)keys.next();
        System.out.print("\n VPID: "+VPID+"     ");
        TraceCounterRecord p_tcr = (TraceCounterRecord)vp_trs.get(VPID);
        if (p_tcr == null) {
          System.err.println("***aggregate() vp_trs.get("+VPID+") == null!***");
          continue;
        }
        p_tcr.printCPI(traceHeader);
        System.out.println();
        p_tcr.print(traceHeader);
        System.out.println();
        vp_sum_tr.accumulate(p_tcr);
        p_trips++;

        HashMap tids = (HashMap)vp_tids_trs.get(VPID);
        if (tids == null) {
          System.out.println("***aggregate() vp_tids_trs.get("+VPID+") == null!***");
          continue;
        }
        int t_trips = 0;
        //      System.out.println("\nDump aggregate HPM counter values per thread");
        for (Iterator t_keys = tids.keySet().iterator(); t_keys.hasNext();) {
          Integer TID = (Integer)t_keys.next();
          TraceCounterRecord tid_tcr = (TraceCounterRecord)tids.get(TID);
          if (tid_tcr == null) {
            System.err.println("***aggregate() vp_trs.get("+TID+") == null!***");
            continue;
          }
          int tid = TID.intValue();
          if (options.tid == CommandLineOptions.UNINITIALIZED || options.tid == Math.abs(tid)) {
            tid_sum_tr.accumulate(tid_tcr);
            String thread_name = traceHeader.threadName(tid);
            System.out.print(" ThreadIndex: " + tid + " "+ thread_name + "     ");
            tid_tcr.printCPI(traceHeader);
            System.out.println();
            tid_tcr.print(traceHeader);
            System.out.println();
            t_trips++;
          }
        }
        if(t_trips > 1) {
          System.out.print("\nAggregate HPM counter values across threads");
          tid_sum_tr.printCPI(traceHeader);
          System.out.println();
          tid_sum_tr.print(traceHeader);
          System.out.println();
        }
        tid_sum_tr.reset();
      }
      if (p_trips>1) {
        System.out.print("\nDump aggregate HPM counter values for VPIDs");
        vp_sum_tr.printCPI(traceHeader);
        System.out.println();
        vp_sum_tr.print(traceHeader);
        System.out.println();
      }
    }
  }

}
