/*
 * (c) 2003-2018 MuleSoft, Inc. This software is protected under international copyright
 * law. All use of this software is subject to MuleSoft's Master Subscription Agreement
 * (or other master license agreement) separately entered into in writing between you and
 * MuleSoft. If such an agreement is not in place, you may not use the software.
 */
package org.mule.dynamic.routing.provider.api;

import static org.mule.metadata.api.model.MetadataFormat.JAVA;
import static org.mule.runtime.api.meta.Category.SELECT;
import static org.mule.runtime.api.meta.ExpressionSupport.NOT_SUPPORTED;
import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.runtime.api.meta.model.declaration.fluent.ConfigurationDeclarer;
import org.mule.runtime.api.meta.model.declaration.fluent.ExtensionDeclarer;
import org.mule.runtime.api.meta.model.declaration.fluent.ParameterGroupDeclarer;
import org.mule.runtime.extension.api.loader.ExtensionLoadingContext;
import org.mule.runtime.extension.api.loader.ExtensionLoadingDelegate;

/**
 * Declares extension to perform runtime registration of an API to
 * a dynamic router
 *
 * @since 1.0
 */
public class CustomConfigurationPropertiesExtensionLoadingDelegate implements ExtensionLoadingDelegate {

    public static final String EXTENSION_NAME = "Dynamic Router Properties Provider";
    public static final String CONFIG_ELEMENT = "config";

  @Override
  public void accept(ExtensionDeclarer extensionDeclarer, ExtensionLoadingContext context) {
    ConfigurationDeclarer configurationDeclarer = extensionDeclarer.named(EXTENSION_NAME)
        .describedAs(String.format("Crafted %s Extension", EXTENSION_NAME))
        .withCategory(SELECT)
        .onVersion("1.0.0")
        .fromVendor("Mulesoft")
        .withConfig(CONFIG_ELEMENT);

    ParameterGroupDeclarer defaultParameterGroup = configurationDeclarer.onDefaultParameterGroup();
    // you can add/remove configuration parameter using the code below.
    	/*
    	defaultParameterGroup
    	.withRequiredParameter("protocol").ofType(BaseTypeBuilder.create(JAVA).stringType().enumOf("HTTP","HTTPS").defaultValue("HTTP").build())
    	.withExpressionSupport(NOT_SUPPORTED)
    	.describedAs(" URL for the API");
    	*/
    defaultParameterGroup
        .withRequiredParameter("routerAddress").ofType(BaseTypeBuilder.create(JAVA).stringType().build())
        .withExpressionSupport(NOT_SUPPORTED)
        .describedAs("URL of the dynamic router for API registration");
  }

}
