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

package org.craftercms.studio.api.v2.security;

import org.craftercms.studio.api.v1.log.Logger;
import org.craftercms.studio.api.v1.log.LoggerFactory;

import java.util.List;

import static org.craftercms.studio.permissions.StudioPermissions.ACTION_ADD_REMOTE;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_AUDIT_LOG;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_CANCEL_FAILED_PULL;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_CANCEL_PUBLISH;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_CHANGE_CONTENT_TYPE;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_CLONE_CONTENT_CMIS;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_COMMIT_RESOLUTION;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_CREATE_CLUSTER;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_CREATE_CONTENT;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_CREATE_FOLDER;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_CREATE_GROUPS;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_CREATE_SITE;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_CREATE_USERS;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_DELETE;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_DELETE_CLUSTER;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_DELETE_CONTENT;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_DELETE_GROUPS;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_DELETE_USERS;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_ENCRYPTION_TOOL;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_GET_PUBLISHING_QUEUE;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_LIST_CMIS;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_LIST_REMOTES;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_PUBLISH;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_PULL_FROM_REMOTE;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_PUSH_TO_REMOTE;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_READ;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_READ_CLUSTER;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_READ_GROUPS;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_READ_LOGS;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_READ_USERS;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_REBUILD_DATABASE;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_REMOVE_REMOTE;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_RESOLVE_CONFLICT;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_S3_READ;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_S3_WRITE;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_SEARCH_CMIS;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_SITE_DIFF_CONFLICTED_FILE;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_SITE_STATUS;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_UPDATE_CLUSTER;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_UPDATE_GROUPS;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_UPDATE_USERS;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_UPLOAD_CONTENT_CMIS;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_WEBDAV_READ;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_WEBDAV_WRITE;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_WRITE;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_WRITE_CONFIGURATION;
import static org.craftercms.studio.permissions.StudioPermissions.ACTION_WRITE_GLOBAL_CONFIGURATION;

public enum AvailableActions {

    CONTENT_CREATE("Content: Create", 0),
    CONTENT_UPDATE("Content: Update", 1),
    CONTENT_DELETE("Content: Delete", 2),
    CONTENT_CUT("Content: Cut", 3),
    CONTENT_COPY("Content: Copy", 4),
    CONTENT_PASTE("Content: Paste", 5),
    CONTENT_MOVE_RENAME("Content: Move/Rename", 6),
    CONTENT_DUPLICATE("Content: Duplicate", 7),
    CONTENT_TRANSLATE("Content: Translate", 8),
    REQUEST_PUBLISH("Request Publish", 9),
    APPROVE_PUBLISH("Approve Publish", 10),
    REJECT_PUBLISH("Reject Publish", 11),
    CANCEL_PUBLISH("Cancel Publish", 12),
    BULK_PUBLISH("Bulk Publish", 13),
    READ_PUBLISHING_QUEUE("Read Publishing Queue", 14),
    READ_PUBLISHING_STATUS("Read Publishing Status", 15),
    START_PUBLISHING("Start Publishing", 16),
    STOP_PUBLISHING("Stop Publishing", 17),
    PUBLISH_BY_COMMIT_ID("Publish by Commit ID", 18),
    SET_WORKFLOW_STATE("Set Workflow State", 19),
    READ_AUDIT_LOG("Read Audit Log", 20),
    READ_SITE_LOG("Read Site Log", 21),
    ADD_REMOTE_REPOSITORY("Add Remote Repository", 22),
    REMOVE_REMOTE_REPOSITORY("Remove Remote Repositroy", 23),
    PULL_FROM_REMOTE_REPOSITORY("Pull From Remote Repository", 24),
    PUSH_TO_REMOTE_REPOSITORY("Push To Remote Repository", 25),
    RESOLVE_CONFLICTS("Resolve Conflicts", 26),
    SYSTEM_CREATE("System Create", 27),
    SYSTEM_READ("System Read", 28),
    SYSTEM_UPDATE("System Update", 29),
    SYSTEM_DELETE("System Delete", 30),
    READ_STUDIO_LOG_SETTINGS("Read Studio Log Settings", 31),
    UPDATE_STUDIO_LOG_SETTINGS("Update Studio Log Settings", 32);

    public final long value;
    public final String label;

    AvailableActions(String label, long exponent) {
        this.value = Math.round(Math.pow(2, exponent));
        this.label = label;
    }

    private static final Logger logger = LoggerFactory.getLogger(AvailableActions.class);

