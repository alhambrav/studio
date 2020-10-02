/*
 * Copyright (C) 2007-2020 Crafter Software Corporation. All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.craftercms.studio.impl.v2.upgrade;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.craftercms.commons.config.ConfigurationException;
import org.craftercms.commons.config.YamlConfiguration;
import org.craftercms.commons.entitlements.exception.EntitlementException;
import org.craftercms.commons.entitlements.validator.DbIntegrityValidator;
import org.craftercms.commons.upgrade.UpgradePipeline;
import org.craftercms.commons.upgrade.UpgradePipelineFactory;
import org.craftercms.commons.upgrade.VersionProvider;
import org.craftercms.commons.upgrade.exception.UpgradeException;
import org.craftercms.commons.upgrade.impl.AbstractUpgradeManager;
import org.craftercms.commons.upgrade.impl.UpgradeContext;
import org.craftercms.studio.api.v1.log.Logger;
import org.craftercms.studio.api.v1.log.LoggerFactory;
import org.craftercms.studio.api.v1.repository.ContentRepository;
import org.craftercms.studio.api.v1.repository.RepositoryItem;
import org.craftercms.studio.api.v2.service.system.InstanceService;
import org.craftercms.studio.api.v2.upgrade.StudioUpgradeManager;
import org.craftercms.studio.api.v2.utils.StudioConfiguration;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static java.nio.file.Paths.get;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.text.StringSubstitutor.replace;
import static org.craftercms.studio.api.v2.upgrade.UpgradeConstants.CONFIG_KEY_CONFIGURATIONS;
import static org.craftercms.studio.api.v2.upgrade.UpgradeConstants.CONFIG_KEY_ENVIRONMENT;
import static org.craftercms.studio.api.v2.upgrade.UpgradeConstants.CONFIG_KEY_MODULE;
import static org.craftercms.studio.api.v2.upgrade.UpgradeConstants.CONFIG_KEY_PATH;
import static org.craftercms.studio.api.v2.upgrade.UpgradeConstants.VERSION_3_0_0;
import static org.craftercms.studio.api.v2.utils.StudioConfiguration.CONFIGURATION_SITE_CONFIG_BASE_PATH_PATTERN;
import static org.craftercms.studio.api.v2.utils.StudioConfiguration.CONFIGURATION_SITE_MUTLI_ENVIRONMENT_CONFIG_BASE_PATH_PATTERN;

/**
 * Default implementation for {@link StudioUpgradeManager}.
 * @author joseross
 */
@SuppressWarnings("unchecked, rawtypes")
public class StudioUpgradeManagerImpl extends AbstractUpgradeManager<String> implements StudioUpgradeManager {

    private static final Logger logger = LoggerFactory.getLogger(StudioUpgradeManagerImpl.class);

    public static final String SQL_QUERY_SITES_3_0_0 = "select site_id from cstudio_site where system = 0";
    public static final String SQL_QUERY_SITES = "select site_id from site where system = 0";

    protected VersionProvider dbVersionProvider;
    protected UpgradePipelineFactory<String> dbPipelineFactory;

    protected UpgradePipelineFactory<String> bpPipelineFactory;

    protected Resource configurationFile;

    protected DataSource dataSource;
    protected DbIntegrityValidator integrityValidator;
    protected ContentRepository contentRepository;
    protected StudioConfiguration studioConfiguration;
    protected InstanceService instanceService;

