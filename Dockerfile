FROM alpine

COPY /build/libs/process-1.0.0-SNAPSHOT.jar process.jar
COPY /locale/ /locale/