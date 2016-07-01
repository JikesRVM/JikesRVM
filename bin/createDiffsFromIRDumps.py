#!/usr/bin/python3
#
#  This file is part of the Jikes RVM project (http://jikesrvm.org).
#
#  This file is licensed to You under the Eclipse Public License (EPL);
#  You may not use this file except in compliance with the License. You
#  may obtain a copy of the License at
#
#      http://www.opensource.org/licenses/eclipse-1.0.php
#
#  See the COPYRIGHT.txt file distributed with this work for information
#  regarding copyright ownership.
#
# This script creates diffs for all .irdump files in a separate directory
# by running 'diff --unified' for each sliding window of 2 files in the
# alphabetically sorted list. It assumes that all the IR dumps belong to
# the same method.
#
# Example:
# files 00.irdump a.irdump b.irdump
# diffs 000-to-001.irdump 001-to-002.irdump (in the directory 'diff')
#
from pathlib import Path
import os
import subprocess
currentDir = Path('.')
files = list(currentDir.glob('*.irdump'))
files.sort()
diffDirName = './diff'
diffDir = Path(diffDirName)
if not os.path.exists(diffDirName):
  diffDir.mkdir()
length = len(files)
for first in range(0, length-1):
  second = first + 1
  firstFileName = str(files[first])
  secondFileName = str(files[second])
  diffFileName = '{:03d}'.format(first) + '-to-' + '{:03d}'.format(second) + '.diff'
  outFile = open(diffDirName + '/' + diffFileName, 'w')
  subprocess.call(['diff', '--unified', firstFileName, secondFileName], stdout = outFile, stderr = outFile)

