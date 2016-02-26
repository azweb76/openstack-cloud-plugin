/*
 * The MIT License
 *
 * Copyright (c) Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jenkins.plugins.openstack.compute;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.trilead.ssh2.Connection;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.RelativePath;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Describable;
import hudson.model.ItemGroup;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.plugins.openstack.compute.internal.Openstack;
import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.openstack4j.api.exceptions.AuthenticationException;
import org.openstack4j.model.compute.Flavor;
import org.openstack4j.model.image.Image;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Configured options for a slave to create.
 *
 * This object can be scoped to cloud or template (or perhaps some other things). Whenever details are needed to provision/connect
 * particular slave, the most specific SlaveOptions object should be used.
 *
 * @author ogondza.
 */
public class SlaveOptions implements Describable<SlaveOptions> {

    // Provisioning attributes
    private final @CheckForNull String imageId;
    private final @CheckForNull String hardwareId;
    private final @CheckForNull String networkId;
    private final @CheckForNull String userDataId;
    private final @CheckForNull Integer instanceCap;
    private final @CheckForNull Boolean floatingIps;
    private final @CheckForNull String securityGroups;
    private final @CheckForNull String availabilityZone;
    private final @CheckForNull Integer startTimeout;
    private final @CheckForNull String keyPairName;

    // Slave launch attributes
    private final @CheckForNull Integer numExecutors;
    private final @CheckForNull String jvmOptions;
    private final @CheckForNull String fsRoot;
    private final @CheckForNull String credentialsId;
    private final @CheckForNull JCloudsCloud.SlaveType slaveType;

    // Slave attributes
    private final @CheckForNull Integer retentionTime;

    public @CheckForNull String getFsRoot() {
        return fsRoot;
    }

    public @CheckForNull String getImageId() {
        return imageId;
    }

    public @CheckForNull String getHardwareId() {
        return hardwareId;
    }

    public @CheckForNull String getNetworkId() {
        return networkId;
    }

    public @CheckForNull String getUserDataId() {
        return userDataId;
    }

    public @CheckForNull Integer getInstanceCap() {
        return instanceCap;
    }

    public @CheckForNull Boolean isFloatingIps() {
        return floatingIps;
    }

    public @CheckForNull String getSecurityGroups() {
        return securityGroups;
    }

    public @CheckForNull String getAvailabilityZone() {
        return availabilityZone;
    }

    public @CheckForNull Integer getStartTimeout() {
        return startTimeout;
    }

    public @CheckForNull String getKeyPairName() {
        return keyPairName;
    }

    public @CheckForNull Integer getNumExecutors() {
        return numExecutors;
    }

    public @CheckForNull String getJvmOptions() {
        return jvmOptions;
    }

    public @CheckForNull String getCredentialsId() {
        return credentialsId;
    }

    public @CheckForNull JCloudsCloud.SlaveType getSlaveType() {
        return slaveType;
    }

    public @CheckForNull Integer getRetentionTime() {
        return retentionTime;
    }

    public SlaveOptions(Builder b) {
        this(
                b.imageId,
                b.hardwareId,
                b.networkId,
                b.userDataId,
                b.instanceCap,
                b.floatingIps,
                b.securityGroups,
                b.availabilityZone,
                b.startTimeout,
                b.keyPairName,
                b.numExecutors,
                b.jvmOptions,
                b.fsRoot,
                b.credentialsId,
                b.slaveType,
                b.retentionTime
        );
    }

    @DataBoundConstructor @Restricted(NoExternalUse.class)
    public SlaveOptions(
            String imageId,
            String hardwareId,
            String networkId,
            String userDataId,
            Integer instanceCap,
            Boolean floatingIps,
            String securityGroups,
            String availabilityZone,
            Integer startTimeout,
            String keyPairName,
            Integer numExecutors,
            String jvmOptions,
            String fsRoot,
            String credentialsId,
            JCloudsCloud.SlaveType slaveType,
            Integer retentionTime
    ) {
        this.imageId = imageId;
        this.hardwareId = hardwareId;
        this.networkId = networkId;
        this.userDataId = userDataId;
        this.instanceCap = instanceCap;
        this.floatingIps = floatingIps;
        this.securityGroups = securityGroups;
        this.availabilityZone = availabilityZone;
        this.startTimeout = startTimeout;
        this.keyPairName = keyPairName;
        this.numExecutors = numExecutors;
        this.jvmOptions = jvmOptions;
        this.fsRoot = fsRoot;
        this.credentialsId = credentialsId;
        this.slaveType = slaveType;
        this.retentionTime = retentionTime;
    }

