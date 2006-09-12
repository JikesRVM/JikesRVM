/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id:&

/**
 * Represent a trace that is read from a trace file.
 * <p>
 * The trace files contents are:
 *  version number(int), length(int), headerFilename(String)
 *  followed by one or more of:
 *  1) trace record
 *    encoding(int), hpm_tid(int), start_wall_time(long), end_wall_time(long), counters(long)*
 *  where the number of counters is machine dependent and encoding contains
 *  the format_number 1(7 bits), buffer code (1 bit), VPID(8 bits), thread id (16 bits)
 *  *) callback record
 *    format_number(int), length(int), String
 *
 * @author Peter F. Sweeney
 * @date 2/22/2003
 */

public final class Trace
{
  // debugging
  static private final int debug = 0;

  // trace header
  TraceHeader   header;
  // array for trace records
  TraceRecord[] records;

  /**
   * Constructor
   * The length of records array is the number of trace records contained in it.
   */
  Trace(TraceHeader tr, TraceRecord[] records) {
    header = tr;
    this.records = records;
  }

  public void merge(Trace trace)
  {
    //    header.combine(trace.header);
    records = mergeTraceRecords(records, trace.records);
  }


  /*
   * A new array of trace records sorted by wall time
   * CONSTRAINT: neither array of records is null!
   *
   * @param records1 array of trace records
   * @param records2 array of trace records
   * @param index         next trace record to be returned
   * @return new array of trace records sorted by wall time
   */
  private TraceRecord[] 
  mergeTraceRecords(TraceRecord[] records1, TraceRecord[] records2)
  {
    int length1 = records1.length; 
    int length2 = records2.length;
    TraceRecord[] records = new TraceRecord[length1+length2];

    int index = 0; int index1 = 0; int index2 = 0;
    while (index1 < length1 && index2 < length2) {
      if        (! (records1[index1] instanceof TraceCounterRecord)) {
        records[index++] = records1[index1++];
      } else if (! (records2[index2] instanceof TraceCounterRecord)) {
        records[index++] = records2[index2++];
      } else {
        TraceCounterRecord tcr1 = (TraceCounterRecord)records1[index1];
        TraceCounterRecord tcr2 = (TraceCounterRecord)records2[index2];
        if (tcr1.start_wall_time <= tcr2.start_wall_time) {
          records[index++] = records1[index1++];
        } else {
          records[index++] = records2[index2++];
        }
      }
    }
    while (index1 < length1) {
      records[index++] = records1[index1++];
    }
    while (index2 < length2) {
      records[index++] = records2[index2++];
    }
    return records;
  }
}