    public StudioUpgradeManagerImpl(VersionProvider dbVersionProvider,
                                    UpgradePipelineFactory<String> dbPipelineFactory,
                                    UpgradePipelineFactory<String> bpPipelineFactory, Resource configurationFile,
                                    DataSource dataSource, DbIntegrityValidator integrityValidator,
                                    ContentRepository contentRepository, StudioConfiguration studioConfiguration,
                                    InstanceService instanceService) {
        this.dbVersionProvider = dbVersionProvider;
        this.dbPipelineFactory = dbPipelineFactory;
        this.bpPipelineFactory = bpPipelineFactory;
        this.configurationFile = configurationFile;
        this.dataSource = dataSource;
        this.integrityValidator = integrityValidator;
        this.contentRepository = contentRepository;
        this.studioConfiguration = studioConfiguration;
        this.instanceService = instanceService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void upgradeDatabaseAndConfiguration() throws UpgradeException, ConfigurationException {
        logger.info("Checking upgrades for the database and configuration");

        var context = createUpgradeContext(StringUtils.EMPTY);
        var pipeline = dbPipelineFactory.getPipeline(context);
        pipeline.execute(context);

    }

    protected VersionProvider getVersionProvider(String name, Object... args) {
        return (VersionProvider) applicationContext.getBean(name, args);
    }

    protected UpgradePipelineFactory<String> getPipelineFactory(String factoryName) {
        return (UpgradePipelineFactory<String>) applicationContext.getBean(factoryName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doUpgrade(final UpgradeContext<String> context) throws UpgradeException, ConfigurationException {
        logger.info("Checking upgrades for site {0}", context.getTarget());

        UpgradePipeline pipeline = getPipelineFactory("sitePipelineFactory").getPipeline(context);
        pipeline.execute(context);

        upgradeSiteConfiguration((StudioUpgradeContext) context);
    }

    @Override
    protected List<String> doGetTargets() throws Exception {
        String currentDbVersion = dbVersionProvider.getVersion(createUpgradeContext(StringUtils.EMPTY));

        List<String> sites;
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        if(currentDbVersion.equals(VERSION_3_0_0)) {
            sites = jdbcTemplate.queryForList(SQL_QUERY_SITES_3_0_0, String.class);
        } else {
            sites = jdbcTemplate.queryForList(SQL_QUERY_SITES, String.class);
        }

        return sites.stream().filter(this::checkIfSiteRepoExists).collect(toList());
    }

    @Override
    protected UpgradeContext<String> createUpgradeContext(String site) {
        return new StudioUpgradeContext(site, studioConfiguration, dataSource, instanceService);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void upgradeSiteConfiguration(StudioUpgradeContext context) throws UpgradeException {
        var site = context.getTarget();
        logger.info("Checking upgrades for configuration in site {0}", site);

        HierarchicalConfiguration config = loadUpgradeConfiguration();
        List<HierarchicalConfiguration> managedFiles = config.childConfigurationsAt(CONFIG_KEY_CONFIGURATIONS);
        String configPath = null;

        try {
            for (HierarchicalConfiguration configFile : managedFiles) {
                String module = configFile.getString(CONFIG_KEY_MODULE);
                String file = configFile.getString(CONFIG_KEY_PATH);
                List<String> environments = getExistingEnvironments(site);

                for (String env : environments) {
                    Map<String, String> values = new HashMap<>();
                    values.put(CONFIG_KEY_MODULE, module);
                    values.put(CONFIG_KEY_ENVIRONMENT, env);
                    String basePath;

                    if (StringUtils.isEmpty(env)) {
                        basePath = studioConfiguration.getProperty(CONFIGURATION_SITE_CONFIG_BASE_PATH_PATTERN);
                    } else {
                        basePath = studioConfiguration.getProperty(
                                CONFIGURATION_SITE_MUTLI_ENVIRONMENT_CONFIG_BASE_PATH_PATTERN);
                    }
                    configPath = get(replace(basePath, values, "{", "}"), file).toString();
                    logger.info("Checking upgrades for file {0}", configPath);
                    context.setCurrentConfigName(configFile.getRootElementName());
                    context.setCurrentConfigPath(configPath);

                    var pipeline = getPipelineFactory("configurationPipelineFactory").getPipeline(context);
                    pipeline.execute(context);
                }
            }
        } catch (Exception e) {
            logger.error("Error upgrading configuration file {0}", e, configPath);
        } finally {
            context.clearCurrentConfig();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void upgradeExistingSites() throws UpgradeException {
        upgrade();
    }

    protected boolean checkIfSiteRepoExists(String site) {
        boolean toRet = false;
        String firstCommitId = contentRepository.getRepoFirstCommitId(site);
        if (!StringUtils.isEmpty(firstCommitId)) {
            toRet = true;
        }
        return toRet;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void upgradeBlueprints() throws UpgradeException, ConfigurationException {
        logger.info("Checking upgrades for the blueprints");

        var context = createUpgradeContext(StringUtils.EMPTY);
        UpgradePipeline pipeline = bpPipelineFactory.getPipeline(context);
        pipeline.execute(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getExistingEnvironments(String site) {
        logger.debug("Looking for existing environments in site {0}", site);
        List<String> result = new LinkedList<>();

        // add the default env that will always exist
        result.add(StringUtils.EMPTY);

        String basePath = studioConfiguration.getProperty(CONFIGURATION_SITE_CONFIG_BASE_PATH_PATTERN);
        String envPath = studioConfiguration.getProperty(CONFIGURATION_SITE_MUTLI_ENVIRONMENT_CONFIG_BASE_PATH_PATTERN);

        RepositoryItem[] modules = contentRepository.getContentChildren(site,
                replace(basePath, Collections.singletonMap(CONFIG_KEY_MODULE, StringUtils.EMPTY), "{", "}"));

        for (RepositoryItem module : modules) {
            logger.debug("Looking for existing environments for module {0} in site {1}", module.name, site);

            Map<String, String> values = new HashMap<>();
            values.put(CONFIG_KEY_MODULE, module.name);
            values.put(CONFIG_KEY_ENVIRONMENT, StringUtils.EMPTY);

            RepositoryItem[] environments =
                    contentRepository.getContentChildren(site, replace(envPath, values, "{", "}"));

            for (RepositoryItem env : environments) {
                logger.debug("Adding environment {0}", env.name);
                result.add(env.name);
            }
        }

        return result;
    }

    /**
     * Obtains the current version and starts the upgrade process.
     * @throws UpgradeException if there is any error in the upgrade process
     * @throws EntitlementException if there is any validation error after the upgrade process
     */
    public void init() throws UpgradeException, EntitlementException, ConfigurationException {

        upgradeBlueprints();
        upgradeDatabaseAndConfiguration();
        upgradeExistingSites();

        try {
            integrityValidator.validate(dataSource.getConnection());
        } catch (SQLException e) {
            logger.error("Could not connect to database for integrity validation", e);
            throw new UpgradeException("Could not connect to database for integrity validation", e);
        }
    }

    protected HierarchicalConfiguration loadUpgradeConfiguration() throws UpgradeException {
        YamlConfiguration configuration = new YamlConfiguration();
        try (InputStream is = configurationFile.getInputStream()) {
            configuration.read(is);
        } catch (Exception e) {
            throw  new UpgradeException("Error reading configuration file", e);
        }
        return configuration;
    }

}
