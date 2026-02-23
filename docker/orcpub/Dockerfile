FROM clojure:temurin-21-lein-alpine as builder

RUN apk add --no-cache curl unzip

# Vendor jars (pdfbox snapshots) → Maven local repo
ADD ./lib/ /root/.m2/repository/

# Datomic Pro peer jar isn't in public Maven repos.
# Download the distribution zip and install the peer jar + pom manually
# (bin/maven-install calls mvn, which isn't in this image).
ENV DATOMIC_VERSION=1.0.7482
RUN curl --fail --location --silent --show-error \
      -o /tmp/datomic-pro.zip \
      "https://datomic-pro-downloads.s3.amazonaws.com/${DATOMIC_VERSION}/datomic-pro-${DATOMIC_VERSION}.zip" \
 && unzip -q /tmp/datomic-pro.zip -d /tmp \
 && mkdir -p /root/.m2/repository/com/datomic/peer/${DATOMIC_VERSION} \
 && cp /tmp/datomic-pro-${DATOMIC_VERSION}/peer-${DATOMIC_VERSION}.jar \
       /root/.m2/repository/com/datomic/peer/${DATOMIC_VERSION}/ \
 && cp /tmp/datomic-pro-${DATOMIC_VERSION}/pom.xml \
       /root/.m2/repository/com/datomic/peer/${DATOMIC_VERSION}/peer-${DATOMIC_VERSION}.pom \
 && rm -rf /tmp/datomic-pro*

WORKDIR /orcpub
COPY project.clj /orcpub/
RUN lein deps

ADD ./ /orcpub
RUN printenv &&\
	lein uberjar

# Alpine runner — BusyBox wget handles healthchecks (use -q --spider, not GNU flags)
FROM eclipse-temurin:21-jre-alpine-3.22 as runner

# PDFBox requires fontconfig, fonts, and lcms2 for PDF character sheet generation
RUN apk add --no-cache fontconfig ttf-dejavu freetype lcms2

COPY --from=builder /orcpub/target/orcpub.jar /orcpub.jar

ENTRYPOINT ["java", "-jar"]
CMD ["/orcpub.jar"]
