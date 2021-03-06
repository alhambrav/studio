/*
 * Crafter Studio Web-content authoring solution
 * Copyright (C) 2007-2016 Crafter Software Corporation.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.craftercms.studio.impl.v1.service.configuration;

import org.apache.commons.lang3.StringUtils;
import org.craftercms.commons.validation.annotations.param.ValidateParams;
import org.craftercms.commons.validation.annotations.param.ValidateStringParam;
import org.craftercms.studio.api.v1.constant.StudioConstants;
import org.craftercms.studio.api.v1.log.Logger;
import org.craftercms.studio.api.v1.log.LoggerFactory;
import org.craftercms.studio.api.v1.service.GeneralLockService;
import org.craftercms.studio.api.v1.service.configuration.ContentTypesConfig;
import org.craftercms.studio.api.v1.service.content.ContentService;
import org.craftercms.studio.api.v1.to.*;
import org.craftercms.studio.api.v1.util.StudioConfiguration;
import org.craftercms.studio.impl.v1.service.StudioCacheContext;
import org.craftercms.studio.impl.v1.util.ContentFormatUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Pattern;

import static org.craftercms.studio.api.v1.constant.StudioConstants.CONTENT_TYPE_UNKNOWN;
import static org.craftercms.studio.api.v1.constant.StudioConstants.FILE_SEPARATOR;
import static org.craftercms.studio.api.v1.util.StudioConfiguration.CONFIGURATION_SITE_CONTENT_TYPES_CONFIG_FILE_NAME;
import static org.craftercms.studio.api.v1.util.StudioConfiguration.CONFIGURATION_SITE_CONTENT_TYPES_CONFIG_PATH;

/**
 * @author Dejan Brkic
 */
public class ContentTypesConfigImpl implements ContentTypesConfig {

    private static final Logger logger = LoggerFactory.getLogger(ContentTypesConfigImpl.class);

