# Datomic Pro transactor — dev storage protocol (datomic:dev://)
FROM eclipse-temurin:21-jre-alpine-3.22 as base

ENV DATOMIC_VERSION=1.0.7482

RUN apk add --no-cache curl unzip

RUN curl --fail --location --silent --show-error \
      -o /tmp/datomic-pro.zip \
      "https://datomic-pro-downloads.s3.amazonaws.com/${DATOMIC_VERSION}/datomic-pro-${DATOMIC_VERSION}.zip" \
 && unzip -q /tmp/datomic-pro.zip -d /tmp \
 && mv /tmp/datomic-pro-${DATOMIC_VERSION} /datomic \
 && rm /tmp/datomic-pro.zip

WORKDIR /datomic

# Use dev-transactor-template (dev storage protocol for datomic:dev://)
RUN cp /datomic/config/samples/dev-transactor-template.properties transactor.properties

RUN mkdir -p /data /log
RUN sed "s/# data-dir=data/data-dir=\/data/" -i transactor.properties
RUN sed "s/# log-dir=log/log-dir=\/log/" -i transactor.properties

RUN sed "s/host=localhost/host=0.0.0.0/" -i transactor.properties
RUN sed "s/# storage-access=local/storage-access=remote/" -i transactor.properties

ADD deploy/start.sh /datomic/
RUN chmod +x /datomic/start.sh

EXPOSE 4334 4335 4336
CMD ["/datomic/start.sh"]
