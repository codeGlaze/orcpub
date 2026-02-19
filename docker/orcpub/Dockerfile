FROM openjdk:8u242-jre as builder

# Build cache layer
ADD ./lib/ /root/.m2/repository/
WORKDIR /orcpub
COPY project.clj /orcpub/
RUN lein deps

ADD ./ /orcpub
RUN printenv &&\
	lein uberjar

FROM openjdk:8u242-jre as runner

COPY --from=builder /orcpub/target/orcpub.jar /orcpub.jar

ENTRYPOINT ["java", "-jar"]
CMD ["/orcpub.jar"]
