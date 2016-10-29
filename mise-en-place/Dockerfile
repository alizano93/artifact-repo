FROM centos:centos7

RUN yum -y install --setopt=tsflags=nodocs epel-release && \
    yum -y update && yum clean all && \
    yum -y install openssl curl which gnupg2 openssh sudo python27 openssh-clients expect

RUN curl -O https://bootstrap.pypa.io/get-pip.py && \
    python2.7 get-pip.py
RUN pip install aws-cloudhsm-cli

ENV HOME=/home/hsmclient
ENV USER=hsmclient

ARG user=hsmclient
ARG group=hsmclient
ARG uid=1000
ARG gid=1000

RUN groupadd -g ${gid} ${group} \
    && useradd -d "$HOME" -u ${uid} -g ${gid} -m -s /bin/bash ${user} \
    && passwd -d ${user}
RUN echo "$USER ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers

COPY luna-packages /usr/src/luna-packages
WORKDIR /usr/src/luna-packages

RUN rpm -i ckdemo-5.4.1-2.x86_64.rpm cklog-5.4.1-2.x86_64.rpm \
    ckSample-5.4.1-2.x86_64.rpm configurator-5.4.1-2.x86_64.rpm \
    htl_client-5.4.1-2.x86_64.rpm libcryptoki-5.4.1-2.x86_64.rpm \
    libshim-5.4.1-2.x86_64.rpm lunacm-5.4.1-2.x86_64.rpm \
    lunacmu-5.4.1-2.x86_64.rpm lunadiag-5.4.1-2.x86_64.rpm \
    lunadpc-1.1.0-4.x86_64.rpm multitoken-5.4.1-2.x86_64.rpm \
    salogin-5.4.1-2.x86_64.rpm uhd-5.4.1-2.src.rpm \
    vkd-5.4.1-2.src.rpm vtl-5.4.1-2.x86_64.rpm

RUN chown ${user} /etc/Chrystoki.conf \
    && chmod +w /etc/Chrystoki.conf

RUN chown ${user} -R /usr/safenet/lunaclient/

RUN rm -rf /usr/src/luna-packages

USER hsmclient

ENV RUBY_VERSION 2.3.1

# install RVM, Ruby, and Bundler
RUN gpg --keyserver hkp://keys.gnupg.net --recv-keys D39DC0E3
RUN curl -L https://get.rvm.io | bash -s stable
USER root
RUN /bin/bash -l -c "rvm requirements"
USER hsmclient
RUN echo "source ~/.profile" >> $HOME/.bash_profile
RUN /bin/bash -l -c "rvm install ruby-$RUBY_VERSION"
RUN /bin/bash -l -c "gem install bundle"

USER root
COPY hsmclient-provisioner $HOME/hsmclient-provisioner
COPY entrypoint.sh $HOME
COPY load-agent.exp $HOME
COPY jdk1.8.0_101 $HOME/.jdk1.8.0_101
COPY hydrate-security-module $HOME/hydrate-security-module
RUN chown -R ${user}:${user} $HOME
USER hsmclient
WORKDIR $HOME/hsmclient-provisioner
RUN /bin/bash -l -c "bundle install"

WORKDIR $HOME
ENV PATH /usr/safenet/lunaclient/bin:/home/hsmclient/.jdk1.8.0_101/bin:$PATH
ENV JAVA_HOME /home/hsmclient/.jdk1.8.0_101

ARG bucket=cloudhsm-ssh-keypair
ARG key=hsm-client-key
ARG passfile=passphrase.yml
ARG ip_address=10.0.201.209

#ENTRYPOINT ["hsmclient-setup.rb" -d "$HOME" -b ${bucket} -k ${key} -u ${user} -i ${ip_address}]
