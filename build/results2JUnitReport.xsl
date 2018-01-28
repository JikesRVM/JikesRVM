<?xml version="1.0"?>
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
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:output method="xml" indent="yes"/>

  <xsl:template match="/results">
    <xsl:comment>
    This file contains the most essential information from the source Jikes RVM
    XML report for use in systems that support the JUnit XML format. Note that the
    JUnit XML format is actually not defined by JUnit but by the Ant JUnit plugin.

    --- Information about the format ---
    1. We do not use the errors element because the Jikes RVM XML format does not distinguish between errors and failures.
    we're only using the failures element so the error count is always 0.
    2. We use the error output of testcase elements to save some information from our reports that
    we could not display otherwise.
    </xsl:comment>

    <xsl:variable name="totalTests" select="count(//test)"/>
    <xsl:variable name="totalSkippedTests" select="count(//test/test-execution/result/text()[.='EXCLUDED'])"/>
    <xsl:variable name="totalFailedTests" select="count(//test/test-execution/result/text()[.='FAILURE'])"/>

    <xsl:comment>JUnit displays the time in seconds but our format uses milliseconds.</xsl:comment>
    <xsl:variable name="totalTimeInMilliSeconds" select="sum(//test/test-execution/duration/text())"/>
    <xsl:variable name="totalTimeInSeconds" select="number($totalTimeInMilliSeconds) div 1000"/>

    <xsl:variable name="startTime" select="/results/start-time/text()"/>
    
    <xsl:variable name="resultsName" select="/results/name/text()"/>
    <xsl:variable name="resultsVariant" select="/results/variant/text()"/>
    <xsl:variable name="testsuitesName" select="concat($resultsName,' - variant: ',$resultsVariant)"/>

    <testsuites errors="0" failures="{$totalFailedTests}" name="{$testsuitesName}" skipped="{$totalSkippedTests}" tests="{$totalTests}" time="{$totalTimeInSeconds}" timestamp="{$startTime}">
      <xsl:apply-templates select="test-configuration"/>
    </testsuites>

  </xsl:template>

  <xsl:template match="test-configuration">
    <xsl:variable name="buildConfig" select="build-configuration/text()"/>
    <xsl:variable name="testConfigName" select="name/text()"/>

    <xsl:for-each select="group">
      <xsl:call-template name="produceTestsuiteElements">
        <xsl:with-param name="buildConfig" select="$buildConfig"/>
        <xsl:with-param name="testConfigName" select="$testConfigName"/>
      </xsl:call-template>
    </xsl:for-each>
  </xsl:template>

  <xsl:template name="produceTestsuiteElements">
    <xsl:param name="buildConfig"/>
    <xsl:param name="testConfigName"/>
    <xsl:variable name="groupName" select="name/text()"/>
    <xsl:variable name="testsuiteName" select="concat($buildConfig,' - ',$testConfigName,' -- Tests: ',$groupName)"/>

    <xsl:variable name="testConfigTotalTests" select="count(test)"/>
    <xsl:variable name="testConfigSkippedTests" select="count(test/test-execution/result/text()[.='EXCLUDED'])"/>
    <xsl:variable name="testConfigFailedTestsDueToFailure" select="count(test/test-execution/result/text()[.='FAILURE'])"/>
    <xsl:variable name="testConfigFailedTestsDueToOvertime" select="count(test/test-execution/result/text()[.='OVERTIME'])"/>
    <xsl:variable name="testConfigFailedTests" select="number($testConfigFailedTestsDueToOvertime)+number($testConfigFailedTestsDueToFailure)"/>

    <xsl:variable name="testConfigTimeInMilliSeconds" select="sum(test/test-execution/duration/text())"/>
    <xsl:variable name="testConfigTimeInSeconds" select="number($testConfigTimeInMilliSeconds) div 1000"/>

    <testsuite errors="0" failures="{$testConfigFailedTests}" name="{$testsuiteName}" skipped="{$testConfigSkippedTests}" tests="{$testConfigTotalTests}" time="{$testConfigTimeInSeconds}">
      <xsl:apply-templates select="test">
        <xsl:with-param name="testsuiteInformation" select="$testsuiteName"/>
      </xsl:apply-templates>

    </testsuite>

  </xsl:template>

  <xsl:template match="test">
    <xsl:param name="testsuiteInformation"/>
    <xsl:variable name="className" select="name/text()"/>
    <xsl:variable name="testDurationInMilliSeconds" select="test-execution/duration/text()"/>
    <xsl:variable name="testDurationInSeconds" select="number($testDurationInMilliSeconds) div 1000"/>

    <xsl:variable name="testResult" select="test-execution/result/text()"/>
    <xsl:variable name="resultExplanation" select="test-execution/result-explanation/text()"/>

    <xsl:variable name="exitCode" select="test-execution/exit-code/text()"/>
    <xsl:variable name="commandLine" select="command/text()"/>
    <xsl:variable name="nameSuffix" select="concat(' (',$testsuiteInformation,')')"/>
    <xsl:variable name="fullTestName" select="concat($className,$nameSuffix)"/>
    
    <!--  Normal node -->
    <xsl:if test="test-execution/duration">
      <testcase classname="{$className}" name="{$fullTestName}" time="{$testDurationInSeconds}">
        <xsl:if test="$testResult = 'EXCLUDED'">
          <skipped/>
        </xsl:if>

        <xsl:if test="$testResult = 'FAILURE'">
          <failure message="{$resultExplanation}"/>
        </xsl:if>
        <system-out>
          <xsl:copy-of select="test-execution/output/text()"/>
        </system-out>

        <xsl:if test="$testResult = 'OVERTIME'">
          <failure message="{$resultExplanation}"/>
        </xsl:if>
        <system-out>
          <xsl:copy-of select="test-execution/output/text()"/>
        </system-out>

        <system-err>
