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
package org.jikesrvm.tools.ant;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FilenameFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.LogLevel;


/**
 * Generates a summary CSV file that contains information about the total
 * size of the bootimage .map files for each VM image in the image directory.
 * The first row of the CSV file has the image names, the second one has the
 * total size in bytes, the separator is a comma.
 * <p>
 * Note that the Ant Task can't distinguish images that existed before the
 * current build and images that were just built: it simply generates image
 * size summaries for all images that it finds in the image directory.
 * <p>
 * Detailed information such as bootimage compiler, size limit for each .image
 * file according to the configuration, architecture and so on isn't available.
 * This is by design. Providing that information by executing code on the images
 * to read out properties isn't advisable because this task is supposed to
 * be used in tests and the images might be broken. It would also be possible
 * to extract detailed information in the build for each image and then summarize
 * it here. Detailed information isn't required for the current (Feb 2016) testing
 * setup on the Jikes RVM project regression machines, so we don't take the trouble
 * to do this.
 */
public class GenerateBootImageSizeSummary extends Task {

  private static final ImageNameFilter IMAGE_NAMES_FILTER = new ImageNameFilter();

  private static final int VERBOSE = LogLevel.VERBOSE.getLevel();

  /** Charset to use for writing the CSV file **/
  private static final String CHARSET = "UTF-8";

  /** File to write results to */
  private File summaryFile;

  /** Directory containing the VM image directories */
  private File imageDir;

  /** Sets the file that will contain the summary information */
  public void setSummaryFile(File summaryFile) {
    this.summaryFile = summaryFile;
  }

  /**
   * Sets the directory containing the boot image directories,
   * i.e. the dist dir.
   */
  public void setImageDir(File imageDir) {
    this.imageDir = imageDir;
  }

  private void verifyTaskAttributes() {
    if (summaryFile == null) {
      throw new BuildException("summaryFile not set. " +
        "It must be set to the name of a not yet existing file.");
    } else if (summaryFile.isDirectory()) {
      throw new BuildException("Expected summaryFile (" +
        summaryFile + ") to be a file but it's a directory.");
    }

    log("Summary file set to " + summaryFile, VERBOSE);

    if (imageDir == null) {
      throw new BuildException("imageDir not set. " +
        "It must be set to the directory containing the boot image directories " +
        "(e.g. dist by default).");
    } else if (imageDir.isFile()) {
      throw new BuildException("Expected imageDir (" +
          imageDir + ") to be a directory but it's a file.");
    }

    log("Image directory set to " + imageDir, VERBOSE);
  }

  private SortedMap<String, String> computeTotalImageSizes() {
    SortedMap<String, String> imageNameToTotalImageSize = new TreeMap<String,String>();
    File[] subDirectories = imageDir.listFiles();
    if (subDirectories == null) {
      throw new BuildException("Image directory " + imageDir + " doesn't exist!");
    }
    for (File dir : subDirectories) {
      if (!dir.isDirectory()) {
        continue;
      } else {
        log("Found (potential) boot image directory: " + dir.getAbsolutePath(), VERBOSE);
        long totalSize = 0;
        for (File imageFile : dir.listFiles(IMAGE_NAMES_FILTER)) {
          log("Found image file: " + imageFile.getAbsolutePath(), VERBOSE);
          totalSize += imageFile.length();
        }
        if (totalSize > 0) {
          imageNameToTotalImageSize.put(dir.getName(), Long.toString(totalSize));
          log("Total image size for " + dir + " was: " + totalSize, VERBOSE);
        } else {
          log("Directory " + dir + " didn't contain valid image files.", VERBOSE);
        }
      }
    }
    return imageNameToTotalImageSize;
  }

  private void writeIteratorContentsToCSVRow(Iterator<String> it, BufferedWriter writer)
      throws IOException {
      while (it.hasNext()) {
        writer.write(it.next());
        if (it.hasNext()) {
          writer.write(",");
        } else {
          writer.write("\n");
        }
      }
    }

  private void writeCSVFile(SortedMap<String, String> imageNameToTotalImageSize) {
    try {
      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
          new FileOutputStream(summaryFile), CHARSET));
      writeIteratorContentsToCSVRow(imageNameToTotalImageSize.keySet().iterator(),
          writer);
      writeIteratorContentsToCSVRow(imageNameToTotalImageSize.values().iterator(),
          writer);
      writer.close();
    } catch (IOException e) {
      throw new BuildException("Error writing summary to " + summaryFile, e);
    }

    log("Generated boot image size summaries to " + summaryFile);
  }

  public void execute() throws BuildException {
    verifyTaskAttributes();
    SortedMap<String, String> imageNameToTotalImageSize = computeTotalImageSizes();
    writeCSVFile(imageNameToTotalImageSize);
  }

  /**
   * Accepts only files that match the (hard-coded) known image names.
   */
  private static class ImageNameFilter implements FilenameFilter {
    public boolean accept(File dir, String name) {
      return "RVM.code.image".equals(name) || "RVM.data.image".equals(name) ||
        "RVM.rmap.image".equals(name);
    }
  }

}
