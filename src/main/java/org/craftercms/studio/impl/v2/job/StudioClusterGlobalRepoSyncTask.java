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

package org.craftercms.studio.impl.v2.job;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.StringUtils;
import org.craftercms.commons.crypto.CryptoException;
import org.craftercms.studio.api.v1.exception.ServiceLayerException;
import org.craftercms.studio.api.v1.exception.repository.InvalidRemoteUrlException;
import org.craftercms.studio.api.v1.job.Job;
import org.craftercms.studio.api.v1.log.Logger;
import org.craftercms.studio.api.v1.log.LoggerFactory;
import org.craftercms.studio.api.v1.repository.ContentRepository;
import org.craftercms.studio.api.v2.dal.ClusterMember;
import org.craftercms.studio.api.v2.utils.StudioConfiguration;
import org.craftercms.studio.impl.v2.service.cluster.StudioClusterUtils;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.RemoteSetUrlCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.URIish;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import static org.craftercms.studio.api.v2.utils.StudioConfiguration.REPO_SYNC_DB_COMMIT_MESSAGE_NO_PROCESSING;
import static org.craftercms.studio.impl.v1.repository.git.GitContentRepositoryConstants.CLUSTER_NODE_REMOTE_NAME_PREFIX;
import static org.craftercms.studio.impl.v1.repository.git.GitContentRepositoryConstants.CONFIG_PARAMETER_URL;
import static org.craftercms.studio.impl.v1.repository.git.GitContentRepositoryConstants.CONFIG_SECTION_REMOTE;
import static org.craftercms.studio.impl.v1.repository.git.GitContentRepositoryConstants.GIT_ROOT;

public class StudioClusterGlobalRepoSyncTask implements Job {

    private static final Logger logger = LoggerFactory.getLogger(StudioClusterGlobalRepoSyncTask.class);
    private static ReentrantLock singleWorkerLock = new ReentrantLock();
    private static Map<String, String> existingRemotes = new HashMap<String, String>();

    private int executeEveryNCycles;
    private int counter;
    private StudioClusterUtils studioClusterUtils;
    private StudioConfiguration studioConfiguration;
    private ContentRepository contentRepository;



    public StudioClusterGlobalRepoSyncTask(int executeEveryNCycles,
                                           StudioClusterUtils studioClusterUtils,
                                           StudioConfiguration studioConfiguration,
                                           ContentRepository contentRepository) {
        this.executeEveryNCycles = executeEveryNCycles;
        this.counter = executeEveryNCycles;
        this.studioClusterUtils = studioClusterUtils;
        this.studioConfiguration = studioConfiguration;
        this.contentRepository = contentRepository;
    }

    private synchronized boolean checkCycleCounter() {
        return !(--counter > 0);
    }

    @Override
    public void execute() {
        if (checkCycleCounter()) {
            executeInternal();
            counter = executeEveryNCycles;
        }
    }

    private void executeInternal() {
        // Lock site and begin sync
        if (singleWorkerLock.tryLock()) {
            // Log start time
            long startTime = System.currentTimeMillis();
            logger.debug("Worker starts syncing cluster node global repo");
            try {
                HierarchicalConfiguration<ImmutableNode> registrationData = studioClusterUtils.getClusterConfiguration();
                if (registrationData != null && !registrationData.isEmpty()) {
                    String localAddress = studioClusterUtils.getClusterNodeLocalAddress();
                    List<ClusterMember> clusterNodes = studioClusterUtils.getClusterNodes(localAddress);
                    // Check if repo exists
                    logger.debug("Check if global repository exists");
                    boolean success = true;

                    if (!checkIfRepoExists()) {
                        // Site doesn't exist locally, create it
                        success = cloneRepository(clusterNodes);
                    }

                    if (success) {
                        try {
                            // Add the remote repositories to the local repository to sync from if not added already
                            logger.debug("Add remotes for global repository");
                            addRemotes(clusterNodes);

                        } catch (InvalidRemoteUrlException | ServiceLayerException | CryptoException e) {
                            logger.error("Error while adding remotes on cluster node for global repo", e);
                        }

                        try {
                            // Sync with remote and update the local cache with the last commit ID to speed things up
                            logger.debug("Update content for global repo");
                            updateContent(clusterNodes);
                        } catch (IOException | CryptoException | ServiceLayerException e) {
                            logger.error("Error while updating content for global repo on cluster node.", e);
                        }

                    }
                }
            } catch (ServiceLayerException | CryptoException e) {
                logger.error("Error while cloning global repository from other nodes", e);
            } finally {
                if (singleWorkerLock != null) {
                    singleWorkerLock.unlock();
                }
            }

            // Compute execution duration and log it
            long duration = System.currentTimeMillis() - startTime;
            logger.debug("Worker finished syncing cluster node for global repo");
            logger.debug("Worker performed cluster node sync for global repo in " + duration + "ms");
        } else {
            // Couldn't get the site lock, another worker is active, abandoning this cycle
            logger.debug("Unable to get cluster lock, another worker is holding the lock for global repo");
        }
        logger.debug("Finished Cluster Node Sync task for global repo");
    }