    // Map permissions to available actions
    // add_remote
    public static final long ADD_REMOTE = ADD_REMOTE_REPOSITORY.value;
    // audit_log
    public static final long AUDIT_LOG = READ_AUDIT_LOG.value;
    // cancel_failed_pull
    public static final long CANCEL_FAILED_PULL = RESOLVE_CONFLICTS.value;
    // cancel_publish
    public static final long CANCEL_PUBLISH_PERMISSION = CANCEL_PUBLISH.value;
    // Change Content Type
    public static final long CHANGE_CONTENT_TYPE = CONTENT_UPDATE.value;
    // clone_content_cmis
    public static final long CLONE_CONTENT_CMIS = CONTENT_CREATE.value + CONTENT_UPDATE.value;
    // commit_resolution
    public static final long COMMIT_RESOLUTION = RESOLVE_CONFLICTS.value;
    // Create Content
    public static final long CREATE_CONTENT = CONTENT_CREATE.value;
    // Create Folder
    public static final long CREATE_FOLDER = CONTENT_CREATE.value;
    // create_cluster
    public static final long CREATE_CLUSTER = SYSTEM_CREATE.value;
    // create_groups
    public static final long CREATE_GROUPS = SYSTEM_CREATE.value;
    // create_users
    public static final long CREATE_USERS = SYSTEM_CREATE.value;
    // create-site
    public static final long CREATE_SITE = SYSTEM_CREATE.value;
    // Delete
    public static final long DELETE = CONTENT_DELETE.value;
    // delete_cluster
    public static final long DELETE_CLUSTER = SYSTEM_DELETE.value;
    // delete_content
    public static final long DELETE_CONTENT = CONTENT_DELETE.value;
    // delete_groups
    public static final long DELETE_GROUPS = SYSTEM_DELETE.value;
    // delete_users
    public static final long DELETE_USERS = SYSTEM_DELETE.value;
    // encryption_tool
    public static final long ENCRYPTION_TOOL = CONTENT_UPDATE.value + SYSTEM_UPDATE.value;
    // get_publishing_queue
    public static final long GET_PUBLISHING_QUEUE = READ_PUBLISHING_QUEUE.value;
    // list_cmis
    public static final long LIST_CMIS = 0L;
    // list_remotes
    public static final long LIST_REMOTES = ADD_REMOTE_REPOSITORY.value;
    // Publish
    public static final long PUBLISH = APPROVE_PUBLISH.value + REJECT_PUBLISH.value;
    // pull_from_remote
    public static final long PULL_FROM_REMOTE = PULL_FROM_REMOTE_REPOSITORY.value;
    // push_to_remote
    public static final long PUSH_TO_REMOTE = PUSH_TO_REMOTE_REPOSITORY.value;
    // Read
    public static final long READ = 0L;
    // read_cluster
    public static final long READ_CLUSTER = SYSTEM_READ.value;
    // read_groups
    public static final long READ_GROUPS = SYSTEM_READ.value;
    // read_logs
    public static final long READ_LOGS = READ_SITE_LOG.value;
    // read_users
    public static final long READ_USERS = SYSTEM_READ.value;
    // rebuild_database
    public static final long REBUILD_DATABASE = SYSTEM_UPDATE.value;
    // remove_remote
    public static final long REMOVE_REMOTE = REMOVE_REMOTE_REPOSITORY.value;
    // resolve_conflict
    public static final long RESOLVE_CONFLICT = RESOLVE_CONFLICTS.value;
    // S3 Read
    public static final long S3_READ = 0L;
    // S3 Write
    public static final long S3_WRITE = CONTENT_CREATE.value + CONTENT_UPDATE.value;
    // search_cmis
    public static final long SEARCH_CMIS = 0L;
    // site_diff_conflicted_file
    public static final long SITE_DIFF_CONFLICTED_FILE = RESOLVE_CONFLICTS.value;
    // site_status
    public static final long SITE_STATUS = SYSTEM_READ.value;
    // update_cluster
    public static final long UPDATE_CLUSTER = SYSTEM_UPDATE.value;
    // update_groups
    public static final long UPDATE_GROUPS = SYSTEM_UPDATE.value;
    // update_users
    public static final long UPDATE_USERS = SYSTEM_UPDATE.value;
    // upload_content_cmis
    public static final long UPLOAD_CONTENT_CMIS = CONTENT_CREATE.value + CONTENT_UPDATE.value;
    // webdav_read
    public static final long WEBDAV_READ = 0L;
    // webdav_write
    public static final long WEBDAV_WRITE = CONTENT_CREATE.value + CONTENT_UPDATE.value;
    // Write
    public static final long WRITE =
            CONTENT_CREATE.value + CONTENT_UPDATE.value + CONTENT_CUT.value + CONTENT_COPY.value +
                    CONTENT_PASTE.value + CONTENT_MOVE_RENAME.value + CONTENT_DUPLICATE.value;
    // write_configuration
    public static final long WRITE_CONFIGURATION = CONTENT_CREATE.value + CONTENT_UPDATE.value;
    // write_global_configuration
    public static final long WRITE_GLOBAL_CONFIGURATION = SYSTEM_CREATE.value + SYSTEM_UPDATE.value;

