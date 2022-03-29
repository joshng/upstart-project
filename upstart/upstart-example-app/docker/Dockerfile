FROM ${docker.base.image}

ENTRYPOINT ["java", "-jar", "${project.build.finalName}.jar"]
WORKDIR /${project.artifactId}

COPY lib /${project.artifactId}/lib
COPY ${project.build.finalName}.jar /${project.artifactId}/

