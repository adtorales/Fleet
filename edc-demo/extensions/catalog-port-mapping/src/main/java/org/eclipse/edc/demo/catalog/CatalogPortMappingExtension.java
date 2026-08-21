package org.eclipse.edc.demo.catalog;

import org.eclipse.edc.runtime.metamodel.annotation.Configuration;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.runtime.metamodel.annotation.Settings;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;

@Extension(CatalogPortMappingExtension.NAME)
public class CatalogPortMappingExtension implements ServiceExtension {
    public static final String NAME = "Catalog Port Mapping";

    private static final String API_CONTEXT = "catalog";
    private static final int DEFAULT_PORT = 8185;
    private static final String DEFAULT_PATH = "/catalog";

    @Configuration
    private CatalogApiConfiguration apiConfiguration;

    @Inject
    private PortMappingRegistry portMappingRegistry;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        portMappingRegistry.register(new PortMapping(API_CONTEXT, apiConfiguration.port(), apiConfiguration.path()));
    }

    @Settings
    record CatalogApiConfiguration(
            @Setting(key = "web.http." + API_CONTEXT + ".port", description = "Port for " + API_CONTEXT + " api context", defaultValue = DEFAULT_PORT + "")
            int port,
            @Setting(key = "web.http." + API_CONTEXT + ".path", description = "Path for " + API_CONTEXT + " api context", defaultValue = DEFAULT_PATH)
            String path
    ) {
    }
}