    /**
     * Derive SlaveOptions taking this instance as baseline and overriding with argument.
     */
    public @Nonnull SlaveOptions override(@Nonnull SlaveOptions o) {
        return new Builder()
                .imageId(_override(this.imageId, o.imageId))
                .hardwareId(_override(this.hardwareId, o.hardwareId))
                .networkId(_override(this.networkId, o.networkId))
                .userDataId(_override(this.userDataId, o.userDataId))
                .instanceCap(_override(this.instanceCap, o.instanceCap)) // TODO: this is not right for instance cap
                .floatingIps(_override(this.floatingIps, o.floatingIps))
                .securityGroups(_override(this.securityGroups, o.securityGroups))
                .availabilityZone(_override(this.availabilityZone, o.availabilityZone))
                .startTimeout(_override(this.startTimeout, o.startTimeout))
                .keyPairName(_override(this.keyPairName, o.keyPairName))
                .numExecutors(_override(this.numExecutors, o.numExecutors))
                .jvmOptions(_override(this.jvmOptions, o.jvmOptions))
                .fsRoot(_override(this.fsRoot, o.fsRoot))
                .credentialsId(_override(this.credentialsId, o.credentialsId))
                .slaveType(_override(this.slaveType, o.slaveType))
                .retentionTime(_override(this.retentionTime, o.retentionTime))
                .build()
        ;
    }

    private @CheckForNull <T> T _override(@CheckForNull T base, @CheckForNull T override) {
        return override == null ? base : override;
    }

    /**
     * Derive new options from current leaving <tt>null</tt> where same as default.
     */
    public @Nonnull SlaveOptions eraseDefaults(@Nonnull SlaveOptions defaults) {
        return new Builder()
                .imageId(_erase(this.imageId, defaults.imageId))
                .hardwareId(_erase(this.hardwareId, defaults.hardwareId))
                .networkId(_erase(this.networkId, defaults.networkId))
                .userDataId(_erase(this.userDataId, defaults.userDataId))
                .instanceCap(_erase(this.instanceCap, defaults.instanceCap)) // TODO: this is not right for instance cap
                .floatingIps(_erase(this.floatingIps, defaults.floatingIps))
                .securityGroups(_erase(this.securityGroups, defaults.securityGroups))
                .availabilityZone(_erase(this.availabilityZone, defaults.availabilityZone))
                .startTimeout(_erase(this.startTimeout, defaults.startTimeout))
                .keyPairName(_erase(this.keyPairName, defaults.keyPairName))
                .numExecutors(_erase(this.numExecutors, defaults.numExecutors))
                .jvmOptions(_erase(this.jvmOptions, defaults.jvmOptions))
                .fsRoot(_erase(this.fsRoot, defaults.fsRoot))
                .credentialsId(_erase(this.credentialsId, defaults.credentialsId))
                .slaveType(_erase(this.slaveType, defaults.slaveType))
                .retentionTime(_erase(this.retentionTime, defaults.retentionTime))
                .build()
        ;
    }

    private @CheckForNull <T> T _erase(@CheckForNull T base, @CheckForNull T def) {
        if (def == null) return base;
        if (def.equals(base)) return null;
        return base;
    }

