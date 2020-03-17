package com.xebialabs.gradle.dependency

import com.xebialabs.gradle.dependency.domain.GroupArtifact
import com.xebialabs.gradle.dependency.domain.GroupArtifactVersion
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.DependencySubstitutions
import org.gradle.api.artifacts.ResolutionStrategy

class DependencyManagementProjectConfigurer {

  static def configureProject(Project project, DependencyManagementContainer container) {
    // Contract for all is that it executes the closure for all currently assigned objects, and any objects added later.
    project.getConfigurations().all { Configuration config ->
      if (config.name != 'zinc') { // The Scala compiler 'zinc' configuration should not be managed by us
        config.resolutionStrategy { ResolutionStrategy rs ->
          rs.eachDependency(manageDependency(project, container))
          rs.dependencySubstitution(substituteDependency(project, container))
        }
        configureExcludes(project, config, container)
      }
    }
  }

  static def configureExcludes(Project project, Configuration config, DependencyManagementContainer container) {
    container.blackList.each { ga ->
      container.resolveIfNecessary()
      project.logger.debug("Excluding ${ga.toMap()} from configuration ${config.getName()}")
      config.exclude ga.toMap()
    }
  }

  private static Action<? super DependencySubstitutions> substituteDependency(Project project, DependencyManagementContainer container) {
    return new Action<DependencySubstitutions>() {
      @Override
      void execute(final DependencySubstitutions dependencySubstitutions) {
        container.resolveIfNecessary()
        substitute(dependencySubstitutions)
      }

      private void substitute(DependencySubstitutions dependencySubstitutions) {
        def rewrites = container.rewrites
        rewrites.forEach { GroupArtifact requested, GroupArtifact targetGroupArtifact ->
          def requestedVersion = container.getManagedVersion(requested.group, requested.artifact)
          def rewriteVersion = container.getManagedVersion(targetGroupArtifact.group, targetGroupArtifact.artifact) ?: requestedVersion
          if (rewriteVersion) {
            GroupArtifactVersion targetGroupArtifactVersion = targetGroupArtifact.withVersion(rewriteVersion)
            def source = dependencySubstitutions.module("${requested.group}:${requested.artifact}")
            def target = dependencySubstitutions.module("${targetGroupArtifactVersion.group}:${targetGroupArtifactVersion.artifact}:${targetGroupArtifactVersion.version}")
            dependencySubstitutions.substitute(source).because("rewrite").with(target)
          }
        }
      }
    }
  }

  private static Action<? super DependencyResolveDetails> manageDependency(Project project, DependencyManagementContainer container) {
    return new Action<DependencyResolveDetails>() {
      @Override
      void execute(DependencyResolveDetails details) {
        container.resolveIfNecessary()
        rewrite(details)
        enforceVersion(details)
      }

      private void rewrite(DependencyResolveDetails details) {
        def rewrites = container.rewrites

        def fromGa = new GroupArtifact(details.requested.group, details.requested.name)
        GroupArtifact groupArtifact = rewrites[fromGa]
        if (groupArtifact) {
          def requestedVersion = container.getManagedVersion(details.requested.group, details.requested.name) ?: details.requested.version
          def rewriteVersion = container.getManagedVersion(groupArtifact.group, groupArtifact.artifact)
          if (rewriteVersion) {
            groupArtifact = groupArtifact.withVersion(rewriteVersion)
            rewrites[fromGa] = groupArtifact
          } else {
            groupArtifact = groupArtifact.withVersion(requestedVersion)
            rewrites[fromGa] = groupArtifact
          }
          project.logger.debug("Rewriting $fromGa -> $groupArtifact")
          details.useTarget(groupArtifact.toMap(details.requested))

        }
      }

      private void enforceVersion(DependencyResolveDetails details) {
        def version = container.getManagedVersion(details.requested.group, details.requested.name)
        if (version) {
          project.logger.debug("Resolved version $version for ${details.requested.group}:${details.requested.name}")
          details.useVersion(version)
        } else {
          project.logger.debug("No managed version for ${details.requested.group}:${details.requested.name} --> using version ${details.requested.version}")
        }
      }
    }
  }

}
