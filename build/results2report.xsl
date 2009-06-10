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

<xsl:output method="xml" indent="yes"/>

  <xsl:template match="aggregate">
    <report version="1.1">
      <xsl:copy-of select="results/*[not(name() = 'test-configuration')]"/>
      <xsl:apply-templates select="buildresults/build-target"/>
    </report>
  </xsl:template>

  <xsl:template match="build-target">
    <xsl:copy>
      <xsl:copy-of select="name"/>
      <xsl:copy-of select="parameters"/>
      <xsl:apply-templates select="build-configuration"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="build-configuration">
    <xsl:copy>
      <xsl:copy-of select="*"/>
      <xsl:variable name="config" select="name"/>
      <xsl:apply-templates select="/aggregate/results/test-configuration[build-configuration = $config]"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="test-configuration">
    <xsl:copy>
      <xsl:copy-of select="name"/>
      <xsl:copy-of select="parameters"/>
      <xsl:apply-templates select="group"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="group">
    <xsl:copy>
      <xsl:copy-of select="name"/>
      <xsl:copy-of select="test/test-execution[not(result = 'EXCLUDED')]/.."/>
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>
