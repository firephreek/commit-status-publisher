package jetbrains.buildServer.commitPublisher;

import com.google.gson.Gson;
import java.security.KeyStore;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import jetbrains.buildServer.serverSide.BuildTypeIdentity;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Map;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;

public abstract class BasePublisherSettings implements CommitStatusPublisherSettings {

  private static final String PARAM_PUBLISH_BUILD_QUEUED_STATUS = "teamcity.commitStatusPublisher.publishQueuedBuildStatus";

  protected final PluginDescriptor myDescriptor;
  protected final WebLinks myLinks;
  protected final ExecutorServices myExecutorServices;
  protected final CommitStatusPublisherProblems myProblems;
  private final SSLTrustStoreProvider myTrustStoreProvider;
  private final ConcurrentHashMap<String, TimestampedServerVersion> myServerVersions;
  protected final Gson myGson = new Gson();

  public BasePublisherSettings(@NotNull final ExecutorServices executorServices,
                               @NotNull PluginDescriptor descriptor,
                               @NotNull WebLinks links,
                               @NotNull CommitStatusPublisherProblems problems,
                               @NotNull SSLTrustStoreProvider trustStoreProvider) {
    myDescriptor = descriptor;
    myLinks= links;
    myExecutorServices = executorServices;
    myProblems = problems;
    myTrustStoreProvider = trustStoreProvider;
    myServerVersions = new ConcurrentHashMap<>();
  }

  @Nullable
  public Map<String, String> getDefaultParameters() {
    return null;
  }

  @Nullable
  @Override
  public Map<String, String> transformParameters(@NotNull Map<String, String> params) {
    return null;
  }

  @NotNull
  @Override
  public String describeParameters(@NotNull final Map<String, String> params) {
    return String.format("Post commit status to %s", getName());
  }

  @NotNull
  @Override
  public Map<OAuthConnectionDescriptor, Boolean> getOAuthConnections(final SProject project, final SUser user) {
    return Collections.emptyMap();
  }

  public boolean isEnabled() {
    return true;
  }

  @Override
  public boolean isPublishingForVcsRoot(final VcsRoot vcsRoot) {
    return true;
  }

  @Override
  public boolean isEventSupported(Event event, final SBuildType buildType, final Map<String, String> params) {
    return getSupportedEvents(buildType, params).contains(event);
  }

  @Override
  public boolean isTestConnectionSupported() { return false; }

  @Override
  public void testConnection(@NotNull BuildTypeIdentity buildTypeOrTemplate, @NotNull VcsRoot root, @NotNull Map<String, String> params) throws PublisherException {
    throw new UnsupportedOperationException(String.format("Test connection functionality is not supported by %s publisher", getName()));
  }

  @Nullable
  @Override
  public KeyStore trustStore() {
    return myTrustStoreProvider.getTrustStore();
  }

  protected Set<Event> getSupportedEvents(final SBuildType buildType, final Map<String, String> params) {
    return Collections.emptySet();
  }

  protected boolean isBuildQueuedSupported(final SBuildType buildType, final Map<String, String> params) {
    return "true".equalsIgnoreCase(buildType.getParameterValue(PARAM_PUBLISH_BUILD_QUEUED_STATUS));
  }

  @Override
  @Nullable
  public String getServerVersion(@NotNull String url) {
    TimestampedServerVersion version = myServerVersions.get(url);
    if (version != null && !version.isObsolete())
      return version.get();
    final String v;
    try {
       v = retrieveServerVersion(url);
    } catch (PublisherException ex) {
      if (version != null) {
        // if we failed to retrieve the information, just renew the timestamp of the old one for now
        myServerVersions.put(url, new TimestampedServerVersion(version.get()));
        return version.get();
      }
      return null;
    }
    if (v != null) {
      version = new TimestampedServerVersion(v);
      myServerVersions.put(url, version);
      return v;
    }
    return null;
  }

  @Nullable
  protected String retrieveServerVersion(@NotNull String url) throws PublisherException {
    return null;
  }

  private static class TimestampedServerVersion {
    final static long EXPIRATION_TIME_MS = TimeUnit.DAYS.toMillis(1);
    final private String myServerVersion;
    final private long myTimestamp;

    TimestampedServerVersion(@NotNull String version) {
      myServerVersion = version;
      myTimestamp = System.currentTimeMillis();
    }

    @NotNull
    String get() {
      return myServerVersion;
    }

    boolean isObsolete() {
      return System.currentTimeMillis() - myTimestamp > EXPIRATION_TIME_MS;
    }
  }
}
