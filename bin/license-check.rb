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

JAVA_HEADER = <<JAVA
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
JAVA

XML_HEADER = <<XML
<!--
 ~  This file is part of the Jikes RVM project (http://jikesrvm.org).
 ~
 ~  This file is licensed to You under the Eclipse Public License (EPL);
 ~  You may not use this file except in compliance with the License. You
 ~  may obtain a copy of the License at
 ~
 ~      http://www.opensource.org/licenses/eclipse-1.0.php
 ~
 ~  See the COPYRIGHT.txt file distributed with this work for information
 ~  regarding copyright ownership.
 -->
XML

HASH_HEADER = <<XML
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
XML

FileTypes = {
  'properties' => {:header => HASH_HEADER, :start_match => /^[^#]/},
  'xml' => {:header => XML_HEADER, :start_match => /^\<[a-zA-Z]/},
  'java' => {:header => JAVA_HEADER, :start_match => /^package |^import |(public (final))(class|interface) /}
}

def add_header(filename,params)
  content = nil
  f = File.open(filename, 'r')
  content = f.readlines
  f.close
  start_match = params[:start_match]
  index = 0;
  while index < content.size and not (content[index] =~ start_match)
    index = index + 1
  end
  new_content = params[:header] + content[index,content.size-index].join('')
  File.open(filename, 'w') {|f| f.write(new_content) }
end

def check_files(files, dry_run)
  count = 0
  files.each do |filename|
    ext = filename =~ /\.([^\.]*)$/
    params = FileTypes[$1]
    next unless params
    f = File.new(filename)
    # Checking for the header in the 5 first lines
    match = false
    5.times do
      match ||= (/This file is licensed to You under the Common Public License/ =~ f.readline) rescue nil
    end
    f.close
    unless match
      if dry_run
        puts "Missing header in #{filename}"
      else
        add_header(filename,params)
      end
      count += 1
    end
  end
  if dry_run
    puts "#{count} files don't have the Jikes RVM license header."
  else
    puts "#{count} files have been changed to include the Jikes RVM license header."
  end
end

check_files(ARGV, false)
