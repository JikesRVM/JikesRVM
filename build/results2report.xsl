<!--
 ~  This file is part of the Jikes RVM project (http://jikesrvm.org).
 ~
 ~  This file is licensed to You under the Common Public License (CPL);
 ~  You may not use this file except in compliance with the License. You
 ~  may obtain a copy of the License at
 ~
 ~      http://www.opensource.org/licenses/cpl1.0.php
 ~
 ~  See the COPYRIGHT.txt file distributed with this work for information
 ~  regarding copyright ownership.
 -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <xsl:key name="configurations" match="results" use="build-parameters/parameter[@key='config.name']/@value"/>
  <xsl:key name="tags" match="test-configuration" use="@tag"/>

  <xsl:template match="test-run">
    <report version="1.0">
      <id>
        <xsl:value-of select="@name"/>
      </id>
      <xsl:copy-of select="revision|time|variant|builds|target"/>
      <xsl:for-each
          select="test-configuration/results[generate-id() = generate-id(key('configurations',build-parameters/parameter[@key='config.name']/@value))]">
        <xsl:call-template name="configuration">
          <xsl:with-param name="config-name" select="build-parameters/parameter[@key='config.name']/@value"/>
        </xsl:call-template>
      </xsl:for-each>
    </report>
  </xsl:template>

  <xsl:template name="configuration">
    <xsl:param name="config-name"/>
    <configuration>
      <id>
        <xsl:value-of select="$config-name"/>
      </id>
      <parameters>
        <xsl:copy-of
            select="/test-run/test-configuration/results[generate-id() = generate-id(key('configurations',$config-name))]/build-parameters/parameter[starts-with(@key,'config.')]"/>
      </parameters>
      <xsl:call-template name="do-test-configuration">
        <xsl:with-param name="excludes" select="'|'"/>
        <xsl:with-param name="config-name" select="$config-name"/>
        <xsl:with-param name="tag-name"
                        select="/test-run/test-configuration/results/build-parameters/parameter[@key='config.name' and @value=$config-name]/../../../@tag"/>
      </xsl:call-template>
    </configuration>
  </xsl:template>

  <xsl:template name="do-test-configuration">
    <xsl:param name="config-name"/>
    <xsl:param name="tag-name"/>
    <xsl:param name="excludes"/>

    <xsl:variable name="test-excludes" select="concat($excludes,$tag-name,'|')"/>

    <test-configuration>
      <id>
        <xsl:value-of select="$tag-name"/>
      </id>
      <name>
        <xsl:value-of select="/test-run/test-configuration[@tag=$tag-name]/@name"/>
      </name>
      <parameters>
        <xsl:copy-of
            select="/test-run/test-configuration[@tag=$tag-name][1]/results[1]/test-parameters/parameter[@key='extra.args' or @key='mode']"/>
      </parameters>
      <xsl:for-each
          select="/test-run/test-configuration[@tag=$tag-name][1]/results/build-parameters/parameter[@key='config.name' and @value=$config-name]">
        <test-group>
          <id>
            <xsl:value-of select="../../group/text()"/>
          </id>
          <xsl:for-each select="../../test/result[text() != 'EXCLUDED']/..">
            <test>
              <id>
                <xsl:value-of select="tag"/>
              </id>
              <xsl:copy-of select="*[not(local-name()='tag')]"/>
            </test>
          </xsl:for-each>
        </test-group>
      </xsl:for-each>
    </test-configuration>

    <xsl:if
        test="/test-run/test-configuration[not(contains($test-excludes,concat(@tag,'|')))]/results/build-parameters/parameter[@key='config.name' and @value=$config-name]">
      <xsl:call-template name="do-test-configuration">
        <xsl:with-param name="config-name" select="$config-name"/>
        <xsl:with-param name="tag-name"
                        select="/test-run/test-configuration[not(contains($test-excludes,concat(@tag,'|')))]/results/build-parameters/parameter[@key='config.name' and @value=$config-name]/../../../@tag"/>
        <xsl:with-param name="excludes" select="$test-excludes"/>
      </xsl:call-template>
    </xsl:if>
  </xsl:template>

</xsl:stylesheet>