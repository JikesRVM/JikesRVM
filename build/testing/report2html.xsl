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
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <xsl:variable name="target" select="/report/target/parameters/parameter[@key='target.name']/@value"/>

  <xsl:template match="report">
    <html>
      <head>
        <title>Tests Results for <xsl:value-of select="name"/> on <xsl:value-of select="host/name"/></title>
        <style>
          body {
          margin-left: 10;
          margin-right: 10;
          font:normal 80% arial,helvetica,sanserif;
          background-color:#FFFFFF;
          color:#000000;
          }
          table.errors { width: 80%; }
          tr.oddrow td { background: #efefef; }
          tr.evenrow td { background: #fff; }
          th, td { text-align: left; vertical-align: top; }
          th { font-weight:bold; background: #ccc; color: black; }
          table, th, td { font-size:100%; border: none }
          h2 { font-weight:bold; font-size:140%; margin-bottom: 5; }
        </style>
      </head>
      <body>
        <xsl:variable name="total-tests" select="count(build-target/build-configuration/test-configuration/group/test)"/>
        <xsl:variable name="total-executions" select="count(build-target/build-configuration/test-configuration/group/test/test-execution)"/>
        <xsl:variable name="total-successes" select="count(build-target/build-configuration/test-configuration/group/test/test-execution/result[text() = 'SUCCESS'])"/>

        <h2>Total Success Rate <xsl:value-of select="$total-successes"/>/<xsl:value-of select="$total-tests"/></h2>
        <p>Subversion Revision: <xsl:value-of select="revision"/></p>
        <xsl:if test="($total-tests - $total-successes) != 0">
          <table class="errors">
            <tr>
              <th>Result</th>
              <th>Configuration</th>
              <th>Group</th>
              <th>Test</th>
              <th>Run</th>
              <th>Reason</th>
            </tr>
            <xsl:apply-templates select="build-target/build-configuration/test-configuration/group/test/test-execution/result[not(text()='SUCCESS' or text()='EXCLUDED')]"/>
          </table>
        </xsl:if>
      </body>
    </html>
  </xsl:template>

  <xsl:template match="test-execution/result">
    <tr>
      <xsl:call-template name="alternated-row"/>
      <td><xsl:value-of select="."/></td>
      <td><xsl:value-of select="../../../../../name"/>.<xsl:value-of select="../../../../name"/></td>
      <td><xsl:value-of select="../../../name"/></td>
      <td><xsl:value-of select="../../name"/></td>
      <td><xsl:value-of select="../@name"/></td>
      <td><xsl:value-of select="../result-explanation"/></td>
    </tr>
  </xsl:template>

  <xsl:template name="alternated-row">
    <xsl:attribute name="class">
      <xsl:if test="position() mod 2 = 1">oddrow</xsl:if>
      <xsl:if test="position() mod 2 = 0">evenrow</xsl:if>
    </xsl:attribute>
  </xsl:template>
</xsl:stylesheet>
