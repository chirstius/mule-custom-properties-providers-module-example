/*
 * (c) 2003-2018 MuleSoft, Inc. This software is protected under international copyright
 * law. All use of this software is subject to MuleSoft's Master Subscription Agreement
 * (or other master license agreement) separately entered into in writing between you and
 * MuleSoft. If such an agreement is not in place, you may not use the software.
 */
package org.mule.dynamic.routing.provider.api;

import static org.mule.dynamic.routing.provider.api.CustomConfigurationPropertiesExtensionLoadingDelegate.CONFIG_ELEMENT;
import static org.mule.dynamic.routing.provider.api.CustomConfigurationPropertiesExtensionLoadingDelegate.EXTENSION_NAME;
import static org.mule.runtime.api.component.ComponentIdentifier.builder;
import static org.mule.runtime.extension.api.util.NameUtils.defaultNamespace;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.config.api.dsl.model.ConfigurationParameters;
import org.mule.runtime.config.api.dsl.model.ResourceProvider;
import org.mule.runtime.config.api.dsl.model.properties.ConfigurationPropertiesProvider;
import org.mule.runtime.config.api.dsl.model.properties.ConfigurationPropertiesProviderFactory;
import org.mule.runtime.config.api.dsl.model.properties.ConfigurationProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


/**
 * Performs automatic runtime registration of an API to a dynamic router
 *
 * @since 1.0
 */
public class CustomConfigurationPropertiesProviderFactory implements ConfigurationPropertiesProviderFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(CustomConfigurationPropertiesProviderFactory.class);
  public static final String EXTENSION_NAMESPACE = defaultNamespace(EXTENSION_NAME);
  private static final ComponentIdentifier CUSTOM_PROPERTIES_PROVIDER =
      builder().namespace(EXTENSION_NAMESPACE).name(CONFIG_ELEMENT).build();
  private final static String CUSTOM_PROPERTIES_PREFIX = "dynamic-router-properties-provider::";
  private static final String PROPERTY_KEY = "routerAddress";

  @Override
  public ComponentIdentifier getSupportedComponentIdentifier() {
    return CUSTOM_PROPERTIES_PROVIDER;
  }

  @Override
  public ConfigurationPropertiesProvider createProvider(ConfigurationParameters parameters,
      ResourceProvider externalResourceProvider) {
    String routerUrl = parameters.getStringParameter("routerAddress");

    // Register this API to the dynamic router - this is the real point of this provider
    registerToRouter(routerUrl);

    // This is mostly superfluous but implmented so that should the router information be required
    // it would be available to any flow element that wanted to reference it
    return new ConfigurationPropertiesProvider() {

      @Override
      public Optional<ConfigurationProperty> getConfigurationProperty(String configurationAttributeKey) {
        // TODO change implementation to discover properties values from your custom source
        if (configurationAttributeKey.startsWith(CUSTOM_PROPERTIES_PREFIX)) {
          String effectiveKey = configurationAttributeKey.substring(CUSTOM_PROPERTIES_PREFIX.length());
          if (effectiveKey.equals(PROPERTY_KEY)) {
            return Optional.of(new ConfigurationProperty() {

              @Override
              public Object getSource() {
                return "dynamic router provider source";
              }

              @Override
              public Object getRawValue() {
                return routerUrl;
              }

              @Override
              public String getKey() {
                return PROPERTY_KEY;
              }
            });
          }
        }
        return Optional.empty();
      }

      @Override
      public String getDescription() {
        return "Dynamic Router properties provider";
      }
    };
  }

  private String extractRaml(String appRamlZip) throws IOException {
    String ramlContent = null;
    ZipInputStream zis = new ZipInputStream(new FileInputStream(appRamlZip));
    ZipEntry zipEntry = zis.getNextEntry();
    while (zipEntry != null) {
      if (zipEntry.getName().endsWith(".raml")) {
        LOGGER.debug("FOUND: " + zipEntry.getName());
        ramlContent = IOUtils.toString(zis, Charsets.toCharset("utf-8"));
        break;
      }
    }
    zis.closeEntry();
    zis.close();
    return ramlContent;
  }

  private Path findRamlZip() throws RouterRegistrationException {
    String ramlZipPath = "";
    try (Stream<Path> walk = Files.walk(Paths.get(System.getProperty("mule.home"), "apps"), 10)) {
      List<String> ramlFiles = walk
          .filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().endsWith("-raml.zip"))
          .map(x -> x.toString())
          .collect(Collectors.toList());
      ramlFiles.forEach(LOGGER::debug);
      if (ramlFiles.size() != 1) {
        throw new RouterRegistrationException(
            "Found too many, or too few, matching results for API RAML zip location.\nExpected 1, got "
                + ramlFiles.size());
      }
      ramlZipPath = ramlFiles.get(0);
      LOGGER.info("FOUND API RAML AT:\n" + ramlZipPath);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return Paths.get(ramlZipPath);
  }

  private RequestBody buildPayload(String routeName, String raml) {
    // Prepare request body
    JSONObject payload = new JSONObject();
    payload.put("RouteName", routeName);
    payload.put("RouteRAML", raml);
    LOGGER.debug("\nPAYLOAD IS:\n" + payload.toString());
    return RequestBody.create(
        payload.toString(),
        MediaType.parse("application/json"));
  }
  
  private void registerToRouter(String routerAddress) {
    if (System.getenv("MULE_BASE") == null) {
      LOGGER.info(
          "\n*** Don't seem to be running inside a Mule runtime, router registration will not be attempted ***\n");
      return;
    }

    LOGGER.info("Attempting to register to dynamic router...");
    
    try {
      // Find and extract the RAML file for this API
      String raml = extractRaml(findRamlZip().toString());

      // Prepare the registration client and request
      OkHttpClient client = new OkHttpClient.Builder()
              .readTimeout(10, TimeUnit.SECONDS)
              .writeTimeout(10, TimeUnit.SECONDS)
          .build();

      Request request = new Request.Builder()
              .url(routerAddress)
              .post(buildPayload("Test API", raml))
          .build();

      // Submit the RAML to the router for registration
      Response response = client.newCall(request).execute();

      if (!response.isSuccessful()) {
        throw new RouterRegistrationException("ROUTER REGISTRATION ERROR:\nCODE: " + response.code() + "\nMSG: " + response.message());
      }

      JSONObject responseJson = new JSONObject(response.body().string());
      LOGGER.debug("\nROUTER RESPONSE IS:\nCODE: " + response.code() + "\nBODY:\n" + responseJson.toString() + "\n");
      
      LOGGER.info("Registration to dynamic router complete!");
    } catch (IOException | RouterRegistrationException e) {
      LOGGER.error("ROUTER REGISTRATION FAILED!");
      e.printStackTrace();
    }
  }
}
