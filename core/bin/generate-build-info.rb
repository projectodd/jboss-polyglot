#!/usr/bin/env ruby

require 'java'
require 'rexml/document'

class BuildInfo
  PARENT_POM = File.dirname( __FILE__ ) + '/../../pom.xml'
  PROPS_FILE = File.dirname( __FILE__ ) + '/../target/classes/org/projectodd/polyglot/polyglot.properties'
    
  def initialize()
    @versions = { }
  end

  def determine_polyglot_versions
    version = from_parent_pom( "project/version" )
    version = 'unknown' if version.nil? || version.empty?

    build_revision = `git rev-parse HEAD`.strip
    git_output = `git status -s`.lines
    build_revision << ' +modifications' if git_output.any? {|line| line =~ /^ M/ }

    @versions['polyglot.version'] = version
    @versions['polyglot.build.revision'] = build_revision
    @versions['polyglot.build.user'] = ENV['USER']
    @versions['polyglot.build.number'] = ENV['BUILD_NUMBER']
  end

  def determine_component_versions
    doc = REXML::Document.new( File.read( PARENT_POM ) )
    doc.each_element( 'project/properties/*' ) do |el|
      @versions[el.name] = el.text if el.name =~ %r{^version\.}
    end
  end

  def dump_versions
    File.open( PROPS_FILE, 'w' ) do |out|
      @versions.each do |key, value|
        out.write("#{key}=#{value}\n")
      end
    end
  end

  def from_parent_pom(selector)
    doc = REXML::Document.new( File.read( PARENT_POM ) )
    doc.get_elements(selector).first.text
  end

  def go!()
    determine_polyglot_versions
    determine_component_versions
    dump_versions
  end
end

BuildInfo.new.go!