    @Override
    public String toString() {
        return "SlaveOptions{" +
                "imageId='" + imageId + '\'' +
                ", hardwareId='" + hardwareId + '\'' +
                ", networkId='" + networkId + '\'' +
                ", userDataId='" + userDataId + '\'' +
                ", floatingIps=" + floatingIps +
                ", securityGroups='" + securityGroups + '\'' +
                ", availabilityZone='" + availabilityZone + '\'' +
                ", startTimeout=" + startTimeout +
                ", keyPairName='" + keyPairName + '\'' +
                ", numExecutors=" + numExecutors +
                ", jvmOptions='" + jvmOptions + '\'' +
                ", fsRoot='" + fsRoot + '\'' +
                ", credentialsId='" + credentialsId + '\'' +
                ", slaveType=" + slaveType +
                ", retentionTime=" + retentionTime +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SlaveOptions that = (SlaveOptions) o;

        if (imageId != null ? !imageId.equals(that.imageId) : that.imageId != null) return false;
        if (hardwareId != null ? !hardwareId.equals(that.hardwareId) : that.hardwareId != null) return false;
        if (networkId != null ? !networkId.equals(that.networkId) : that.networkId != null) return false;
        if (userDataId != null ? !userDataId.equals(that.userDataId) : that.userDataId != null) return false;
        if (instanceCap != null ? !instanceCap.equals(that.instanceCap) : that.instanceCap != null) return false;
        if (floatingIps != null ? !floatingIps.equals(that.floatingIps) : that.floatingIps != null) return false;
        if (securityGroups != null ? !securityGroups.equals(that.securityGroups) : that.securityGroups != null) return false;
        if (availabilityZone != null ? !availabilityZone.equals(that.availabilityZone) : that.availabilityZone != null) return false;
        if (startTimeout != null ? !startTimeout.equals(that.startTimeout) : that.startTimeout != null) return false;
        if (keyPairName != null ? !keyPairName.equals(that.keyPairName) : that.keyPairName != null) return false;
        if (numExecutors != null ? !numExecutors.equals(that.numExecutors) : that.numExecutors != null) return false;
        if (jvmOptions != null ? !jvmOptions.equals(that.jvmOptions) : that.jvmOptions != null) return false;
        if (fsRoot != null ? !fsRoot.equals(that.fsRoot) : that.fsRoot != null) return false;
        if (credentialsId != null ? !credentialsId.equals(that.credentialsId) : that.credentialsId != null) return false;
        if (slaveType != that.slaveType) return false;
        return retentionTime != null ? retentionTime.equals(that.retentionTime) : that.retentionTime == null;

    }

    @Override
    public int hashCode() {
        int result = imageId != null ? imageId.hashCode() : 0;
        result = 31 * result + (hardwareId != null ? hardwareId.hashCode() : 0);
        result = 31 * result + (networkId != null ? networkId.hashCode() : 0);
        result = 31 * result + (userDataId != null ? userDataId.hashCode() : 0);
        result = 31 * result + (instanceCap != null ? instanceCap.hashCode() : 0);
        result = 31 * result + (floatingIps != null ? floatingIps.hashCode() : 0);
        result = 31 * result + (securityGroups != null ? securityGroups.hashCode() : 0);
        result = 31 * result + (availabilityZone != null ? availabilityZone.hashCode() : 0);
        result = 31 * result + (startTimeout != null ? startTimeout.hashCode() : 0);
        result = 31 * result + (keyPairName != null ? keyPairName.hashCode() : 0);
        result = 31 * result + (numExecutors != null ? numExecutors.hashCode() : 0);
        result = 31 * result + (jvmOptions != null ? jvmOptions.hashCode() : 0);
        result = 31 * result + (fsRoot != null ? fsRoot.hashCode() : 0);
        result = 31 * result + (credentialsId != null ? credentialsId.hashCode() : 0);
        result = 31 * result + (slaveType != null ? slaveType.hashCode() : 0);
        result = 31 * result + (retentionTime != null ? retentionTime.hashCode() : 0);
        return result;
    }

    public static @Nonnull Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private @CheckForNull String imageId;
        private @CheckForNull String hardwareId;
        private @CheckForNull String networkId;
        private @CheckForNull String userDataId;
        private @CheckForNull Integer instanceCap;
        private @CheckForNull Boolean floatingIps;
        private @CheckForNull String securityGroups;
        private @CheckForNull String availabilityZone;
        private @CheckForNull Integer startTimeout;
        private @CheckForNull String keyPairName;