    @Override
    @ValidateParams
    public ContentTypeConfigTO getContentTypeConfig(@ValidateStringParam(name = "site") final String site, @ValidateStringParam(name = "contentType") final String contentType) {
        if (StringUtils.isNotEmpty(contentType) && !StringUtils.equals(contentType, CONTENT_TYPE_UNKNOWN)) {
            return loadConfiguration(site, contentType);
        } else {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    @ValidateParams
    public ContentTypeConfigTO loadConfiguration(@ValidateStringParam(name = "site") String site, @ValidateStringParam(name = "contentType") String contentType) {
        String siteConfigPath = getConfigPath().replaceAll(StudioConstants.PATTERN_SITE, site)
                .replaceAll(StudioConstants.PATTERN_CONTENT_TYPE, contentType);
        String configFileFullPath = siteConfigPath + FILE_SEPARATOR + getConfigFileName();
        Document document = null;
        try {
            if (contentService.contentExists(site, configFileFullPath)) {
                document = contentService.getContentAsDocument(site, configFileFullPath);
            }
        } catch (DocumentException e) {
            logger.debug("No content type configuration document found at " + configFileFullPath, e);
        }
        if (document != null) {
            Element root = document.getRootElement();
            String name = root.valueOf("@name");
            ContentTypeConfigTO contentTypeConfig = new ContentTypeConfigTO();
            contentTypeConfig.setName(name);
            contentTypeConfig.setLabel(root.valueOf("label"));
            String imageThumbnail=root.valueOf("image-thumbnail");
            if(imageThumbnail != null)
                contentTypeConfig.setImageThumbnail(imageThumbnail);
            contentTypeConfig.setForm(root.valueOf("form"));
            boolean previewable = ContentFormatUtils.getBooleanValue(root.valueOf("previewable"));
            contentTypeConfig.setFormPath(root.valueOf("form-path"));
            contentTypeConfig.setPreviewable(previewable);
            contentTypeConfig.setModelInstancePath(root.valueOf("model-instance-path"));
            boolean contentAsFolder = ContentFormatUtils.getBooleanValue(root.valueOf("content-as-folder"));
            contentTypeConfig.setContentAsFolder(contentAsFolder);
            boolean useRoundedFolder = ContentFormatUtils.getBooleanValue(root.valueOf("use-rounded-folder"));
            contentTypeConfig.setUseRoundedFolder(useRoundedFolder);
            List<String> pathIncludes = getPaths(root, "paths/includes/pattern");
            if (pathIncludes.size() == 0) {
                // if no configuration, include every path
                pathIncludes.add(".*");
            }
            contentTypeConfig.setPathIncludes(pathIncludes);
            List<String> pathExcludes = getPaths(root, "paths/excludes/pattern");
            contentTypeConfig.setPathExcludes(pathExcludes);
            loadRoles(contentTypeConfig, root.selectNodes("allowed-roles/role"));
            loadDeleteDependencies(contentTypeConfig, root.selectNodes("delete-dependencies/delete-dependency"));
            loadCopyDependencyPatterns(contentTypeConfig, root.selectNodes("copy-dependencies/copy-dependency"));
            contentTypeConfig.setLastUpdated(ZonedDateTime.now(ZoneOffset.UTC));
            contentTypeConfig.setType(getContentTypeTypeByName(name));
            return contentTypeConfig;
        } else {
            logger.debug("No content type configuration document found at " + configFileFullPath);
            return null;
        }
    }

    /**
     * load delete dependencies mapping
     *
     * @param contentTypeConfig
     * @param nodes
     */
    protected void loadDeleteDependencies(ContentTypeConfigTO contentTypeConfig, List<Node> nodes) {
        List<DeleteDependencyConfigTO> deleteConfigs = new ArrayList<>();
        if (nodes != null) {
            for (Node node : nodes) {
                Node patternNode = node.selectSingleNode("pattern");
                Node removeFolderNode = node.selectSingleNode("remove-empty-folder");
                if(patternNode!=null){
                    String pattern = patternNode.getText();
                    String removeEmptyFolder = removeFolderNode.getText();
                    boolean isRemoveEmptyFolder=false;
                    if(removeEmptyFolder!=null){
                        isRemoveEmptyFolder = Boolean.valueOf(removeEmptyFolder);
                    }
                    if(StringUtils.isNotEmpty(pattern)){
                        DeleteDependencyConfigTO deleteConfigTO = new DeleteDependencyConfigTO(pattern, isRemoveEmptyFolder);
                        deleteConfigs.add(deleteConfigTO);
                    }
                }
            }
            contentTypeConfig.setDeleteDependencies(deleteConfigs);
        }
    }

    /**
     * Checks name for naming convention.
     * @param name Name to be check
     * @return <ul>
     * <li><b>component</b> if the name matches component naming convention</li>
     * <li><b>page</b> if the name matches page naming convention</li>
     * <li><b>unknown</b> if name don't match any known convention</li>
     * </ul>
     */
    private String getContentTypeTypeByName(String name) {
        if (Pattern.matches("/component/.*?", name)) {
            return "component";
        } else if (Pattern.matches("/page/.*?", name))
            return "page";
        else {
            return "unknown";
        }
    }

    /**
     * get paths
     *
     * @param root
     * @param path
     * @return get paths
     */
    @SuppressWarnings("unchecked")
    private List<String> getPaths(Element root, String path) {
        List<String> paths = null;
        List<Node> nodes = root.selectNodes(path);
        if (nodes != null && nodes.size() > 0) {
            paths = new ArrayList<String>(nodes.size());
            for (Node node : nodes) {
                String role = node.getText();
                if (!StringUtils.isEmpty(role)) {
                    paths.add(role);
                }
            }
        } else {
            paths = new ArrayList<String>();
        }
        return paths;
    }

    /**
     * load a list of allowed roles
     * @param config
     * @param nodes
     */
    protected void loadRoles(ContentTypeConfigTO config, List<Node> nodes) {
        Set<String> roles = null;
        if (nodes != null && nodes.size() > 0) {
            roles = new HashSet<String>(nodes.size());
            for (Node node : nodes) {
                String role = node.getText();
                if (!StringUtils.isEmpty(role)) {
                    roles.add(role);
                }
            }
        } else {
            roles = new HashSet<String>();
        }
        config.setAllowedRoles(roles);
    }

    /**
     *
     * @param config
     * @param copyDependencyNodes
     */
    protected void loadCopyDependencyPatterns(ContentTypeConfigTO config, List<Node> copyDependencyNodes) {
        List<CopyDependencyConfigTO> copyConfig = new ArrayList<CopyDependencyConfigTO>();
        if (copyDependencyNodes != null) {
            for (Node copyDependency : copyDependencyNodes) {
                Node patternNode = copyDependency.selectSingleNode("pattern");
                Node targetNode = copyDependency.selectSingleNode("target");
                if(patternNode!=null && targetNode!=null){
                    String pattern = patternNode.getText();
                    String target = targetNode.getText();
                    if(StringUtils.isNotEmpty(pattern) && StringUtils.isNotEmpty(target)){
                        CopyDependencyConfigTO copyDependencyConfigTO  = new CopyDependencyConfigTO(pattern,target);
                        copyConfig.add(copyDependencyConfigTO);
                    }
                }
            }
        }
        config.setCopyDepedencyPattern(copyConfig);

    }


    @Override
    @ValidateParams
    public ContentTypeConfigTO reloadConfiguration(@ValidateStringParam(name = "site") String site, @ValidateStringParam(name = "contentType") String contentType) {
        StudioCacheContext cacheContext = new StudioCacheContext(site, true);
        String siteConfigPath = getConfigPath().replaceAll(StudioConstants.PATTERN_SITE, site)
                .replaceAll(StudioConstants.PATTERN_CONTENT_TYPE, contentType);
        ContentTypeConfigTO config = loadConfiguration(site, contentType);
        return config;
    }

    public String getConfigPath() {
        return studioConfiguration.getProperty(CONFIGURATION_SITE_CONTENT_TYPES_CONFIG_PATH);
    }

    public String getConfigFileName() {
        return studioConfiguration.getProperty(CONFIGURATION_SITE_CONTENT_TYPES_CONFIG_FILE_NAME);
    }

    public ContentService getContentService() { return contentService; }
    public void setContentService(ContentService contentService) { this.contentService = contentService; }

    public GeneralLockService getGeneralLockService() { return generalLockService; }
    public void setGeneralLockService(GeneralLockService generalLockService) { this.generalLockService = generalLockService; }

    public StudioConfiguration getStudioConfiguration() { return studioConfiguration; }
    public void setStudioConfiguration(StudioConfiguration studioConfiguration) { this.studioConfiguration = studioConfiguration; }

    protected ContentService contentService;
    protected GeneralLockService generalLockService;
    protected StudioConfiguration studioConfiguration;
}
