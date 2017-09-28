/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.module.deployment.internal;

import static java.lang.System.getProperty;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.apache.commons.io.FileUtils.copyFileToDirectory;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mule.functional.services.TestServicesUtils.buildHttpServiceFile;
import static org.mule.runtime.api.deployment.meta.Product.MULE;
import static org.mule.runtime.container.api.MuleFoldersUtil.getServicesFolder;
import static org.mule.runtime.deployment.model.api.artifact.ArtifactDescriptorConstants.MULE_LOADER_ID;
import static org.mule.runtime.module.deployment.impl.internal.policy.PropertiesBundleDescriptorLoader.PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID;
import static org.mule.runtime.module.deployment.internal.TestPolicyProcessor.invocationCount;
import static org.mule.runtime.module.deployment.internal.TestPolicyProcessor.policyParametrization;

import org.mule.runtime.api.deployment.meta.MuleArtifactLoaderDescriptor;
import org.mule.runtime.api.deployment.meta.MulePolicyModel;
import org.mule.runtime.core.api.policy.PolicyParametrization;
import org.mule.runtime.core.api.policy.PolicyPointcut;
import org.mule.runtime.module.deployment.impl.internal.builder.ApplicationFileBuilder;
import org.mule.runtime.module.deployment.impl.internal.builder.ArtifactPluginFileBuilder;
import org.mule.runtime.module.deployment.impl.internal.builder.PolicyFileBuilder;

import java.io.File;

import org.junit.Test;

/**
 * Contains test for application deployment with policies on the default domain
 */
public class Mule12016TestCase extends AbstractDeploymentTestCase {

  private static final String POLICY_PROPERTY_VALUE = "policyPropertyValue";
  private static final String POLICY_PROPERTY_KEY = "policyPropertyKey";
  private static final String FOO_POLICY_NAME = "fooPolicy";

  private ArtifactPluginFileBuilder httpExtension = new ArtifactPluginFileBuilder("http") {

    @Override
    public String getGroupId() {
      return "org.mule.connectors";
    }

    @Override
    public String getArtifactId() {
      return "mule-http-connector";
    }

    @Override
    public String getClassifier() {
      return "mule-plugin";
    }

    @Override
    public String getType() {
      return "jar";
    }

    @Override
    public String getVersion() {
      return "1.0.0-SNAPSHOT";
    }
  };

  // Policy artifact file builders
  private final PolicyFileBuilder fooPolicyFileBuilder =
      new PolicyFileBuilder(FOO_POLICY_NAME).describedBy(new MulePolicyModel.MulePolicyModelBuilder()
          .setMinMuleVersion(MIN_MULE_VERSION)
          .setName(FOO_POLICY_NAME)
          .setRequiredProduct(MULE)
          .withBundleDescriptorLoader(createBundleDescriptorLoader(FOO_POLICY_NAME, MULE_POLICY_CLASSIFIER,
                                                                   PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID))
          .withClassLoaderModelDescriptorLoader(new MuleArtifactLoaderDescriptor(MULE_LOADER_ID, emptyMap()))
          .build())
          .dependingOn(httpExtension)
          .usingLightwayPackage();


  public Mule12016TestCase(boolean parallelDeployment) {
    super(parallelDeployment);
  }

  public static File getMavenLocalRepository() {
    String userHome = getProperty("user.home");

    String localRepositoryProperty = getProperty("localRepository");
    if (localRepositoryProperty == null) {
      localRepositoryProperty = userHome + "/.m2/repository";
    }

    File mavenLocalRepositoryLocation = new File(localRepositoryProperty);
    if (!mavenLocalRepositoryLocation.exists()) {
      throw new IllegalArgumentException("Maven repository location couldn't be found, please check your configuration");
    }
    return mavenLocalRepositoryLocation;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    copyFileToDirectory(buildHttpServiceFile(compilerWorkFolder.newFolder("httpService")), getServicesFolder());
  }

  @Test
  public void appliesApplicationPolicy() throws Exception {
    System.setProperty("muleRuntimeConfig.maven.repositoryLocation", getMavenLocalRepository().getAbsolutePath());

    doApplicationPolicyExecutionTest(parameters -> true, 1, POLICY_PROPERTY_VALUE);
  }

  private void doApplicationPolicyExecutionTest(PolicyPointcut pointcut, int expectedPolicyInvocations,
                                                Object expectedPolicyParametrization)
      throws Exception {
    policyManager.registerPolicyTemplate(fooPolicyFileBuilder.getArtifactFile());

    ApplicationFileBuilder applicationFileBuilder = new ApplicationFileBuilder("mule12016")
        .definedBy("mule12016-app.xml").dependingOn(httpExtension).usingLightwayPackage();
    addPackedAppFromBuilder(applicationFileBuilder);

    startDeployment();
    assertApplicationDeploymentSuccess(applicationDeploymentListener, applicationFileBuilder.getId());

    policyManager.addPolicy(applicationFileBuilder.getId(), fooPolicyFileBuilder.getId(),
                            new PolicyParametrization(FOO_POLICY_ID, pointcut, 1,
                                                      singletonMap(POLICY_PROPERTY_KEY, POLICY_PROPERTY_VALUE),
                                                      getResourceFile("/mule12016-policy.xml"), emptyList()));


    executeApplicationFlow("main");
    assertThat(invocationCount, equalTo(expectedPolicyInvocations));
    assertThat(policyParametrization, equalTo(expectedPolicyParametrization));
  }

}
