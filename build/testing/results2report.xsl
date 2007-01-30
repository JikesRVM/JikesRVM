<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <xsl:key name="configurations" match="results" use="build-parameters/parameter[@key='config.name']/@value"/>
  <xsl:key name="tags" match="test-run" use="@tag"/>

  <xsl:template match="master-results">
    <report>
      <id>
        <xsl:value-of select="@name"/>
      </id>
      <xsl:copy-of select="revision"/>
      <target>
        <parameters>
          <xsl:copy-of select="test-run[1]/results[1]/build-parameters/parameter[starts-with(@key,'target.')]"/>
        </parameters>
      </target>
      <xsl:for-each
          select="test-run/results[generate-id() = generate-id(key('configurations',build-parameters/parameter[@key='config.name']/@value))]">
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
            select="/master-results/test-run/results[generate-id() = generate-id(key('configurations',$config-name))]/build-parameters/parameter[starts-with(@key,'config.')]"/>
      </parameters>
      <xsl:call-template name="do-test-run">
        <xsl:with-param name="excludes" select="'|'"/>
        <xsl:with-param name="config-name" select="$config-name"/>
        <xsl:with-param name="tag-name"
                        select="/master-results/test-run/results/build-parameters/parameter[@key='config.name' and @value=$config-name]/../../../@tag"/>
      </xsl:call-template>
    </configuration>
  </xsl:template>

  <xsl:template name="do-test-run">
    <xsl:param name="config-name"/>
    <xsl:param name="tag-name"/>
    <xsl:param name="excludes"/>

    <xsl:variable name="test-excludes" select="concat($excludes,$tag-name,'|')"/>

    <runtime-configuration>
      <id>
        <xsl:value-of select="/master-results/test-run[@tag=$tag-name]/@name"/>
      </id>
      <parameters>
        <xsl:copy-of
            select="/master-results/test-run[@tag=$tag-name][1]/results[1]/run-parameters/parameter[@key='extra.args' or @key='mode']"/>
      </parameters>
      <xsl:for-each
          select="/master-results/test-run[@tag=$tag-name][1]/results/build-parameters/parameter[@key='config.name' and @value=$config-name]">
        <test-group>
          <id>
            <xsl:value-of select="../../group/@name"/>
          </id>
          <xsl:for-each select="../../group/test">
            <test>
              <id>
                <xsl:value-of select="tag"/>
              </id>
              <xsl:copy-of select="result|duration"/>
              <xsl:if test="result[text()='SUCCESS']">
                <xsl:copy-of select="statistics"/>
              </xsl:if>
              <xsl:if test="not(result[text()='SUCCESS'] or result[text()='EXCLUDED'])">
                <xsl:copy-of select="result-explanation|exit-code|working-directory|command|output"/>
              </xsl:if>
            </test>
          </xsl:for-each>
        </test-group>
      </xsl:for-each>
    </runtime-configuration>

    <xsl:if
        test="/master-results/test-run[not(contains($test-excludes,concat(@tag,'|')))]/results/build-parameters/parameter[@key='config.name' and @value=$config-name]">
      <xsl:call-template name="do-test-run">
        <xsl:with-param name="config-name" select="$config-name"/>
        <xsl:with-param name="tag-name"
                        select="/master-results/test-run[not(contains($test-excludes,concat(@tag,'|')))]/results/build-parameters/parameter[@key='config.name' and @value=$config-name]/../../../@tag"/>
        <xsl:with-param name="excludes" select="$test-excludes"/>
      </xsl:call-template>
    </xsl:if>
  </xsl:template>

</xsl:stylesheet>