        private @CheckForNull Integer numExecutors;
        private @CheckForNull String jvmOptions;
        private @CheckForNull String fsRoot;
        private @CheckForNull String credentialsId;

        private @CheckForNull JCloudsCloud.SlaveType slaveType;
        private @CheckForNull Integer retentionTime;

        public Builder() {}

        public @Nonnull SlaveOptions build() {
            return new SlaveOptions(this);
        }

        public @Nonnull Builder imageId(String imageId) {
            this.imageId = Util.fixEmpty(imageId);
            return this;
        }

        public @Nonnull Builder hardwareId(String hardwareId) {
            this.hardwareId = Util.fixEmpty(hardwareId);
            return this;
        }

        public @Nonnull Builder networkId(String networkId) {
            this.networkId = Util.fixEmpty(networkId);
            return this;
        }

        public @Nonnull Builder userDataId(String userDataId) {
            this.userDataId = Util.fixEmpty(userDataId);
            return this;
        }

        public @Nonnull Builder instanceCap(Integer instanceCap) {
            this.instanceCap = instanceCap;
            return this;
        }

        public @Nonnull Builder floatingIps(Boolean floatingIps) {
            this.floatingIps = floatingIps;
            return this;
        }

        public @Nonnull Builder securityGroups(String securityGroups) {
            this.securityGroups = Util.fixEmpty(securityGroups);
            return this;
        }

        public @Nonnull Builder availabilityZone(String availabilityZone) {
            this.availabilityZone = Util.fixEmpty(availabilityZone);
            return this;
        }

        public @Nonnull Builder startTimeout(Integer startTimeout) {
            this.startTimeout = startTimeout;
            return this;
        }

        public @Nonnull Builder keyPairName(String keyPairName) {
            this.keyPairName = Util.fixEmpty(keyPairName);
            return this;
        }

        public @Nonnull Builder numExecutors(Integer numExecutors) {
            this.numExecutors = numExecutors;
            return this;
        }

        public @Nonnull Builder jvmOptions(String jvmOptions) {
            this.jvmOptions = Util.fixEmpty(jvmOptions);
            return this;
        }

        public @Nonnull Builder fsRoot(String fsRoot) {
            this.fsRoot = Util.fixEmpty(fsRoot);
            return this;
        }

        public @Nonnull Builder credentialsId(String credentialsId) {
            this.credentialsId = Util.fixEmpty(credentialsId);
            return this;
        }

        public @Nonnull Builder slaveType(JCloudsCloud.SlaveType slaveType) {
            this.slaveType = slaveType;
            return this;
        }