COMMAND LINE: <xsl:value-of select="$commandLine"/>
EXIT CODE: <xsl:value-of select="$exitCode"/>
<xsl:text>&#xa;</xsl:text>
<xsl:text>--- TEST PARAMETERS ---</xsl:text>
<xsl:text>&#xa;</xsl:text>
          <xsl:for-each select="parameters/parameter">
            <xsl:variable name="paramKey" select="@key"/>
            <xsl:variable name="paramValue" select="@value"/>
<xsl:value-of select="$paramKey"/>: <xsl:value-of select="$paramValue"/>
<xsl:text>&#xa;</xsl:text>
          </xsl:for-each>

          <xsl:text>--- TEST SUITE PARAMETERS ---</xsl:text>
<xsl:text>&#xa;</xsl:text>
        <xsl:for-each select="ancestor::test-configuration[1]/parameters/parameter">
          <xsl:variable name="testSuiteParamKey" select="@key"/>
          <xsl:variable name="testSuiteParamValue" select="@value"/>
          <xsl:value-of select="$testSuiteParamKey"/>: <xsl:value-of select="$testSuiteParamValue"/>
<xsl:text>&#xa;</xsl:text>
        </xsl:for-each>

        </system-err>
      </testcase>
    </xsl:if>

    <!--  Statistics node (e.g. as in compiler dna report) -->
    <xsl:if test="not(test-execution/duration)">
      <testcase classname="{$className}" name="Statistics" >
        <system-err>
<xsl:text>&#xa;</xsl:text>
<xsl:text>--- STATISTICS ---</xsl:text>
<xsl:text>&#xa;</xsl:text>
          <xsl:for-each select="test-execution/statistics/statistic">
            <xsl:variable name="statisticsKey" select="@key"/>
            <xsl:variable name="statisticsValue" select="@value"/>
<xsl:value-of select="$statisticsKey"/>: <xsl:value-of select="statisticsValue"/>
<xsl:text>&#xa;</xsl:text>
          </xsl:for-each>
        </system-err>
      </testcase>
    </xsl:if>

  </xsl:template>

</xsl:stylesheet>