    private boolean checkIfRepoExists() {
        String firstCommitId = contentRepository.getRepoFirstCommitId(StringUtils.EMPTY);
        if (!StringUtils.isEmpty(firstCommitId)) {
            return true;
        } else {
            return false;
        }
    }

    private boolean cloneRepository(List<ClusterMember> clusterNodes)
            throws CryptoException, ServiceLayerException {
        // Clone from the first node in the cluster (it doesn't matter which one to clone from, so pick the first)
        // we will eventually to catch up to the latest
        boolean cloned = false;
        int idx = 0;
        while (!cloned && idx < clusterNodes.size()) {
            ClusterMember remoteNode = clusterNodes.get(idx++);
            logger.debug("Cloning global repository from " + remoteNode.getLocalAddress());

            // prepare a new folder for the cloned repository
            Path siteSandboxPath = Paths.get(studioConfiguration.getProperty(StudioConfiguration.REPO_BASE_PATH),
                    studioConfiguration.getProperty(StudioConfiguration.GLOBAL_REPO_PATH));
            File localPath = siteSandboxPath.toFile();
            localPath.delete();
            // then clone
            logger.debug("Cloning from " + remoteNode.getGitUrl() + " to " + localPath);
            CloneCommand cloneCommand = Git.cloneRepository();
            Git cloneResult = null;

            try {
                final Path tempKey = Files.createTempFile(UUID.randomUUID().toString(), ".tmp");
                logger.debug("Add user credentials if provided");

                studioClusterUtils.configureAuthenticationForCommand(remoteNode, cloneCommand, tempKey);

                String cloneUrl = remoteNode.getGitUrl().replace("/sites/{siteId}", "/global");

                logger.debug("Executing clone command");
                cloneResult = cloneCommand
                        .setURI(cloneUrl)
                        .setRemote(remoteNode.getGitRemoteName())
                        .setDirectory(localPath)
                        .setCloneAllBranches(true)
                        .call();
                Files.deleteIfExists(tempKey);
                cloned = true;

            } catch (InvalidRemoteException e) {
                logger.error("Invalid remote repository: " + remoteNode.getGitRemoteName() +
                        " (" + remoteNode.getGitUrl() + ")", e);
            } catch (TransportException e) {
                if (StringUtils.endsWithIgnoreCase(e.getMessage(), "not authorized")) {
                    logger.error("Bad credentials or read only repository: " + remoteNode.getGitRemoteName() +
                            " (" + remoteNode.getGitUrl() + ")", e);
                } else {
                    logger.error("Remote repository not found: " + remoteNode.getGitRemoteName() +
                            " (" + remoteNode.getGitUrl() + ")", e);
                }
            } catch (GitAPIException | IOException e) {
                logger.error("Error while creating repository for site with path" + siteSandboxPath.toString(), e);
            } finally {
                if (cloneResult != null) {
                    cloneResult.close();
                }
            }
        }
        return cloned;
    }

    protected void addRemotes(List<ClusterMember> clusterNodes)
            throws InvalidRemoteUrlException, ServiceLayerException, CryptoException {
        logger.debug("Add cluster members as remotes to local sandbox repository");
        for (ClusterMember member : clusterNodes) {
            if (existingRemotes != null && existingRemotes.containsKey(member.getGitRemoteName())) {
                continue;
            }

            try {
                if (existingRemotes == null) {
                    existingRemotes = new HashMap<String, String>();
                }

                String remoteUrl = member.getGitUrl().replace("/sites/{siteId}", "/global");
                addRemoteRepository(member, remoteUrl);
                existingRemotes.put(member.getGitRemoteName(), StringUtils.EMPTY);

            } catch (IOException e) {
                logger.error("Failed to open repository", e);
            }
        }
    }
    protected void addRemoteRepository(ClusterMember member, String remoteUrl)
            throws IOException, InvalidRemoteUrlException, ServiceLayerException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();

        Repository repo = builder
                .setGitDir(Paths.get(studioConfiguration.getProperty(StudioConfiguration.REPO_BASE_PATH),
                        studioConfiguration.getProperty(StudioConfiguration.GLOBAL_REPO_PATH)).resolve(GIT_ROOT)
                        .toFile())
                .readEnvironment()
                .findGitDir()
                .build();