        public @Nonnull Builder retentionTime(Integer retentionTime) {
            this.retentionTime = retentionTime;
            return this;
        }
    }

    @Override
    public Descriptor getDescriptor() {
        return (Descriptor) Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    @Extension
    public static final class Descriptor extends hudson.model.Descriptor<SlaveOptions> {

        @Override
        public String getDisplayName() {
            return "Slave Options";
        }

        @Restricted(DoNotUse.class)
        public FormValidation doCheckInstanceCap(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }

        @Restricted(DoNotUse.class)
        public FormValidation doCheckStartTimeout(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }

        @Restricted(DoNotUse.class)
        public FormValidation doCheckNumExecutors(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }

        @Restricted(DoNotUse.class)
        public ListBoxModel doFillSlaveTypeItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("Inherited", null);
            items.add("SSH", "SSH");
            items.add("JNLP", "JNLP");

            return items;
        }

        @Restricted(DoNotUse.class)
        public ListBoxModel doFillHardwareIdItems(@QueryParameter String hardwareId,
                                                  @RelativePath("..") @QueryParameter String endPointUrl,
                                                  @RelativePath("..") @QueryParameter String identity,
                                                  @RelativePath("..") @QueryParameter String credential,
                                                  @RelativePath("..") @QueryParameter String zone
        ) {

            ListBoxModel m = new ListBoxModel();
            m.add("None specified", "");

            try {
                final Openstack openstack = JCloudsCloud.getOpenstack(endPointUrl, identity, credential, zone);
                for (Flavor flavor : openstack.getSortedFlavors()) {
                    m.add(String.format("%s (%s)", flavor.getName(), flavor.getId()), flavor.getId());
                }
                return m;
            } catch (AuthenticationException |FormValidation _) {
                // Incorrect credentials - noop
            } catch (Exception ex) {
                // TODO LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            }

            if (Util.fixEmpty(hardwareId) != null) {
                m.add(hardwareId);
            }

            return m;
        }

        @Restricted(DoNotUse.class)
        public ListBoxModel doFillImageIdItems(@QueryParameter String imageId,
                                               @RelativePath("..") @QueryParameter String endPointUrl,
                                               @RelativePath("..") @QueryParameter String identity,
                                               @RelativePath("..") @QueryParameter String credential,
                                               @RelativePath("..") @QueryParameter String zone
        ) {

            ListBoxModel m = new ListBoxModel();
            m.add("None specified", "");

            try {
                final Openstack openstack = JCloudsCloud.getOpenstack(endPointUrl, identity, credential, zone);
                for (Image image : openstack.getSortedImages()) {
                    m.add(String.format("%s (%s)", image.getName(), image.getId()), image.getId());
                }
                return m;
            } catch (AuthenticationException|FormValidation _) {
                // Incorrect credentials - noop
            } catch (Exception ex) {
                // TODO LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            }

            if (Util.fixEmpty(imageId) != null) {
                m.add(imageId);
            }

            return m;
        }

        @Restricted(DoNotUse.class)
        public ListBoxModel doFillNetworkIdItems(@QueryParameter String networkId,
                                                 @RelativePath("..") @QueryParameter String endPointUrl,
                                                 @RelativePath("..") @QueryParameter String identity,
                                                 @RelativePath("..") @QueryParameter String credential,
                                                 @RelativePath("..") @QueryParameter String zone
        ) {

            ListBoxModel m = new ListBoxModel();
            m.add("None specified", "");

            try {
                Openstack openstack = JCloudsCloud.getOpenstack(endPointUrl, identity, credential, zone);
                for (org.openstack4j.model.network.Network network: openstack.getSortedNetworks()) {
                    m.add(String.format("%s (%s)", network.getName(), network.getId()), network.getId());
                }
                return m;
            } catch (AuthenticationException|FormValidation _) {
                // Incorrect credentials - noop
            } catch (Exception ex) {
                // TODO LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            }

            if (Util.fixEmpty(networkId) != null) {
                m.add(networkId);
            }

            return m;
        }

        @Restricted(DoNotUse.class)
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {
            if (!(context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getInstance()).hasPermission(Computer.CONFIGURE)) {
                return new ListBoxModel();
            }
            return new StandardUsernameListBoxModel().withMatching(SSHAuthenticator.matcher(Connection.class),
                    CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, context,
                            ACL.SYSTEM, SSHLauncher.SSH_SCHEME));
        }

        @Restricted(DoNotUse.class)
        public FormValidation doCheckOverrideRetentionTime(@QueryParameter String value) {
            try {
                if (Integer.parseInt(value) == -1) {
                    return FormValidation.ok();
                }
            } catch (NumberFormatException e) {
            }
            return FormValidation.validateNonNegativeInteger(value);
        }

        @Restricted(DoNotUse.class)
        public ListBoxModel doFillUserDataIdItems() {

            ListBoxModel m = new ListBoxModel();
            m.add("None specified", "");

            ConfigProvider provider = getConfigProvider();
            for(Config config : provider.getAllConfigs()) {
                m.add(config.name, config.id);
            }

            return m;
        }

        private ConfigProvider getConfigProvider() {
            ExtensionList<ConfigProvider> providers = ConfigProvider.all();
            return providers.get(UserDataConfig.UserDataConfigProvider.class);
        }

        @Restricted(DoNotUse.class)
        public FormValidation doCheckRetentionTime(@QueryParameter String value) {
            try {
                if (Integer.parseInt(value) == -1)
                    return FormValidation.ok();
            } catch (NumberFormatException e) {
            }
            return FormValidation.validateNonNegativeInteger(value);
        }
    }
}
