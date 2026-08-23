FROM maven:3.9.16-eclipse-temurin-21 AS format

RUN apt-get update \
    && apt-get install -y --no-install-recommends git rsync \
    && rm -rf /var/lib/apt/lists/*

COPY . /snapshot
WORKDIR /work
RUN git clone --branch main --single-branch https://github.com/lynelk/CPay.git repo \
    && rsync -a --delete --exclude='.git' /snapshot/ /work/repo/ \
    && cd /work/repo \
    && git config user.email 'verification@cito.local' \
    && git config user.name 'Cito Release Verification' \
    && git add -A \
    && git commit -m 'PR 67 formatting snapshot'

WORKDIR /work/repo/InitializrSpringbootProjectFresh
RUN mvn -B spotless:apply --no-transfer-progress

WORKDIR /work/repo
RUN git diff --name-only -- InitializrSpringbootProjectFresh/src/ \
      | grep '\.java$' > /work/formatted-files.txt \
    && test -s /work/formatted-files.txt \
    && tar -czf /work/formatted-java.tar.gz -T /work/formatted-files.txt

FROM nginx:1.27-alpine
LABEL purpose="pr67-spotless-artifact"
COPY --from=format /work/formatted-java.tar.gz /usr/share/nginx/html/formatted-java.tar.gz
COPY --from=format /work/formatted-files.txt /usr/share/nginx/html/formatted-files.txt
RUN printf 'PR 67 Spotless formatting artifact ready\n' > /usr/share/nginx/html/index.html
EXPOSE 80
CMD ["sh", "-c", "echo FMT_TAR_BEGIN; base64 /usr/share/nginx/html/formatted-java.tar.gz | tr -d '\\n' | fold -w 3000 | sed 's/^/FMT_TAR_CHUNK|/'; echo FMT_TAR_END; exec nginx -g 'daemon off;'"]
