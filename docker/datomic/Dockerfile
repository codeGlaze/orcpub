FROM eclipse-temurin:8-jre-alpine

ENV DATOMIC_VERSION 0.9.5703

RUN wget https://github.com/Orcpub/orcpub/raw/refs/heads/develop/lib/datomic-free-0.9.5703.tar.gz -qO /tmp/datomic.tar.gz \
  && cd /tmp \
  && tar -xvzf /tmp/datomic.tar.gz \
  && rm /tmp/datomic.tar.gz \
  && mv datomic-free-0.9.5703 /datomic

WORKDIR /datomic

RUN cp /datomic/config/samples/free-transactor-template.properties transactor.properties

RUN mkdir /data
RUN sed "s/# data-dir=data/data-dir=\/data/" -i transactor.properties

RUN mkdir /log
RUN sed "s/# log-dir=log/log-dir=\/log/" -i transactor.properties

RUN sed "s/host=localhost/host=0.0.0.0/" -i transactor.properties
RUN sed "s/# storage-access=local/storage-access=remote/" -i transactor.properties

ADD deploy/start.sh /datomic/
RUN chmod +x /datomic/start.sh

EXPOSE 4334 4335 4336
CMD ["/datomic/start.sh"]