    public static long mapPermissionToAvailableActions(String permission) {
        long result = 0L;
        switch (permission) {
            case ACTION_ADD_REMOTE:
                result = ADD_REMOTE;
                break;
            case ACTION_AUDIT_LOG:
                result = AUDIT_LOG;
                break;
            case ACTION_CANCEL_FAILED_PULL:
                result = CANCEL_FAILED_PULL;
                break;
            case ACTION_CANCEL_PUBLISH:
                result = CANCEL_PUBLISH_PERMISSION;
                break;
            case ACTION_CHANGE_CONTENT_TYPE:
                result = CHANGE_CONTENT_TYPE;
                break;
            case ACTION_CLONE_CONTENT_CMIS:
                result = CLONE_CONTENT_CMIS;
                break;
            case ACTION_COMMIT_RESOLUTION:
                result = COMMIT_RESOLUTION;
                break;
            case ACTION_CREATE_CONTENT:
                result = CREATE_CONTENT;
                break;
            case ACTION_CREATE_FOLDER:
                result = CREATE_FOLDER;
                break;
            case ACTION_CREATE_CLUSTER:
                result = CREATE_CLUSTER;
                break;
            case ACTION_CREATE_GROUPS:
                result = CREATE_GROUPS;
                break;
            case ACTION_CREATE_USERS:
                result = CREATE_USERS;
                break;
            case ACTION_CREATE_SITE:
                result = CREATE_SITE;
                break;
            case ACTION_DELETE:
                result = DELETE;
                break;
            case ACTION_DELETE_CLUSTER:
                result = DELETE_CLUSTER;
                break;
            case ACTION_DELETE_CONTENT:
                result = DELETE_CONTENT;
                break;
            case ACTION_DELETE_GROUPS:
                result = DELETE_GROUPS;
                break;
            case ACTION_DELETE_USERS:
                result = DELETE_USERS;
                break;
            case ACTION_ENCRYPTION_TOOL:
                result = ENCRYPTION_TOOL;
                break;
            case ACTION_GET_PUBLISHING_QUEUE:
                result = GET_PUBLISHING_QUEUE;
                break;
            case ACTION_LIST_CMIS:
                result = LIST_CMIS;
                break;
            case ACTION_LIST_REMOTES:
                result = LIST_REMOTES;
                break;
            case ACTION_PUBLISH:
                result = PUBLISH;
                break;
            case ACTION_PULL_FROM_REMOTE:
                result = PULL_FROM_REMOTE;
                break;
            case ACTION_PUSH_TO_REMOTE:
                result = PUSH_TO_REMOTE;
                break;
            case ACTION_READ:
                result = READ;
                break;
            case ACTION_READ_CLUSTER:
                result = READ_CLUSTER;
                break;
            case ACTION_READ_GROUPS:
                result = READ_GROUPS;
                break;
            case ACTION_READ_LOGS:
                result = READ_LOGS;
                break;
            case ACTION_READ_USERS:
                result = READ_USERS;
                break;
            case ACTION_REBUILD_DATABASE:
                result = REBUILD_DATABASE;
                break;
            case ACTION_REMOVE_REMOTE:
                result = REMOVE_REMOTE;
                break;
            case ACTION_RESOLVE_CONFLICT:
                result = RESOLVE_CONFLICT;
                break;
            case ACTION_S3_READ:
                result = S3_READ;
                break;
            case ACTION_S3_WRITE:
                result = S3_WRITE;
                break;
            case ACTION_SEARCH_CMIS:
                result = SEARCH_CMIS;
                break;
            case ACTION_SITE_DIFF_CONFLICTED_FILE:
                result = SITE_DIFF_CONFLICTED_FILE;
                break;
            case ACTION_SITE_STATUS:
                result = SITE_STATUS;
                break;
            case ACTION_UPDATE_CLUSTER:
                result = UPDATE_CLUSTER;
                break;
            case ACTION_UPDATE_GROUPS:
                result = UPDATE_GROUPS;
                break;
            case ACTION_UPDATE_USERS:
                result = UPDATE_USERS;
                break;
            case ACTION_UPLOAD_CONTENT_CMIS:
                result = UPLOAD_CONTENT_CMIS;
                break;
            case ACTION_WEBDAV_READ:
                result = WEBDAV_READ;
                break;
            case ACTION_WEBDAV_WRITE:
                result = WEBDAV_WRITE;
                break;
            case ACTION_WRITE:
                result = WRITE;
                break;
            case ACTION_WRITE_CONFIGURATION:
                result = WRITE_CONFIGURATION;
                break;
            case ACTION_WRITE_GLOBAL_CONFIGURATION:
                result = WRITE_GLOBAL_CONFIGURATION;
                break;
            default:
                logger.warn("Permission " + permission + " not declared with available actions");
                break;
        }
        return result;
    }

    public static long mapPermissionsToAvailableActions(List<String> permissions) {
        return permissions.stream().mapToLong(AvailableActions::mapPermissionToAvailableActions).sum();
    }
}
