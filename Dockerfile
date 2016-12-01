FROM java:8

RUN apt-get update && apt-get -y install lsof

# Installs Ant
ENV ANT_VERSION 1.9.4
RUN cd && \
    wget -q http://archive.apache.org/dist/ant/binaries/apache-ant-${ANT_VERSION}-bin.tar.gz && \
    tar -xzf apache-ant-${ANT_VERSION}-bin.tar.gz && \
    mv apache-ant-${ANT_VERSION} /opt/ant && \
    rm apache-ant-${ANT_VERSION}-bin.tar.gz
ENV ANT_HOME /opt/ant
ENV PATH ${PATH}:/opt/ant/bin

# Add lucene/solr files
RUN mkdir -p /lucene-solr

WORKDIR /lucene-solr
ADD build.xml build.xml
ADD lucene/ lucene/
ADD solr/ solr/

# Install ivy
RUN ant ivy-bootstrap

RUN ant compile

WORKDIR /lucene-solr/solr
RUN ant create-package

WORKDIR /lucene-solr

RUN unzip /lucene-solr/solr/package/solr-5.3.2-SNAPSHOT.zip -d /opt/

RUN cp -r /opt/solr-5.3.2-SNAPSHOT/server/solr/ /solr-home

CMD /opt/solr-5.3.2-SNAPSHOT/bin/solr start -f -Dsolr.solr.home=/solr-home -p 8983

