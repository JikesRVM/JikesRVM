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
          <xsl:copy-of select="test-run/results[1]/build-parameters/parameter[starts-with(@key,'target.')]"/>
        </parameters>
      </target>
      <configurations>
        <xsl:for-each
            select="test-run/results[generate-id() = generate-id(key('configurations',build-parameters/parameter[@key='config.name']/@value))]">
          <xsl:variable name="config-name" select="build-parameters/parameter[@key='config.name']/@value"/>
          <configuration>
            <parameters>
              <xsl:copy-of select="build-parameters/parameter[starts-with(@key,'config.')]"/>
            </parameters>
            <test-runs>
              <xsl:for-each
                  select="../../test-run[generate-id() = generate-id(key('tags',@tag))]/results[generate-id() = generate-id(key('configurations',build-parameters/parameter[@key='config.name']/@value))]">
                <xsl:variable name="tag-name" select="../../test-run/@tag"/>
                <test-run>
                  <tag>
                    <xsl:value-of select="$tag-name"/>
                  </tag>
                  <xsl:for-each
                      select="../../test-run[@tag=$tag-name]/results/build-parameters/parameter[@key='config.name' and @value=$config-name]">
                    <test-group>
                      <name>
                        <xsl:value-of select="../../group/@name"/>
                      </name>
                      <xsl:for-each select="../../group/test">
                        <test>
                          <id>
                            <xsl:value-of select="selector"/>
                            <xsl:value-of select="class"/>
                          </id>
                          <xsl:copy-of select="result"/>
                          <xsl:if test="result[text()='SUCCESS']">
                            <xsl:copy-of select="statistics"/>
                          </xsl:if>
                          <xsl:if test="not(result[text()='SUCCESS'])">
                            <xsl:copy-of select="result-explanation|exit-code|working-directory|command|output"/>
                          </xsl:if>
                        </test>
                      </xsl:for-each>
                    </test-group>
                  </xsl:for-each>
                </test-run>
              </xsl:for-each>
            </test-runs>
          </configuration>
        </xsl:for-each>
      </configurations>
    </report>
  </xsl:template>
</xsl:stylesheet>