package com.xebialabs.gradle.dependency

import com.xebialabs.gradle.dependency.domain.GroupArtifact
import com.xebialabs.gradle.dependency.domain.GroupArtifactVersion
import com.xebialabs.gradle.dependency.supplier.DependencyManagementSupplier
import groovy.text.SimpleTemplateEngine
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.ExtraPropertiesExtension

class DependencyManagementContainer {
  private static final Logger logger = Logging.getLogger(DependencyManagementContainer.class)

  private SimpleTemplateEngine engine = new SimpleTemplateEngine()
  private List<DependencyManagementSupplier> suppliers = []
  private List<Project> projects = []

  Map versions = [:].withDefault { "" }
  Map managedVersions = [:]
  List<GroupArtifact> blackList = []
  Map rewrites = [:]

  DependencyManagementContainer(Project project) {
    projects.addAll(project.allprojects)
  }

  def resolveIfNecessary() {
//        // First collect all versions
//        suppliers.each {
//            it.collectVersions(this)
//        }
//        // Then all keys
//        suppliers.each {
//            it.collectDependencies(this)
//        }
//        suppliers.clear()
  }

  def addSupplier(DependencyManagementSupplier supplier) {
//        suppliers.add(supplier)
    supplier.collectVersions(this)
    supplier.collectDependencies(this)
    supplier.collectExclusions(this)
    supplier.collectRewrites(this)
  }

  def registerVersionKey(String key, String version) {
    if (!versions[key]) {
      versions.put(key, version)
      logger.info("Registering version $key = $version")
      // Also register the version key on each project, useful with for example $scalaVersion
      projects.each {
        it.extensions.findByType(ExtraPropertiesExtension).set(key, version)
      }
    }
  }

  def addManagedVersion(String group, String artifact, String version) {
    def ga = resolve("$group:$artifact")
    managedVersions[ga] = resolve(version)
  }

  String getManagedVersion(String group, String artifact) {
    String ga = "$group:$artifact"
    logger.debug("Trying to resolve version for $ga")
    if (managedVersions[ga]) {
      return managedVersions[ga]
    }

    return null
  }

  def resolve(String s) {
    return s ? engine.createTemplate(s).make(versions).toString() : s
  }

  def blackList(String group, String artifact) {
    def ga = new GroupArtifact(resolve(group), resolve(artifact))
    blackList.add(ga)
  }

  def rewrite(String fromGroup, String fromArtifact, String toGroup, String toArtifact) {
    def fromGa = new GroupArtifact(fromGroup, fromArtifact)
    def toGa = new GroupArtifact(toGroup, toArtifact)
    this.rewrites.put(fromGa, toGa)
  }
}