        try (Git git = new Git(repo)) {

            Config storedConfig = repo.getConfig();
            Set<String> remotes = storedConfig.getSubsections(CONFIG_SECTION_REMOTE);

            if (remotes.contains(member.getGitRemoteName().replaceFirst(CLUSTER_NODE_REMOTE_NAME_PREFIX, ""))) {
                try {
                    studioClusterUtils.removeRemote(git,
                            member.getGitRemoteName().replaceFirst(CLUSTER_NODE_REMOTE_NAME_PREFIX, ""));
                } catch (GitAPIException e) {
                    logger.debug("Error while cleaning remote repositories for global repo", e);
                }
            }

            if (remotes.contains(member.getGitRemoteName())) {
                logger.debug("Remote " + member.getGitRemoteName() + " already exists for global repo");
                String storedRemoteUrl = storedConfig.getString(CONFIG_SECTION_REMOTE,
                        member.getGitRemoteName(), CONFIG_PARAMETER_URL);
                if (!StringUtils.equals(storedRemoteUrl, remoteUrl)) {
                    RemoteSetUrlCommand remoteSetUrlCommand = git.remoteSetUrl();
                    remoteSetUrlCommand.setName(member.getGitRemoteName());
                    remoteSetUrlCommand.setUri(new URIish(remoteUrl));
                    remoteSetUrlCommand.call();
                }
            } else {
                logger.debug("Add " + member.getLocalAddress() + " as remote to sandbox");
                RemoteAddCommand remoteAddCommand = git.remoteAdd();
                remoteAddCommand.setName(member.getGitRemoteName());
                remoteAddCommand.setUri(new URIish(remoteUrl));
                remoteAddCommand.call();
            }

        } catch (URISyntaxException e) {
            logger.error("Remote URL is invalid " + remoteUrl, e);
            throw new InvalidRemoteUrlException();
        } catch (GitAPIException e) {
            logger.error("Error while adding remote " + member.getGitRemoteName() + " (url: " + remoteUrl +
                    ") for global repo", e);
            throw new ServiceLayerException("Error while adding remote " + member.getGitRemoteName() +
                    " (url: " + remoteUrl + ") for global repo", e);
        }
    }

    protected void updateContent(List<ClusterMember> clusterNodes)
            throws IOException, CryptoException, ServiceLayerException {
        logger.debug("Update global repo");

        Path siteSandboxPath = Paths.get(studioConfiguration.getProperty(StudioConfiguration.REPO_BASE_PATH),
                studioConfiguration.getProperty(StudioConfiguration.GLOBAL_REPO_PATH)).resolve(GIT_ROOT);
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repo = builder
                .setGitDir(siteSandboxPath.toFile())
                .readEnvironment()
                .findGitDir()
                .build();

        try (Git git = new Git(repo)) {
            logger.debug("Update content from each active cluster memeber");
            for (ClusterMember remoteNode : clusterNodes) {
                updateBranch(git, remoteNode);
            }
        } catch (GitAPIException e) {
            logger.error("Error while syncing cluster node global repo content", e);
        }
    }

    private void updateBranch(Git git, ClusterMember remoteNode) throws CryptoException, GitAPIException,
            IOException, ServiceLayerException {
        final Path tempKey = Files.createTempFile(UUID.randomUUID().toString(), ".tmp");
        FetchCommand fetchCommand = git.fetch().setRemote(remoteNode.getGitRemoteName());
        fetchCommand = studioClusterUtils.configureAuthenticationForCommand(remoteNode, fetchCommand, tempKey);
        FetchResult fetchResult = fetchCommand.call();

        ObjectId commitToMerge;
        Ref r;
        if (fetchResult != null) {
            r = fetchResult.getAdvertisedRef(Constants.MASTER);
            if (r == null) {
                r = fetchResult.getAdvertisedRef(Constants.R_HEADS + Constants.MASTER);
            }
            if (r != null) {
                commitToMerge = r.getObjectId();

                MergeCommand mergeCommand = git.merge();
                mergeCommand.setMessage(studioConfiguration.getProperty(REPO_SYNC_DB_COMMIT_MESSAGE_NO_PROCESSING));
                mergeCommand.setCommit(true);
                mergeCommand.include(remoteNode.getGitRemoteName(), commitToMerge);
                mergeCommand.setStrategy(MergeStrategy.THEIRS);
                mergeCommand.call();
            }
        }

        Files.delete(tempKey);
    }
}
